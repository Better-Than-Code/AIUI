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


