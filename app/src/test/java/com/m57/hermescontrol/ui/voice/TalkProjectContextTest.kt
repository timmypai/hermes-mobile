package com.m57.hermescontrol.ui.voice

import com.m57.hermescontrol.data.model.KanbanBoard
import com.m57.hermescontrol.data.model.ProjectFolder
import com.m57.hermescontrol.data.model.ProjectInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkProjectContextTest {
    private val catalog =
        TalkProjectCatalog.from(
            projects =
                listOf(
                    ProjectInfo(
                        id = "p_mobile",
                        name = "hermes-mobile",
                        folders =
                            listOf(
                                ProjectFolder("/srv/mobile-extra"),
                                ProjectFolder("/srv/mobile", isPrimary = true),
                            ),
                    ),
                    ProjectInfo(id = "p_docs", name = "docs", primaryPath = "/srv/docs"),
                    ProjectInfo(id = "p_old", name = "old", archived = true),
                ),
            boards =
                listOf(
                    KanbanBoard(id = "mobile", name = "Mobile", projectId = "p_mobile"),
                    KanbanBoard(id = "mobile-old", projectId = "p_mobile", archived = true),
                    KanbanBoard(id = "default"),
                ),
            focusId = "p_mobile",
            autoDecompose = true,
        )

    @Test
    fun `catalog keeps active projects and binds only their live boards`() {
        assertEquals(listOf("p_mobile", "p_docs"), catalog.projects.map { it.id })
        val mobile = catalog.find("p_mobile")!!
        assertEquals("/srv/mobile", mobile.primaryPath)
        assertEquals(listOf("mobile"), mobile.boards.map { it.slug })
        assertTrue(catalog.find("p_docs")!!.boards.isEmpty())
    }

    @Test
    fun `focus outside the active catalog is dropped`() {
        val stale =
            TalkProjectCatalog.from(
                listOf(ProjectInfo(id = "p_old", archived = true)),
                emptyList(),
                "p_old",
                null,
            )
        assertNull(stale.focusId)
        assertTrue(stale.instructions().contains("Focus: none."))
    }

    @Test
    fun `brief tag is a proposal parsed only at the start`() {
        assertEquals(ProjectTag.Named("p_mobile"), TalkBrief.tag("  [project: p_mobile]  Fix the build"))
        assertEquals(ProjectTag.None, TalkBrief.tag("[PROJECT:none] Check the weather"))
        assertEquals(ProjectTag.Missing, TalkBrief.tag("Fix it [project:p_mobile]"))
        assertEquals("Fix the build\nDone: green", TalkBrief.body("[project:p_mobile] Fix the build\nDone: green"))
        assertEquals("Fix the build", TalkBrief.title("[project:p_mobile]\n\n## Fix the build\nDone: green"))
    }

    @Test
    fun `tagged handoff uses the named project and builds a triage task on its board`() {
        val handoff = hold("[project:p_mobile] Add voice handoff\nDone: tests pass")

        assertEquals("p_mobile", handoff.project?.id)
        assertFalse(handoff.fromFocus)
        val body = handoff.kanbanTask("mobile")!!
        assertEquals("Add voice handoff", body.title)
        assertEquals("Add voice handoff\nDone: tests pass", body.body)
        assertTrue(body.triage)
        assertEquals("p_mobile", body.projectId)
        assertNull(body.assignee)
        assertNull(body.workspacePath)
    }

    @Test
    fun `a board outside the shown project never receives the task`() {
        assertNull(hold("[project:p_mobile] Add voice handoff").kanbanTask("default"))
    }

    @Test
    fun `untagged handoff pre-selects focus but none and unknown tags do not`() {
        val untagged = hold("Add voice handoff")
        assertEquals("p_mobile", untagged.project?.id)
        assertTrue(untagged.fromFocus)

        val none = hold("[project:none] Check the weather")
        assertNull(none.project)
        assertFalse(none.canUseKanban)

        val unknown = hold("[project:p_ghost] Add voice handoff")
        assertNull(unknown.project)
        assertEquals(ProjectTag.Named("p_ghost"), unknown.tag)
        assertNull(unknown.kanbanTask("mobile"))
    }

    @Test
    fun `project without a bound board cannot use Kanban`() {
        val docs = hold("[project:p_docs] Update the guide")
        assertEquals("p_docs", docs.project?.id)
        assertFalse(docs.canUseKanban)
    }

    private fun hold(task: String) =
        TalkHandoff.hold("call-1", JsonObject(mapOf("task" to JsonPrimitive(task))), catalog, nowMillis = 0L)
}
