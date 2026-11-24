package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.model.AttachmentType
import com.example.model.ChatMessage
import com.example.model.EishlarModel
import com.example.model.GroundingSource
import com.example.model.MessageRole
import com.example.model.PendingAttachment
import com.example.utils.MediaFileHelper
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "FirebaseAiService"

sealed interface AiStreamEvent {
    data class Progress(
        val thinkingText: String,
        val answerText: String
    ) : AiStreamEvent

    data class Completed(
        val thinkingText: String?,
        val answerText: String,
        val groundingSources: List<GroundingSource> = emptyList(),
        val generatedImageUri: android.net.Uri? = null,
        val generatedImageName: String? = null
    ) : AiStreamEvent
}

class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isCurrentlyOnline(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    val isOnlineFlow: Flow<Boolean> = callbackFlow {
        trySend(isCurrentlyOnline())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isCurrentlyOnline())
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        awaitClose {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }
}

class FirebaseAiService(private val context: Context) {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun systemInstructionText(model: EishlarModel): String = """
        Siz E-Ishlar-Max (brend kodi: EISHLARAI) — Abdullojon tomonidan yaratilgan yuqori darajadagi sun'iy intellekt yordamchisisiz.
        Foydalanuvchi qaysi tilda (O'zbek, Ingliz, Rus va boshqalar) yozsa, o'sha tilda aniq, tushunarli va professional javob bering.
        Agar foydalanuvchi kod so'rasa, toza markdown kod bloklari (```kotlin, ```python va h.k.) bering.
        Agar Search yoki Maps vositalari yoqilgan bo'lsa, savolga javob berishdan oldin eng so'nggi kerakli ma'lumotlarni qidirib, faqat dolzarb va foydali qismini taqdim eting.
        Joriy faol model: ${model.displayName} (${model.modelName}).
    """.trimIndent()

    fun generateReplyStream(
        model: EishlarModel,
        prompt: String,
        history: List<ChatMessage>,
        attachment: PendingAttachment?,
        enableThinking: Boolean,
        enableSearch: Boolean,
        enableMaps: Boolean
    ): Flow<AiStreamEvent> = flow {
        // 1. Offline AI fallback
        if (model.isOffline) {
            val offlineResult = OfflineAiEngine.respond(prompt, history)
            if (enableThinking) {
                emit(AiStreamEvent.Progress(offlineResult.thinkingTrace, ""))
            }
            emit(
                AiStreamEvent.Completed(
                    thinkingText = if (enableThinking) offlineResult.thinkingTrace else null,
                    answerText = offlineResult.responseText
                )
            )
            return@flow
        }

        // 2. Image Generation / Editing via gemini-nano-banana-2.1
        if (model == EishlarModel.NANO_BANANA_STUDIO) {
            val generated = withTimeout(60_000L) {
                generateOrEditImage(prompt, attachment)
            }
            emit(generated)
            return@flow
        }

        // 3. Enrich with real GitHub repo info if prompt references github.com
        val githubContext = fetchGithubContextIfPresent(prompt)
        val effectivePrompt = if (githubContext != null) {
            "$prompt\n\n[GitHub Repository Real-Time Data]\n$githubContext"
        } else {
            prompt
        }

        // 4. Build model configuration dynamically
        val shouldThink = enableThinking || model == EishlarModel.HYDROGEN_THINKING || model == EishlarModel.ARGON_CODER
        val useMapsTool = model == EishlarModel.HYDROGEN_PRO && (enableMaps || looksLikeMapsQuery(prompt))
        val useSearchTool = !useMapsTool && (enableSearch || model == EishlarModel.HYDROGEN_PRO || model == EishlarModel.HYDROGEN_THINKING)

        val toolsList = buildList {
            if (useMapsTool) add(Tool.googleMaps())
            else if (useSearchTool) add(Tool.googleSearch())
        }

        val config = generationConfig {
            if (shouldThink) {
                thinkingConfig = thinkingConfig {
                    includeThoughts = true
                }
            }
        }

        val resolvedModelName = when {
            attachment?.type == AttachmentType.IMAGE || attachment?.type == AttachmentType.VIDEO ->
                EishlarModel.NEON_VISION.modelName
            attachment?.type == AttachmentType.AUDIO ->
                EishlarModel.AUDIO_TRANSCRIBE.modelName
            else -> model.modelName
        }

        val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = resolvedModelName,
            generationConfig = config,
            tools = toolsList.ifEmpty { null },
            systemInstruction = content { text(systemInstructionText(model)) }
        )

        val userContent = buildUserContent(effectivePrompt, attachment)
        val thinkingBuilder = StringBuilder()
        val answerBuilder = StringBuilder()
        val collectedSources = mutableListOf<GroundingSource>()

