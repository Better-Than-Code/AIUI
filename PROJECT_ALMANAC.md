# PROJECT ALMANAC - Pally AI / SignalDeck V2

## 1. Agile Charter & V2 Goals
- **Mission**: Transition Pally AI from a robust cellular RPC messaging client into a secure, encrypted, and bandwidth-optimized decentralized mini-app platform.
- **Definition of Done**: All features must have zero external mock data, pass Robolectric tests, compile successfully, and be backed by SQLite/Room persistence.

---

## 2. Architecture Decision Records (ADR)
- **ADR-001: Cryptographic Wire Format**: Use standard AES-GCM encryption wrapped inside cellular wire frames (`CryptoKeyManager`).
- **ADR-002: Differential Patching**: Delta-patch JSON trees using standard JSON Patch (RFC 6902) subsets over MMS or compressed PDU streams.
- **ADR-003: Carrier-Grade Lossless Payload Carriers**: Mandate lossless PDF containers and PNG metadata chunks (`paLY`) for zero-data state sync and blueprints, discarding lossy video steganography due to carrier transcoder macroblocking.
- **ADR-004: Single-Container MMS Batching**: Enforce single 300KB - 600KB compressed batch containers (Zstandard/Gzip) to avoid carrier MMSC queue throttling and 10-20s transaction latency overhead.
- **ADR-005: Edge SLM CPU-Only Execution & mmap Lifecycle**: Pin sub-billion parameter SLMs (SmollM2 / Qwen2.5) to efficiency cores via CPU/XNNPACK with memory-mapped load-on-demand to respect LMK limits on budget hardware (Motorola XT2513V / 4GB RAM).
- **ADR-006: Native Continuous Gesture Leaf Primitives & Watchdog Safe-Boot Guard**: Pre-compile high-frequency continuous touch primitives (`CanvasLeafView`) directly into the APK while keeping tree topology dynamic via SDUI. Guard all runtime dynamic mutations (blueprints, themes) with `WatchdogSafeBootManager` utilizing a 5-second unhandled exception probation window before committing state to stable disk.
- **ADR-007: Zero-Network Canvas Isolation & Hardware/Theming Safety Guardians**: Enforce that all drawing strokes, vector points, and gesture interactions are 100% on-device and local-only, backed by in-memory `CanvasStrokeStore` and local file snapshot persistence without network transmission. Enforce WCAG 2.2 Level AA contrast (minimum 4.5:1) in `ChatThemeConfig` to guarantee readability against AI-generated color palettes, and harden `scheduleAlarm` against Android 12/13/14 `SecurityException` using `canScheduleExactAlarms()` runtime checks.
- **ADR-008: Hybrid Dynamic Scripting Engine & Native SDUI Selection Primitives**: Pair deterministic recursive-descent arithmetic (`SimpleMathParser`) with asynchronous sandboxed JavaScript execution (`JavaScriptSandbox` via AndroidX JavaScriptEngine, capped with a 50ms hard timeout) to power on-device math mutations without network roundtrips. Render native Material 3 segmented pill buttons (`Select`/`Radio` node) and interpolate dynamic string templates with localized currency and numeric formatting directly inside the client host.
- **ADR-009: Non-Default Telephony Ingestion, Concatenation Clustering & MMS Backoff**: When operating as a non-default SMS app, intercept cellular communications via reactive `Telephony.Sms.CONTENT_URI` and `Telephony.Mms.CONTENT_URI` `ContentObserver`s hosted in `HardenedTelephonyObserverService`. Debounce multi-part SMS arrivals (1200ms) with monotonic sequence assembly (`_id ASC`) grouped by sender and 4-second arrival clusters. Extract MMS payloads using a 3-stage exponential backoff loop (`0ms`, `1500ms`, `3500ms`) verifying `m_type == 132` (PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF) and resolving `type == 137` (`FROM` address) to prevent partial reads during asynchronous carrier MMSC downloads. Reconcile delta queries on `onResume()` to ensure zero dropped messages across OEM background terminations.
- **ADR-010: Headless AI Dev Harness, Schemaless Document Persistence & Semantic Design System**: Eliminate dynamic SQL DDL runtime hazards by standardizing on a single Room entity `MiniAppDocumentEntity` backed by `MiniAppDocumentDao` for schemaless document storage. Guard runtime blueprints with `BlueprintLinter` for static node/binding validation, `BlueprintFuzzer` for boundary-condition action testing, `SemanticDesignTokens` for dynamic theme-aware styling and vector icon resolution, and `BlueprintPatcher` for state-preserving RFC 6902 delta updates.
- **ADR-011: Inbound Transport Failover Protocol (256B SMS Ceiling & 32x32 MMS Bundle Promotion)**: Strictly enforce a 256-byte ceiling for plain SMS delivery (<= 2 segments) to eliminate carrier-level silent packet drops and multi-segment shadowbans observed during active on-device testing on Verizon/TracFone. Payloads exceeding 256B are promoted to a single-container MMS bundle carrying a lightweight, carrier-compliant 32x32 visual anchor (`aiui_mms_anchor.png`) and payload body encapsulated within SMIL markup. Companion SMS preambles are strictly prohibited. The 32x32 visual anchor is classified as `AttachmentType.VISUAL_ANCHOR` to prevent cluttering the chat bubble interface while satisfying carrier PDN routing requirements.
- **ADR-012: MMS Part Table Latency Retry & SMIL Body Accumulator**: Address carrier MMSC asynchronous write latencies where PDU header records land in telephony SQLite seconds ahead of binary/text parts (`content://mms/{id}/part`). Employ a 4-stage adaptive retry backoff (`0ms`, `1200ms`, `2500ms`, `4500ms`) in `HardenedMmsParser`. In `extractPartsDetailed`, accumulate multi-tag sequential `<text>` elements across `<par>` regions, decode direct `text` and `alt` attributes on self-closing media tags, and execute recursive 2-pass XML entity unescaping (`&quot;`, `&amp;`, `&lt;`, `&gt;`, `&#39;`). Synchronize extraction delegation between `HardenedTelephonyObserverService` and `PallyMmsHelper` to prevent redundant carrier part parsing.
- **ADR-013: Telephony Background Survival, Strict Slash Commands & Flexible Schema Keys**: Demote `HardenedTelephonyObserverService` from aggressive background foreground starts on Android 12+ (API 31+) with graceful `ForegroundServiceStartNotAllowedException` guards, relying on `PallySmsReceiver` with `goAsync()` as the primary background SMS ingress. Enforce strict `/` prefix matching for local commands to prevent swallowing natural conversational text. Support flexible JSON schema keys (`sym`/`symbol`, `chg`/`changePercent`/`change_percent`) and multi-asset dynamic badges in `MarketTicker` and `MarketChatCard`.
- **ADR-014: UI/UX Decluttering, Character Standard Counter & Hoisted Scroll Preservation**: Remove raw wire diagnostics ("CRC Verified", "PDU Count") from incoming chat bubbles by default, surfacing standard timestamps and preserving wire inspection strictly via the long-press overlay drawer. Standardize input bar telemetry to Google Messages style ("chars left / segment"). Replace the static "Cellular Gateway" header pill with active carrier cooldown countdowns and simulation badges. Hoist `LazyListState` to `MainActivity` scope to prevent scroll jumping and reflow during bottom tab transitions. Equip `MarketChatCard` with an interactive inline search field supporting custom ticker RPC queries (`market_ticker:<SYM>`).
- **ADR-015: 2-Tab Architecture, Ingress Observers, Sender Validation & Anchor Stripping**: Enforce strict 2-tab bottom navigation (Chat & Apps) with `UnifiedAppDrawerTab` unifying glanceable horizontal widget shelves, a 4-column mini-app grid, and long-press blueprint management. Intercept system MMS arrivals via `TelephonyMmsObserver`, validate all senders via `CellularServiceManager.isSenderRecognized`, suppress 32x32 carrier visual anchors before reaching UI, and isolate local slash commands via `OfflineCommandRouter` without consuming cellular radio bandwidth.
- **ADR-016: Feed-Tail Card Iteration Projection & Superseded State Anchoring**: When an incoming SDUI payload updates or patches an existing widget or mini-app (`widgetId`), project the refreshed card at the tail of the chat thread with an incremented revision (`v1.x`) and an "Updated from previous revision" badge. Mark upstream historical versions as `isSuperseded = true` with a pointer to the newest revision (`supersededByMessageId`). In the UI, render superseded cards in a clean, compact collapsible state with a direct "See latest at tail ↓" jump affordance, eliminating in-place scroll reflows and keeping the active card anchored in context.

