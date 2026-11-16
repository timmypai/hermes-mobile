package com.m57.hermescontrol.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
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
    // The lobby picks the project first; nothing connects until Start.
    var inCall by remember(launch) { mutableStateOf(false) }
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
                if (event == Lifecycle.Event.ON_STOP && inCall) {
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
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The dialog has its own window: keep that window's screen on for the whole call.
        val view = LocalView.current
        DisposableEffect(view, inCall) {
            view.keepScreenOn = inCall
            onDispose { view.keepScreenOn = false }
        }
        if (!inCall) {
            TalkLobby(
                projects = state.projects,
                selected = state.focusProject,
                onSelect = controller::select,
                onStart = {
                    inCall = true
                    connect(false)
                },
                onClose = close,
            )
        } else {
            RealtimeTalkContent(
                state = state,
                permissionDenied = permissionDenied,
                backgroundEnded = backgroundEnded,
                onClose = close,
                onPermission = { connect(false) },
                onReconnect = { connect(true) },
                onMute = controller::mute,
                onSpeaker = controller::speaker,
                onInterrupt = controller::interrupt,
                onStop = { pendingStop = it },
                onResult = controller::showResult,
            )
        }
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
    onSpeaker: () -> Unit,
    onInterrupt: () -> Unit,
    onStop: (JsonObject) -> Unit,
    onResult: (JsonObject) -> Unit,
) {
    var activityVisible by remember { mutableStateOf(false) }
    val canControl =
        !permissionDenied && !backgroundEnded &&
            when (state.phase) {
                "listening", "speaking", "executing", "connected" -> true
                else -> false
            }
    val canInterrupt = canControl && (state.phase == "speaking" || state.phase == "executing")
    BackHandler(enabled = activityVisible) { activityVisible = false }
    HermesScaffold(
        modifier = Modifier.fillMaxSize(),
        title = { TalkProjectTitle(state.focusProject, connecting = state.phase == "connecting") },
        navigationIcon = NavIcon.Back { if (activityVisible) activityVisible = false else onClose() },
        drawerGesturesEnabled = false,
        showConnectionBanner = false,
        pinTopBar = true,
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
                        phase = state.phase,
                        error = state.error,
                        permissionDenied = permissionDenied,
                        backgroundEnded = backgroundEnded,
                        canInterrupt = canInterrupt,
                        onPermission = onPermission,
                        onReconnect = onReconnect,
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
                        onReconnect = onReconnect,
                        onInterrupt = onInterrupt,
                        onActivity = { activityVisible = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                TalkControls(
                    muted = state.muted,
                    speaker = state.speaker,
                    canControl = canControl,
                    activityVisible = activityVisible,
                    onMute = onMute,
                    onSpeaker = onSpeaker,
                    onClose = onClose,
                    onActivity = { activityVisible = !activityVisible },
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
    onReconnect: () -> Unit,
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
                phase = state.phase,
                error = state.error,
                permissionDenied = permissionDenied,
                backgroundEnded = backgroundEnded,
                onPermission = onPermission,
                onReconnect = onReconnect,
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
            TalkPhaseBadge(state.phase, state.muted, permissionDenied, backgroundEnded)
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

/** Phase as one icon; the spoken name is only for accessibility. */
@Composable
private fun TalkPhaseBadge(
    phase: String,
    muted: Boolean,
    permissionDenied: Boolean,
    backgroundEnded: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val (icon, label) =
        when {
            permissionDenied -> Icons.Filled.MicOff to R.string.realtime_state_permission
            backgroundEnded || phase == "ended" -> Icons.Filled.CallEnd to R.string.realtime_state_ended
            phase == "error" -> Icons.Filled.ErrorOutline to R.string.realtime_state_error
            phase == "speaking" -> Icons.AutoMirrored.Filled.VolumeUp to R.string.realtime_state_speaking
            phase == "executing" -> Icons.Filled.Sync to R.string.realtime_state_working
            muted -> Icons.Filled.MicOff to R.string.realtime_state_muted
            phase == "listening" || phase == "connected" -> Icons.Filled.Mic to R.string.realtime_state_listening
            phase == "idle" -> Icons.Filled.Refresh to R.string.realtime_state_ready
            else -> Icons.Filled.Sync to R.string.realtime_state_connecting
        }
    val description = stringResource(label)
    Surface(
        shape = CircleShape,
        color = if (phase == "error") colors.errorContainer else colors.secondaryContainer,
        contentColor = if (phase == "error") colors.onErrorContainer else colors.onSecondaryContainer,
        modifier =
            Modifier
                .size(48.dp)
                .testTag("voice_phase")
                .clearAndSetSemantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                },
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
private fun TalkNotice(
    phase: String,
    error: String?,
    permissionDenied: Boolean,
    backgroundEnded: Boolean,
    onPermission: () -> Unit,
    onReconnect: () -> Unit,
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
        if (!permissionDenied && (backgroundEnded || phase == "idle" || phase == "error" || phase == "ended")) {
            Button(onClick = onReconnect, modifier = Modifier.heightIn(min = 48.dp).testTag("voice_reconnect")) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.realtime_reconnect), Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun TalkControls(
    muted: Boolean,
    speaker: Boolean,
    canControl: Boolean,
    activityVisible: Boolean,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onClose: () -> Unit,
    onActivity: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val toggleColors =
        IconButtonDefaults.iconToggleButtonColors(
            containerColor = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
            checkedContainerColor = colors.primary,
            checkedContentColor = colors.onPrimary,
        )
    val muteLabel = stringResource(if (muted) R.string.realtime_unmute else R.string.realtime_mute)
    val micState = stringResource(if (muted) R.string.realtime_state_muted else R.string.realtime_mic_on)
    val speakerLabel = stringResource(if (speaker) R.string.realtime_speaker_on else R.string.realtime_speaker_off)
    val activityLabel =
        stringResource(if (activityVisible) R.string.realtime_voice_view else R.string.realtime_activity)
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp).testTag("voice_controls"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconToggleButton(
            checked = muted,
            onCheckedChange = { onMute() },
            enabled = canControl,
            modifier = Modifier.size(64.dp).testTag("voice_mute").semantics { stateDescription = micState },
            colors = toggleColors,
        ) {
            Icon(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, contentDescription = muteLabel)
        }
        IconToggleButton(
            checked = speaker,
            onCheckedChange = { onSpeaker() },
            modifier = Modifier.size(64.dp).testTag("voice_speaker"),
            colors = toggleColors,
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = speakerLabel)
        }
        IconToggleButton(
            checked = activityVisible,
            onCheckedChange = { onActivity() },
            modifier = Modifier.size(64.dp).testTag("voice_activity"),
            colors = toggleColors,
        ) {
            Icon(
                if (activityVisible) Icons.Filled.GraphicEq else Icons.AutoMirrored.Filled.Notes,
                contentDescription = activityLabel,
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(72.dp).testTag("voice_end"),
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
                modifier = Modifier.size(30.dp),
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
    phase: String,
    error: String?,
    permissionDenied: Boolean,
    backgroundEnded: Boolean,
    canInterrupt: Boolean,
    onPermission: () -> Unit,
    onReconnect: () -> Unit,
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
            TalkNotice(phase, error, permissionDenied, backgroundEnded, onPermission, onReconnect)
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

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
internal fun RealtimeTalkPreview() {
    TalkScenePreview(
        TalkCallState(
            phase = "listening",
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
    TalkScenePreview(TalkCallState(phase = "listening", muted = true, speaker = false), ThemePreference.LIGHT)
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
            onSpeaker = {},
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
                    phase = "speaking",
                    error = null,
                    permissionDenied = false,
                    backgroundEnded = false,
                    canInterrupt = true,
                    onPermission = {},
                    onReconnect = {},
                    onInterrupt = {},
                    onStop = {},
                    onResult = {},
                    modifier = Modifier.weight(1f),
                )
                TalkControls(false, true, true, true, {}, {}, {}, {})
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
