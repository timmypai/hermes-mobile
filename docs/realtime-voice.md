# Native shared Hermes Talk voice

## Scope and source mapping

This integration uses **global shared Hermes Talk**, as explicitly requested after
initial device testing. It does not bind voice to the current Bot or chat session.
Voice transcripts stay in the call. Do not claim that global Talk work belongs to
the Bot whose composer opened it.

Mobile base: `008d9e88778ec3f7a70aab11af2b9917cf541c14` (upstream `main`, fetched 2026-11-16).
Talk protocol source: `3b8f88bc9b39e7916523964debdce504af4d4990`.

| Concern | Existing owner | Android integration |
| --- | --- | --- |
| Shared entry | `ChatScreen` → `ChatInputBar` → `ComposerToolbar` | Separate Realtime action; chat stays mounted |
| Authentication | Mobile `DataScope`, existing headers/cookies/bearer | Frozen connection credentials; close on identity changes |
| Model/auth | Talk `talk_auth.py`, `talk_wire.py` | Native `gpt-realtime-2.1`, exact `codex-oauth`; no API-key fallback |
| Session | Talk `GET /status`, `POST /session` | Empty session body; ephemeral credential only |
| Audio | Existing dashboard `TalkTransport` protocol | Native PeerConnection, microphone, speaker and `oai-events` |
| Tools | Talk `POST /tool` | Only `name` and `arguments`; ordered outputs and response barrier |
| Work status/results | Talk `GET /runs` | Server `runId`, `kind`, `status`, `output`; no client task registry |
| Completion announcement | Existing dashboard global run watcher | Watch only this call's `WORK_STARTED` receipts, announce once while idle |
| Project context | Gateway `projects.list`, Kanban boards and orchestration settings | One additive system item per call; no session instruction or tool change |
| Delegation | Talk `delegate_task` | Held by the app; posted or sent to Kanban triage only after an on-screen choice |

Sources:
- https://github.com/TheSmokeDev/hermes-talk/tree/3b8f88bc9b39e7916523964debdce504af4d4990
- https://github.com/Hy4ri/hermes-mobile/tree/008d9e88778ec3f7a70aab11af2b9917cf541c14

## No backend program patch

Use the existing Talk installation and Codex login. Its official configuration is
OpenAI provider, native voice mode, model `gpt-realtime-2.1`, and
`TALK_PREFER_CODEX_OAUTH=true`. Keep long-lived OAuth credentials on the server.
The custom mobile mint routes have been removed; no Hermes Agent core changes,
canonical Bot binding, new OAuth manager or task executor are required.

Both preflight and the session descriptor must declare Codex OAuth and the exact
native model. Missing subscription support is an error, not a reason to use an API
key, GPT-Live, WebView or STT→LLM→TTS. Entitlement still needs a real call test.

SDP is sent only to `https://api.openai.com/v1/realtime/calls` using an isolated
external client and the ephemeral credential. Dashboard tokens, cookies and
Cloudflare Access headers are not sent to OpenAI. Redirects and automatic retries
are disabled. Server errors are not copied verbatim into logs or UI.

## Lifecycle and work

Calls are foreground-only. The microphone button mutes only the local microphone.
Barge-in and Cancel voice response interrupt provider output only. Hang up releases
local media and keeps the screen open; Back closes it. Neither ever calls a task
cancellation endpoint. Stop Work is a distinct confirmed
operation using Talk's existing `stop_work` tool after refreshing server runs.
Spoken approvals use the existing `resolve_approval` tool; no second approval
registry is introduced.

Reconnect is explicit, creates a fresh voice session and never replays old tool
calls. Previously accepted work remains in Talk. Its raw server status/results
are visible through `/runs`; historical runs are not automatically announced as
new completions. `accepted`, `completed` and `verified` are not interchangeable.

The earlier exact-Bot experiment refused a host without canonical task context.
That requirement and its backend patch were explicitly withdrawn. See the
[acceptance record](testing/realtime-voice-acceptance.md) for measured results and
remaining live-device checks. See [the patch guide](../patches/README.md) for
reapplying only the Android customization after upstream upgrades.

## Project context and handoff

Talk is still global. When the voice dialog opens, the app reads the active
profile's projects (`projects.list`), live Kanban boards bound through
`projectId`, and `GET kanban/orchestration`. There is no project picker: the
focus project is the chat's project (`sessionProjectId`, else the staged
project), and the call connects as soon as the dialog opens. After
`session.created` the app sends one `conversation.item.create` system item with
the projects, the focus project and the handoff rules. It does not replace
Talk's session instructions or tools, and it never requests a response. Catalog
failure narrows the confirmation card; it does not block voice.

Every `delegate_task` call is held. The function output tells the model that
nothing started. The confirmation card shows the proposed project, its folder,
bound boards, the Orchestrator auto-decompose setting and the full brief. The
model's `[project:<id>]` prefix is only a proposal. An untagged brief may
pre-select the focus project, which the card labels as not named in speech.

| Operator choice | Effect |
| --- | --- |
| Send to Orchestrator | `POST kanban/tasks?board=…` with `triage: true`, title, brief and `project_id`; no assignee or workspace override |
| Run now in Talk | The held `name` and `arguments` are posted once, unchanged, to Talk `/tool` |
| Wrong project / Cancel | Nothing is posted; the model is told to re-identify the project or acknowledge |

The handoff is removed before any effect, so a repeated tap cannot act twice.
A held handoff expires after ten minutes; ending or reconnecting the call drops
it. Kanban 4xx is reported as not created; other failures are uncertain and are
not retried. Decisions reach the model as system items only when the
conversation is idle. The UI shows observed tool state (running, waiting,
accepted, created, declined, uncertain), not an inferred user intent.

The tag cannot prove that the user confirmed the project in speech, and Talk's
folder text and `resource_keys` do not route work. Kanban triage carries the
project as structured board/task fields; whether workers honor the board's
project directory is Hermes Kanban behavior, not verified by this app.

## Voice interface

[Dots Focus design](realtime-voice-design.md) records the original immersive UI
and its references. The current call screen keeps the voice-first stage:

- Top bar: the chat's project, then Activity and a link indicator
  (connected / connecting / not connected; tap to reconnect when not connected).
- Centre: the official Hermes Agent logo (the `NousResearch/hermes-agent`
  desktop icon, `drawable-nodpi/hermes_agent_logo.webp`), the dots, and a status
  pill with an animated icon: idle, listening, thinking, running, replying, muted,
  connecting, error or ended. "Thinking" comes from speech end, `response.created`
  and continuation requests; "running" only from a tool call.
- Bottom, in thumb reach: audio output (left), Call / Hang up (centre, red while
  in a call, green when not connected), microphone (right).

Audio output cycles Speaker (default) → Earpiece → Sound off. Speaker selects the
built-in loudspeaker as the communication device, re-applied when playout starts;
Earpiece returns to the earpiece or a connected headset; Sound off silences
assistant playout only, never the microphone. When the system routes the call
through a Bluetooth headset, the microphone button shows a Bluetooth icon.
The dialog window keeps the screen on for the whole call. Leaving the
foreground still ends voice.
