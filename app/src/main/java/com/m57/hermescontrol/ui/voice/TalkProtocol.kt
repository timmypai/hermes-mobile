package com.m57.hermescontrol.ui.voice

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

internal fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

internal fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

internal fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: JsonObject(emptyMap())

internal fun JsonObject.rows(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

/** Only app-authored setup refusals, never raw provider or server payloads. */
internal class TalkSetupException(
    message: String,
) : IllegalStateException(message)

/**
 * What one delegate_task POST to Talk `/tool` proves. Every Talk backend is asynchronous: acceptance is a
 * `WORK_STARTED` receipt (`#<run> kind=…`, or `— …` on lanes without a run id). Talk refuses in plain text.
 */
internal sealed interface DelegateOutcome {
    /** Unreadable, missing or `ok: false` reply: the work may or may not exist. */
    data object Uncertain : DelegateOutcome

    data class Refused(
        val text: String,
    ) : DelegateOutcome

    /** [runId] is empty when the accepting lane reports none. */
    data class Started(
        val runId: String,
        val text: String,
    ) : DelegateOutcome

    companion object {
        private val RUN = Regex("""^WORK_STARTED #(\d+)""")

        fun of(reply: JsonObject?): DelegateOutcome {
            if (reply == null || reply["output"] == null || (reply["ok"] != null && !reply.flag("ok"))) {
                return Uncertain
            }
            val output = reply.text("output").trimStart()
            if (!output.startsWith("WORK_STARTED")) return Refused(output)
            return Started(
                RUN
                    .find(output)
                    ?.groupValues
                    ?.get(1)
                    .orEmpty(),
                output,
            )
        }
    }
}

/** The existing global Talk lane. No Bot/session binding or custom server capability is required. */
internal object TalkProtocol {
    const val MODEL = "gpt-realtime-2.1"

    fun validateStatus(status: JsonObject) {
        verify(status.flag("ok") && status.flag("configured")) { "Hermes Talk subscription is unavailable" }
        verify(status.text("voiceMode") == "native" && status.text("model") == MODEL) {
            "Hermes Talk must use native gpt-realtime-2.1; other models and cascade are not supported"
        }
        verify(status.text("source") == "codex-oauth") { "Codex subscription required; API billing is disabled" }
    }

    fun validateSession(session: JsonObject) {
        verify(session.flag("ok") && session.text("voiceMode") == "native" && session.text("model") == MODEL) {
            "Voice session model mismatch"
        }
        verify(session.text("authSource") == "codex-oauth") { "Codex subscription required; API billing is disabled" }
        verify(session.text("clientSecret").isNotBlank()) { "Missing ephemeral voice credential" }
    }

    private inline fun verify(
        value: Boolean,
        message: () -> String,
    ) {
        if (!value) throw TalkSetupException(message())
    }
}
