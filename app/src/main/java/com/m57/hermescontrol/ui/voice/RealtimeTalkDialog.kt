package com.m57.hermescontrol.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.NavIcon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import com.m57.hermescontrol.ui.common.ExpressiveButton as Button
import com.m57.hermescontrol.ui.common.ExpressiveIconButton as IconButton
import com.m57.hermescontrol.ui.common.ExpressiveTextButton as TextButton

/** A modal composition: chat, its draft, and pending attachments remain mounted. */
@Composable
fun RealtimeTalkDialog(
    launch: TalkLaunch,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember(launch) { TalkCallController(context.applicationContext, launch) }
    val state by controller.state.collectAsStateWithLifecycle()
    var permissionDenied by remember(launch) { mutableStateOf(false) }
    var backgroundEnded by remember(launch) { mutableStateOf(false) }
    var pendingStop by remember(launch) { mutableStateOf<JsonObject?>(null) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionDenied = !granted
            if (granted && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                backgroundEnded = false
                controller.start()
            }
        }

    fun connect(reconnect: Boolean) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            permissionDenied = false
            backgroundEnded = false
            if (reconnect) controller.reconnect() else controller.start()
        }
    }
    DisposableEffect(controller, owner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    controller.end()
                    backgroundEnded = true
                }
            }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            controller.dispose()
        }
    }
    val close = {
        controller.end()
        onDismiss()
    }
    // The call uses the chat's project; there is no picker. Connect as soon as the dialog opens.
    LaunchedEffect(controller) { connect(false) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The dialog has its own window: keep that window's screen on for the whole call.
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
        RealtimeTalkContent(
            state = state,
            permissionDenied = permissionDenied,
            backgroundEnded = backgroundEnded,
            onClose = close,
            onPermission = { connect(false) },
            onReconnect = { connect(true) },
            onMute = controller::mute,
            onOutput = controller::cycleOutput,
            onHangUp = controller::end,
            onInterrupt = controller::interrupt,
            onStop = { pendingStop = it },
            onResult = controller::showResult,
        )
        pendingStop?.let { job ->
            AlertDialog(
                onDismissRequest = { pendingStop = null },
                icon = { Icon(Icons.Filled.StopCircle, contentDescription = null) },
                title = { Text(stringResource(R.string.realtime_stop_work)) },
                text = { Text(stringResource(R.string.realtime_stop_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingStop = null
                            controller.stopWork(job)
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.realtime_stop_work))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingStop = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.realtime_keep_work))
                    }
                },
            )
        }
        state.handoffs.firstOrNull()?.let { handoff ->
            // No dismiss by back or outside tap: the operator must choose, and Cancel is always available.
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            ) {
                TalkHandoffCard(
                    handoff = handoff,
                    onKanban = { board -> controller.handToKanban(handoff, board) },
                    onTalk = { controller.runInTalk(handoff) },
                    onWrongProject = { controller.decline(handoff, wrongProject = true) },
                    onCancel = { controller.decline(handoff, wrongProject = false) },
                )
            }
        }
    }
}

