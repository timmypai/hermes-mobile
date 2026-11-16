package com.m57.hermescontrol.ui.voice

import android.content.Context
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.local.DataScope
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.repository.KanbanRepository
import com.m57.hermescontrol.data.repository.KanbanRepositoryImpl
import com.m57.hermescontrol.data.ws.ProjectsRepository
import com.m57.hermescontrol.data.ws.ProjectsResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Authentication identity plus an optional focus project (the project of the chat that opened voice).
 * The focus is a suggestion for the model and the confirmation card; Talk stays unbound.
 */
data class TalkLaunch(
    val scope: DataScope,
    val focusProjectId: String? = null,
)

/** Call audio output. MUTED silences assistant playout only; the microphone and the call continue. */
enum class TalkOutput { SPEAKER, EARPIECE, MUTED }

data class TalkCallState(
    val phase: String = "idle",
    val muted: Boolean = false,
    /** Loudspeaker by default. Kept across reconnects within one dialog. */
    val output: TalkOutput = TalkOutput.SPEAKER,
    /** The system routes call audio through a Bluetooth headset. */
    val bluetooth: Boolean = false,
    /** The chat's project, resolved after the catalog loads. Null when none or not yet known. */
    val focusProject: TalkProject? = null,
    val level: Float = 0f,
    val userText: String = "",
    val assistantText: String = "",
    val error: String? = null,
    val jobs: List<JsonObject> = emptyList(),
    val receipt: String = "",
    val handoffs: List<TalkHandoff> = emptyList(),
    val tool: TalkToolActivity? = null,
)

