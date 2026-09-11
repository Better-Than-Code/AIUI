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




 