        withTimeout(60_000L) {
            if (attachment == null && history.isNotEmpty() && toolsList.isEmpty()) {
                val chatHistory = history.takeLast(14).mapNotNull { msg ->
                    if (msg.text.isBlank()) null
                    else content(if (MessageRole.fromString(msg.role) == MessageRole.USER) "user" else "model") {
                        text(msg.text)
                    }
                }
                val chat = generativeModel.startChat(history = chatHistory)
                chat.sendMessageStream(effectivePrompt).collect { chunk ->
                    chunk.thoughtSummary?.let { thinkingBuilder.append(it) }
                    chunk.text?.let { answerBuilder.append(it) }
                    extractGroundingSources(chunk.candidates.firstOrNull()?.groundingMetadata, collectedSources)
                    emit(
                        AiStreamEvent.Progress(
                            thinkingText = thinkingBuilder.toString(),
                            answerText = answerBuilder.toString()
                        )
                    )
                }
            } else {
                generativeModel.generateContentStream(userContent).collect { chunk ->
                    chunk.thoughtSummary?.let { thinkingBuilder.append(it) }
                    chunk.text?.let { answerBuilder.append(it) }
                    extractGroundingSources(chunk.candidates.firstOrNull()?.groundingMetadata, collectedSources)
                    emit(
                        AiStreamEvent.Progress(
                            thinkingText = thinkingBuilder.toString(),
                            answerText = answerBuilder.toString()
                        )
                    )
                }
            }
        }

        val finalAnswer = answerBuilder.toString().ifBlank {
            throw IllegalStateException("Model bo'sh javob qaytardi. Qayta urinib ko'ring.")
        }
        val finalThinking = thinkingBuilder.toString().takeIf { it.isNotBlank() }

