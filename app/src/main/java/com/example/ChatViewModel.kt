package com.example

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cellular.rpc.data.local.*
import com.cellular.rpc.domain.payload.CellularAction
import com.cellular.rpc.domain.payload.CellularRequest
import com.cellular.rpc.domain.payload.CellularResponse
import com.cellular.rpc.domain.protocol.Frame
import com.cellular.rpc.domain.protocol.GsmSafeBase85
import com.cellular.rpc.domain.protocol.SlidingWindowController
import com.cellular.rpc.domain.schema.CellularSchemaRegistry
import com.cellular.rpc.engine.WidgetData
import com.cellular.rpc.orchestrator.AiTemplate
import com.cellular.rpc.orchestrator.CellularAiOrchestrator
import com.cellular.rpc.orchestrator.OrchestratorSchemaType
import com.cellular.rpc.orchestrator.OrchestratorTemplateCatalog
import com.cellular.rpc.transport.handler.CellularMessageDispatcher
import com.cellular.rpc.transport.queue.CarrierSafeQueueEngine
import com.cellular.rpc.transport.service.CellularRpcForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

data class TestResult(
    val title: String,
    val passed: Boolean,
    val message: String,
    val details: String = ""
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val outboxDao = db.outboxDao()
    private val packetLogDao = db.packetLogDao()
    private val chatMessageDao = db.chatMessageDao()
    private val dynamicFeatureDao = db.dynamicFeatureDao()
    private val conversationThreadDao = db.conversationThreadDao()
    private val customActionDao = db.customActionDao()
    val chatRepository = com.cellular.rpc.data.repository.ChatRepository(chatMessageDao)

    private val queueEngine: CarrierSafeQueueEngine
        get() = CellularRpcForegroundService.activeEngine ?: CellularRpcApp.instance.queueEngine

    // Custom Instant Cellular AI Actions
    val customActions: StateFlow<List<com.cellular.rpc.data.local.CustomActionEntity>> =
        customActionDao.getAllActionsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    private val _activeThreadId = MutableStateFlow("th_main")
    val activeThreadId: StateFlow<String> = _activeThreadId.asStateFlow()

    // Open Tabs for Tabbed Chat UI
    private val _openTabThreadIds = MutableStateFlow<List<String>>(listOf("th_main"))
    val openTabThreadIds: StateFlow<List<String>> = _openTabThreadIds.asStateFlow()

    // All conversation threads
    val conversationThreads: StateFlow<List<ConversationThreadEntity>> = conversationThreadDao.getActiveThreadsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active thread object
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeThread: StateFlow<ConversationThreadEntity?> = _activeThreadId
        .flatMapLatest { threadId -> conversationThreadDao.getThreadFlow(threadId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Chat messages filtered to active thread
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val chatMessages: StateFlow<List<com.cellular.rpc.engine.ChatMessage>> = _activeThreadId
        .flatMapLatest { threadId -> chatRepository.getMessagesForThread(threadId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun triggerPullSync() {
        val context = getApplication<Application>()
        val intent = Intent(context, com.cellular.rpc.widget.PullBroadcastReceiver::class.java).apply {
            action = com.cellular.rpc.widget.PullBroadcastReceiver.ACTION_WIDGET_MANUAL_REFRESH
            putExtra(com.cellular.rpc.widget.PullBroadcastReceiver.EXTRA_WIDGET_TYPE, "weather")
        }
        context.sendBroadcast(intent)
        // Also query chat
        queryWidget("weather")
    }

    private var activeSessionId = 0x1A2F
    private var lastQueriedType: String = "weather"

    init {
        com.cellular.rpc.domain.service.CellularServiceManager.initialize(application)
        com.cellular.rpc.domain.dynamic.DynamicFeatureManager.seedSampleFeaturesIfEmpty(application)

        // Initialize default thread and welcome conversation if repository is empty
        viewModelScope.launch(Dispatchers.IO) {
            conversationThreadDao.insertDefaultThread(
                ConversationThreadEntity(
                    threadId = "th_main",
                    title = "General Chat",
                    createdAtMs = System.currentTimeMillis(),
                    lastMessageTimestamp = System.currentTimeMillis(),
                    lastSnippet = "Cellular RPC Gateway connected over SMS",
                    isPinned = true
                )
            )

            val currentList = chatMessageDao.getAllMessages().first()
            if (currentList.isEmpty()) {
                val welcomeWeather = WidgetData.Weather(72, "San Francisco", "Sunny", high = 76, low = 58)
                val initialMessages = listOf(
                    com.cellular.rpc.engine.ChatMessage(
                        threadId = "th_main",
                        sender = com.cellular.rpc.engine.MessageSender.AI_GATEWAY,
                        text = "Cellular RPC Gateway connected over SMS (Port 8901). MTU budget: 133B. Responses render as native interactive cards without exposing raw code.",
                        wirePacket = "~1A2F:02:0000:00000000::39B1#",
                        byteSize = 44,
                        pduCount = 1
                    ),
                    com.cellular.rpc.engine.ChatMessage(
                        threadId = "th_main",
                        sender = com.cellular.rpc.engine.MessageSender.AI_GATEWAY,
                        text = "",
                        widgetData = welcomeWeather,
                        wirePacket = "~1A2F:04:0001:00000000:${welcomeWeather.toJson()}:64F1#",
                        byteSize = welcomeWeather.toJson().length,
                        pduCount = 1
                    )
                )
                chatRepository.saveMessages(initialMessages)
            }
        }

        // Clean up historical duplicate messages and corrupted binary text from previous runs
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val all = chatRepository.messages
                val db = com.cellular.rpc.data.local.AppDatabase.getInstance(application)
                val allEntities = db.chatMessageDao().getAllMessagesList()
                val seenSignatures = HashSet<String>()
                for (msg in allEntities) {
                    if (com.cellular.rpc.transport.receiver.PallySmsTracker.isCorruptedOrBinaryText(msg.text) && msg.attachmentUri == null) {
                        db.chatMessageDao().deleteMessageById(msg.id)
                        continue
                    }
                    val signature = "${msg.sender}:${msg.text.trim()}:${msg.timestampMs / 15_000L}"
                    if (seenSignatures.contains(signature)) {
                        db.chatMessageDao().deleteMessageById(msg.id)
                    } else {
                        seenSignatures.add(signature)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ChatViewModel", "Startup message deduplication error: ${e.message}")
            }
        }

        // Observe incoming delivered frames from the cellular engine
        viewModelScope.launch {
            queueEngine.inboundDeliveredFlow.collect { (frame, payloadStr) ->
                handleDeliveredChatPacket(frame, payloadStr)
            }
        }

        // Also observe standardized responses from CellularMessageDispatcher (from SMS, MMS WAP Push, Loopback)
        viewModelScope.launch {
            CellularMessageDispatcher.inboundEvents.collect { response ->
                handleStandardizedInboundResponse(response)
            }
        }


        // Preload default Instant Cellular AI Actions
        viewModelScope.launch(Dispatchers.IO) {
            if (customActionDao.getCount() == 0) {
                val presets = listOf(
                    com.cellular.rpc.data.local.CustomActionEntity(
                        id = "act_weather",
                        label = "Weather",
                        prompt = "Please provide current weather in JSON format: {\"type\":\"weather\"}",
                        type = "weather",
                        iconName = "weather",
                        colorHex = "#00E5FF",
                        isPreset = true
                    ),
                    com.cellular.rpc.data.local.CustomActionEntity(
                        id = "act_markets",
                        label = "Markets",
                        prompt = "Please provide market prices in JSON format: {\"type\":\"market_ticker\"}",
                        type = "market_ticker",
                        iconName = "market",
                        colorHex = "#00E676",
                        isPreset = true
                    ),
                    com.cellular.rpc.data.local.CustomActionEntity(
                        id = "act_news",
                        label = "News",
                        prompt = "Please provide top news in JSON format: {\"type\":\"news_digest\"}",
                        type = "news_digest",
                        iconName = "news",
                        colorHex = "#FFB300",
                        isPreset = true
                    ),
                    com.cellular.rpc.data.local.CustomActionEntity(
                        id = "act_poll",
                        label = "Poll",
                        prompt = "Please create a poll in JSON format: {\"type\":\"poll\"}",
                        type = "poll",
                        iconName = "poll",
                        colorHex = "#E040FB",
                        isPreset = true
                    ),
                    com.cellular.rpc.data.local.CustomActionEntity(
                        id = "act_tasks",
                        label = "Tasks",
                        prompt = "Please provide a task checklist in JSON format: {\"type\":\"task_checklist\"}",
                        type = "task_checklist",
                        iconName = "task",
                        colorHex = "#7C4DFF",
                        isPreset = true
                    ),
                    com.cellular.rpc.data.local.CustomActionEntity(
                        id = "act_calc",
                        label = "Calculator",
                        prompt = "Please calculate tip and split in JSON format: {\"type\":\"tool\",\"title\":\"Tip Calculator\"}",
                        type = "tool",
                        iconName = "calc",
                        colorHex = "#40C4FF",
                        isPreset = true
                    )
                )
                customActionDao.insertAll(presets)
            }
        }
    }

    fun saveCustomAction(action: com.cellular.rpc.data.local.CustomActionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            customActionDao.insertAction(action)
        }
    }

    fun deleteCustomAction(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            customActionDao.deleteAction(id)
        }
    }


    /**
     * Dispatches a user chat message over the cellular SMS/MMS RPC engine.
     */
    fun sendChatMessage(text: String, attachments: List<com.cellular.rpc.engine.MessageAttachment> = emptyList()) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return

        val mainAttachment = attachments.firstOrNull()
        val byteCount = trimmed.toByteArray(Charsets.UTF_8).size + if (mainAttachment != null) 30 else 0
        val pduCount = ((byteCount + 139) / 140).coerceAtLeast(1)

        val currentTid = _activeThreadId.value.ifBlank { "th_main" }
        val userMessage = com.cellular.rpc.engine.ChatMessage(
            threadId = currentTid,
            sender = com.cellular.rpc.engine.MessageSender.USER,
            text = trimmed,
            attachment = mainAttachment,
            byteSize = byteCount,
            pduCount = pduCount
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                chatRepository.saveMessage(userMessage)
                conversationThreadDao.updateLastMessage(currentTid, trimmed.take(60), System.currentTimeMillis())

                // Build outbound prompt incorporating attachment references if present
                val promptBody = buildString {
                    if (mainAttachment != null) {
                        when (mainAttachment.type) {
                            com.cellular.rpc.engine.AttachmentType.IMAGE -> append("[Attached Image: ${mainAttachment.fileName}] ")
                            com.cellular.rpc.engine.AttachmentType.FILE -> append("[Attached File: ${mainAttachment.fileName}] ")
                            com.cellular.rpc.engine.AttachmentType.VOICE_NOTE -> append("[Attached Voice Note: ${mainAttachment.durationMs / 1000}s] ")
                        }
                    }
                    append(trimmed)
                }.trim()

                // Determine if user is querying a widget or requesting an action
                val router = com.cellular.rpc.engine.CellularIntentRouter(getApplication())
                val isLocalOnly = router.isLocalUiIntent(promptBody)
                
                if (isLocalOnly) {
                    android.util.Log.i("ChatViewModel", "Sprint 6.4: Intent Router classified prompt as local UI command. Suppressing cellular transmission.")
                    
                    // Respond locally immediately
                    val localResponse = com.cellular.rpc.engine.ChatMessage(
                        threadId = currentTid,
                        sender = com.cellular.rpc.engine.MessageSender.AI_GATEWAY,
                        text = "Local command acknowledged. UI has been updated (Offline Mode).",
                        byteSize = 0,
                        pduCount = 0
                    )
                    chatRepository.saveMessage(localResponse)
                    conversationThreadDao.updateLastMessage(currentTid, "Local command acknowledged...", System.currentTimeMillis())
                    
                    return@launch // Stop execution, don't send over radio
                }

                val lower = promptBody.lowercase()
                val detectedType = when {
                    lower.contains("weather") -> "weather"
                    lower.contains("news") -> "news_digest"
                    lower.contains("market") || lower.contains("btc") || lower.contains("crypto") || lower.contains("price") -> "market_ticker"
                    lower.contains("send") || lower.contains("pay") || lower.contains("transfer") || lower.contains("$") -> "transfer"
                    lower.contains("poll") || lower.contains("vote") -> "poll"
                    lower.contains("tool") || lower.contains("tip") || lower.contains("calc") || lower.contains("split") -> "tool"
                    lower.contains("calendar") || lower.contains("event") || lower.contains("meeting") -> "calendar_event"
                    lower.contains("task") || lower.contains("todo") || lower.contains("checklist") -> "task_checklist"
                    lower.contains("system") || lower.contains("telemetry") || lower.contains("status") -> "system_status"
                    else -> null
                }

                if (detectedType != null) {
                    lastQueriedType = detectedType
                }

                val activeService = com.cellular.rpc.domain.service.CellularServiceManager.getActiveService(getApplication())
                val basePrompt = if (activeService.promptPrefix.isNotBlank()) {
                    "${activeService.promptPrefix} $trimmed"
                } else {
                    trimmed
                }

                // Multiplex thread ID over cellular wire format: [TID:<threadId>] <prompt>
                val promptToSend = if (currentTid != "th_main") {
                    "[TID:$currentTid] $basePrompt"
                } else {
                    basePrompt
                }

                // If an attachment is present, dispatch via native carrier MMS
                if (mainAttachment != null) {
                    try {
                        val targetNum = queueEngine.destinationAddress.replace(Regex("[^0-9+]"), "").ifBlank {
                            activeService.phoneNumber.replace(Regex("[^0-9+]"), "")
                        }
                        val attUri = android.net.Uri.parse(mainAttachment.uri)
                        com.cellular.rpc.transport.receiver.PallyMmsHelper.dispatchCarrierMms(
                            context = getApplication(),
                            destinationNumber = targetNum,
                            text = trimmed,
                            attachmentUri = attUri,
                            mimeType = mainAttachment.mimeType ?: "image/*"
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("ChatViewModel", "Carrier MMS dispatch error: ${e.message}")
                    }
                }

                // Always enqueue the user's natural text prompt so AI agent receives human-readable text
                queueEngine.enqueuePayload(
                    sessionId = activeSessionId,
                    pktType = Frame.PKT_RPC_REQ,
                    payload = promptToSend.toByteArray(Charsets.UTF_8)
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to enqueue chat message: ${e.message}", e)
            }
        }
    }

    /**
     * Dispatches a structured Mini-App or SDUI Widget template request to the AI Gateway.
     */
    fun sendTemplateRequest(template: AiTemplate, userCustomization: String? = null) {
        val currentTid = _activeThreadId.value.ifBlank { "th_main" }
        val promptText = if (!userCustomization.isNullOrBlank()) {
            userCustomization.trim()
        } else {
            template.defaultPrompt
        }

        val orchestrator = CellularAiOrchestrator.getInstance(getApplication())
        val schemaType = when (template.category) {
            com.cellular.rpc.orchestrator.TemplateCategory.MINI_APP -> OrchestratorSchemaType.DYNAMIC_MINIAPP
            com.cellular.rpc.orchestrator.TemplateCategory.SDUI_WIDGET -> OrchestratorSchemaType.SDUI_BLUEPRINT
            com.cellular.rpc.orchestrator.TemplateCategory.DATA_TOOL -> OrchestratorSchemaType.TOOL_WIDGET
        }

        val orchestrated = orchestrator.prepareOutbound(
            userPrompt = promptText,
            threadId = currentTid,
            schemaType = schemaType,
            templateId = template.id
        )

        val userMessage = com.cellular.rpc.engine.ChatMessage(
            threadId = currentTid,
            sender = com.cellular.rpc.engine.MessageSender.USER,
            text = "✨ [${template.title}] $promptText",
            byteSize = orchestrated.estimatedBytes,
            pduCount = ((orchestrated.estimatedBytes + 139) / 140).coerceAtLeast(1)
        )

        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.saveMessage(userMessage)
            conversationThreadDao.updateLastMessage(currentTid, "✨ [${template.title}] $promptText".take(60), System.currentTimeMillis())

            // If loopback simulation is active, immediately register mock offline payload
            if (com.cellular.rpc.widget.WidgetPreferences.isLoopbackSimulationEnabled(getApplication())) {
                delay(600)
                CellularMessageDispatcher.dispatchInbound(
                    context = getApplication(),
                    message = com.cellular.rpc.transport.handler.InboundCellularMessage(
                        transportType = com.cellular.rpc.transport.handler.CellularTransportType.LOOPBACK_SIMULATION,
                        senderAddress = "+16462619684",
                        rawText = "[TID:$currentTid] Here is your ${template.title}:\n\n${template.mockOfflinePayload}"
                    )
                )
            } else {
                queueEngine.enqueuePayload(
                    sessionId = activeSessionId,
                    pktType = Frame.PKT_RPC_REQ,
                    payload = orchestrated.wireText.toByteArray(Charsets.UTF_8)
                )
            }
        }
    }

    val availableTemplates: List<AiTemplate> = OrchestratorTemplateCatalog.TEMPLATES

    private fun handleStandardizedInboundResponse(response: CellularResponse) {
        viewModelScope.launch(Dispatchers.IO) {
            val schemaTarget = if (response.schemaId.isNotEmpty() && response.schemaId != "unknown") response.schemaId else lastQueriedType
            if (response.schemaId.isNotEmpty()) {
                lastQueriedType = schemaTarget
            }
            // Persistence to Room is handled directly and deterministically by CellularMessageDispatcher
            // to guarantee single-source-of-truth without duplicate inserts.
        }
    }

    private fun handleDeliveredChatPacket(frame: Frame, payloadStr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cellularRes = CellularResponse.fromWire(payloadStr)
            val effectiveTarget = if (cellularRes.schemaId.isNotEmpty() && cellularRes.schemaId != "unknown") cellularRes.schemaId else lastQueriedType
            if (cellularRes.schemaId.isNotEmpty()) {
                lastQueriedType = effectiveTarget
            }
            // Persistence to Room is handled directly and deterministically by CellularMessageDispatcher.
        }
    }

    fun castVote(pollId: String, optionIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = chatMessages.value
            val target = current.firstOrNull { it.widgetData is WidgetData.CellularPoll && it.widgetData.id == pollId }
            if (target != null && target.widgetData is WidgetData.CellularPoll) {
                val updatedVotes = target.widgetData.votes.toMutableList()
                if (optionIndex in updatedVotes.indices) {
                    updatedVotes[optionIndex] = updatedVotes[optionIndex] + 1
                }
                val updatedPoll = target.widgetData.copy(
                    votes = updatedVotes,
                    userVoteIndex = optionIndex
                )
                chatRepository.saveMessage(target.copy(widgetData = updatedPoll))
            }

            // Transmit compact vote frame over SMS: VOTE:<pollId>:<optionIndex>
            val votePayload = "VOTE:$pollId:$optionIndex".toByteArray(Charsets.UTF_8)
            queueEngine.enqueuePayload(activeSessionId, Frame.PKT_RPC_REQ, votePayload)
        }
    }

    fun confirmTransfer(transferId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = chatMessages.value
            val target = current.firstOrNull { it.widgetData is WidgetData.CellularTransfer && it.widgetData.id == transferId }
            if (target != null && target.widgetData is WidgetData.CellularTransfer) {
                val updatedTransfer = target.widgetData.copy(status = "AUTHORIZATION_SENT_SMS")
                chatRepository.saveMessage(target.copy(widgetData = updatedTransfer))
            }

            val authPayload = "AUTH_TRANSFER:$transferId".toByteArray(Charsets.UTF_8)
            queueEngine.enqueuePayload(activeSessionId, Frame.PKT_RPC_REQ, authPayload)
        }
    }

    /**
     * Dispatches an RPC query for a widget type (e.g. "weather", "news_digest", "market_ticker").
     */
    fun queryWidget(type: String) {
        lastQueriedType = type
        val currentTid = _activeThreadId.value.ifBlank { "th_main" }
        val rawQueryPrompt = when (type.lowercase()) {
            "weather" -> "Please provide the current weather in JSON format: {\"type\":\"weather\",\"city\":\"San Francisco\",\"temp\":72,\"cond\":\"Sunny\"}"
            "news_digest", "news" -> "Please provide top news headlines in JSON format: {\"type\":\"news_digest\",\"headlines\":[{\"title\":\"Top News\",\"source\":\"Global\",\"summary\":\"Summary of events\"}]}"
            "market_ticker", "market" -> "Please provide current market prices in JSON format: {\"type\":\"market_ticker\",\"symbols\":[{\"symbol\":\"SPY\",\"price\":510.50,\"changePercent\":0.75}]}"
            "task_checklist", "tasks" -> "Please provide a task checklist in JSON format: {\"type\":\"task_checklist\",\"title\":\"Tasks\",\"items\":[{\"id\":\"1\",\"text\":\"Review report\",\"completed\":false}]}"
            "calendar_event", "calendar" -> "Please provide upcoming calendar events in JSON format: {\"type\":\"calendar_event\",\"title\":\"Meeting\",\"time\":\"2:00 PM\",\"location\":\"Office\"}"
            "system_status", "system" -> "Please provide system status in JSON format: {\"type\":\"system_status\",\"status\":\"ONLINE\",\"latencyMs\":45}"
            "poll" -> "Please provide a community poll in JSON format: {\"type\":\"poll\",\"question\":\"Preferred option?\",\"options\":[\"Option A\",\"Option B\"]}"
            else -> "Please provide $type in JSON format: {\"type\":\"$type\"}"
        }

        val queryPrompt = if (currentTid != "th_main") "[TID:$currentTid] $rawQueryPrompt" else rawQueryPrompt

        val userMessage = com.cellular.rpc.engine.ChatMessage(
            threadId = currentTid,
            sender = com.cellular.rpc.engine.MessageSender.USER,
            text = "Request: $type update",
            byteSize = queryPrompt.length,
            pduCount = 1
        )

        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.saveMessage(userMessage)
            conversationThreadDao.updateLastMessage(currentTid, "Request: $type update", System.currentTimeMillis())
            queueEngine.enqueuePayload(
                sessionId = activeSessionId,
                pktType = Frame.PKT_RPC_REQ,
                payload = queryPrompt.toByteArray(Charsets.UTF_8)
            )
        }
    }



    fun deleteChatMessage(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.deleteMessage(messageId)
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentTid = _activeThreadId.value
            chatRepository.deleteMessagesForThread(currentTid)
            conversationThreadDao.updateLastMessage(currentTid, "", System.currentTimeMillis())
        }
    }

    // Thread & Tab Operations
    fun selectThread(threadId: String) {
        _activeThreadId.value = threadId
        if (!_openTabThreadIds.value.contains(threadId)) {
            _openTabThreadIds.value = _openTabThreadIds.value + threadId
        }
        CellularMessageDispatcher.activeThreadId = threadId
        viewModelScope.launch(Dispatchers.IO) {
            conversationThreadDao.markThreadRead(threadId)
        }
    }

    fun openTab(threadId: String) {
        selectThread(threadId)
    }

    fun closeTab(threadId: String) {
        val currentTabs = _openTabThreadIds.value
        val remaining = currentTabs.filter { it != threadId }
        val updatedTabs = remaining.ifEmpty { listOf("th_main") }
        _openTabThreadIds.value = updatedTabs

        // If the closed tab was active, switch to the last remaining tab
        if (_activeThreadId.value == threadId) {
            val newActive = updatedTabs.last()
            _activeThreadId.value = newActive
            CellularMessageDispatcher.activeThreadId = newActive
            viewModelScope.launch(Dispatchers.IO) {
                conversationThreadDao.markThreadRead(newActive)
            }
        }
    }

    fun createNewThread(title: String = "New Conversation"): String {
        val newThreadId = "th_" + java.util.UUID.randomUUID().toString().take(6)
        val now = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            val newThread = ConversationThreadEntity(
                threadId = newThreadId,
                title = title.ifBlank { "Conversation ${newThreadId.takeLast(4)}" },
                createdAtMs = now,
                lastMessageTimestamp = now,
                lastSnippet = "Started conversation"
            )
            conversationThreadDao.insertOrUpdate(newThread)
            selectThread(newThreadId)
        }
        return newThreadId
    }

    fun renameThread(threadId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            conversationThreadDao.renameThread(threadId, newTitle.trim())
        }
    }

    fun togglePinThread(threadId: String, currentPinned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            conversationThreadDao.setPinned(threadId, !currentPinned)
        }
    }

    fun deleteThread(threadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.deleteMessagesForThread(threadId)
            conversationThreadDao.deleteThread(threadId)
            val updatedTabs = _openTabThreadIds.value.filter { it != threadId }.ifEmpty { listOf("th_main") }
            _openTabThreadIds.value = updatedTabs
            if (_activeThreadId.value == threadId) {
                selectThread(updatedTabs.last())
            }
        }
    }
}
