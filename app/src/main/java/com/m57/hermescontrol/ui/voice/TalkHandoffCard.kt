package com.m57.hermescontrol.ui.voice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import com.m57.hermescontrol.theme.ThemePreference
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.m57.hermescontrol.ui.common.ExpressiveButton as Button
import com.m57.hermescontrol.ui.common.ExpressiveOutlinedButton as OutlinedButton
import com.m57.hermescontrol.ui.common.ExpressiveTextButton as TextButton

/** The only place a held delegate_task becomes work. Every choice is an explicit tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TalkHandoffCard(
    handoff: TalkHandoff,
    onOrchestrator: (board: String) -> Unit,
    onTalk: () -> Unit,
    onWrongProject: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val project = handoff.project
    var board by remember(handoff.callId) {
        mutableStateOf(
            project?.boards?.let { boards ->
                (boards.firstOrNull { it.current } ?: boards.firstOrNull())?.slug
            },
        )
    }
    Surface(
        modifier = modifier.widthIn(max = 560.dp).testTag("voice_handoff"),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerHigh,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = colors.primaryContainer, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(stringResource(R.string.realtime_handoff_title), style = MaterialTheme.typography.titleLarge)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = LocalHermesStatusColors.current.warning,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            stringResource(R.string.realtime_handoff_not_started),
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalHermesStatusColors.current.warning,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }

            ProjectBlock(handoff)

            if (project != null) {
                if (project.boards.isEmpty()) {
                    InfoLine(Icons.Filled.LinkOff, stringResource(R.string.realtime_handoff_no_board), warning = true)
                } else {
                    FlowRow(
                        Modifier.selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        project.boards.forEach { option ->
                            val selected = board == option.slug
                            FilterChip(
                                selected = selected,
                                onClick = { board = option.slug },
                                label = { Text(option.name) },
                                leadingIcon = {
                                    Icon(
                                        if (selected) Icons.Filled.Check else Icons.Filled.ViewKanban,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    InfoLine(Icons.Filled.AccountTree, stringResource(R.string.realtime_handoff_intake))
                }
            }

            TaskBlock(handoff)

            val selectedBoard = board
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { selectedBoard?.let(onOrchestrator) },
                    enabled = handoff.canHandToOrchestrator && selectedBoard != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("voice_handoff_orchestrator"),
                ) {
                    Icon(Icons.Filled.AccountTree, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.realtime_handoff_orchestrator), Modifier.padding(start = 10.dp))
                }
                OutlinedButton(
                    onClick = onTalk,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("voice_handoff_talk"),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.realtime_handoff_talk), Modifier.padding(start = 10.dp))
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onWrongProject, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.realtime_handoff_wrong_project), Modifier.padding(start = 6.dp))
                    }
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("voice_handoff_cancel"),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.realtime_handoff_cancel), Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectBlock(handoff: TalkHandoff) {
    val colors = MaterialTheme.colorScheme
    val project = handoff.project
    val name =
        when {
            project != null -> project.name
            handoff.tag == ProjectTag.None -> stringResource(R.string.realtime_handoff_no_project)
            handoff.tag is ProjectTag.Named -> stringResource(R.string.realtime_handoff_unknown_project, handoff.tag.id)
            else -> stringResource(R.string.realtime_handoff_missing_project)
        }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerHighest,
        contentColor = if (project != null) colors.onSurface else colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().testTag("voice_handoff_project"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (project != null) Icons.Filled.Folder else Icons.Filled.FolderOff,
                    contentDescription = null,
                    tint = if (project != null) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    project?.primaryPath?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (handoff.fromFocus) {
                InfoLine(Icons.Filled.Info, stringResource(R.string.realtime_handoff_from_focus), warning = true)
            }
        }
    }
}

@Composable
private fun TaskBlock(handoff: TalkHandoff) {
    var expanded by remember(handoff.callId) { mutableStateOf(false) }
    var clipped by remember(handoff.callId) { mutableStateOf(false) }
    // The brief's first line is the title; show only the rest.
    val extra =
        handoff.brief
            .trim()
            .removePrefix(handoff.title.trim())
            .trim()
            .takeIf { it.isNotEmpty() }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    handoff.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            extra?.let { brief ->
                SelectionContainer {
                    Text(
                        brief,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!expanded) clipped = it.hasVisualOverflow },
                    )
                }
                if (clipped || expanded) {
                    Text(
                        stringResource(
                            if (expanded) R.string.realtime_handoff_less else R.string.realtime_handoff_more,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { expanded = !expanded }
                                .padding(vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLine(
    icon: ImageVector,
    text: String,
    warning: Boolean = false,
) {
    val tint = if (warning) LocalHermesStatusColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = tint, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Observed tool execution only. It never states what the user intended. */