---

## 3. Epics & Sprint Breakdown

### Epic 1: End-to-End Asymmetric Encryption (Cellular Cryptographic Wire Layer) [COMPLETED]
- **Sprint 1.1**: Key Management & AES-GCM Encryption (`CryptoKeyManager.kt`). [DONE]
- **Sprint 1.2**: Wire frame decryption and verification integration in `WidgetData.parse`. [DONE]

### Epic 2: Differential Mini-App Patching (OTA Deltas) [COMPLETED]
- **Sprint 2.1**: JSON AST delta patch engine (`JsonPatchEngine.kt`) adhering to RFC 6902 for bandwidth-optimized mini-app updates over cellular pipes. [DONE]

### Epic 3: Cellular Voice MMS Pipeline [COMPLETED]
- **Sprint 3.1**: Audio recording compression and MMS multi-part carrier wrapper.
  - Implemented `CellularAudioCompressor.kt` with AMR-WB / low-bandwidth adaptive transcoding to conform to carrier MMS size thresholds (300KB - 600KB).
  - Integrated `FileProvider` content:// URI conversion and multi-part intent wrapping in `PallyMmsHelper.dispatchCarrierMms`.
  - Integrated compression pipeline into `ChatViewModel` outbound attachment flow and `AudioRecorderManager` stop flow.
  - Added comprehensive Robolectric unit test verifying compression ratio computation and carrier MMS intent generation. [DONE]

### Epic 4: Closed-Loop Delivery Watchdog & Cross-Channel Fallback Engine [COMPLETED]
- **Sprint 4.1**: Backend Dispatch Watchdog & Webhook Listener (35s timeout & state machine). [DONE]
- **Sprint 4.2**: Transport Fallback Orchestrator (RCS -> MMS -> SMS ladder & re-encoding). [DONE]
- **Sprint 4.3**: Client-Side Delivery Receiver (`DeliveryBroadcastReceiver`) & App Ack Loop (`ack:<msg_id>`). [DONE]

### Epic 5: Zero-Touch Self-Healing Messaging Engine [IN PROGRESS]
- **Sprint 5.1**: Autonomous Background Outbox Recovery (Exponential backoff, jitter, and silent self-healing queue flushing without user intervention - "Don't Make Me Think"). [COMPLETED]
- **Sprint 5.2**: Clean UI Principles Enforcement (Preserving minimalist chat interface; keeping all developer diagnostic tools strictly inside the toggled Dev Tab). [COMPLETED]

### Epic 6: The Living Software Ecosystem (Self-Healing Edge Architecture) [COMPLETED]
- **Sprint 6.1**: **The Black Box (Mutation Tracking Infrastructure)**. Construct local `MutationLogEntity` Room Database tables. Build a manual JSON export capability in the Diagnostics Tab for manual sync with AI Studio. Implement safe dynamic Server-Driven UI (SDUI) JSON patching. [COMPLETED]
- **Sprint 6.2**: **The Compressor (Algorithmic BPE Engine)**. Implement a static Byte-Pair Encoding (BPE) tokenizer to heavily compress outbound prompts and decompress inbound payloads in `CarrierSafeQueueEngine.kt`. [COMPLETED]
- **Sprint 6.3**: **The Medic (Anomaly & Fallback Guardian)**. Wire a global error interceptor for JSON parsing and UI faults. Introduce silent `[DIAGNOSTIC_PING]` SMS fallback routing and auto-rollback mechanics to previous known-good blueprints. [COMPLETED]
- **Sprint 6.4**: **The Router (Intent Classification via TFLite)**. Integrate quantized sub-15MB TFLite classification (TinyBERT/MobileBERT). Route `LOCAL_UI_CHANGE` intents to local execution and `EXTERNAL_KNOWLEDGE` intents to the remote SMS AI. [COMPLETED]

