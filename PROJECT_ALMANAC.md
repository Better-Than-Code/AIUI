# PROJECT ALMANAC: Pally AI Cellular RPC & Widget Transport

**Document Purpose:** The persistent source of truth and architectural memory for the project.

---

## 1. Agile Charter & Definition of Done

### Mission
Deliver a resilient, consumer-grade AI assistant application that operates completely offline using **Pally AI cellular SMS/MMS** as a headless RPC transport and rich widget protocol for Android.

### High-Level Architectural Principles
1. **Rich Chat Centered UI:** The in-app chat is the primary consumer interface. Code and JSON payload schemas from the server are abstracted away, rendering rich, native interactive components (Weather, News, Market Ticker, Cellular Cash, Polls, and Cellular Tools).
2. **True Android Home Screen Widgets:** Widgets are not embedded inside the app; they live directly on the Android Home Screen launcher (`AppWidgetProvider`).
3. **Dedicated Per-Widget Settings:** Placed widgets feature user customization (City/ZIP, temperature units, news categories, background opacity slider from frosted glass to solid).
4. **Pull-Oriented Synchronization:** Pull-based rather than server-push. Employs `WorkManager` background polling and manual tap-to-refresh with 304 ETag caching to save cellular bandwidth and prevent battery drain.
5. **Dedicated Single Carrier Number:** All cellular communication is routed to/from a dedicated, user-configurable Pally phone number (default: `+18005550199`).

---

## 2. Architecture Decision Records (ADRs)

- **ADR-001: Hybrid Wire Format & MTU Management**
  - Binary frames over Port 8901 (UDH header, 8-byte frame header, CRC16) for high efficiency.
  - Fallback ASCII wire encoding (`~<session>:<type>:<seq>:<ack>:<payload>:<crc>#`) using GSM-safe Base85 for broad carrier compatibility.
  - Safe payload limit capped at 122 bytes per single SMS PDU.
- **ADR-002: Sliding Window & Carrier Velocity Mitigation**
  - Sliding window size $W=4$ with selective repeat ACK bitmask (32 bits).
  - Strict transmission rate-limiting: 2200ms base gap + 200–700ms random jitter to avoid SMS spam blocks.
- **ADR-003: 304 Not Modified Content Caching**
  - Client sends 4-byte truncated hash of current widget state.
  - Backend responds with a 3-byte `304` packet when data is unchanged, saving bandwidth.
- **ADR-004: Dual Transport (SMS + MMS WAP Push)**
  - Broadcast receivers for `SMS_RECEIVED` and `WAP_PUSH_RECEIVED` seamlessly route inbound payloads into the same queue engine.

---

## 3. Sprint History & Outcomes

### Sprint 1: Core Cellular Protocol & Physical Layer Engine (COMPLETED)
- Implemented `Frame` wire parser, binary serializer, CRC-16 CCITT, and `GsmSafeBase85` encoder/decoder.
- Implemented `SlidingWindowController` supporting out-of-order packet reassembly and duplicate suppression.
- Room database schema (`OutboxEntity`, `WidgetCacheEntity`, `PacketLogEntity`).
- Comprehensive unit tests verifying protocol math, MTU bounds, and CRC integrity.

### Sprint 2: Home Screen Widgets, Pull Synchronization & Rich Chat (COMPLETED)
- **AppWidget Providers:** `CellularWeatherAppWidgetProvider` and `CellularNewsAppWidgetProvider` for the Android home screen launcher with RemoteViews.
- **Widget Configuration:** `WidgetConfigurationActivity` allowing transparency adjustment (0–100%), location/topic selection, and sync interval configuration.
- **Telephony Pipeline:** `PallySmsReceiver` for incoming SMS interception filtered by dedicated Pally phone number.
- **Pull Architecture:** `PullBroadcastReceiver` and `CellularPullWorker` supporting manual tap-to-refresh and background polling with 304 ETag validation.
- **Rich Chat UI:**
  - Integrated `ToolChatCard` for interactive calculation tools (e.g., Tip & Split calculator).
  - Modern header with Pally AI branding, carrier connection badge, and dedicated settings bottom sheet.
  - Live PDU byte counter (`X / 140B • Y SMS PDU`).
  - Toggleable protocol telemetry bar and inspector for debugging without cluttering consumer UI.