@Composable
internal fun TalkToolLine(
    tool: TalkToolActivity?,
    waiting: Int,
    modifier: Modifier = Modifier,
) {
    if (waiting == 0 && tool == null) return
    val status = if (waiting > 0) TalkToolStatus.AWAITING_CONFIRMATION else checkNotNull(tool).status
    val icon =
        when (status) {
            TalkToolStatus.RUNNING -> Icons.Filled.Build
            TalkToolStatus.AWAITING_CONFIRMATION -> Icons.Filled.PendingActions
            TalkToolStatus.FINISHED -> Icons.Filled.CheckCircle
            TalkToolStatus.TALK_ACCEPTED -> Icons.Filled.RocketLaunch
            TalkToolStatus.INTAKE_STARTED -> Icons.Filled.AccountTree
            TalkToolStatus.DECLINED -> Icons.Filled.Block
            TalkToolStatus.UNCERTAIN -> Icons.AutoMirrored.Filled.HelpOutline
        }
    val name = tool?.tool.orEmpty()
    // Some Talk lanes accept work without a run id; never render an empty "#".
    val run =
        tool
            ?.reference
            ?.takeIf { it.isNotEmpty() }
            ?.let { " #$it" }
            .orEmpty()
    val text =
        when (status) {
            TalkToolStatus.RUNNING -> {
                stringResource(R.string.realtime_tool_running, name)
            }

            TalkToolStatus.AWAITING_CONFIRMATION -> {
                stringResource(R.string.realtime_tool_waiting)
            }

            TalkToolStatus.FINISHED -> {
                stringResource(R.string.realtime_tool_finished, name)
            }

            TalkToolStatus.TALK_ACCEPTED -> {
                stringResource(R.string.realtime_tool_talk_accepted, run, name)
            }

            TalkToolStatus.INTAKE_STARTED -> {
                stringResource(R.string.realtime_tool_intake_started, run)
            }

            TalkToolStatus.DECLINED -> {
                stringResource(R.string.realtime_tool_declined)
            }

            TalkToolStatus.UNCERTAIN -> {
                stringResource(R.string.realtime_tool_uncertain)
            }
        }
    TalkChip(
        icon = icon,
        text = text,
        warning = status == TalkToolStatus.AWAITING_CONFIRMATION || status == TalkToolStatus.UNCERTAIN,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("voice_tool_status"),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 900, locale = "zh-rTW")
@Composable
internal fun TalkHandoffCardPreview() {
    val project =
        TalkProject(
            id = "p_mobile",
            name = "hermes-mobile",
            primaryPath = "/home/user/apps/hermes-mobile",
            boards = listOf(TalkBoard("mobile", "Mobile", current = true), TalkBoard("mobile-ops", "Mobile Ops")),
        )
    val task =
        "[project:p_mobile] Add project-aware voice handoff\n" +
            "Goal: hold delegate_task until the operator confirms.\nDone: unit tests and preview pass."
    HermesControlTheme(themePreference = ThemePreference.DARK) {
        TalkHandoffCard(
            handoff =
                TalkHandoff(
                    callId = "call-1",
                    arguments = JsonObject(mapOf("task" to JsonPrimitive(task))),
                    task = task,
                    tag = ProjectTag.Named("p_mobile"),
                    project = project,
                    fromFocus = false,
                    heldAtMillis = 0L,
                ),
            onOrchestrator = {},
            onTalk = {},
            onWrongProject = {},
            onCancel = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
internal fun TalkHandoffCardMissingProjectPreview() {
    val task = "Summarize what changed in the last release notes."
    HermesControlTheme(themePreference = ThemePreference.LIGHT) {
        TalkHandoffCard(
            handoff =
                TalkHandoff(
                    callId = "call-2",
                    arguments = JsonObject(mapOf("task" to JsonPrimitive(task))),
                    task = task,
                    tag = ProjectTag.Missing,
                    project = null,
                    fromFocus = false,
                    heldAtMillis = 0L,
                ),
            onOrchestrator = {},
            onTalk = {},
            onWrongProject = {},
            onCancel = {},
        )
    }
}