### Epic 7: Safety & Governance (The Human Override) [COMPLETED]
- **Sprint 7.1**: **Admin Approval Mode (Immediate Action Popup)**. Implement a global interceptor for incoming SDUI mutations and Dynamic Feature deployments. Instead of instantly executing, changes are queued in the `MutationLogEntity` (Black Box) as `PENDING_ADMIN_APPROVAL`. A floating `AlertDialog` popup forces human review (Approve, Reject, or Ignore to queue), ensuring the user maintains final authority over self-modifying code. [COMPLETED]

### Epic 8: Self-Contained Edge Execution & Zero-Network Canvas Hardening [COMPLETED]
- **Sprint 8.1**: **100% On-Device Canvas Vector Resilience & Local Storage**. Decoupled canvas drawing from any network transmission. Integrated `CanvasStrokeStore` for recomposition state retention and offline storage snapshot writing to internal app cache. [COMPLETED]
- **Sprint 8.2**: **Cellular Transport Parser Hardening**. Hardened `DualResponseParser` against unpadded Base64, whitespace, line-wrap corruptions, and multi-part SMS boundary splits. [COMPLETED]
- **Sprint 8.3**: **Dynamic Theming WCAG 2.2 AA Contrast Guardian**. Implemented automated relative luminance & contrast ratio validation in `ChatThemeConfig` to guarantee 4.5:1 text-to-background contrast. [COMPLETED]
- **Sprint 8.4**: **Android 12/13/14 Exact Alarm Permission Hardening**. Guarded `scheduleAlarm` in `DynamicNativeBridge` with `canScheduleExactAlarms()` checks and graceful fallbacks. [COMPLETED]

### Epic 9: Hybrid Dynamic Scripting Engine & Native SDUI Selection Primitives [COMPLETED]
- **Sprint 9.1**: **Sub-50ms Sandboxed JavaScript & Arithmetic Execution Bridge**. Integrated AndroidX `JavaScriptEngine` / `JavaScriptSandbox` isolate execution alongside a deterministic, zero-latency recursive-descent expression parser (`SimpleMathParser` / `SafeScriptEngine`) inside `ActionExecutor.kt` for instant mathematical and algorithmic state transformations without network roundtrips. [COMPLETED]
- **Sprint 9.2**: **Native SDUI Select / Segmented Button / Radio Group Component**. Added first-class support for `select`, `segmented`, `radio`, `tabs`, and `chips` nodes with accessible 48dp touch targets, active state styling, and bidirectional state synchronization in `DynamicAppHost.kt`. [COMPLETED]
- **Sprint 9.3**: **Dynamic Text Template Interpolation & Localized Number/Currency Formatting**. Implemented regex token interpolation (`$state.var`, `${state.var}`, `item.prop`) with automated currency (`$%.2f`), percent, and fixed decimal formatting in text and badge nodes. [COMPLETED]
- **Sprint 9.4**: **Durable Mini-App Deck Persistence & In-Feed Action Footers**. Auto-injected in-feed preview action bars with one-tap "Install to Deck" workflows writing blueprints and local state snapshots to Room (`AppBlueprintDao` / `MiniAppDeckManager`). [COMPLETED]

### Epic 10: Non-Default Cellular Telephony Ingestion & MMS Extraction Pipeline [COMPLETED]
- **Sprint 10.1**: **Hardened Telephony Observer Foreground Service**. Built `HardenedTelephonyObserverService` hosting dual `ContentObserver` instances (`Telephony.Sms.CONTENT_URI` and `Telephony.Mms.CONTENT_URI`), debouncing multi-part carrier SMS arrivals (1200ms) with chronological `_id ASC` segment reassembly and partial wake-lock protection. [COMPLETED]
- **Sprint 10.2**: **Carrier-Grade MMS Part Parser & Async Retry Engine**. Built `HardenedMmsParser` with 3-stage backoff retries (`0ms`, `1500ms`, `3500ms`), resolving `type == 137` (`PduHeaders.FROM`) and extracting inline/stream `text/plain` and media streams to cached `MessageAttachment` files. [COMPLETED]
- **Sprint 10.3**: **Lifecycle Missed-Message Delta Reconciliation**. Linked `MainActivity.onResume()` to `HardenedTelephonyObserverService.reconcileMissedMessages()` to query and dispatch any SMS/MMS messages arriving during process hibernation or deep sleep. [COMPLETED]

### Epic 11: Headless AI Development Harness & Dynamic Micro-App Runtime [COMPLETED]
- **Sprint 11.1**: **Schemaless SQLite Document Store**. Implemented `MiniAppDocumentEntity` and `MiniAppDocumentDao` in Room Database v8, supporting structured document upserts, deletions, and reactive queries (`observeCollection`) without requiring dynamic SQL DDL mutations. [COMPLETED]
- **Sprint 11.2**: **AST Schema Linter & Structural Validator**. Created `BlueprintLinter.kt` verifying component node types against registered Compose factories, ensuring accessibility compliance and validating variable bindings against declared initial state. [COMPLETED]
- **Sprint 11.3**: **Semantic Design Token Palette & Icon Registry**. Built `SemanticDesignTokens.kt` resolving spacing, corner radii, theme-aware contrast colors, and vector icons (`Search`, `Check`, `Settings`, `ArrowBack`, etc.) dynamically for micro-apps. [COMPLETED]
- **Sprint 11.4**: **Pre-Render Property Fuzzer & Headless Test Runner**. Built `BlueprintFuzzer.kt` running synthetic inputs (0, negative numbers, boundary strings) through `ActionExecutor` to verify state mutations deterministically before rendering cards. [COMPLETED]
- **Sprint 11.5**: **State-Preserving AST Delta Patcher**. Built `BlueprintPatcher.kt` to apply RFC 6902 JSON updates to live UI trees while maintaining active user input and interaction state. [COMPLETED]

---

