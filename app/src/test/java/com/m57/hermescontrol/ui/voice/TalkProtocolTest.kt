package com.m57.hermescontrol.ui.voice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TalkProtocolTest {
    private fun json(value: String) = Json.parseToJsonElement(value) as JsonObject

    private val ready =
        json(
            """{"ok":true,"configured":true,"voiceMode":"native","model":"gpt-realtime-2.1",
        "source":"codex-oauth","taskContinuity":{"supported":false}}""",
        )
    private val session =
        json(
            """{"ok":true,"voiceMode":"native","model":"gpt-realtime-2.1","authSource":"codex-oauth",
        "clientSecret":"ephemeral-test","offerUrl":"https://api.openai.com/v1/realtime/calls"}""",
        )

    @Test
    fun `API keys and lookalike subscription sources cannot authorize voice`() {
        for (source in listOf("configured", "env", "subscription", "not-codex-oauth")) {
            assertThrows(TalkSetupException::class.java) {
                TalkProtocol.validateStatus(JsonObject(ready + ("source" to JsonPrimitive(source))))
            }
        }
    }

    @Test
    fun `live and cascade cannot silently replace native realtime`() {
        for (mode in listOf("live", "cascade")) {
            assertThrows(TalkSetupException::class.java) {
                TalkProtocol.validateStatus(JsonObject(ready + ("voiceMode" to JsonPrimitive(mode))))
            }
        }
        assertThrows(TalkSetupException::class.java) {
            TalkProtocol.validateStatus(JsonObject(ready + ("model" to JsonPrimitive("gpt-live-1-codex"))))
        }
    }

    @Test
    fun `unavailable subscription cannot proceed to negotiation`() {
        assertThrows(TalkSetupException::class.java) {
            TalkProtocol.validateStatus(JsonObject(ready + ("configured" to JsonPrimitive(false))))
        }
    }

    @Test
    fun `minted session must still declare Codex OAuth`() {
        for (source in listOf("configured", "env", "")) {
            assertThrows(TalkSetupException::class.java) {
                TalkProtocol.validateSession(JsonObject(session + ("authSource" to JsonPrimitive(source))))
            }
        }
    }

    @Test
    fun `negotiation cannot use a missing or empty ephemeral credential`() {
        assertThrows(
            TalkSetupException::class.java,
        ) { TalkProtocol.validateSession(JsonObject(session - "clientSecret")) }
        assertThrows(TalkSetupException::class.java) {
            TalkProtocol.validateSession(JsonObject(session + ("clientSecret" to JsonPrimitive(""))))
        }
    }

    @Test
    fun `provider descriptor cannot replace the requested model`() {
        assertThrows(TalkSetupException::class.java) {
            TalkProtocol.validateSession(JsonObject(session + ("model" to JsonPrimitive("gpt-realtime-mini"))))
        }
    }

    // Reply texts are from hermes-talk 3b8f88b talk_tools.py / talk_host.py run_agent.
    @Test
    fun `Talk plain-text refusals are not started work`() {
        for (text in listOf(
            "delegate_task's execution_mode must be 'exclusive' or 'parallel_read_only'.",
            "delegate_task could not use those resource_keys: empty key.",
            "I can't start that yet — run 12 holds repo:mobile.",
            "I couldn't start that work — that failed: delegation paused",
            "",
        )) {
            assertEquals(
                DelegateOutcome.Refused(text),
                DelegateOutcome.of(json("""{"ok":true,"output":${JsonPrimitive(text)}}""")),
            )
        }
    }

    @Test
    fun `only a WORK_STARTED receipt is started, with or without a run id`() {
        assertEquals(
            DelegateOutcome.Started("41", "WORK_STARTED #41 kind=agent — running"),
            DelegateOutcome.of(json("""{"ok":true,"output":"WORK_STARTED #41 kind=agent — running"}""")),
        )
        assertEquals(
            DelegateOutcome.Started("", "WORK_STARTED — on it"),
            DelegateOutcome.of(json("""{"output":"WORK_STARTED — on it"}""")),
        )
        assertEquals(
            DelegateOutcome.Refused("Earlier WORK_STARTED #41 was refused"),
            DelegateOutcome.of(json("""{"ok":true,"output":"Earlier WORK_STARTED #41 was refused"}""")),
        )
    }

    @Test
    fun `unreadable or failed tool replies stay uncertain`() {
        assertEquals(DelegateOutcome.Uncertain, DelegateOutcome.of(null))
        assertEquals(DelegateOutcome.Uncertain, DelegateOutcome.of(json("""{"ok":true}""")))
        assertEquals(
            DelegateOutcome.Uncertain,
            DelegateOutcome.of(json("""{"ok":false,"output":"WORK_STARTED #41 kind=agent"}""")),
        )
    }
}
