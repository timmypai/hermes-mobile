package com.m57.hermescontrol.ui.voice

import com.m57.hermescontrol.data.model.KanbanBoard
import com.m57.hermescontrol.data.model.ProjectInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A Kanban board bound to a Hermes Project through `projectId`. */
data class TalkBoard(
    val slug: String,
    val name: String,
    val current: Boolean = false,
)

data class TalkProject(
    val id: String,
    val name: String,
    val primaryPath: String? = null,
    val description: String? = null,
    val boards: List<TalkBoard> = emptyList(),
)

/**
 * App-side project facts for one call. They identify candidates only; the operator's on-screen
 * confirmation, not the model's tag, decides which project a handoff belongs to.
 */
data class TalkProjectCatalog(
    val projects: List<TalkProject> = emptyList(),
    val focusId: String? = null,
) {
    fun find(id: String?): TalkProject? = projects.firstOrNull { it.id == id }

    /** Additive system context; Talk's own session instructions and tools are left untouched. */
    fun instructions(): String =
        buildString {
            appendLine("Context from the operator's Hermes Mobile app (not spoken by the user).")
            if (projects.isEmpty()) {
                appendLine(
                    "Projects: none could be listed. Treat every handoff as [project:none] unless the user names one.",
                )
            } else {
                appendLine(
                    "Projects on this Hermes profile (id | name | primary folder | Kanban boards | description):",
                )
                projects.take(MAX_PROJECTS).forEach { project ->
                    append("- ").append(project.id).append(" | ").append(project.name)
                    append(" | ").append(project.primaryPath ?: "no folder")
                    append(" | ").append(project.boards.joinToString { it.slug }.ifEmpty { "no bound board" })
                    project.description?.takeIf { it.isNotBlank() }?.let {
                        append(
                            " | ",
                        ).append(it.take(DESCRIPTION_LIMIT))
                    }
                    appendLine()
                }
            }
            val focus = find(focusId)
            appendLine(
                if (focus == null) {
                    "Focus: none."
                } else {
                    "Focus: ${focus.id} (${focus.name}), the project of the chat the operator opened this call from."
                },
            )
            appendLine("Rules:")
            appendLine(
                "1. While the user is still discussing a requirement, keep discussing. Do not hand anything off.",
            )
            appendLine(
                "2. Project for a handoff: the project the user names, else one already confirmed in this call, " +
                    "else the focus. With no focus and no named project, ask. Never pick between candidates yourself.",
            )
            appendLine(
                "3. delegate_task.task must start with [project:<id>] or [project:none], followed by a complete, " +
                    "self-contained brief: goal, scope, numbered acceptance criteria (AC-1, AC-2, ...) that the user " +
                    "can check, what must not be touched, the user's own words, and the folder when one applies.",
            )
            appendLine(
                "4. The app holds every delegate_task for on-screen confirmation. The operator chooses Orchestrator " +
                    "(Hermes runs devflow-intake, which creates an [EPIC] card that the orchestrator plans and " +
                    "drives), an immediate Talk background run, or cancel. Until a later app message reports the " +
                    "result, say only that it is waiting for confirmation.",
            )
            append("5. Never invent progress. Use tools that report it, or say that you do not know.")
        }

    companion object {
        const val MAX_PROJECTS = 40
        const val DESCRIPTION_LIMIT = 120

        fun from(
            projects: List<ProjectInfo>,
            boards: List<KanbanBoard>,
            focusId: String?,
        ): TalkProjectCatalog {
            val live = boards.filterNot { it.archived }
            val active =
                projects.filterNot { it.isArchived }.map { project ->
                    TalkProject(
                        id = project.id,
                        name = project.name.ifBlank { project.slug ?: project.id },
                        primaryPath =
                            project.primaryPath
                                ?: project.folders.firstOrNull { it.isPrimary == true }?.path
                                ?: project.folders.firstOrNull()?.path,
                        description = project.description,
                        boards =
                            live
                                .filter { it.projectId == project.id }
                                .map { TalkBoard(it.slug, it.displayName, it.isCurrent) },
                    )
                }
            return TalkProjectCatalog(active, focusId?.takeIf { id -> active.any { it.id == id } })
        }
    }
}

