package com.example.ui.chat

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.AiStreamEvent
import com.example.data.ai.FirebaseAiService
import com.example.data.ai.NetworkMonitor
import com.example.data.local.OfflineChatDao
import com.example.data.local.OfflineChatDatabase
import com.example.data.local.OfflineConversationEntity
import com.example.data.local.OfflineMessageEntity
import com.example.data.repository.FirestoreChatRepository
import com.example.model.AttachmentType
import com.example.model.ChatConversation
import com.example.model.ChatMessage
import com.example.model.EishlarModel
import com.example.model.MessageRole
import com.example.model.PendingAttachment
import com.example.model.TranslateLanguage
import com.example.utils.MediaFileHelper
import com.example.utils.VoiceAudioRecorder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "ChatViewModel"

sealed interface ConversationsUiState {
    data object Loading : ConversationsUiState
    data class Success(val items: List<ChatConversation>) : ConversationsUiState
    data class Error(val message: String) : ConversationsUiState
}

class ChatViewModel(
    private val appContext: Context,
    private val currentUserId: String?,
    private val isOfflineGuestOnly: Boolean,
    private val firestoreRepository: FirestoreChatRepository?,
    private val offlineDao: OfflineChatDao,
    private val aiService: FirebaseAiService,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val voiceRecorder = VoiceAudioRecorder(appContext)
    private var activeGenerationJob: Job? = null
    private var recordingTimerJob: Job? = null

    private val _selectedModel = MutableStateFlow(
        if (isOfflineGuestOnly || !networkMonitor.isCurrentlyOnline()) EishlarModel.OFFLINE_LOCAL
        else EishlarModel.HYDROGEN_PRO
    )
    val selectedModel: StateFlow<EishlarModel> = _selectedModel.asStateFlow()

    private val _enableThinking = MutableStateFlow(true)
    val enableThinking: StateFlow<Boolean> = _enableThinking.asStateFlow()

    private val _enableSearch = MutableStateFlow(true)
    val enableSearch: StateFlow<Boolean> = _enableSearch.asStateFlow()

    private val _enableMaps = MutableStateFlow(false)
    val enableMaps: StateFlow<Boolean> = _enableMaps.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<PendingAttachment?>(null)
    val pendingAttachment: StateFlow<PendingAttachment?> = _pendingAttachment.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingThinking = MutableStateFlow("")
    val streamingThinking: StateFlow<String> = _streamingThinking.asStateFlow()

    private val _streamingAnswer = MutableStateFlow("")
    val streamingAnswer: StateFlow<String> = _streamingAnswer.asStateFlow()

    private val _isRecordingAudio = MutableStateFlow(false)
    val isRecordingAudio: StateFlow<Boolean> = _isRecordingAudio.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnlineFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = networkMonitor.isCurrentlyOnline()
    )

    val conversationsState: StateFlow<ConversationsUiState> =
        if (!isOfflineGuestOnly && currentUserId != null && firestoreRepository != null) {
            firestoreRepository.observeConversations(currentUserId)
                .map<List<ChatConversation>, ConversationsUiState> { ConversationsUiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    Log.w(TAG, "Error observing conversations", root)
                    emit(ConversationsUiState.Error(root.message ?: "Suhbatlarni yuklab bo'lmadi"))
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ConversationsUiState.Loading)
        } else {
            offlineDao.observeConversations()
                .map { entities ->
                    ConversationsUiState.Success(
                        entities.map {
                            ChatConversation(
                                id = it.id,
                                userId = "offline_local",
                                title = it.title,
                                modelId = it.modelId,
                                lastMessagePreview = it.lastMessagePreview,
                                messageCount = it.messageCount,
                                updatedAt = Timestamp(it.updatedAtMillis / 1000, 0)
                            )
                        }
                    ) as ConversationsUiState
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ConversationsUiState.Loading)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId == null) {
                flowOf(emptyList())
            } else if (!isOfflineGuestOnly && currentUserId != null && firestoreRepository != null) {
                firestoreRepository.observeMessages(currentUserId, convId)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing messages", firestoreError ?: error)
                        _errorMessage.value = (firestoreError ?: error).localizedMessage
                        emit(emptyList())
                    }
            } else {
                offlineDao.observeMessages(convId).map { list ->
                    list.map {
                        ChatMessage(
                            id = it.id,
                            userId = "offline_local",
                            role = it.role,
                            text = it.text,
                            thinkingText = it.thinkingText,
                            modelId = it.modelId,
                            attachmentType = it.attachmentType,
                            attachmentName = it.attachmentName,
                            attachmentUri = it.attachmentUri
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun selectModel(model: EishlarModel) {
        if (isOfflineGuestOnly && !model.isOffline) {
            _toastMessage.value = "Bulutli Gemini modellari uchun Google bilan kiring. Hozir Offline AI faol."
            return
        }
        _selectedModel.value = model
        if (model == EishlarModel.HYDROGEN_THINKING || model == EishlarModel.ARGON_CODER) {
            _enableThinking.value = true
        }
        if (model == EishlarModel.HYDROGEN_PRO) {
            _enableSearch.value = true
        }
    }

    fun toggleThinking() {
        _enableThinking.value = !_enableThinking.value
    }

    fun toggleSearch() {
        val next = !_enableSearch.value
        _enableSearch.value = next
        if (next) _enableMaps.value = false
    }

    fun toggleMaps() {
        val next = !_enableMaps.value
        _enableMaps.value = next
        if (next) _enableSearch.value = false
    }

    fun startNewChat() {
        activeGenerationJob?.cancel()
        _isGenerating.value = false
        _streamingThinking.value = ""
        _streamingAnswer.value = ""
        _activeConversationId.value = null
        _pendingAttachment.value = null
        _errorMessage.value = null
    }

    fun selectConversation(conversation: ChatConversation) {
        activeGenerationJob?.cancel()
        _isGenerating.value = false
        _streamingThinking.value = ""
        _streamingAnswer.value = ""
        _activeConversationId.value = conversation.id
        val savedModel = EishlarModel.fromId(conversation.modelId)
        if (!isOfflineGuestOnly || savedModel.isOffline) {
            _selectedModel.value = savedModel
        }
        _errorMessage.value = null
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (_activeConversationId.value == conversationId) {
                startNewChat()
            }
            if (!isOfflineGuestOnly && currentUserId != null && firestoreRepository != null) {
                firestoreRepository.deleteConversation(currentUserId, conversationId)
                    .onFailure { _errorMessage.value = it.localizedMessage ?: "O'chirishda xatolik" }
            } else {
                offlineDao.deleteMessagesForConversation(conversationId)
                offlineDao.deleteConversation(conversationId)
            }
        }
    }

    fun attachUri(
        uri: Uri,
        type: AttachmentType,
        explicitMimeType: String? = null,
        isImageTranslate: Boolean = false,
        translateTarget: TranslateLanguage = TranslateLanguage.UZ
    ) {
        val name = MediaFileHelper.getFileName(appContext, uri)
        val size = MediaFileHelper.getFileSize(appContext, uri)
        val mime = explicitMimeType
            ?: appContext.contentResolver.getType(uri)
            ?: when (type) {
                AttachmentType.IMAGE -> "image/jpeg"
                AttachmentType.VIDEO -> "video/mp4"
                AttachmentType.AUDIO -> "audio/mp4"
                else -> "text/plain"
            }

        _pendingAttachment.value = PendingAttachment(
            uri = uri,
            displayName = name,
            mimeType = mime,
            type = type,
            sizeBytes = size,
            isImageTranslate = isImageTranslate,
            translateTarget = translateTarget
        )

        if (!isOfflineGuestOnly) {
            when (type) {
                AttachmentType.IMAGE, AttachmentType.VIDEO -> {
                    if (_selectedModel.value != EishlarModel.NANO_BANANA_STUDIO) {
                        _selectedModel.value = EishlarModel.NEON_VISION
                    }
                }
                AttachmentType.AUDIO -> _selectedModel.value = EishlarModel.AUDIO_TRANSCRIBE
                else -> Unit
            }
        }
    }

    fun updateTranslateLanguage(lang: TranslateLanguage) {
        _pendingAttachment.value = _pendingAttachment.value?.copy(
            isImageTranslate = true,
            translateTarget = lang
        )
    }

    fun clearAttachment() {
        _pendingAttachment.value = null
    }

    fun startVoiceRecording() {
        if (_isRecordingAudio.value) return
        try {
            voiceRecorder.startRecording()
            _isRecordingAudio.value = true
            _recordingSeconds.value = 0
            recordingTimerJob?.cancel()
            recordingTimerJob = viewModelScope.launch {
                while (_isRecordingAudio.value) {
                    delay(1000)
                    _recordingSeconds.value += 1
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio recording start failed", e)
            _errorMessage.value = "Mikrofonni ishga tushirib bo'lmadi: ${e.localizedMessage}"
        }
    }

    fun stopVoiceRecordingAndAttach() {
        recordingTimerJob?.cancel()
        _isRecordingAudio.value = false
        val file = voiceRecorder.stopRecording()
        if (file != null) {
            attachUri(
                uri = Uri.fromFile(file),
                type = AttachmentType.AUDIO,
                explicitMimeType = "audio/mp4"
            )
            if (!isOfflineGuestOnly) {
                _selectedModel.value = EishlarModel.AUDIO_TRANSCRIBE
            }
        } else {
            _errorMessage.value = "Ovoz yozilmadi, qayta urinib ko'ring."
        }
    }

    fun cancelVoiceRecording() {
        recordingTimerJob?.cancel()
        _isRecordingAudio.value = false
        voiceRecorder.stopSilently()
    }

    fun sendMessage(rawPrompt: String) {
        val attachment = _pendingAttachment.value
        val prompt = rawPrompt.trim().ifBlank {
            when {
                attachment?.isImageTranslate == true -> "Rasmdan matnlarni ${attachment.translateTarget.label} tiliga tarjima qil."
                attachment?.type == AttachmentType.IMAGE -> "Ushbu rasmni tahlil qil."
                attachment?.type == AttachmentType.VIDEO -> "Ushbu videoni tahlil qilib asosiy ma'lumotlarni chiqar."
                attachment?.type == AttachmentType.AUDIO -> "Ovoz yozuvini matnga aylantir va tushuntir."
                attachment != null -> "Ushbu faylni tahlil qilib ber: ${attachment.displayName}"
                else -> return
            }
        }

        if (_isGenerating.value) return

        // Auto switch to Offline AI if internet is off and user hasn't forced cloud
        val effectiveModel = if (!isOnline.value && !_selectedModel.value.isOffline) {
            _toastMessage.value = "Internet yo'q: E-Ishlar-Max Offline AI ishga tushirildi."
            EishlarModel.OFFLINE_LOCAL
        } else if (isOfflineGuestOnly) {
            EishlarModel.OFFLINE_LOCAL
        } else {
            _selectedModel.value
        }

        _pendingAttachment.value = null
        _errorMessage.value = null
        _isGenerating.value = true
        _streamingThinking.value = ""
        _streamingAnswer.value = ""

        val currentHistory = messages.value
        activeGenerationJob = viewModelScope.launch {
            try {
                // Ensure conversation exists
                var convId = _activeConversationId.value
                var isFirstMessage = false
                if (convId == null) {
                    isFirstMessage = true
                    convId = createConversationInternal(
                        title = prompt.take(48),
                        modelId = effectiveModel.id,
                        preview = prompt
                    )
                    _activeConversationId.value = convId
                }

                val existingCount = currentHistory.size.toLong()
                val userMsg = ChatMessage(
                    userId = currentUserId ?: "offline_local",
                    role = MessageRole.USER.name,
                    text = prompt,
                    modelId = effectiveModel.id,
                    attachmentType = attachment?.type?.name,
                    attachmentName = attachment?.displayName,
                    attachmentUri = attachment?.uri?.toString()
                )

                saveMessageInternal(
                    convId = convId,
                    message = userMsg,
                    newTitle = if (isFirstMessage) prompt.take(48) else null,
                    newCount = existingCount + 1L
                )

                var finalCompleted: AiStreamEvent.Completed? = null
                aiService.generateReplyStream(
                    model = effectiveModel,
                    prompt = prompt,
                    history = currentHistory,
                    attachment = attachment,
                    enableThinking = _enableThinking.value,
                    enableSearch = _enableSearch.value,
                    enableMaps = _enableMaps.value
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.Progress -> {
                            _streamingThinking.value = event.thinkingText
                            _streamingAnswer.value = event.answerText
                        }
                        is AiStreamEvent.Completed -> {
                            finalCompleted = event
                        }
                    }
                }

                val completed = finalCompleted ?: throw IllegalStateException("Javob olinmadi.")
                val groundingUrls = completed.groundingSources.filter { !it.isMaps }.map { "${it.title}|${it.uri}" }
                val mapsPlaces = completed.groundingSources.filter { it.isMaps }.map { "${it.title}|${it.uri}" }

                val modelMsg = ChatMessage(
                    userId = currentUserId ?: "offline_local",
                    role = MessageRole.MODEL.name,
                    text = completed.answerText,
                    thinkingText = completed.thinkingText,
                    modelId = effectiveModel.id,
                    attachmentType = if (completed.generatedImageUri != null) AttachmentType.GENERATED_IMAGE.name else null,
                    attachmentName = completed.generatedImageName,
                    attachmentUri = completed.generatedImageUri?.toString(),
                    groundingUrls = groundingUrls,
                    mapsPlaceNames = mapsPlaces
                )

                saveMessageInternal(
                    convId = convId,
                    message = modelMsg,
                    newTitle = null,
                    newCount = existingCount + 2L
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI generation timed out", e)
                _errorMessage.value = "So'rov 60 soniyadan oshdi (timeout). Qayta urinib ko'ring."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI generation failed", e)
                _errorMessage.value = e.localizedMessage ?: "Xatolik yuz berdi."
            } finally {
                _isGenerating.value = false
                _streamingThinking.value = ""
                _streamingAnswer.value = ""
            }
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        _isGenerating.value = false
        _streamingThinking.value = ""
        _streamingAnswer.value = ""
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun consumeToast() {
        _toastMessage.value = null
    }

    fun showMessage(msg: String) {
        _toastMessage.value = msg
    }

    private suspend fun createConversationInternal(
        title: String,
        modelId: String,
        preview: String
    ): String {
        if (!isOfflineGuestOnly && currentUserId != null && firestoreRepository != null) {
            return firestoreRepository.createConversation(currentUserId, title, modelId, preview)
                .getOrThrow()
        } else {
            val id = UUID.randomUUID().toString().replace("-", "")
            offlineDao.upsertConversation(
                OfflineConversationEntity(
                    id = id,
                    title = title.ifBlank { "Offline Suhbat" },
                    modelId = modelId,
                    lastMessagePreview = preview.take(300),
                    messageCount = 0L,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            return id
        }
    }

    private suspend fun saveMessageInternal(
        convId: String,
        message: ChatMessage,
        newTitle: String?,
        newCount: Long
    ) {
        if (!isOfflineGuestOnly && currentUserId != null && firestoreRepository != null) {
            firestoreRepository.saveMessageAndUpdateConversation(
                userId = currentUserId,
                conversationId = convId,
                message = message,
                newTitleIfNeeded = newTitle,
                updatedMessageCount = newCount
            ).getOrThrow()
        } else {
            val now = System.currentTimeMillis()
            offlineDao.insertMessage(
                OfflineMessageEntity(
                    id = UUID.randomUUID().toString().replace("-", ""),
                    conversationId = convId,
                    role = message.role,
                    text = message.text,
                    thinkingText = message.thinkingText,
                    modelId = message.modelId,
                    attachmentType = message.attachmentType,
                    attachmentName = message.attachmentName,
                    attachmentUri = message.attachmentUri,
                    createdAtMillis = now
                )
            )
            offlineDao.upsertConversation(
                OfflineConversationEntity(
                    id = convId,
                    title = newTitle ?: "Offline Suhbat",
                    modelId = message.modelId,
                    lastMessagePreview = message.text.take(300),
                    messageCount = newCount,
                    updatedAtMillis = now
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecorder.stopSilently()
    }

    companion object {
        fun provideFactory(
            currentUserId: String?,
            isOfflineGuestOnly: Boolean
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val repo = if (!isOfflineGuestOnly && currentUserId != null) {
                    FirestoreChatRepository(app)
                } else null
                val offlineDao = OfflineChatDatabase.getInstance(app).chatDao()
                ChatViewModel(
                    appContext = app,
                    currentUserId = currentUserId,
                    isOfflineGuestOnly = isOfflineGuestOnly,
                    firestoreRepository = repo,
                    offlineDao = offlineDao,
                    aiService = FirebaseAiService(app),
                    networkMonitor = NetworkMonitor(app)
                )
            }
        }
    }
}