### Sprint 3: Standardized Schema Framework & Extensible Cellular Dispatcher (COMPLETED)
- **Extensible Schema Engine (`CellularSchemaRegistry`):**
  - Standardized JSON schema specification with field types, validations, versioning, and MD5-truncated content hash calculation for ETag generation.
  - Catalog of 10 standardized schemas: Weather, News, Finance/Market Ticker, Cellular Cash, Poll, Tip Tool, Quick Translate, Calendar Event, Task Checklist, and System Status.
- **Unified Wire Envelope Architecture:**
  - `CellularRequest` envelope supporting compact wire format (`REQ:<reqId>:<action>:<target>[:etag=<hash>][:<k>=<v>...]`) and JSON payloads.
  - `CellularResponse` envelope supporting type-safe status codes (`CellularStatusCode`: 200, 304, 400, 404, 429, 500), 304 Not Modified wire compression (`RES:<reqId>:304:<schemaId>:<etag>`), and automatic domain object serialization.
- **Central Message Dispatcher (`CellularMessageDispatcher`):**
  - Pluggable `SchemaPayloadConsumer` pipeline automatically matching inbound SMS/MMS payloads to registered schemas, updating Room database widget cache, triggering home screen AppWidgets, and notifying the chat UI via SharedFlow.
- **Rich Chat UI Integrations:**
  - Added dedicated Chat Cards for `CalendarEventChatCard`, `TaskChecklistChatCard`, and `SystemStatusChatCard`.
  - Added schema validation telemetry in the diagnostic suite.
  - Validated via comprehensive Robolectric unit test suite (`ExampleRobolectricTest`).

### Sprint 4: Single-Push Genesis MCP Discovery & Delta Invalidation (COMPLETED)
- **Model Context Protocol (MCP) Client Registry (`CellularMcpRegistry`):**
  - Designed the Single-Push Genesis manifest engine (`v=2.1.0`) serializing all 10 registered widget schemas, 4 native executable tools (`cast_poll_vote`, `confirm_cellular_transfer`, `query_cellular_widget`, `query_device_telemetry`), and cellular MTU constraints.
  - Deterministic 8-hex-character catalog hash calculation (`computeCatalogHash()`) for instantaneous ETag and delta invalidation.
- **Zero-Overhead Memory Optimization:**
  - Pushes full capability metadata once upon initial connection or manifest change.
  - Leverages persistent AI agent memory, eliminating redundant schema handshakes on cellular bandwidth budgets.
- **Diagnostics & Interactive UI:**
  - Integrated MCP Genesis Sync status card into the Protocol Inspector with live JSON schema inspection dialog and manual push triggers.
  - Added Robolectric test coverage for deterministic manifest creation and tool registration.

### Sprint 5: Autonomous Offline Extension Engine (Dynamic UI & Sandboxed Execution) (COMPLETED)
- **Zero-Internet Feature Deployment Engine:**
  - Enables remote AI backends to deploy interactive micro-apps, forms, and custom tools via cellular `[APP:BUILD:<id>]` SMS text payloads without APK modifications or Play Store updates.
- **AST Layout Graph Interpreter (`AstNode`, `AstParser`, `DynamicScreenHost`):**
  - Recursive Jetpack Compose AST interpreter supporting `Column`, `Row`, `Card`, `Text`, `Input` (TextField), `Slider`, `Toggle` (Switch), `Button`, and `Divider`.
  - Dynamic two-way state binding with Mustache-style `{variable}` template string resolution and reactive Compose updates.
- **Sandboxed Execution Runtime (`DynamicScriptSandbox`, `DynamicNativeBridge`):**
  - Isolated execution environment using `androidx.javascriptengine` with a fallback state mutation interpreter for unsupported hardware.
  - Audited `@JavascriptInterface` bridge (`commit`, `notify`, `vibrate`, `log`) strictly barring access to disk or unexposed hardware APIs.
  - Enforced 3000ms timeout guard to prevent infinite loops or frozen UI threads.
- **Custom AI Home-Screen Launcher Widget (`CellularCustomAppWidgetProvider`, `widget_custom_ai.xml`):**
  - Built-in customizable home-screen widget provider allowing users to display live telemetry from any dynamic micro-app (e.g. Solar Estimator, Inventory Counter) on their Android launcher without touching the native Weather & News widgets.
  - Fully integrated with `WidgetConfigurationActivity` for custom title, feature ID, metric key, and wallpaper transparency configuration.
