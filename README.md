# Universal AI Agent

A native Android, bring-your-own-key AI assistant with multi-provider routing, screen
understanding, push-to-talk voice, and confirmation-gated Android UI automation.

There is no developer backend and no bundled AI API key. Requests go directly from the
device to the providers you configure, using keys encrypted on the device.

## Features

- **Multi-provider chat** — OpenAI, Google Gemini, Anthropic Claude, Groq, OpenRouter, and
  custom OpenAI-compatible endpoints.
- **Multiple keys per provider** — each configuration has its own nickname, model, priority,
  enable switch, connection test, and error/cooldown state.
- **Per-task routing** — a fallback order for general chat, coding, image understanding,
  screen analysis, action planning, and audio-related task types.
- **Manual routing** — pin one configuration for a chat, with optional fallback.
- **Real streaming APIs** — provider-specific streaming parsers, token usage, and
  provider-reported costs where available.
- **Model discovery** — fetch models from the provider; manually enter a model ID when
  discovery is unavailable.
- **Screen understanding** — explicit Android MediaProjection consent, single-frame capture,
  local preview, optional vision analysis, and a persistent Stop notification.
- **Agent mode in chat** — structured JSON action plans (no model-generated code,
  JavaScript, shell, or coordinates), accessibility snapshots, live tap ripples, a floating
  stop control, and sensitive-action confirmations. The agent keeps working until you cancel.
- **Voice assistant** — push-to-talk Android speech recognition with a live amplitude orb,
  text-to-speech, distinct user/assistant haptics, and one-tap handoff to Agent mode.
- **Local history and diagnostics** — conversations, attachments, request metadata, token
  counts, error categories, and read-only route diagnostics.
- **Security controls** — Android Keystore AES-GCM key encryption, masked keys, sanitized
  exports, backup exclusions, and local data deletion controls.

## Screens

Welcome/onboarding, chat with Agent mode, API key manager, add/edit provider, model browser,
routing priority, voice assistant, screen understanding, task progress, history, settings,
privacy/security, and diagnostics.

## Requirements

- Android 8.0 (API 26) or newer
- JDK 21 for building
- Android SDK Platform 36 and Build-Tools 36.0.0
- The repository's Gradle wrapper (Gradle 8.13)

The project uses AGP 8.13.2, Kotlin 2.3.21, Jetpack Compose, Navigation Compose,
DataStore, OkHttp, and kotlinx.serialization.

## Build locally