@Composable
internal fun RealtimeTalkContent(
    state: TalkCallState,
    permissionDenied: Boolean,
    backgroundEnded: Boolean,
    onClose: () -> Unit,
    onPermission: () -> Unit,
    onReconnect: () -> Unit,
    onMute: () -> Unit,
    onOutput: () -> Unit,
    onHangUp: () -> Unit,
    onInterrupt: () -> Unit,
    onStop: (JsonObject) -> Unit,
    onResult: (JsonObject) -> Unit,
) {
    var activityVisible by remember { mutableStateOf(false) }
    val canControl =
        !permissionDenied && !backgroundEnded &&
            when (state.phase) {
                "listening", "hearing", "thinking", "speaking", "executing", "connected" -> true
                else -> false
            }
    val canInterrupt = canControl && state.phase in setOf("speaking", "thinking", "executing")
    val link = talkLink(state.phase, permissionDenied, backgroundEnded)
    BackHandler(enabled = activityVisible) { activityVisible = false }
    HermesScaffold(
        modifier = Modifier.fillMaxSize(),
        title = { TalkProjectTitle(state.focusProject, connecting = state.phase == "connecting") },
        navigationIcon = NavIcon.Back { if (activityVisible) activityVisible = false else onClose() },
        drawerGesturesEnabled = false,
        showConnectionBanner = false,
        pinTopBar = true,
        actions = {
            TalkTopActions(
                link = link,
                activityVisible = activityVisible,
                onActivity = { activityVisible = !activityVisible },
                onReconnect = onReconnect,
            )
        },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (activityVisible) {
                    TalkActivity(
                        userText = state.userText,
                        assistantText = state.assistantText,
                        jobs = state.jobs,
                        receipt = state.receipt,
                        tool = state.tool,
                        waiting = state.handoffs.size,
                        error = state.error,
                        permissionDenied = permissionDenied,
                        backgroundEnded = backgroundEnded,
                        canInterrupt = canInterrupt,
                        onPermission = onPermission,
                        onInterrupt = onInterrupt,
                        onStop = onStop,
                        onResult = onResult,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    TalkVoiceStage(
                        state = state,
                        permissionDenied = permissionDenied,
                        backgroundEnded = backgroundEnded,
                        canInterrupt = canInterrupt,
                        onPermission = onPermission,
                        onInterrupt = onInterrupt,
                        onActivity = { activityVisible = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                TalkControls(
                    output = state.output,
                    muted = state.muted,
                    bluetooth = state.bluetooth,
                    inCall = link != TalkLink.OFFLINE,
                    canControl = canControl,
                    onOutput = onOutput,
                    onMute = onMute,
                    onCall = onReconnect,
                    onHangUp = onHangUp,
                )
            }
        }
    }
}

/** The chat's project, so the operator always sees which project a handoff will default to. */
@Composable
private fun TalkProjectTitle(
    project: TalkProject?,
    connecting: Boolean,
) {
    if (project == null && connecting) {
        Text(stringResource(R.string.realtime_title))
        return
    }
    val name = project?.name ?: stringResource(R.string.realtime_no_focus)
    val description = stringResource(R.string.realtime_project, name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description }.testTag("voice_project"),
    ) {
        Icon(
            if (project != null) Icons.Filled.Folder else Icons.Filled.FolderOff,
            contentDescription = null,
            tint =
                if (project !=
                    null
                ) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.size(22.dp),
        )
        Text(
            name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp),
            color =
                if (project !=
                    null
                ) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TalkVoiceStage(
    state: TalkCallState,
    permissionDenied: Boolean,
    backgroundEnded: Boolean,
    canInterrupt: Boolean,
    onPermission: () -> Unit,
    onInterrupt: () -> Unit,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notice = permissionDenied || backgroundEnded || state.error != null
    val running = remember(state.jobs) { state.jobs.count { it.text("status") == "running" } }
    val latest = remember(state.assistantText, state.userText) { state.assistantText.ifBlank { state.userText } }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(
                    rememberScrollState(),
                ).heightIn(min = maxHeight)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            TalkNotice(
                error = state.error,
                permissionDenied = permissionDenied,
                backgroundEnded = backgroundEnded,
                onPermission = onPermission,
            )
            TalkLogo(if (notice) 88.dp else 136.dp)
            TalkVoiceVisual(
                phase = if (permissionDenied || backgroundEnded) "ended" else state.phase,
                muted = state.muted || permissionDenied || backgroundEnded,
                level = state.level,
                interactive = canInterrupt,
                interruptLabel = stringResource(R.string.realtime_interrupt),
                onInterrupt = onInterrupt,
                modifier = Modifier.fillMaxWidth().height(if (notice) 72.dp else 120.dp).testTag("voice_interrupt"),
            )
            TalkStatus(state.phase, state.muted, permissionDenied, backgroundEnded)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TalkToolLine(state.tool, state.handoffs.size)
                if (running > 0) {
                    TalkChip(
                        icon = Icons.Filled.Work,
                        text = pluralStringResource(R.plurals.realtime_running_work, running, running),
                        onClick = onActivity,
                    )
                }
            }
            if (latest.isNotBlank()) {
                Text(
                    latest,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button, onClick = onActivity)
                            .padding(horizontal = 12.dp),
                )
            }
        }
    }
}