## 4. Sprint History & Build Log
- **Build 17 (Version 2.6)**: Completed monochrome contrast system, floating horizontal circular action bar, balanced-brace JSON stream demuxer, multi-thread conversation persistence, and V2 End-to-End Encryption Layer (`CryptoKeyManager`).
- **Build 19 (Version 2.8)**: Packaged release v18, incremented versionCode to 18 and versionName to 2.7, updated `releases.json` and `version.json`, and pruned older APKs in `apk/releases/` to strictly maintain the last 5 release builds (v14 to v18).
- **Build 20 (Version 2.9)**: Initiated Closed-Loop Delivery Watchdog & Cross-Channel Fallback Engine sprint, designing client-side acknowledgement and telephony delivery monitoring.
- **Build 21 (Version 3.0)**: Executed Sprint 5.1 & 5.2 (Zero-Touch Self-Healing Messaging Engine), implementing autonomous background watchdog recovery for stalled in-flight outbox packets (>35s) and maintaining a pristine, uncluttered chat UI.
- **Build 22 (Version 3.1)**: Executed Sprint 4.2 (Transport Fallback Orchestrator), implementing tier-based transmission fallback routing (Tier 1 RCS/Direct SMS → Tier 2 MMS Binary Container → Tier 3 Concatenated 140ch SMS Shorthand) in `CarrierSafeQueueEngine` and `OutboxDao`.
- **Build 23 (Version 3.2)**: Executed Sprint 2.1 (Differential Mini-App Patching / OTA Deltas), implementing `JsonPatchEngine.kt` for RFC 6902 compliant JSON AST delta patching over compressed cellular channels.
- **Build 24 (Version 3.2 / v19)**: Packaged release v19 (versionCode 19, versionName "3.2"), updated `releases.json` and `version.json`, and pruned older release APKs in `apk/releases/` to strictly maintain the last 5 release builds (`v15` to `v19`).
- **Build 25 (Version 4.0)**: Executed Epic 6 (The Living Software Ecosystem) and Epic 7 (Safety & Governance). Implemented offline Black Box mutation tracking, BPE payload micro-compression, automated SDUI Medic crash-fallback loop, TFLite local intent routing, and Admin Approval Mode for SDUI mutations. Incremented versionCode to 21 and versionName to "4.0".
- **Build 26 (Version 4.0 / v21)**: Packaged release v21 (`pallyai-v21.apk`, `pallyai-latest.apk`, `pally-cellular-ai.apk`), updated `releases.json` and `version.json`, and cleaned up temporary migration scripts.
- **Build 27 (Version 4.1)**: Executed Epic 3 (Cellular Voice MMS Pipeline). Implemented `CellularAudioCompressor` with adaptive AMR-WB / low-bandwidth encoding, secured `FileProvider` content:// URI transformation for MMS attachments, integrated end-to-end voice note compression in `ChatViewModel` and `AudioRecorderManager`, and verified with Robolectric unit tests and clean debug APK compilation.
- **Build 28 (Version 4.2)**: Hardened Cellular SMS/MMS Ingestion and Transmission Pipeline. Bound hardware radio `sentIntent` (`SMS_SENT`) and `deliveryIntent` (`SMS_DELIVERED`) PendingIntents to single- and multipart SMS dispatches in `CarrierSafeQueueEngine`, transitioned OutboxEntity management to acknowledge upon hardware confirmation, upgraded `DeliveryBroadcastReceiver` to handle radio failures with auto-retry, and introduced a 3-stage adaptive polling loop in `PallyMmsHelper` to resolve carrier MMSC download latency gaps.
- **Build 29 (Version 4.2 / v22)**: Packaged release v22 (`pallyai-v22.apk`, `pallyai-latest.apk`, `pally-cellular-ai.apk`), incremented versionCode to 22 and versionName to "4.2", updated `apk/version.json` and `apk/releases.json`, and pruned older release builds to maintain the rolling 5 latest versions.
- **Build 30 (Version 4.2 / v22 Binary Optimization)**: Reduced release binary footprint to 28.5 MB (29,882,190 bytes) by standardizing on 64-bit ARM (`arm64-v8a`) with legacy compressed JNI packaging, successfully bringing the APK well below the cloud control-plane proxy limits (32 MB) and GitHub single-file commit thresholds. Updated distribution targets in `apk/releases/pallyai-v22.apk`.
- **Build 31 (Version 4.3)**: Implemented 60-120Hz continuous native gesture `CanvasLeafView` for dynamic mini-app drawing, offline `DynamicAlarmReceiver` and storage bridge methods, `WatchdogSafeBootManager` with 5s crash probation & automated rollback, cellular transport GZIP Base64 unpacking (`DATA:GZ`), and live cellular RFC 6902 JSON-patchable `ChatThemeManager`.
- **Build 32 (Version 4.4)**: Executed Epic 8 (Self-Contained Edge Execution & Zero-Network Canvas Hardening). Locked down `CanvasLeafView` as 100% on-device interactive leaf with `CanvasStrokeStore` recomposition persistence and offline file snapshot exports. Hardened `DualResponseParser` with unpadded Base64 and carriage-return resilience, integrated WCAG 2.2 Level AA Contrast Guardian in `ChatThemeConfig`, and protected `scheduleAlarm` on Android 12/13/14+ against `SecurityException`. Verified via `gradle assembleDebug`.
- **Build 33 (Version 4.5)**: Executed Epic 9 (Hybrid Dynamic Scripting Engine & Native SDUI Selection Primitives). Resolved the interactive tip calculator and arithmetic calculation bottlenecks by introducing `SimpleMathParser` + `SafeScriptEngine` and `JavaScriptSandbox` bridge inside `ActionExecutor.kt`. Added native Material 3 segmented button / radio selection components (`Select`), dynamic template string interpolation (`$state.var`, `${state.var}`) with currency and percent formatting, and one-tap persistent Room database installation in `DynamicAppHost.kt`. Verified with unit tests (`:app:testDebugUnitTest`) and compiled debug APK (`:app:assembleDebug`).
- **Build 34 (Version 4.6)**: Executed Epic 10 (Non-Default Cellular Telephony Ingestion & MMS Extraction Pipeline). Implemented `HardenedTelephonyObserverService` with dual reactive SMS/MMS content observers, multi-part segment reassembly with 1200ms debounce, `HardenedMmsParser` with 3-stage backoff (`0ms`, `1500ms`, `3500ms`) and `PduHeaders.FROM` (type 137) resolution, and `MainActivity.onResume()` delta reconciliation. All 24 unit tests passing green and debug APK successfully generated.
- **Build 35 (Version 4.6 / Release v24 Packaged)**: Executed Epic 11 (Headless AI Development Harness & Dynamic Micro-App Runtime). Integrated schemaless `MiniAppDocumentEntity` & `MiniAppDocumentDao` into Room Database v8, built AST `BlueprintLinter`, pre-render `BlueprintFuzzer`, `SemanticDesignTokens` palette and icon resolver, and state-preserving `BlueprintPatcher` (RFC 6902). Full test suite passed and debug APK generated.
- **Build 36 (Version 4.7 / Release v25 Packaged)**: Executed Epic 12 (High-Leverage Cellular & State Guardians). Implemented `FuzzyStreamDemuxer` for self-repairing JSON stream recovery from truncated SMS frames, `LocalHealerAgent` for on-device math formula and syntax sanitization, `Gsm7Normalizer` with live wire tariff PDU cost badge in the chat input bar, `AtomicStateMutex` with automated SQLite state checkpointing on `onPause()` to eliminate LMK form amnesia, and RDP `VectorCurveDecimator` reducing canvas drawing vector points by 85%. Deployed binary `pallyai-v25.apk` (30.0 MB).
- **Build 37 (Version 5.0 / Epic 1 Implemented)**: Executed Epic 1 (Telephony Observer Cursor Hardening & Batch Reconciliation). Implemented `TelephonyCheckpointStore` for persistent monotonic row ID tracking surviving OS process kills, dynamic delta windowing (`WHERE _id > ? ORDER BY date ASC, _id ASC`) eliminating `LIMIT 20` burst truncations, non-cancelling sliding-window debouncer (`Channel<Unit>(Channel.CONFLATED)` with 350ms/800ms throttle), and multi-part SMS clustering with sliding temporal proximity (<= 5000ms) and dual-key monotonic sorting. Verified with Robolectric test suite and compiled debug APK via `gradle assembleDebug`.
- **Build 38 (Version 5.0 / Epic 2 Implemented)**: Executed Epic 2 (Inbound Transport Failover Protocol). Implemented `TransportFailoverEngine` establishing the strict 256B ceiling for plain SMS delivery and automatic promotion to MMS bundles with a 32x32 visual anchor (`aiui_mms_anchor.png`). Encapsulated payload bodies inside single-container SMIL markup and prohibited companion SMS preambles. Classified visual anchors under `AttachmentType.VISUAL_ANCHOR` so they do not show up as user media while satisfying carrier PDN routing. Integrated in `CarrierSafeQueueEngine`, `CellularMessageDispatcher`, `HardenedMmsParser`, and `PallyMmsHelper`. Verified via Robolectric test suite (`:app:testDebugUnitTest`) and compiled debug APK (`:app:assembleDebug`).
- **Build 39 (Version 5.0 / Epic 3 Implemented)**: Executed Epic 3 (MMS Part Table Latency Retry & SMIL Body Accumulator). Replaced 3-stage backoff with a 4-stage adaptive retry loop (`0ms`, `1200ms`, `2500ms`, `4500ms`) to accommodate carrier MMSC latency. Built `extractPartsDetailed` and `extractTextFromSmil` in `HardenedMmsParser` supporting multi-tag sequential `<text>` accumulation, self-closing media tag `text`/`alt` attribute resolution, and 2-pass recursive XML entity unescaping (`&quot;`, `&amp;`, `&lt;`, `&gt;`, `&#39;`). Delegated `PallyMmsHelper.extractMmsParts` directly to `HardenedMmsParser` to standardize MMS ingestion. Verified via full Robolectric test suite (`:app:testDebugUnitTest`) and compiled debug APK (`:app:assembleDebug`).
- **Build 40 (Version 5.0 / Epic 4 Implemented)**: Executed Epic 4 (15-Minute Carrier Cooldown Circuit Breaker). Implemented `CarrierCooldownCircuitBreaker` with 3-state finite state machine (`CLOSED`, `OPEN`, `HALF_OPEN`), persistent SharedPreferences backing against OS LMK process kills, and 15-minute cooldown (900,000ms). Trips automatically on 3 consecutive radio failures or immediately on `RESULT_ERROR_LIMIT_EXCEEDED` (code 5) / `RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED`. Halts outbound cellular transmission loops and watchdog thrashing in `CarrierSafeQueueEngine` while preserving queued messages in SQLite Outbox. Features line-clearance canary probing in `HALF_OPEN`, manual administrative reset/probe overrides in `PallySettingsBottomSheet`, live countdown banner in `CellularChatTab`, and comprehensive Robolectric unit test coverage passing green. Verified via `gradle assembleDebug`.
- **Build 41 (Version 5.0 / Release v28 Packaged)**: Packaged production release v28 (Version 5.0, `versionCode = 28`, `versionName = "5.0"`). Consolidated the 4 core carrier hardening epics: (1) Telephony Observer Cursor Hardening & Batch Reconciliation (`TelephonyCheckpointStore`, dynamic monotonic delta windowing, sliding debouncer), (2) Inbound Transport Failover Protocol (256B SMS ceiling, 32x32 visual anchor MMS promotion, single-container SMIL markup), (3) MMS Part Table Latency Retry & SMIL Body Accumulator (4-stage backoff `0ms/1200ms/2500ms/4500ms`, multi-tag `<text>` accumulator, recursive XML entity unescaping), and (4) 15-Minute Carrier Cooldown Circuit Breaker (3-state FSM, rate-limit protection, line-clearance canary probe, UI countdown banner & admin controls). Synchronized `app/build.gradle.kts`, `version.json`, `releases.json`, `apk/version.json`, `apk/releases.json`, `app/src/main/assets/version.json`, and `app/src/main/assets/releases.json`. Deployed binary `pallyai-v28.apk` (29.0 MB) and updated `pallyai-latest.apk`. Pruned legacy artifacts to maintain the rolling 5 latest releases.
- **Build 42 (Version 5.1 / Sprint 1 Implemented)**: Executed Sprint 1 (Telephony Background Survival, Strict Slash Commands & Widget Schema Keys). Hardened `HardenedTelephonyObserverService` against Android 12+ `ForegroundServiceStartNotAllowedException` with safe fallbacks and preserved `PallySmsReceiver` with `goAsync()` for primary background SMS delivery. Enforced strict `/` prefix matching in `CellularIntentRouter` to prevent local swallowing of conversational messages. Expanded `MarketTicker` and `MarketChatCard` to support flexible JSON schema keys (`symbol`/`sym`, `change_percent`/`changePercent`/`chg`), multi-symbol arrays, and asset-aware symbol badges (₿, Ξ, ◎, $). Verified across unit tests (`testDebugUnitTest`) and compiled debug APK (`assembleDebug`).
- **Build 43 (Version 5.1 / Sprint 2 Implemented)**: Executed Sprint 2 (UI/UX Simplification & Decluttering). Removed raw wire diagnostics (`CRC Verified`, PDU count) from assistant chat bubbles by default, preserving wire details via long-press overlay drawer. Replaced raw wire tariff badge with standard Google Messages-style character/segment counter. Replaced static "Cellular Gateway" header pill with active Carrier Cooldown countdown and Simulation Mode badges. Hoisted `LazyListState` to `MainActivity` scope to prevent scroll jumping and reflow during bottom tab switches. Added inline interactive search field to `MarketChatCard` for instant on-demand crypto and stock ticker queries. Verified via Robolectric test suite and compiled debug APK.
- **Build 44 (Version 5.2 / Sprint 3 Implemented)**: Executed Sprint 3 (Carrier Ingress, Sender Validation, Debounce & 2-Tab Navigation).
  - **INC-17 (Telephony Ingestion)**: Created `TelephonyMmsObserver` observing system `Telephony.Mms.CONTENT_URI` with lifecycle registration in `CellularRpcApp` and `MainActivity.onResume()`.
  - **INC-18 (Sender Validation)**: Centralized sender validation through `CellularServiceManager.isSenderRecognized`, replacing fragmented phone string matching.
  - **INC-20 (Outbox Debounce & Dispatch Separation)**: Implemented in-memory debounce gate in `ChatViewModel` to suppress rapid triplicate sends, added cross-session duplicate payload checking in `CarrierSafeQueueEngine`, and separated Carrier MMS attachment dispatch from text SMS queueing.
  - **INC-21 (Slash-Command Routing)**: Created `OfflineCommandRouter` to execute local slash commands (`/help`, `/status`, `/ping`, `/theme`, `/widget`, `/net`, `/safe`, `/apps`, `/clear`, `/reset`) entirely on-device without consuming cellular radio bandwidth.
  - **INC-22 (Anchor Suppression & Media Intent)**: Implemented `processMediaIntentAndAnchors` in `HardenedMmsParser` with 32x32 visual anchor stripping and routing of `widget_asset` payloads into local storage.
  - **INC-19 (Default Chat UI & 2-Tab Navigation)**: Stripped cellular subline from composer, made dynamic segment counter visible strictly while typing, removed 3-dot overflow menu from header, and deployed the 2-Tab Navigation Architecture (Chat & Apps) powered by `UnifiedAppDrawerTab` with glanceable widget carousel, 4-column mini-app grid, and long-press blueprint management.
  - Verified compilation via `compile_applet` and packaged debug APK via `gradle assembleDebug` (30.8 MB).