- **Persistence & Interception Layer:**
  - Room table `dynamic_features` (schema version 3) and `DynamicFeatureDao` for instant offline loading and execution.
  - Wired into `CellularMessageDispatcher` for automatic SMS interception and in-app chat deployment notifications.
- **Apps Navigation & Interactive Management UI (`DynamicFeaturesTab`):**
  - Dedicated "Apps" tab in the bottom navigation for browsing, launching, inspecting AST/JS logic, and deleting dynamic micro-apps.
  - Pre-seeded with sample offline extensions ("Solar Array Estimator" and "Field Inventory Counter").
- **Verification:**
  - 100% test pass rate across protocol math, MCP discovery, wire regex parsing, AST resolution, sandbox state execution, and custom AppWidget provider binding. Debug APK generated and ready for deployment.
  - Successfully verified unit tests and generated up-to-date debug APK (`gradle assembleDebug`).

### Sprint 6: Carrier SMS Multi-Part Reassembly, Dual-Layer Deduplication & Native MMS Ingestion/Dispatch (COMPLETED)
- **Multi-Part SMS Assembly & Debouncing (`PallySmsObserver`):**
  - Implemented 1200ms debounce buffer to allow multi-segment carrier concatenated SMS (UDH) to land in the system telephony database before ingestion.
  - Grouped segments by sender and time window, sorting strictly by `_ID ASC` to prevent jumbled fragments.
- **Cross-Pipeline Deduplication (`PallySmsTracker` & `CellularMessageDispatcher`):**
  - Unified memory signature cache across `BroadcastReceiver` and `ContentObserver` paths with 60-second sliding expiration.
  - Added Room-backed verification (`countRecentMatchingMessages`) to eliminate duplicates from race conditions.
  - Removed duplicate insertion points in `CellularRpcViewModel` ensuring `CellularMessageDispatcher` is the single source of truth for message persistence.
  - Added startup cleanup to wipe historical duplicate rows and corrupted `<H3re2h..` binary fragments.
- **Corrupted / Binary PDU Filtering:**
  - Added strict heuristics in `PallySmsTracker` and `CellularMessageDispatcher` to reject binary protocol control frames and garbled carrier headers from rendering as raw text in user chat.
- **Full MMS Pipeline (`PallyMmsHelper` & `PallyWapPushReceiver`):**
  - Implemented `PallyMmsHelper` ContentObserver observing `content://mms` with automatic part extraction for text and media (images, files, voice notes).
  - Wired `PallyWapPushReceiver` to trigger `PallyMmsHelper` with a 2000ms delay allowing the Android telephony stack to finish downloading MMS PDU parts.
  - Added outbound carrier MMS dispatch in `sendChatMessage` via `PallyMmsHelper.dispatchCarrierMms()`.
- **Verification & Build:**
  - Successfully compiled and generated updated debug APK (`app-debug.apk`) via `gradle assembleDebug`.

### Sprint 7: Resilient Multi-Source App Update Mechanism & 5 Recent Releases Catalog (COMPLETED)
- **Dynamic Version Resolution:**
  - Replaced hardcoded version text with dynamic inspection via `PackageManager` / `BuildConfig` (`AppUpdateManager.getCurrentVersion()`), displaying the true installed version (e.g. `Current Version: 2.1 (v12)`).
- **Multi-Source Release Fetching:**
  - Implemented a multi-tier fallback pipeline in `AppUpdateManager.fetchRecentReleases()`:
    1. Bundled asset release metadata (`releases.json` / `version.json`).
    2. GitHub raw JSON (`apk/releases.json` and `apk/version.json`).
    3. GitHub API repository contents (`/contents/apk/releases`).
    4. GitHub HTML directory scraping of `apk/releases/` with regex extraction.
- **Recent 5 Releases Catalog Display:**
  - Displays the 5 most recent APK releases sorted by build version descending.
  - Highlights the currently installed version with a `CURRENT` badge and allows one-tap download and installation of any release (including rollback or reinstallation) even if automatic version verification encounters network or branch discrepancies.
- **Verification & Build:**
  - Verified with `compile_applet` and executed `gradle assembleDebug` to keep compiled APKs synchronized.