/** The official Hermes Agent mark (NousResearch/hermes-agent desktop icon), not the Mobile launcher icon. */
@Composable
internal fun TalkLogo(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painterResource(R.drawable.hermes_agent_logo),
        contentDescription = null,
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

private enum class TalkMotion { STILL, PULSE, SPIN }

private enum class TalkTone { NEUTRAL, ACTIVE, WARNING, ERROR }

/** What the assistant is doing now, as an animated icon and a word. */
@Composable
private fun TalkStatus(
    phase: String,
    muted: Boolean,
    permissionDenied: Boolean,
    backgroundEnded: Boolean,
) {
    data class Look(
        val icon: ImageVector,
        val label: Int,
        val motion: TalkMotion,
        val tone: TalkTone,
    )
    val look =
        when {
            permissionDenied -> {
                Look(
                    Icons.Filled.MicOff,
                    R.string.realtime_state_permission,
                    TalkMotion.STILL,
                    TalkTone.WARNING,
                )
            }

            backgroundEnded || phase == "ended" -> {
                Look(
                    Icons.Filled.CallEnd,
                    R.string.realtime_state_ended,
                    TalkMotion.STILL,
                    TalkTone.NEUTRAL,
                )
            }

            phase == "error" -> {
                Look(
                    Icons.Filled.ErrorOutline,
                    R.string.realtime_state_error,
                    TalkMotion.STILL,
                    TalkTone.ERROR,
                )
            }

            phase == "idle" -> {
                Look(
                    Icons.Filled.Refresh,
                    R.string.realtime_state_ready,
                    TalkMotion.STILL,
                    TalkTone.NEUTRAL,
                )
            }

            phase == "connecting" -> {
                Look(
                    Icons.Filled.Sync,
                    R.string.realtime_state_connecting,
                    TalkMotion.SPIN,
                    TalkTone.ACTIVE,
                )
            }

            phase == "speaking" -> {
                Look(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    R.string.realtime_state_speaking,
                    TalkMotion.PULSE,
                    TalkTone.ACTIVE,
                )
            }

            phase == "executing" -> {
                Look(
                    Icons.Filled.Autorenew,
                    R.string.realtime_state_working,
                    TalkMotion.SPIN,
                    TalkTone.ACTIVE,
                )
            }

            phase == "thinking" -> {
                Look(
                    Icons.Filled.Psychology,
                    R.string.realtime_state_thinking,
                    TalkMotion.PULSE,
                    TalkTone.ACTIVE,
                )
            }

            muted -> {
                Look(Icons.Filled.MicOff, R.string.realtime_state_muted, TalkMotion.STILL, TalkTone.WARNING)
            }

            phase == "hearing" -> {
                Look(
                    Icons.Filled.Mic,
                    R.string.realtime_state_listening,
                    TalkMotion.PULSE,
                    TalkTone.ACTIVE,
                )
            }

            else -> {
                Look(Icons.Filled.Mic, R.string.realtime_state_idle, TalkMotion.STILL, TalkTone.NEUTRAL)
            }
        }
    val colors = MaterialTheme.colorScheme
    val status = LocalHermesStatusColors.current
    val tint =
        when (look.tone) {
            TalkTone.ACTIVE -> colors.primary
            TalkTone.WARNING -> status.warning
            TalkTone.ERROR -> status.error
            TalkTone.NEUTRAL -> colors.onSurfaceVariant
        }
    val label = stringResource(look.label)
    Surface(
        shape = CircleShape,
        color = colors.surfaceContainerHigh,
        modifier =
            Modifier
                .heightIn(min = 44.dp)
                .animateContentSize()
                .testTag("voice_phase")
                .clearAndSetSemantics {
                    contentDescription = label
                    liveRegion = LiveRegionMode.Polite
                },
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TalkMotionIcon(look.icon, look.motion, tint)
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

/** Infinite transitions exist only while moving, so a settled status costs no frames. */
@Composable
private fun TalkMotionIcon(
    icon: ImageVector,
    motion: TalkMotion,
    tint: Color,
) {
    val layer =
        when (motion) {
            TalkMotion.STILL -> {
                Modifier
            }

            TalkMotion.SPIN -> {
                val turn by rememberInfiniteTransition(label = "talk_spin").animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing)),
                    label = "talk_spin_angle",
                )
                Modifier.graphicsLayer { rotationZ = turn }
            }

            TalkMotion.PULSE -> {
                val beat by rememberInfiniteTransition(label = "talk_pulse").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
                    label = "talk_pulse_beat",
                )
                Modifier.graphicsLayer {
                    val scale = 0.82f + 0.3f * beat
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.55f + 0.45f * beat
                }
            }
        }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp).then(layer))
}

internal enum class TalkLink { CONNECTED, CONNECTING, OFFLINE }

internal fun talkLink(
    phase: String,
    permissionDenied: Boolean,
    backgroundEnded: Boolean,
): TalkLink =
    when {
        permissionDenied || backgroundEnded -> TalkLink.OFFLINE
        phase == "connecting" -> TalkLink.CONNECTING
        phase in setOf("connected", "listening", "hearing", "thinking", "speaking", "executing") -> TalkLink.CONNECTED
        else -> TalkLink.OFFLINE
    }