- **Build 45 (Version 5.2 / FEAT-06 Implemented)**: Executed FEAT-06 (Inline Card Iteration: Feed-Tail Re-anchoring / "Pull Down" on Update).
  - Enforced `widgetId` consistency across all `WidgetData` types (`Weather`, `NewsDigest`, `MarketTicker`, `CellularTransfer`, `TaskChecklist`, `DynamicBlueprint`, `MiniAppPreview`, `MiniAppPatch`).
  - Upgraded `ChatMessage` and `ChatMessageEntity` schema with `revision`, `isSuperseded`, and `supersededByMessageId` fields (Room Database v10).
  - Extended `ChatMessageDao` with `findMatchingWidgetMessages`, `markMatchingWidgetsSuperseded`, and `deleteSkeletonMessages`.
  - Upgraded `CellularMessageDispatcher` to detect existing widget instances in a conversation thread, calculate the incremented revision number (`v1.x`), mark historical versions as superseded with pointer to the new message ID, and project the updated widget payload at the tail of the chat feed.
  - Implemented `NextGenChatMessageItem` UI handling:
    - Active updated card displays an "Updated from previous revision" revision badge (`v1.x • Updated from previous revision`).
    - Superseded upstream cards are rendered in a compact, de-emphasized container with an interactive "See latest at tail ↓" jump affordance and collapsible expand/collapse toggle.
  - Added unit and Robolectric tests in `ExampleUnitTest` and `ExampleRobolectricTest` verifying widget identity, revision increments, and database state transitions. All tests passing green. Debug APK built via `gradle assembleDebug`.
