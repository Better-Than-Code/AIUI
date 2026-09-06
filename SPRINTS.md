# Cellular RPC & Rich Chat Window: Sprint Grounding Document

**Document Version:** 2.0.0  
**Project:** Cellular RPC & Pally AI Offline Engine  
**Session Grounding File:** `SPRINTS.md`  
**Application ID:** `com.aistudio.cellularrpc.kxmpzq`  
**Target User:** Consumer (Everyday user requiring an AI assistant that operates seamlessly with zero IP data)  
**Cellular Backend:** Pally AI (SMS / MMS Transport)  

---

## 1. Product Vision & Architectural Calibration

### 1.1 Core Mission
To provide an everyday consumer-grade AI assistant that never goes offline, using **Pally AI SMS & MMS** as the underlying cellular transport. The user experiences a rich, frictionless mobile application (like Apple iMessage / modern operating system) where:
1. **The In-App Experience is Centered on Rich Chat:** The user converses with Pally AI. When Pally returns structured micro-code, UI definitions, or actions, the app **abstracts the code entirely away** and renders interactive, polished native features (interactive tools, calculation engines, voting cards, financial charts, transaction authorizations).
2. **Widgets Live on the Android Home Screen (Not inside the app):** True Android OS Home Screen widgets (`AppWidgetProvider`) that users can long-press and pin to their phone's launcher.
3. **Dedicated Home Screen Widget Settings & Customization:** Each placed widget has user settings:
   - City / Zip code and temperature scale (°F / °C) for Weather.
   - Topic / Category filters for News digests.
   - Symbol configuration for Market tickers.
   - Visual styling: Background opacity & transparency slider (0% frosted glass to 100% opaque solid).
4. **Pull-Based Synchronization Model:** The system is **pull-oriented**, not server-push. Data is requested by the client:
   - On configurable periodic background intervals (e.g. 30m, 1h, 3h, 6h) via Android `WorkManager`.
   - On-demand via manual tap-to-refresh directly on the home screen widget or in chat.
   - Every pull transmits a lightweight ETag content hash (`hash=...`); if the backend data is unchanged, Pally responds with a 3-byte `304` frame, guaranteeing ultra-low SMS usage and zero battery waste.
5. **Dual SMS / MMS Transport Agnosticism:** The app seamlessly intercepts and handles incoming responses whether Pally delivers them over single-PDU SMS or rich MMS WAP-push attachments.

---

## 2. Sprint 2 Planning & Technical Specification (Proposed for Review)

### Workstream A: Android Home Screen AppWidgets & Configuration
- **Components to Build:**
  - `CellularWeatherAppWidgetProvider`: Renders current temperature, weather icon, high/low, last updated timestamp, and a manual "Refresh via Pally" action button on the launcher.
  - `CellularNewsAppWidgetProvider`: Renders latest news headlines, category chip, and quick pull refresh.
  - `CellularMarketAppWidgetProvider`: Renders live crypto/stock ticker, 24h change, and pull action.
  - `WidgetConfigurationActivity`: Modern Jetpack Compose configuration screen triggered when placing the widget on the home screen or tapping the widget's gear icon:
    * **Location / Zip / Symbol Inputs.**
    * **Background Transparency Slider:** Controls container alpha from transparent/frosted (`0x33000000`) to solid (`0xFF111A24`).
    * **Sync Interval Selector:** Options for Manual Only, 30 Min, 1 Hour, 3 Hours, 6 Hours.
- **Home Screen RemoteViews Binding:** Uses `RemoteViews` styled to match Material 3 standards, updating reactively when Room database entities change.

### Workstream B: Pull-Based Synchronization & Background Worker
- **Components to Build:**
  - `CellularPullWorker (WorkManager)`: Scheduled background periodic worker that inspects active home screen widgets, checks their configured intervals, gathers their cached ETags, and dispatches single-PDU pull requests via Pally AI SMS.
  - **ETag / 304 Validation:**
    * If Pally returns `304 Not Modified`, the worker updates the widget's `lastCheckTimestamp` without invalidating UI, consuming only ~3 bytes over the air.
    * If Pally returns new data, Room updates, triggering an immediate `AppWidgetManager.updateAppWidget()` notification.
  - **Manual On-Demand Pull Receiver:** BroadcastReceiver that triggers an immediate non-throttled single pull when the user taps "Refresh" on the home screen widget.