/** Activity and the link indicator; the call controls stay in thumb reach at the bottom. */
@Composable
private fun TalkTopActions(
    link: TalkLink,
    activityVisible: Boolean,
    onActivity: () -> Unit,
    onReconnect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    IconToggleButton(
        checked = activityVisible,
        onCheckedChange = { onActivity() },
        modifier = Modifier.size(48.dp).testTag("voice_activity"),
        colors =
            IconButtonDefaults.iconToggleButtonColors(
                contentColor = colors.onSurfaceVariant,
                checkedContainerColor = colors.secondaryContainer,
                checkedContentColor = colors.onSecondaryContainer,
            ),
    ) {
        Icon(
            if (activityVisible) Icons.Filled.GraphicEq else Icons.AutoMirrored.Filled.Notes,
            contentDescription =
                stringResource(if (activityVisible) R.string.realtime_voice_view else R.string.realtime_activity),
        )
    }
    val status = LocalHermesStatusColors.current
    val (icon, tint, label) =
        when (link) {
            TalkLink.CONNECTED -> {
                Triple(
                    Icons.Filled.SignalCellularAlt,
                    status.success,
                    R.string.realtime_link_connected,
                )
            }

            TalkLink.CONNECTING -> {
                Triple(
                    Icons.Filled.SignalCellularAlt,
                    status.warning,
                    R.string.realtime_link_connecting,
                )
            }

            TalkLink.OFFLINE -> {
                Triple(Icons.Filled.SignalCellularOff, status.error, R.string.realtime_link_offline)
            }
        }
    val description = stringResource(label)
    Box(
        Modifier
            .size(48.dp)
            .testTag("voice_link")
            .then(
                if (link == TalkLink.OFFLINE) {
                    Modifier.clickable(role = Role.Button, onClick = onReconnect)
                } else {
                    Modifier
                },
            ).clearAndSetSemantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        TalkMotionIcon(icon, if (link == TalkLink.CONNECTING) TalkMotion.PULSE else TalkMotion.STILL, tint)
    }
}

private const val SPIN_MS = 1_200
private const val PULSE_MS = 650

@Composable
private fun TalkNotice(
    error: String?,
    permissionDenied: Boolean,
    backgroundEnded: Boolean,
    onPermission: () -> Unit,
) {
    val statusColors = LocalHermesStatusColors.current
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (permissionDenied) {
            Text(
                stringResource(R.string.realtime_permission),
                color = statusColors.warning,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onPermission, modifier = Modifier.heightIn(min = 48.dp).testTag("voice_permission")) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.realtime_retry_permission), Modifier.padding(start = 8.dp))
            }
        }
        if (backgroundEnded) Text(stringResource(R.string.realtime_background), textAlign = TextAlign.Center)
        error?.let { Text(it, color = statusColors.error, textAlign = TextAlign.Center) }
    }
}