```bash
# Point Gradle at your SDK (not committed)
printf 'sdk.dir=/path/to/Android/Sdk\n' > local.properties

./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The debug application ID is `com.universalaiagent.debug`; release uses
`com.universalaiagent`.

## First run

1. Open **API key manager → Add provider**.
2. Choose a provider, enter a nickname and a real API key, choose or type a model, and
   save. Mark "supports vision" only when the selected model really accepts images.
3. Tap **Test** in the key manager to run a model-discovery/connectivity check. Then open
   **Models** and select a model from the discovered list, or set one manually in the editor.
4. Use **Routing priority** to order fallbacks per task. Individual configurations can be
   excluded without deleting them.
5. Send a chat message. Routing notices show which configuration is being used or retried.

No API keys are required to open the app or browse settings. Requests require a configured
provider.

## Android permissions and setup

The app requests only permissions that correspond to user-visible features:

| Permission | Purpose |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Provider API calls |
| `RECORD_AUDIO` | Push-to-talk speech recognition |
| `VIBRATE` | Distinct haptics for user speech and assistant replies |
| `POST_NOTIFICATIONS` | Screen-sharing foreground notification (Android 13+) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Screen capture session |

Additional setup:

- **Accessibility**: enable **Universal AI Agent automation** in Android Settings →
  Accessibility before using Agent mode. The service reads the active window, draws the
  ripple/stop overlay, and exposes supported actions only while a task runs.
- **Screen understanding**: Android shows its own MediaProjection consent dialog every time.
  Sharing is visible in the notification shade and can be stopped there or in the app.
- **Voice**: the first microphone use asks for permission. Android or the device's
  recognition service performs speech-to-text; only the transcript is sent to the AI provider.
- **Install APK**: Android may ask you to allow installs from the source you used (browser
  or file manager). The app never asks you to bypass this system control.

## Automation safety model

The model may select from a fixed set of UI actions: launch app, click, long-click, scroll,
input text, press Back, read screen, wait, request confirmation, and finish. It cannot send
arbitrary code or coordinates.

Enforced locally:

- the agent runs until it reports completion, hits an unrecoverable action failure, or you
  press **Cancel** in chat or the floating overlay;
- three planning rounds with no screen change (outside scrolling/waiting) stop the run as
  loop protection;
- sensitive or irreversible actions require confirmation and wait as long as needed;
- the planner is told not to bypass lock screens, biometrics, passwords, or Android
  security dialogs;
- password fields are not read by the accessibility snapshot code.

While a task runs, a small draggable overlay shows **Screen / Sound / Stop** controls and
paints a ripple wherever the agent taps, types, or scrolls. **Screen** hides or shows the
touch indicators, **Sound** toggles spoken feedback, and **Stop** cancels immediately.

Automation uses accessibility only. It cannot unlock a locked device, defeat secure windows,
or grant permissions that Android requires the user to grant directly.

## Routing, retries, and cooldowns

- Requests use the selected task's configured order, or global priority when no order exists.
- Disabled configurations and configurations without the required capability are skipped.
- Vision tasks require a configuration whose saved "supports vision" flag is on or whose
  selected discovered model reports image support.
- Authentication/permission errors are not retried. Network, timeout, server, and rate-limit
  errors can be retried with bounded delays and then fall back to the next configuration.
- Providers enter a cooldown after certain failures; diagnostics shows current state.
- If all eligible providers are cooling down, the error message reports the approximate wait.

## Privacy and data

- API keys are encrypted with an Android Keystore-backed AES-GCM key and stored in private
  app storage. Keys are masked in the UI and excluded from exports and Android backup.
- Conversations are written to `conversations.json` only when history is enabled. Attached
  images are downscaled JPEG copies in private app storage.
- Request metadata (provider, model, tokens/cost when reported, error category) is stored
  locally in `usage_history.json`. Prompts and responses are not copied into usage records.
- Screen analysis sends only the frame you explicitly capture, plus your question, through
  the screen-analysis route. Turning off cloud image processing blocks image uploads.
- Automation sends visible UI text/metadata to the routed action-planning provider for the
  duration of the task.
- No analytics, ads, telemetry, crash-reporting SDK, or developer-operated server.
- Sanitized configuration export omits API keys and copies JSON to the clipboard.

The in-app **Privacy and security** screen documents storage, uploads, Android limitations,
and deletion controls. A compromised or rooted device can expose any client-side secret; use
scoped, revocable provider keys with spending limits.

## Tests

Unit tests cover:

- API configuration/endpoint validation and key masking;
- provider error classification, retry rules, and cooldowns;
- OpenAI, Gemini, and Anthropic model discovery via a local mock HTTP server;
- OpenAI streaming SSE parsing and provider error propagation;
- model/capability parsing and stream deltas;
- routing priority ordering, disabled/vision filtering, manual mode, and fallback order;
- structured action validation, sensitive-action detection, loop signatures, and JSON extraction.

```bash
./gradlew testDebugUnitTest
```

Report: `app/build/reports/tests/testDebugUnitTest/index.html`.

Not covered by JVM unit tests: real Android Keystore operations, MediaProjection capture,
accessibility tree traversal, speech recognition/TTS, and on-device UI instrumentation.
Those require an emulator or physical device and are intentionally not faked in unit tests.

## GitHub Actions

### Debug APK workflow

File: `.github/workflows/android-apk.yml`

Triggers:

- push to `main`;
- pull request targeting `main`;
- manual `workflow_dispatch`.

The workflow uses JDK 21, installs Platform 36/Build-Tools 36.0.0 with the Android SDK
action, validates the Gradle wrapper, runs `testDebugUnitTest`, builds with
`assembleDebug`, verifies the APK is non-empty, and uploads it as:

```text
Universal-AI-Agent-debug-APK
```

The run summary includes build/test/verification status, branch, commit SHA, APK filename,
APK size, variant, upload result, and the run link. Artifacts are retained for 14 days;
test reports are retained for 7 days. Repository permissions are read-only.

### Download the APK

1. Open the repository on GitHub.
2. Select **Actions**.
3. Open the latest successful **Android APK** run.
4. Scroll to **Artifacts** and download `Universal-AI-Agent-debug-APK`.
5. Extract the ZIP; it contains the APK produced by Gradle.
6. Install it on an Android 8.0+ device. Android may ask you to allow installs from the
   selected app.

Do not download artifacts from failed or still-running workflows.

### Signed release workflow (optional)

File: `.github/workflows/android-release.yml`

This workflow is manual only. It refuses to publish an APK unless all signing secrets are
present and `apksigner` verifies the output. Configure these repository secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of your release keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Release key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

Generate the Base64 value locally (do not paste keystores into the repository):

```bash
base64 -w 0 release.keystore
```

Local release builds use the equivalent environment variables `ANDROID_KEYSTORE_PATH`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. If they are
missing, the debug workflow still builds normally and no release APK is claimed.

Never commit a keystore, `keystore.properties`, passwords, or API keys.

## Project structure

```text
app/src/main/java/com/universalaiagent/
  MainActivity.kt              Navigation host and activity-scoped ViewModels
  AppContainer.kt              Manual dependency wiring
  UniversalAiAgentApp.kt       Application/container owner
  data/model/                  Serializable models, task/provider types
  data/local/                  DataStore settings
  data/security/               Keystore crypto and encrypted key vault
  data/provider/               HTTP helpers, adapters, parsers, validation
  data/repository/             Provider, conversation, usage, attachment storage
  data/routing/                Candidate planning, fallback, retries, cooldowns
  automation/                  Accessibility service, action validator, executor, task store
  screen/                      MediaProjection foreground service/controller
  voice/                       SpeechRecognizer + TextToSpeech manager
  ui/                          ViewModels, Compose screens, components, theme
app/src/test/java/...          JVM unit tests
.github/workflows/             Debug and optional signed-release builds
```

## Known limitations

- Provider adapters implement text chat and image input. No provider adapter currently
  accepts raw audio uploads, so voice uses Android speech recognition/TTS and audio task
  routes are not used by the voice screen.
- Model discovery and capability metadata depend on the provider. Vision support may be
  inferred from model IDs; verify it before relying on image requests.
- Screen capture respects secure-window restrictions. Some apps prevent screenshots.
- Automation depends on exposed accessibility nodes; canvas-only or heavily custom UIs may
  not be operable.
- Task reports are held in memory for this build and are not persisted across process death.
- The app cannot guarantee a provider's pricing, retention, or model behavior.

## License

This repository contains no third-party API keys. Add your own provider terms and licensing
before redistributing a modified build.