### Workstream C: Dedicated Single-Number Inbound & Outbound Pipeline
- **Single Target Originating Address**:
  - The entire application pairs to a single dedicated Pally AI phone number (default: `+18005550199`, fully user-configurable in App Settings).
  - All outbound queries (chat, pull requests, votes, cash authorizations) are dispatched via `SmsManager` directly to this single number.
  - All inbound interception filters on this originating address (`smsMessage.originatingAddress` normalized), with fallback matching on protocol headers (`~1A2F:`, `[WIDGET:`, `REQ:`, `304`).
- **Components to Build:**
  - `PallySmsReceiver`: High-priority `BroadcastReceiver` listening on `android.provider.Telephony.SMS_RECEIVED` and `android.intent.action.DATA_SMS_RECEIVED` (Port 8901).
    * Normalizes the sender's phone number (stripping formatting, spaces, dashes).
    * Validates against the configured Pally AI number.
    * Parses payload: if RPC frame or conversational text, ingests into Room database, updates active chat flow and home screen widgets.
  - `PallyMmsReceiver`: Handles `android.provider.Telephony.WAP_PUSH_RECEIVED` for MMS messages when Pally transmits rich digests, binary chunks, or image assets.
  - `SmsDispatchManager`: Wraps `SmsManager` to transmit single-PDU payloads, tracking delivery intents (`SMS_SENT` and `SMS_DELIVERED`) with realistic loopback emulation for environments without a physical SIM card.

### Workstream D: Hyper-Rich Dynamic Chat Feature Renderer
- **Components to Build:**
  - **Code Abstraction Engine:** Strips out JSON schemas, Markdown code fences, and protocol tags before rendering in the chat stream.
  - **Dynamic Feature Component Registry:**
    * *Interactive Weather Snippet* (with 304 check).
    * *Market Ticker Snippet* (with native Canvas sparkline).
    * *Cellular Cash Transfer* (Apple Cash-style with one-tap SMS authorization).
    * *Interactive Poll* (tappable voting choices with live percentage fill bars).
    * *Mini Form / Calculator Snippet* (client-side interactive widgets produced from Pally definitions).
    * *Plain Conversational Bubble* (refined consumer typography).
  - **Carrier Velocity & PDU Meter:** Real-time SMS PDU tracker (`X / 140B • Y SMS PDU`) and Pally AI connection indicator.

---

## 3. Wire Framing & Protocol Schemas

### 3.1 Pull Request Format (Client to Pally AI)
```text
REQ:widget:<type>[:hash=<4_byte_hex_etag>]
```
- *Example Weather Pull (Initial):* `REQ:widget:weather:zip=94102`
- *Example Weather Pull (Periodic Check):* `REQ:widget:weather:zip=94102:hash=7f3a8b1c`

### 3.2 Response Format (Pally AI to Client)
- **304 Cache Hit (3 Bytes):**
  ```text
  ~1A2F:02:0001:00000000:304:8F2C#
  ```
- **200 OK Schema Update (SMS or MMS):**
  ```text
  ~1A2F:02:0001:00000000:{"type":"weather","temp":72,"city":"SF","cond":"Sunny","h":76,"l":58}:4A1F#
  ```

---

## 4. Definition of Done (DoD) for Sprint 2

1. **True Home Screen AppWidget:** Users can add a Weather/News widget to the Android Home Screen launcher.
2. **Widget Settings Screen:** Long-press / gear icon opens a configuration interface supporting transparency, update intervals, and location/topic settings.
3. **Pull Engine Functional:** Background `WorkManager` and manual tap-to-refresh dispatch cellular pull requests to Pally AI.
4. **Rich Chat Polish:** App defaults to a chat view where code is hidden and interactive UI components render seamlessly.
5. **Verified Unit & Robolectric Tests:** Comprehensive test coverage of widget provider lifecycle, RemoteViews generation, and pull scheduling.