        emit(
            AiStreamEvent.Completed(
                thinkingText = finalThinking,
                answerText = finalAnswer,
                groundingSources = collectedSources.distinctBy { it.uri }.take(10)
            )
        )
    }

    private suspend fun generateOrEditImage(
        prompt: String,
        attachment: PendingAttachment?
    ): AiStreamEvent.Completed {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE, ResponseModality.TEXT)
        }
        val imageModel = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = EishlarModel.NANO_BANANA_STUDIO.modelName,
            generationConfig = config
        )

        val response = if (attachment != null && attachment.type == AttachmentType.IMAGE) {
            val inputBitmap = withContext(Dispatchers.IO) {
                MediaFileHelper.decodeScaledBitmap(context, attachment.uri)
            } ?: throw IllegalStateException("Kirish rasmini ochib bo'lmadi.")
            imageModel.generateContent(
                content {
                    image(inputBitmap)
                    text(prompt.ifBlank { "Ushbu rasmni zamonaviy va chiroyli uslubda tahrirla." })
                }
            )
        } else {
            imageModel.generateContent(prompt.ifBlank { "Futuristic luminous AI emblem artwork" })
        }

        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        val outputBitmap: Bitmap = imagePart?.image
            ?: throw IllegalStateException(
                response.text?.takeIf { it.isNotBlank() }
                    ?: "Rasm yaratish uchun Firebase Blaze rejasini yoqish yoki promptni aniqlashtirish talab qilinishi mumkin."
            )

        val savedUri = withContext(Dispatchers.IO) {
            MediaFileHelper.saveGeneratedBitmapLocally(context, outputBitmap)
        }
        val fileName = "eishlar_studio_${System.currentTimeMillis()}.png"
        val caption = response.text?.takeIf { it.isNotBlank() }
            ?: "Rasm **E-Ishlar-Max Studio (`gemini-nano-banana-2.1`)** orqali yaratildi. Bosib kattalashtirishingiz yoki yuklab olishingiz mumkin."

        return AiStreamEvent.Completed(
            thinkingText = null,
            answerText = caption,
            generatedImageUri = savedUri,
            generatedImageName = fileName
        )
    }

    private suspend fun buildUserContent(
        prompt: String,
        attachment: PendingAttachment?
    ): Content = withContext(Dispatchers.IO) {
        if (attachment == null) {
            return@withContext content { text(prompt) }
        }

        when (attachment.type) {
            AttachmentType.IMAGE -> {
                val bitmap = MediaFileHelper.decodeScaledBitmap(context, attachment.uri)
                    ?: throw IllegalStateException("Rasmni o'qib bo'lmadi.")
                val bytes = MediaFileHelper.bitmapToJpegBytes(bitmap)
                val finalPrompt = if (attachment.isImageTranslate) {
                    val targetLang = attachment.translateTarget.label
                    """
                    Rasmdan barcha matnlarni aniq o'qib chiqing va **$targetLang** tiliga tabiiy tarjima qiling.
                    Format:
                    1) **Aniqlangan asl matn**
                    2) **$targetLang tarjimasi**
                    ${if (prompt.isNotBlank()) "\nQo'shimcha izoh: $prompt" else ""}
                    """.trimIndent()
                } else {
                    prompt.ifBlank { "Ushbu rasmni batafsil tahlil qilib bering." }
                }
                content {
                    inlineData(bytes, "image/jpeg")
                    text(finalPrompt)
                }
            }
            AttachmentType.VIDEO -> {
                val bytes = MediaFileHelper.readBytesWithinLimit(context, attachment.uri)
                val mime = attachment.mimeType.ifBlank { "video/mp4" }
                content {
                    inlineData(bytes, mime)
                    text(prompt.ifBlank { "Ushbu videodagi asosiy voqealar va muhim ma'lumotlarni tahlil qilib bering." })
                }
            }
            AttachmentType.AUDIO -> {
                val bytes = MediaFileHelper.readBytesWithinLimit(context, attachment.uri)
                val mime = attachment.mimeType.ifBlank { "audio/mp4" }
                content {
                    inlineData(bytes, mime)
                    text(prompt.ifBlank { "Ushbu audio yozuvdagi nutqni aniq transkripsiya qiling va asosiy mazmunini yozing." })
                }
            }
            AttachmentType.FILE, AttachmentType.CODE, AttachmentType.GENERATED_IMAGE -> {
                val mime = attachment.mimeType.lowercase()
                if (mime.contains("pdf")) {
                    val bytes = MediaFileHelper.readBytesWithinLimit(context, attachment.uri)
                    content {
                        inlineData(bytes, "application/pdf")
                        text(prompt.ifBlank { "Ushbu PDF hujjatni tahlil qiling va qisqacha mazmunini bering." })
                    }
                } else {
                    val rawText = context.contentResolver.openInputStream(attachment.uri)
                        ?.bufferedReader()?.use { it.readText() }
                        ?.take(28000)
                        ?: ""
                    content {
                        text("Biriktirilgan fayl (${attachment.displayName}):\n```\n$rawText\n```\n\nSavol: ${prompt.ifBlank { "Faylni tahlil qilib bering." }}")
                    }
                }
            }
        }
    }

    private fun extractGroundingSources(
        metadata: com.google.firebase.ai.type.GroundingMetadata?,
        target: MutableList<GroundingSource>
    ) {
        val chunks = metadata?.groundingChunks ?: return
        for (chunk in chunks) {
            chunk.web?.let { web ->
                val uri = web.uri
                if (!uri.isNullOrBlank()) {
                    target.add(GroundingSource(title = web.title ?: uri, uri = uri, isMaps = false))
                }
            }
            chunk.maps?.let { maps ->
                val uri = maps.uri
                if (!uri.isNullOrBlank()) {
                    target.add(GroundingSource(title = maps.title ?: "Google Maps", uri = uri, isMaps = true))
                }
            }
        }
    }

    private fun looksLikeMapsQuery(prompt: String): Boolean {
        val lower = prompt.lowercase()
        val keywords = listOf(
            "xarita", "maps", "qayerda", "manzil", "yaqin", "restoran",
            "kafe", "mehmonxona", "hotel", "masjid", "shifoxona",
            "near me", "location", "where is", "directions", "route"
        )
        return keywords.any { lower.contains(it) }
    }

    private suspend fun fetchGithubContextIfPresent(prompt: String): String? = withContext(Dispatchers.IO) {
        val match = Regex("""https?://github\.com/([A-Za-z0-9_.\-]+)/([A-Za-z0-9_.\-]+)""").find(prompt)
            ?: return@withContext null
        val owner = match.groupValues[1]
        val repo = match.groupValues[2].removeSuffix(".git")
        try {
            val repoReq = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo")
                .header("Accept", "application/vnd.github+json")
                .build()
            val repoJson = httpClient.newCall(repoReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                JSONObject(resp.body?.string().orEmpty())
            }
            val desc = repoJson.optString("description")
            val stars = repoJson.optInt("stargazers_count")
            val lang = repoJson.optString("language")

            val contentsReq = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/contents")
                .header("Accept", "application/vnd.github+json")
                .build()
            val filesList = httpClient.newCall(contentsReq).execute().use { resp ->
                if (!resp.isSuccessful) emptyList<String>()
                else {
                    val arr = JSONArray(resp.body?.string().orEmpty())
                    (0 until minOf(arr.length(), 20)).map { idx ->
                        val item = arr.getJSONObject(idx)
                        "${item.optString("type")}: ${item.optString("name")}"
                    }
                }
            }

            val readmeReq = Request.Builder()
                .url("https://raw.githubusercontent.com/$owner/$repo/HEAD/README.md")
                .build()
            val readme = httpClient.newCall(readmeReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string()?.take(3500) else null
            }

            buildString {
                appendLine("Repository: $owner/$repo (Stars: $stars, Language: $lang)")
                if (desc.isNotBlank()) appendLine("Description: $desc")
                if (filesList.isNotEmpty()) appendLine("Top-level files: ${filesList.joinToString(", ")}")
                if (!readme.isNullOrBlank()) appendLine("README excerpt:\n$readme")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "GitHub fetch skipped: ${e.message}")
            null
        }
    }
}