/** Left: audio output (speaker, earpiece, muted). Centre: call or hang up. Right: microphone (Bluetooth when routed). */
@Composable
private fun TalkControls(
    output: TalkOutput,
    muted: Boolean,
    bluetooth: Boolean,
    inCall: Boolean,
    canControl: Boolean,
    onOutput: () -> Unit,
    onMute: () -> Unit,
    onCall: () -> Unit,
    onHangUp: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val status = LocalHermesStatusColors.current
    val sideColors =
        IconButtonDefaults.iconButtonColors(
            containerColor = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
        )
    val (outputIcon, outputName) =
        when (output) {
            TalkOutput.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp to R.string.realtime_output_speaker
            TalkOutput.EARPIECE -> Icons.Filled.PhoneInTalk to R.string.realtime_output_earpiece
            TalkOutput.MUTED -> Icons.AutoMirrored.Filled.VolumeOff to R.string.realtime_output_muted
        }
    val outputLabel = stringResource(R.string.realtime_output, stringResource(outputName))
    val muteLabel = stringResource(if (muted) R.string.realtime_unmute else R.string.realtime_mute)
    val micState =
        stringResource(
            when {
                muted -> R.string.realtime_state_muted
                bluetooth -> R.string.realtime_mic_bluetooth
                else -> R.string.realtime_mic_on
            },
        )
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 28.dp).testTag("voice_controls"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onOutput,
            modifier = Modifier.size(64.dp).testTag("voice_speaker"),
            shape = CircleShape,
            colors =
                if (output == TalkOutput.MUTED) {
                    IconButtonDefaults.iconButtonColors(
                        containerColor = colors.inverseSurface,
                        contentColor = colors.inverseOnSurface,
                    )
                } else {
                    sideColors
                },
        ) {
            Icon(outputIcon, contentDescription = outputLabel)
        }
        if (inCall) {
            IconButton(
                onClick = onHangUp,
                modifier = Modifier.size(76.dp).testTag("voice_end"),
                shape = CircleShape,
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = colors.error,
                        contentColor = colors.onError,
                    ),
            ) {
                Icon(
                    Icons.Filled.CallEnd,
                    contentDescription = stringResource(R.string.realtime_end),
                    modifier = Modifier.size(32.dp),
                )
            }
        } else {
            IconButton(
                onClick = onCall,
                modifier = Modifier.size(76.dp).testTag("voice_call"),
                shape = CircleShape,
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = status.success,
                        contentColor = status.onSuccess,
                    ),
            ) {
                Icon(
                    Icons.Filled.Call,
                    contentDescription = stringResource(R.string.realtime_call),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        IconToggleButton(
            checked = muted,
            onCheckedChange = { onMute() },
            enabled = canControl,
            modifier = Modifier.size(64.dp).testTag("voice_mute").semantics { stateDescription = micState },
            colors =
                IconButtonDefaults.iconToggleButtonColors(
                    containerColor = colors.surfaceContainerHigh,
                    contentColor = colors.onSurface,
                    checkedContainerColor = colors.inverseSurface,
                    checkedContentColor = colors.inverseOnSurface,
                ),
        ) {
            Icon(
                when {
                    muted -> Icons.Filled.MicOff
                    bluetooth -> Icons.Filled.BluetoothAudio
                    else -> Icons.Filled.Mic
                },
                contentDescription = muteLabel,
            )
        }
    }
}