/** Foreground audio only. The existing global Talk executor and Kanban own all durable work. */
class TalkCallController(
    context: Context,
    val launch: TalkLaunch,
    private val projects: ProjectsRepository = ProjectsRepository(),
    private val kanban: KanbanRepository = KanbanRepositoryImpl(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val lifetime = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(TalkCallState())
    val state: StateFlow<TalkCallState> = mutableState.asStateFlow()
    private var call: Job? = null
    private var peer: NativeRealtimePeer? = null
    private var commands: Channel<suspend () -> Unit>? = null
    private var api: TalkGatewayApi? = null
    private var adapter: TalkRealtimeAdapter? = null
    private var catalog = TalkProjectCatalog()

    // Read-only app data, loaded when the dialog opens. Failure only narrows the card.
    private val catalogLoad =
        lifetime.async {
            loadCatalog().also { loaded -> mutableState.update { it.copy(focusProject = loaded.find(loaded.focusId)) } }
        }
    private var epoch = 0L
    private var disposed = false

    fun start() {
        if (disposed || call?.isActive == true) return
        val current = ++epoch
        mutableState.update {
            it.copy(phase = "connecting", error = null, muted = false, level = 0f, handoffs = emptyList(), tool = null)
        }
        call =
            lifetime.launch {
                val queue = Channel<suspend () -> Unit>(64)
                commands = queue
                var voice: NativeRealtimePeer? = null
                var realtime: TalkRealtimeAdapter? = null
                val fence =
                    launch {
                        AuthManager.dataScopeFlow.collect {
                            if (current == epoch && AuthManager.currentDataScope() != launch.scope) {
                                fail("Connection or profile changed; reopen Hermes Talk")
                            }
                        }
                    }
                try {
                    val gateway = TalkGatewayApi(launch.scope)
                    api = gateway
                    TalkProtocol.validateStatus(gateway.get("/status"))
                    val session = gateway.post("/session", JsonObject(emptyMap()))
                    TalkProtocol.validateSession(session)
                    val media =
                        NativeRealtimePeer(
                            appContext,
                            onEvent = { raw ->
                                if (current == epoch &&
                                    !queue
                                        .trySend {
                                            adapter?.handle(Json.parseToJsonElement(raw) as JsonObject)
                                        }.isSuccess
                                ) {
                                    lifetime.launch {
                                        if (current == epoch) fail("Voice event queue overflow; reconnect Hermes Talk")
                                    }
                                }
                            },
                            onState = { value ->
                                lifetime.launch {
                                    if (current == epoch && call?.isActive == true) {
                                        if (value.startsWith("error:")) {
                                            fail("Voice media disconnected; reconnect Hermes Talk")
                                        } else {
                                            mutableState.update {
                                                it.copy(phase = if (value == "connected") "listening" else value)
                                            }
                                        }
                                    }
                                }
                            },
                            onLevel = { level ->
                                if (current == epoch && call?.isActive == true) {
                                    mutableState.update { it.copy(level = if (it.muted) 0f else level) }
                                }
                            },
                            onBluetooth = { value ->
                                lifetime.launch {
                                    if (current == epoch) mutableState.update { it.copy(bluetooth = value) }
                                }
                            },
                        )
                    voice = media
                    media.output(state.value.output)
                    peer = media
                    realtime =
                        TalkRealtimeAdapter(
                            post = gateway::post,
                            send = { event -> current == epoch && media.send(event.toString()) },
                            onTranscript = { role, text, final ->
                                mutableState.update {
                                    if (role == "user") {
                                        it.copy(userText = if (final) text else it.userText + text)
                                    } else {
                                        it.copy(assistantText = if (final) text else it.assistantText + text)
                                    }
                                }
                            },
                            onState = { phase -> mutableState.update { it.copy(phase = phase) } },
                            onError = { message -> mutableState.update { it.copy(error = message) } },
                            onTool = { activity -> mutableState.update { it.copy(tool = activity) } },
                            onDelegation = { id, arguments ->
                                val held = TalkHandoff.hold(id, arguments, catalog, clock())
                                mutableState.update { it.copy(handoffs = it.handoffs + held) }
                            },
                        )
                    adapter = realtime
                    catalog = withTimeoutOrNull(CATALOG_WAIT_MS) { catalogLoad.await() } ?: TalkProjectCatalog()
                    realtime.setContext(catalog.instructions())
                    withTimeout(35_000) {
                        media.connect { sdp ->
                            negotiateRealtime(session.text("offerUrl"), session.text("clientSecret"), sdp)
                        }
                    }
                    val refresh =
                        launch {
                            while (true) {
                                queue.send {
                                    refreshRuns()
                                    expireHandoffs()
                                }
                                delay(5_000)
                            }
                        }
                    try {
                        // All adapter entries, including suspended tool requests, share this actor.
                        for (command in queue) {
                            gateway.checkScope()
                            command()
                        }
                    } finally {
                        refresh.cancel()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (current == epoch) {
                        mutableState.update { it.copy(phase = "error", error = safeMessage(e), level = 0f) }
                    }
                } finally {
                    fence.cancel()
                    queue.close()
                    voice?.close()
                    // Global calls have no server binding to close. Never cancel durable work here.
                    withContext(NonCancellable) { realtime?.close() }
                    if (commands === queue) {
                        peer = null
                        adapter = null
                        api = null
                        commands = null
                    }
                }
            }
    }

    fun mute() {
        val value = !state.value.muted
        peer?.mute(value)
        mutableState.update { it.copy(muted = value, level = if (value) 0f else it.level) }
    }

    /** Cycles speaker -> earpiece -> muted playout -> speaker. */
    fun cycleOutput() {
        val value = TalkOutput.entries[(state.value.output.ordinal + 1) % TalkOutput.entries.size]
        peer?.output(value)
        mutableState.update { it.copy(output = value) }
    }

    fun interrupt() =
        enqueue {
            adapter?.interrupt()
            peer?.interrupt()
        }

    fun reconnect() {
        if (disposed) return
        val previous = call
        end()
        lifetime.launch {
            previous?.join()
            start()
        }
    }

    fun end() {
        ++epoch
        peer?.close()
        call?.cancel()
        mutableState.update { it.copy(phase = "ended", level = 0f, handoffs = emptyList()) }
    }

    fun dispose() {
        disposed = true
        end()
        lifetime.cancel()
    }

    /** Called only after the dialog confirms this explicit work action. */
    fun stopWork(job: JsonObject) =
        enqueue {
            refreshRuns()
            val current = currentRun(job)
            if (current.text("status") != "running") {
                throw TalkSetupException("This work is no longer running")
            }
            val receipt =
                checkNotNull(api).post(
                    "/tool",
                    buildJsonObject {
                        put("name", "stop_work")
                        put(
                            "arguments",
                            buildJsonObject {
                                put("target", current.text("runId"))
                                put("reason", "User confirmed Stop Work in Hermes Talk.")
                            },
                        )
                    },
                )
            mutableState.update { it.copy(receipt = receipt.text("output")) }
            refreshRuns()
        }

    fun showResult(job: JsonObject) =
        enqueue {
            refreshRuns()
            val current = currentRun(job)
            mutableState.update { it.copy(receipt = current.text("output")) }
        }

    /**
     * Operator chose the Orchestrator. Per devflow, the Hermes default bot owns intake: one delegate_task asks it
     * to run `devflow-intake`, which creates the `[EPIC]` card assigned to the orchestrator. This app writes no Kanban.
     */
    fun handToOrchestrator(
        handoff: TalkHandoff,
        board: String,
    ) {
        val held = take(handoff) ?: return
        enqueue {
            val project = held.project
            val arguments = held.orchestratorArguments(board)
            if (project == null || arguments == null) {
                announce(TalkToolActivity(TalkToolStatus.DECLINED, DELEGATE), CANCELLED_NOTICE)
                throw TalkSetupException("The Orchestrator needs a project with a bound board; nothing was sent")
            }
            val started =
                delegate(
                    arguments,
                    accepted = TalkToolStatus.INTAKE_STARTED,
                    notice =
                        "The operator confirmed on screen and Talk accepted the work: Hermes is running " +
                            "devflow-intake to create an [EPIC] card for the orchestrator on board $board " +
                            "(project ${project.name}). No card exists yet. Only when the run reports a card id, " +
                            "say the orchestrator has taken over; if it returns questions, ask them and delegate " +
                            "again with the answers.",
                )
            val run =
                started.runId
                    .takeIf { it.isNotEmpty() }
                    ?.let { " #$it" }
                    .orEmpty()
            mutableState.update { it.copy(receipt = "devflow-intake$run → orchestrator · $board · ${project.name}") }
        }
    }

    /** Operator chose an immediate Talk background run: the held arguments are posted once, unchanged. */
    fun runInTalk(handoff: TalkHandoff) {
        val held = take(handoff) ?: return
        enqueue {
            delegate(
                held.arguments,
                accepted = TalkToolStatus.TALK_ACCEPTED,
                notice = "The operator confirmed an immediate Talk background run on screen.",
            )
        }
    }

    /**
     * Posts one delegate_task, never retried. Only a WORK_STARTED receipt counts as started; Talk's plain-text
     * refusals (admission, missing lane) are reported as not started, never as finished work.
     */
    private suspend fun delegate(
        arguments: JsonObject,
        accepted: TalkToolStatus,
        notice: String,
    ): DelegateOutcome.Started {
        val reply =
            try {
                checkNotNull(api).post(
                    "/tool",
                    buildJsonObject {
                        put("name", DELEGATE)
                        put("arguments", arguments)
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        when (val outcome = DelegateOutcome.of(reply)) {
            DelegateOutcome.Uncertain -> {
                announce(
                    TalkToolActivity(TalkToolStatus.UNCERTAIN, DELEGATE),
                    "The operator confirmed a handoff, but its outcome is uncertain. It will not be retried. " +
                        "Do not claim that work started or completed.",
                )
                throw TalkSetupException("Handoff outcome is uncertain; not retried")
            }

            is DelegateOutcome.Refused -> {
                announce(
                    TalkToolActivity(TalkToolStatus.DECLINED, DELEGATE),
                    "The operator confirmed a handoff, but Talk refused it, so nothing started or was created. " +
                        "Tell the user why and ask how to proceed. Talk said: ${outcome.text.ifEmpty { "(nothing)" }}",
                )
                throw TalkSetupException("Talk did not start the work; nothing was created")
            }

            is DelegateOutcome.Started -> {
                announce(
                    TalkToolActivity(accepted, DELEGATE, outcome.runId),
                    "$notice Talk tool output: ${outcome.text}",
                    watchOutput = outcome.text,
                )
                return outcome
            }
        }
    }

    /** Nothing is posted. [wrongProject] asks the model to re-identify the project first. */
    fun decline(
        handoff: TalkHandoff,
        wrongProject: Boolean,
    ) {
        val held = take(handoff) ?: return
        enqueue {
            announce(
                TalkToolActivity(TalkToolStatus.DECLINED, DELEGATE, held.callId),
                if (wrongProject) WRONG_PROJECT_NOTICE else CANCELLED_NOTICE,
            )
        }
    }

    /** Removes the handoff before any effect, so a repeated tap can never act twice. */
    private fun take(handoff: TalkHandoff): TalkHandoff? {
        val held = state.value.handoffs.firstOrNull { it.callId == handoff.callId } ?: return null
        mutableState.update { current ->
            current.copy(handoffs = current.handoffs.filterNot { it.callId == held.callId })
        }
        if (clock() - held.heldAtMillis > HANDOFF_TTL_MS) {
            enqueue { announce(TalkToolActivity(TalkToolStatus.DECLINED, DELEGATE), EXPIRED_NOTICE) }
            mutableState.update { it.copy(error = "This handoff expired; nothing was started") }
            return null
        }
        return held
    }

    private suspend fun expireHandoffs() {
        val now = clock()
        val expired = state.value.handoffs.filter { now - it.heldAtMillis > HANDOFF_TTL_MS }
        if (expired.isEmpty()) return
        mutableState.update { current -> current.copy(handoffs = current.handoffs - expired.toSet()) }
        expired.forEach { _ -> announce(TalkToolActivity(TalkToolStatus.DECLINED, DELEGATE), EXPIRED_NOTICE) }
    }

    private suspend fun announce(
        activity: TalkToolActivity,
        notice: String,
        watchOutput: String? = null,
    ) {
        mutableState.update { it.copy(tool = activity) }
        adapter?.notify(notice, watchOutput)
    }

    private suspend fun loadCatalog(): TalkProjectCatalog =
        try {
            coroutineScope {
                val listed = async { projects.list() }
                val boards = async { kanban.getBoards() }
                TalkProjectCatalog.from(
                    projects = (listed.await() as? ProjectsResult.Ok)?.value.orEmpty(),
                    boards = (boards.await() as? NetworkResult.Success)?.data?.boards.orEmpty(),
                    focusId = launch.focusProjectId,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            TalkProjectCatalog()
        }

    private fun currentRun(job: JsonObject): JsonObject {
        val id = job.text("runId")
        return state.value.jobs.singleOrNull { id.isNotBlank() && it.text("runId") == id }
            ?: throw TalkSetupException("This work is no longer available on the server")
    }

    /** Polling only: a failed snapshot keeps the last one; it must not end the voice call. */
    private suspend fun refreshRuns() {
        val runs =
            try {
                checkNotNull(api).get("/runs").rows("runs")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return
            }
        mutableState.update { it.copy(jobs = runs) }
        adapter?.updateRuns(runs)
    }

    private fun enqueue(command: suspend () -> Unit) {
        val accepted =
            commands
                ?.trySend {
                    try {
                        command()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        mutableState.update { it.copy(error = safeMessage(e)) }
                    }
                }?.isSuccess == true
        if (!accepted) {
            mutableState.update { it.copy(error = "Voice is not connected; reconnect Hermes Talk") }
        }
    }

    private fun fail(message: String) {
        end()
        mutableState.update { it.copy(phase = "error", error = message) }
    }

    private fun safeMessage(error: Exception): String =
        when (error) {
            is TalkSetupException -> error.message ?: "Voice setup failed"
            is java.io.IOException -> error.message ?: "Voice connection failed"
            else -> "Voice authorization or protocol validation failed; check Talk configuration and reconnect"
        }

    private companion object {
        const val DELEGATE = "delegate_task"
        const val CATALOG_WAIT_MS = 4_000L
        const val HANDOFF_TTL_MS = 600_000L
        const val CANCELLED_NOTICE =
            "The operator cancelled this handoff on screen. Nothing was started. Acknowledge briefly."
        const val WRONG_PROJECT_NOTICE =
            "The operator says the project for this handoff is wrong. Nothing was started. Ask which project, " +
                "then call delegate_task again with a corrected [project:<id>] brief."
        const val EXPIRED_NOTICE =
            "A held handoff expired after ten minutes without confirmation. Nothing was started. Mention it briefly."
    }
}
