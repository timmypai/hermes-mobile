package com.m57.hermescontrol.ui.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
@Composable
internal fun TalkHandoffCard(
    handoff: TalkHandoff,
    onKanban: (board: String) -> Unit,
    onTalk: () -> Unit,
    onWrongProject: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.realtime_handoff_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.realtime_handoff_not_started),
                color = LocalHermesStatusColors.current.warning,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Label(R.string.realtime_handoff_project)
            Text(
                when {
                    project != null -> {
                        project.name
                    }

                    handoff.tag == ProjectTag.None -> {
                        stringResource(R.string.realtime_handoff_no_project)
                    }

                    handoff.tag is ProjectTag.Named -> {
                        stringResource(
                            R.string.realtime_handoff_unknown_project,
                            handoff.tag.id,
                        )
                    }

                    else -> {
                        stringResource(R.string.realtime_handoff_missing_project)
                    }
                },
                style = MaterialTheme.typography.titleMedium,
            )
            project?.primaryPath?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (handoff.fromFocus) {
                Text(
                    stringResource(R.string.realtime_handoff_from_focus),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalHermesStatusColors.current.warning,
                )
            }
            if (project != null) {
                if (project.boards.isEmpty()) {
                    Text(stringResource(R.string.realtime_handoff_no_board), style = MaterialTheme.typography.bodySmall)
                } else {
                    Label(R.string.realtime_handoff_board)
                    Column(Modifier.selectableGroup()) {
                        project.boards.forEach { option ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        selected = board == option.slug,
                                        onClick = { board = option.slug },
                                        role = Role.RadioButton,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = board == option.slug, onClick = null)
                                Text(option.name, Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                    Text(
                        stringResource(
                            when (handoff.autoDecompose) {
                                true -> R.string.realtime_handoff_decompose_on
                                false -> R.string.realtime_handoff_decompose_off
                                null -> R.string.realtime_handoff_decompose_unknown
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            HorizontalDivider()
            Label(R.string.realtime_handoff_task)
            Text(handoff.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SelectionContainer { Text(handoff.brief, style = MaterialTheme.typography.bodyMedium) }
            HorizontalDivider()
            val kanbanBoard = board
            Button(
                onClick = { kanbanBoard?.let(onKanban) },
                enabled = handoff.canUseKanban && kanbanBoard != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("voice_handoff_kanban"),
            ) {
                Text(stringResource(R.string.realtime_handoff_kanban), textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = onTalk,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("voice_handoff_talk"),
            ) {
                Text(stringResource(R.string.realtime_handoff_talk), textAlign = TextAlign.Center)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onWrongProject, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.realtime_handoff_wrong_project))
                }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("voice_handoff_cancel"),
                ) {
                    Text(stringResource(R.string.realtime_handoff_cancel))
                }
            }
        }
    }
}

@Composable
private fun Label(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Observed tool execution only. It never states what the user intended. */
@Composable
internal fun TalkToolLine(
    tool: TalkToolActivity?,
    waiting: Int,
    modifier: Modifier = Modifier,
) {
    val text =
        when {
            waiting > 0 -> {
                stringResource(R.string.realtime_tool_waiting)
            }

            tool == null -> {
                return
            }

            else -> {
                when (tool.status) {
                    TalkToolStatus.RUNNING -> {
                        stringResource(R.string.realtime_tool_running, tool.tool)
                    }

                    TalkToolStatus.AWAITING_CONFIRMATION -> {
                        stringResource(R.string.realtime_tool_waiting)
                    }

                    TalkToolStatus.FINISHED -> {
                        stringResource(R.string.realtime_tool_finished, tool.tool)
                    }

                    TalkToolStatus.TALK_ACCEPTED -> {
                        stringResource(R.string.realtime_tool_talk_accepted, tool.reference)
                    }

                    TalkToolStatus.KANBAN_CREATED -> {
                        stringResource(
                            R.string.realtime_tool_kanban_created,
                            tool.reference,
                        )
                    }

                    TalkToolStatus.DECLINED -> {
                        stringResource(R.string.realtime_tool_declined)
                    }

                    TalkToolStatus.UNCERTAIN -> {
                        stringResource(R.string.realtime_tool_uncertain)
                    }
                }
            }
        }
    val colors = LocalHermesStatusColors.current
    Text(
        text,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("voice_tool_status"),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color =
            when {
                waiting > 0 || tool?.status == TalkToolStatus.UNCERTAIN -> colors.warning
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
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
                    autoDecompose = true,
                    heldAtMillis = 0L,
                ),
            onKanban = {},
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
                    autoDecompose = null,
                    heldAtMillis = 0L,
                ),
            onKanban = {},
            onTalk = {},
            onWrongProject = {},
            onCancel = {},
        )
    }
}