/** Compact icon + text status, optionally a button. */
@Composable
internal fun TalkChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (warning) LocalHermesStatusColors.current.warning else colors.onSurfaceVariant
    val clickable =
        if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Surface(
        shape = CircleShape,
        color = colors.surfaceContainerHigh,
        modifier = modifier.heightIn(min = 36.dp),
    ) {
        Row(
            clickable.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (warning) tint else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TalkActivity(
    userText: String,
    assistantText: String,
    jobs: List<JsonObject>,
    receipt: String,
    tool: TalkToolActivity?,
    waiting: Int,
    error: String?,
    permissionDenied: Boolean,
    backgroundEnded: Boolean,
    canInterrupt: Boolean,
    onPermission: () -> Unit,
    onInterrupt: () -> Unit,
    onStop: (JsonObject) -> Unit,
    onResult: (JsonObject) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    LaunchedEffect(receipt) { if (receipt.isNotBlank()) list.animateScrollToItem(0) }
    LazyColumn(
        modifier.fillMaxWidth().testTag("voice_activity_content"),
        state = list,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TalkNotice(error, permissionDenied, backgroundEnded, onPermission)
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TalkToolLine(tool, waiting)
                if (canInterrupt) {
                    TalkChip(
                        icon = Icons.Filled.Stop,
                        text = stringResource(R.string.realtime_interrupt),
                        onClick = onInterrupt,
                    )
                }
            }
        }
        if (receipt.isNotBlank()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(Icons.AutoMirrored.Filled.ReceiptLong, R.string.realtime_receipt)
                        SelectionContainer { Text(receipt, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
        item {
            if (userText.isBlank() && assistantText.isBlank()) {
                Text(
                    stringResource(R.string.realtime_no_transcript),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Bubble(R.string.realtime_you, userText, mine = true)
            Bubble(R.string.realtime_assistant, assistantText, mine = false)
        }
        item {
            SectionHeader(Icons.Filled.Work, R.string.realtime_jobs, Modifier.padding(top = 12.dp))
            if (jobs.isEmpty()) {
                Text(
                    stringResource(R.string.realtime_no_work),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        items(jobs) { job ->
            TalkJob(job, onStop = { onStop(job) }, onResult = { onResult(job) })
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    text: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            stringResource(text),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun Bubble(
    label: Int,
    text: String,
    mine: Boolean,
) {
    if (text.isBlank()) return
    val colors = MaterialTheme.colorScheme
    val description = stringResource(label)
    Box(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = if (mine) colors.primaryContainer else colors.surfaceContainerHigh,
            contentColor = if (mine) colors.onPrimaryContainer else colors.onSurface,
            modifier = Modifier.widthIn(max = 520.dp).semantics { contentDescription = description },
        ) {
            SelectionContainer { Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
        }
    }
}

@Composable
private fun TalkJob(
    job: JsonObject,
    onStop: () -> Unit,
    onResult: () -> Unit,
) {
    val status = job.text("status")
    val statusColors = LocalHermesStatusColors.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (status) {
                    "running" -> {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }

                    "done", "completed" -> {
                        Icon(
                            Icons.Filled.CheckCircle,
                            null,
                            tint = statusColors.success,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    "failed" -> {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            null,
                            tint = statusColors.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    else -> {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            null,
                            tint = statusColors.warning,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        stringResource(R.string.realtime_run, job.text("runId"), job.text("kind")),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        when (status) {
                            "running" -> stringResource(R.string.realtime_status_running)
                            "done", "completed" -> stringResource(R.string.realtime_status_done)
                            "failed" -> stringResource(R.string.realtime_status_failed)
                            "lost" -> stringResource(R.string.realtime_status_lost)
                            else -> status
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (status == "running" || job.text("output").isNotBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (job.text("output").isNotBlank()) {
                        TextButton(onClick = onResult, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.realtime_result), Modifier.padding(start = 6.dp))
                        }
                    }
                    if (status == "running") {
                        TextButton(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.realtime_stop_work), Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

private val PreviewProject = TalkProject(id = "p_pilot", name = "Agent Team Local Pilot", primaryPath = "/srv/pilot")

@Preview(showBackground = true, widthDp = 360, heightDp = 800, locale = "zh-rTW")
@Composable
internal fun RealtimeTalkPreview() {
    TalkScenePreview(
        TalkCallState(
            phase = "thinking",
            level = 0.35f,
            focusProject = PreviewProject,
            assistantText = "好，要我把這個需求交給 Orchestrator 嗎？",
            tool = TalkToolActivity(TalkToolStatus.TALK_ACCEPTED, "delegate_task", "5"),
        ),
        ThemePreference.DARK,
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
internal fun RealtimeTalkMutedPreview() {
    TalkScenePreview(
        TalkCallState(phase = "listening", muted = true, output = TalkOutput.EARPIECE),
        ThemePreference.LIGHT,
    )
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
internal fun RealtimeTalkErrorPreview() {
    TalkScenePreview(
        TalkCallState(phase = "error", error = "Voice access denied (403); check Access and subscription"),
        ThemePreference.DARK,
    )
}

@Composable
private fun TalkScenePreview(
    state: TalkCallState,
    preference: ThemePreference,
) {
    HermesControlTheme(themePreference = preference) {
        RealtimeTalkContent(
            state = state,
            permissionDenied = false,
            backgroundEnded = false,
            onClose = {},
            onPermission = {},
            onReconnect = {},
            onMute = {},
            onOutput = {},
            onHangUp = {},
            onInterrupt = {},
            onStop = {},
            onResult = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
internal fun RealtimeTalkActivityPreview() {
    val jobs =
        remember {
            listOf(
                """{"runId":5,"kind":"agent","status":"running","output":""}""",
                """{"runId":4,"kind":"agent","status":"done","output":"Created t_5bf04904."}""",
            ).map { Json.parseToJsonElement(it).jsonObject }
        }
    HermesControlTheme(themePreference = ThemePreference.DARK) {
        HermesScaffold(
            title = { TalkProjectTitle(PreviewProject, connecting = false) },
            showConnectionBanner = false,
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                TalkActivity(
                    userText = "Review my changes.",
                    assistantText = "The work is running.",
                    jobs = jobs,
                    receipt = "",
                    tool = TalkToolActivity(TalkToolStatus.TALK_ACCEPTED, "delegate_task", "5"),
                    waiting = 0,
                    error = null,
                    permissionDenied = false,
                    backgroundEnded = false,
                    canInterrupt = true,
                    onPermission = {},
                    onInterrupt = {},
                    onStop = {},
                    onResult = {},
                    modifier = Modifier.weight(1f),
                )
                TalkControls(
                    output = TalkOutput.SPEAKER,
                    muted = false,
                    bluetooth = true,
                    inCall = true,
                    canControl = true,
                    onOutput = {},
                    onMute = {},
                    onCall = {},
                    onHangUp = {},
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 840, heightDp = 640, locale = "zh-rTW")
@Composable
internal fun RealtimeTalkWidePreview() {
    TalkScenePreview(
        TalkCallState(phase = "speaking", muted = true, focusProject = PreviewProject),
        ThemePreference.DARK,
    )
}