- **Build 46 (Version 5.2 / INC-05 Implemented)**: Executed INC-05 (Inbound Cellular Voice Note Ingestion over MMS).
  - **Hardened MMS Part Parser for Audio Formats**: Added detection and extraction for carrier audio MIME types (`audio/amr`, `audio/amr-wb`, `audio/3gpp`, `audio/3gp`, `audio/mp4`, `audio/m4a`, `audio/aac`, `audio/ogg`, `audio/mpeg`, `audio/wav`, `application/ogg`, and `application/octet-stream` audio extensions). Extracted and cached local files in `mms_media`.
  - **Audio Duration & Waveform Extraction**: Integrated `MediaMetadataRetriever` duration detection (with AMR-NB 1600 B/s fallback) and byte-sampled normalized 24-bar waveform generator (`generateAudioWaveform`) ensuring pristine rendering in `MessageAttachmentBubble`.
  - **Multi-Part Prioritization & Anchor Protection**: Prevented 32x32 carrier bypass anchors from overwriting or suppressing incoming audio voice notes when an MMS contains both an anchor and audio parts. Explicitly protected `AttachmentType.VOICE_NOTE` from anchor deletion.
  - **Telephony Ingestion & Dispatcher Hardening**: Updated `TelephonyMmsObserver` to accept voice note MMS from non-preconfigured numbers, hardened `CellularMessageDispatcher` deduplication to prevent false deduplication of textless voice notes, enabled ingestion when text is blank but attachment exists, persisted duration and amplitudes in `ChatMessageEntity`, and set conversation thread snippet to `"🎤 Voice Note (Xs)"`.
  - **Testing & Verification**: Added comprehensive unit tests in `ExampleRobolectricTest` verifying waveform generation and end-to-end voice note ingestion into Room. Full test suite passing (33 tasks, 17 tests green) and debug APK compiled via `gradle assembleDebug`.
