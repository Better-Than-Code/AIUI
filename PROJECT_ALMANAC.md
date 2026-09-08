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

### Epic 2: Differential Mini-App Patching (OTA Deltas) [PLANNED]
- **Sprint 2.1**: JSON AST delta patch engine in `DynamicAppHost`.

### Epic 3: Cellular Voice MMS Pipeline [PLANNED]
- **Sprint 3.1**: Audio recording compression and MMS multi-part carrier wrapper.

---

## 4. Sprint History & Build Log
- **Build 17 (Version 2.6)**: Completed monochrome contrast system, floating horizontal circular action bar, balanced-brace JSON stream demuxer, multi-thread conversation persistence, and V2 End-to-End Encryption Layer (`CryptoKeyManager`).