### Sprint 8: Live Gateway Routing, Protocol Frame Interception, Room Queue Sync, WAP Push MMS & Dynamic SDUI (COMPLETED)
- **Live Pally Gateway Routing:**
  - Standardized gateway phone number matching in `CellularServiceManager.kt` to recognize `+16462619684` along with international/domestic formatting variations.
- **Protocol Frame Interception (`abortBroadcast`):**
  - Added `abortBroadcast()` on ordered `SMS_RECEIVED` and `WAP_PUSH_DELIVER` broadcasts in `PallySmsReceiver` and `PallyWapPushReceiver` whenever protocol frames (`~1A2F:`, `REQ:`, `RES:`, or Base85 frames) are detected, preventing system SMS feed clutter.
- **Room Database Outbox State Synchronization:**
  - Added `getActiveQueueFlow()` and `getPendingCountFlow()` to `OutboxDao.kt`.
  - Updated `CarrierSafeQueueEngine.kt` to prune acknowledged frames and continuously sync in-flight count with Room database state.
  - Bound `CellularRpcViewModel` to live database flows for accurate UI queue counts.
- **MMS WAP Push Receiver Manifest Registration:**
  - Registered `WAP_PUSH_DELIVER` intent filter in `AndroidManifest.xml` with high priority (`999`) and mimeType `application/vnd.wap.mms-message`.
- **Generic Server-Driven UI (SDUI) Blueprint Engine:**
  - Added `WidgetData.DynamicBlueprint` and recursive `WidgetData.DynamicSduiNode` tree models to `WidgetModels.kt`.
  - Integrated generic blueprint parsing in `WidgetData.parse()` to dynamically render custom JSON schemas as interactive visual cards.
  - Implemented `DynamicBlueprintChatCard` and `DynamicSduiNodeView` in `MainActivity.kt` supporting container layouts (Column, Row, Card, Box), typography styles, metrics, progress bars, key-value items, badges, chips, and interactive buttons.
- **Verification & Release:**
  - Executed full Robolectric suite with 100% pass rate.
  - Assembled debug APK `pallyai-v14.apk` and updated release catalogs.

### Sprint 9: Minimalist Top App Bar & Calm Infrastructure UX (COMPLETED)
- **Apple-Inspired Top App Bar:**
  - Streamlined `TopAppBar` in `MainActivity.kt` to a single-tier, calm layout.
  - Eliminated the prominent "ON / OFF" chip, mode switch button strip, and redundant sync icons from the primary chat canvas.
  - Added clean conversational identity with active thread title, an unobtrusive 6dp connection health dot (SignalGreen / SignalAmber), and direct drawer launcher tapping.
  - Streamlined trailing actions to Theme Toggle and More/Settings.
- **Relocated Transport Controls to Settings Sheet:**
  - Integrated the Background Cellular Service toggle with descriptive guidance into Section 5 of `PallySettingsBottomSheet`.
  - Maintained full testability with preserved `service_toggle_button` and `mode_toggle_pill` test tags.

### Sprint 10: Flat Themed App Launcher Icon (✨) (COMPLETED)
- **Flat Adaptive Launcher Icon:**
  - Designed clean vector path for the **✨ (Sparkles)** cluster in `ic_launcher_foreground.xml` conforming to the 66dp safe zone within a 108dp canvas.
  - Set `ic_launcher_background.xml` to a clean, flat dark navy canvas (`#0B132B`).
  - Added dedicated monochrome layer in `ic_launcher_monochrome.xml` configured in `ic_launcher.xml` and `ic_launcher_round.xml` to ensure full dynamic Material You / Android 13+ Themed Icons support.
- **Verification & Build:**
  - Verified with `compile_applet` and executed `gradle assembleDebug` to keep compiled APK artifacts in sync.

---

## 4. The V2 Backlog (Parking Lot)

- [ ] End-to-end PGP / asymmetric encryption on cellular RPC payloads.
- [ ] Compression layer (smaz / deflate) for payloads exceeding 100 bytes.
- [ ] Multi-party cellular broadcast channels over group MMS.
- [ ] Adaptive radio pacing based on cellular signal strength (`SignalStrength` API).
- [ ] Voice-to-SMS audio compression using ultra-low bitrate codecs (e.g. Codec2).