- **Build 47 (Version 5.2 / INC-07 Implemented)**: Executed INC-07 (Outbound MMS Promotion & Android App Chooser Elimination).
  - **Standards-Compliant MMS PDU Composition (`MmsPduComposer`)**: Created binary WAP-209 / OMA MMS 1.2 `M-Send.req` PDU generator that serializes multipart payloads (text parts, 32x32 visual anchors, and audio/media attachments) into compliant PDU files stored in temporary cache storage.
  - **Direct Background MMS Transmission**: Replaced external `Intent.ACTION_SEND` intent delegation with direct `SmsManager.sendMultimediaMessage()` using `FileProvider` content URIs and `MMS_SENT` pending broadcast intents, completely eliminating unwanted system app chooser dialogs and preventing exposure of internal visual anchor assets in Google Messages drafts.
  - **Carrier-Safe Chunked Multi-Part SMS Fallback (Option B)**: Wired silent background fallback in `PallyMmsHelper` to split long message payloads (>250 bytes) into segmented/multipart SMS via `SmsManager.sendMultipartTextMessage()` with delivery telemetry when direct MMS transport is unconfigured or blocked by carrier APN policies.
  - **Telemetry & Receiver Alignment**: Extended `DeliveryBroadcastReceiver` and `AndroidManifest.xml` with `com.cellular.rpc.MMS_SENT` action handler, providing unified circuit-breaker reset and outbox sliding window acknowledgement for outbound MMS packets.
  - **Testing & Verification**: Added Robolectric unit tests for `MmsPduComposer` PDU byte composition and background `PallyMmsHelper` bundle dispatch.
- **Build 49 (Version 5.3 / Release v31 Packaged)**: Packaged production release v31 (`versionCode = 31`, `versionName = "5.3"`).
  - Consolidated features: FEAT-06 Inline Card Iteration & Feed-Tail Re-anchoring, INC-05 Inbound Voice Note MMS Extraction with AMR-NB/WB waveform synthesis, INC-07 Direct Background MMS PDU Egress (eliminating Android App Chooser), and Epic 14 SDUI Primitive Expansion (native Sparkline & Mini-Bar Canvas charts).
  - Synchronized `app/build.gradle.kts`, `version.json`, `releases.json`, `apk/version.json`, `apk/releases.json`, `app/src/main/assets/version.json`, and `app/src/main/assets/releases.json`.
  - Compiled and deployed distribution binary `apk/releases/pallyai-v31.apk` (30.0 MB).
  - Maintained rolling release retention in `apk/releases/` (`pallyai-v29.apk`, `pallyai-v30.apk`, `pallyai-v31.apk`).

- **Build 51 (Version 5.3 / FEAT-08 Multi-Series Comparative Sparklines)**: Implemented FEAT-08 (Multi-Series Comparative Canvas Sparklines & Telemetry).
  - **Dual-Series Normalization & Rendering (`SparklinePrimitives.kt`)**: Extended `SparklineCanvas` to calculate composite amplitude boundaries (`min..max` across primary and secondary datasets) and render secondary comparison curves using styled dashed vector paths (`secondaryLineColor`) with secondary indicator touch anchors.
  - **Live Scrubber Dual-Telemetry Overlay**: Added comparative difference badges (`POINT #X: $VAL vs $SEC_VAL`) to the inspection header on pointer drag and tap gestures.
  - **SDUI Engine Integration (`DynamicAppHost.kt`)**: Wired `secondary_bind` and `secondary_points` node modifiers in `"sparkline"` / `"chart_sparkline"` SDUI components to live dynamic state and static dataset props.
  - **Verification & Deployment**: Added Robolectric test in `ExampleRobolectricTest.kt`, verified all 50+ unit tests passing green (`testDebugUnitTest`), and compiled release binary `apk/releases/pallyai-v31.apk`.

- **Build 52 (Version 5.3 / FEAT-09 Carrier APN Auto-Detection & Direct MMSC Endpoint Resolver)**: Implemented FEAT-09 (Carrier APN Auto-Detection & MMSC Resolver).
  - **Carrier APN Resolver Engine (`CarrierApnResolver.kt`)**: Implemented automated carrier identification parsing `TelephonyManager` MCC/MNC codes, SIM operator names, system telephony APN cursor query (`content://telephony/carriers/current`), and curated carrier fallback tables for T-Mobile, AT&T, Verizon, Mint, Cricket, US Cellular, and TracFone.
  - **Subscription-Aware Telephony Routing (`PallyMmsHelper.kt`)**: Upgraded direct MMS PDU transmission and chunked fallback SMS to route through `SmsManager.getSmsManagerForSubscriptionId(subId)` and supply the resolved MMSC URL endpoint.
  - **Settings Diagnostics Telemetry (`PallySettingsBottomSheet.kt`)**: Added an interactive "Carrier APN & MMSC" diagnostics card displaying active SIM operator, MCC-MNC codes, resolved MMSC URL, and status badge (`SYSTEM APN` vs `KNOWN FALLBACK`).
  - **Verification & Deployment**: Added unit test `testCarrierApnResolverProfiles` in `ExampleUnitTest.kt`, verified entire unit test suite (`testDebugUnitTest`) passing green, and refreshed release binary `apk/releases/pallyai-v31.apk`.

- **Build 53 (Version 5.3 / FEAT-11 Cellular Audio Voice Note Player & Seek Scrubber)**: Implemented FEAT-11 (Inbound Cellular Audio Voice Note Player & Waveform Seek Scrubber).
  - **Waveform Seek Scrubber (`MessageAttachmentBubble.kt`)**: Integrated gesture drag and tap handlers (`detectTapGestures`, `detectDragGestures`) across the 24-bar audio waveform, allowing instant position scrubbing and visual feedback.
  - **Dual Timestamp & Telemetry**: Added dynamic playback position counter alongside total audio duration (`00:14 / 00:42` during playback/scrubbing).
  - **Playback Speed Controller (`AudioEngine.kt` / `MessageAttachmentBubble.kt`)**: Implemented playback speed cycle toggle (`1.0x` -> `1.5x` -> `2.0x`) through Media3 `ExoPlayer.setPlaybackSpeed`.
  - **Verification & Deployment**: Added unit test `testVoiceNoteScrubbingAndSpeedToggles` in `ExampleUnitTest.kt`, verified unit tests passing green, and compiled distribution binary `apk/releases/pallyai-v31.apk`.

