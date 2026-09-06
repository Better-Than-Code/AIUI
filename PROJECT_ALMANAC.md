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
  - Successfully verified unit tests and generated up-to-date debug APK (`gradle assembleDebug`).

---

## 4. The V2 Backlog (Parking Lot)

- [ ] End-to-end PGP / asymmetric encryption on cellular RPC payloads.
- [ ] Compression layer (smaz / deflate) for payloads exceeding 100 bytes.
- [ ] Multi-party cellular broadcast channels over group MMS.
- [ ] Adaptive radio pacing based on cellular signal strength (`SignalStrength` API).
- [ ] Voice-to-SMS audio compression using ultra-low bitrate codecs (e.g. Codec2).