/** What the model wrote at the start of its brief. A proposal only. */
sealed interface ProjectTag {
    data object Missing : ProjectTag

    data object None : ProjectTag

    data class Named(
        val id: String,
    ) : ProjectTag
}

internal object TalkBrief {
    private val TAG = Regex("""^\s*\[project:\s*([^\]\s]+)\s*]\s*""", RegexOption.IGNORE_CASE)
    private const val TITLE_LIMIT = 80

    fun tag(task: String): ProjectTag {
        val id = TAG.find(task)?.groupValues?.get(1) ?: return ProjectTag.Missing
        return if (id.equals("none", ignoreCase = true)) ProjectTag.None else ProjectTag.Named(id)
    }

    fun body(task: String): String = task.replaceFirst(TAG, "").trim()

    fun title(task: String): String {
        val line =
            body(task)
                .lineSequence()
                .map { it.trim().trimStart('#', '-', '*', ' ') }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        return if (line.length <= TITLE_LIMIT) line else line.take(TITLE_LIMIT - 1).trimEnd() + "…"
    }
}

/** A delegate_task call held by the app until the operator decides on screen. Nothing has started. */
data class TalkHandoff(
    val callId: String,
    val arguments: JsonObject,
    val task: String,
    val tag: ProjectTag,
    val project: TalkProject?,
    /** True when the model wrote no tag and the card uses the chat's project. */
    val fromFocus: Boolean,
    val heldAtMillis: Long,
) {
    val brief: String get() = TalkBrief.body(task)
    val title: String get() = TalkBrief.title(task)

    /** Orchestrator work needs a project bound to a board: in devflow one board is one repository. */
    val canHandToOrchestrator: Boolean get() = project != null && project.boards.isNotEmpty() && brief.isNotBlank()

    /**
     * delegate_task arguments for the Hermes default bot's `devflow-intake` skill. Intake checks the
     * acceptance criteria, deduplicates, and creates the `[EPIC]` card assigned to the orchestrator;
     * the app never writes Kanban itself. Other model arguments are kept unchanged.
     */
    fun orchestratorArguments(board: String): JsonObject? =
        project?.takeIf { canHandToOrchestrator && it.boards.any { option -> option.slug == board } }?.let {
            JsonObject(arguments + ("task" to JsonPrimitive(intakeBrief(it, board))))
        }

    private fun intakeBrief(
        project: TalkProject,
        board: String,
    ): String =
        buildString {
            appendLine("用 devflow-intake skill 建 EPIC，交給 orchestrator 規劃與執行。")
            append("repo：").append(board)
            append("（Kanban board ").append(board).append("；Hermes 專案 ").append(project.name)
            append(" ").append(project.id)
            project.primaryPath?.let { append("；資料夾 ").append(it) }
            appendLine("）")
            appendLine("需求（Hermes Talk 語音整理）：")
            appendLine(brief)
            append("已語音確認：使用者已在 Hermes Mobile 畫面確認專案與以上需求。")
        }

    companion object {
        fun hold(
            callId: String,
            arguments: JsonObject,
            catalog: TalkProjectCatalog,
            nowMillis: Long,
        ): TalkHandoff {
            val task = arguments.text("task")
            val tag = TalkBrief.tag(task)
            val focus = catalog.find(catalog.focusId).takeIf { tag == ProjectTag.Missing }
            return TalkHandoff(
                callId = callId,
                arguments = arguments,
                task = task,
                tag = tag,
                project = (tag as? ProjectTag.Named)?.let { catalog.find(it.id) } ?: focus,
                fromFocus = focus != null,
                heldAtMillis = nowMillis,
            )
        }
    }
}

/** Observed tool execution, kept separate from any guess about the user's intent. */
enum class TalkToolStatus {
    RUNNING,
    AWAITING_CONFIRMATION,
    FINISHED,
    TALK_ACCEPTED,
    INTAKE_STARTED,
    DECLINED,
    UNCERTAIN,
}

data class TalkToolActivity(
    val status: TalkToolStatus,
    val tool: String = "",
    val reference: String = "",
)