- **Build 54 (Version 5.3 / FEAT-12 Zero-Data Offline SDUI Studio & Playground)**: Implemented FEAT-12 (Zero-Data Offline SDUI Blueprint Code Editor & Playground Test Bench).
  - **SDUI Studio Interface (`SduiPlaygroundScreen.kt`)**: Built a multi-tab developer workspace with monospaced AST code editor, JSON auto-formatter, preset library selector (Telemetry Lab, Tip Calculator, Task Checklist), and real-time AST lint diagnostics.
  - **Interactive Live Host & State Inspector**: Enabled live hot-reload rendering via `DynamicAppHost` paired with an interactive dynamic JSON runtime state tree inspector and instant reset controls.
  - **Direct Zero-Data OS Installation**: Added one-tap local blueprint installation to Room database (`AppBlueprintDao`), immediately surfacing authored apps in the Universal Apps grid.
  - **Verification & Deployment**: Added unit test `testSduiPlaygroundTemplateParsingAndLinting` in `ExampleUnitTest.kt`, verified all 55 unit tests passing green, and published release binary `apk/releases/pallyai-v31.apk`.

---

- **Build 55 (Version 5.3 / FEAT-13 Cellular Packet Inspector Filters & Timeline Export)**: Implemented FEAT-13 (Cellular Packet Inspector Filters & Timeline Export).
  - **Protocol Inspector UI (`PacketInspectorTab.kt`)**: Added scrollable filter chips (`ALL`, `TX`, `RX`, `MMS`, `GZIP`, `ACK`) and live search bar filtering packets across wire format, payload strings, session IDs, and packet types.
  - **Timeline JSON Export Engine**: Integrated one-tap structured JSON timeline serialization directly to the Android system clipboard with success status banners.
  - **Verification & Deployment**: Added unit test `testPacketInspectorFiltersAndExportSerialization` in `ExampleUnitTest.kt`, verified 56/56 unit tests passing green, and refreshed distribution release binary `apk/releases/pallyai-v31.apk`.

---

### Almanac Patch: FEAT-13 Cellular Packet Inspector Filters & Timeline Export
* **Status:** Completed
* **New ADR:** ADR-026 — [On-Device Telephony Packet Filter Graph & Deterministic JSON Timeline Export: Added low-overhead in-memory filter chips, live search string matching, and structured JSON timeline clipboard export directly from the Protocol Inspector for developer telemetry | 2026-09-13]
* **V2 Backlog Additions:** Multi-SIM Sub-Slot manual override in Carrier Diagnostics sheet.

---

### Almanac Patch: Section 2.13 Long-Press Context Menu Extensions (Fork Thread & Edit Prompt)
* **Status:** Completed
* **New ADR:** ADR-027 — [Non-Destructive Thread Forking & In-Situ Prompt Refinement: Implemented message history cloning up to target cutoff timestamp into isolated sub-threads (`th_fork_...`) with tab bar branching indicators, paired with multi-line Edit Prompt dialog with real-time PDU cost estimation and branching dispatch options | 2026-09-13]
* **V2 Backlog Additions:** Merging branched conversation threads back into parent thread with diff views.

---

### Almanac Patch: Section 2.10 Tier 2/3 Hardware Sensor 2D Mini-Game Engine
* **Status:** Completed
* **New ADR:** ADR-028 — [Hardware Sensor 2D Physics Mini-Game Engine & SDUI Integration: Implemented `HardwareSensorEngine` with EMA low-pass filtering, on-device simulation tilt fallbacks, and multi-tier haptic feedback (`VibrationEffect`/`VibratorManager`), paired with `MiniGamePhysicsEngine` symplectic Euler 2D integration and `SensorGameView` canvas component embedded directly as SDUI node types (`sensor_game`, `physics_game`, `tilt_maze`). Whitelisted in `BlueprintLinter` and pre-seeded into Universal Apps deck and SDUI Studio | 2026-09-13]
* **V2 Backlog Additions:** Procedural maze generator nodes for infinite multi-tier level progression.

---

### Almanac Patch: Section 2.11 Audio Voice Note Live Recorder & Cancellation Scrubber
* **Status:** Completed
* **New ADR:** ADR-029 — [Live Voice Note Recording Bar with 50Hz Dynamic Waveform & Cancellation Scrubber: Upgraded `AudioRecorderManager` with 50Hz amplitude polling, 2-minute safety auto-stop ceiling, and lockable recording state. Created `LiveVoiceRecordingBar` featuring dynamic Canvas wave bars, pulsing LED, tactile haptic feedback, and horizontal slide-to-cancel gestures directly integrated into `FullNativeChatInputBar` with AMR-WB compression | 2026-09-13]
* **V2 Backlog Additions:** Noise-gate filter parameter in voice settings.

---

### Almanac Patch: Section 2.16 Procedural 2D Maze Generator for Sensor Game
* **Status:** Completed
* **New ADR:** ADR-030 — [Procedural 2D Maze Generator & Multi-Tier Progression: Created `ProceduralMazeGenerator` utilizing randomized recursive backtracking (Depth-First Search) to carve passage channels, place dead-end target rings, exit portals, and hazard traps across 4 difficulty tiers (Novice, Intermediate, Expert, Master). Extended `SensorGameView` with level advancement (`STG 1..N`), seed reproducibility for shared cellular SMS maze challenges, and whitelisted SDUI node type `maze_game` in `BlueprintLinter` | 2026-09-13]
* **V2 Backlog Additions:** Fog of war visibility radius option for maze exploration.

---

### Almanac Patch: Release Build v32 (v5.4)
* **Status:** Released
* **Summary:** Promoted build to Version Code 32 (v5.4). Consolidated Section 2.10 (Hardware Sensor Game Engine), Section 2.11 (Audio Voice Note Live Recorder & Scrubber), Section 2.13 (Thread Forking & Edit Prompt), Section 2.14 (Pluggable Bubble Animation Presets), and Section 2.16 (Procedural 2D Maze Generator).










 
