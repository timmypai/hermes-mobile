package com.m57.hermescontrol.ui.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.NavIcon
import com.m57.hermescontrol.ui.common.ExpressiveButton as Button

/** The Hermes Agent mark (the launcher glyph), tinted by the theme. */
@Composable
internal fun TalkLogo(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.size(size),
    ) {
        Icon(
            painterResource(R.drawable.ic_launcher_monochrome),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size),
        )
    }
}

/** Pre-call step: pick the project this call is about, then start. Nothing connects before Start. */
@Composable
internal fun TalkLobby(
    projects: List<TalkProject>?,
    selected: TalkProject?,
    onSelect: (TalkProject?) -> Unit,
    onStart: () -> Unit,
    onClose: () -> Unit,
) {
    HermesScaffold(
        modifier = Modifier.fillMaxSize(),
        title = { Text(stringResource(R.string.realtime_title)) },
        navigationIcon = NavIcon.Back(onClose),
        drawerGesturesEnabled = false,
        showConnectionBanner = false,
        pinTopBar = true,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TalkLogo(96.dp, Modifier.padding(top = 12.dp, bottom = 16.dp))
                Text(
                    stringResource(R.string.realtime_pick_project),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .selectableGroup()
                        .testTag("voice_projects"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        ProjectOption(null, selected == null, onSelect)
                    }
                    if (projects == null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                            }
                        }
                    } else {
                        items(projects, key = { it.id }) { project ->
                            ProjectOption(project, selected?.id == project.id, onSelect)
                        }
                    }
                }
                Button(
                    onClick = onStart,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 24.dp)
                            .heightIn(min = 56.dp)
                            .testTag("voice_start"),
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(
                        stringResource(R.string.realtime_start),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectOption(
    project: TalkProject?,
    selected: Boolean,
    onSelect: (TalkProject?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.secondaryContainer else colors.surfaceContainer,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(project) }),
    ) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (project != null) Icons.Filled.Folder else Icons.Filled.FolderOff,
                contentDescription = null,
                tint = if (project != null) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    project?.name ?: stringResource(R.string.realtime_no_project_option),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                project?.primaryPath?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.primary)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, locale = "zh-rTW")
@Composable
internal fun TalkLobbyPreview() {
    val pilot = TalkProject("p_pilot", "Agent Team Local Pilot", "/srv/agent-team-pilot")
    HermesControlTheme(themePreference = ThemePreference.DARK) {
        TalkLobby(
            projects = listOf(pilot, TalkProject("p_mobile", "hermes-mobile", "/home/user/apps/hermes-mobile")),
            selected = pilot,
            onSelect = {},
            onStart = {},
            onClose = {},
        )
    }
}
