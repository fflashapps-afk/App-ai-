package com.example.model

import android.net.Uri
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class EishlarModel(
    val id: String,
    val modelName: String,
    val displayName: String,
    val badge: String,
    val description: String,
    val supportsThinking: Boolean = false,
    val supportsSearch: Boolean = false,
    val supportsMaps: Boolean = false,
    val supportsVisionVideo: Boolean = false,
    val supportsAudioTranscribe: Boolean = false,
    val supportsImageGen: Boolean = false,
    val isOffline: Boolean = false
) {
    HYDROGEN_PRO(
        id = "hydrogen_pro",
        modelName = "gemini-4-hydrogen",
        displayName = "E-Ishlar-Max Pro",
        badge = "Search & Maps",
        description = "Google Search va Maps orqali eng so'nggi aniq ma'lumotlar",
        supportsThinking = true,
        supportsSearch = true,
        supportsMaps = true
    ),
    HYDROGEN_THINKING(
        id = "hydrogen_thinking",
        modelName = "gemini-4-hydrogen",
        displayName = "E-Ishlar-Max Thinking",
        badge = "Deep Think",
        description = "Chuqur mantiqiy fikrlash va bosqichma-bosqich tahlil",
        supportsThinking = true,
        supportsSearch = true
    ),
    NEON_VISION(
        id = "neon_vision",
        modelName = "gemini-4-neon",
        displayName = "E-Ishlar-Max Vision",
        badge = "Rasm & Video",
        description = "Rasmdan tarjima, foto tahlil va video tushunish (Gemini 4 Neon)",
        supportsVisionVideo = true
    ),
    AUDIO_TRANSCRIBE(
        id = "audio_transcribe",
        modelName = "gemini-3.5-transcribe",
        displayName = "E-Ishlar-Max Audio",
        badge = "Ovoz & Matn",
        description = "Mikrofon va audio fayllarni aniq matnga aylantirish",
        supportsAudioTranscribe = true
    ),
    NANO_BANANA_STUDIO(
        id = "nano_banana_studio",
        modelName = "gemini-nano-banana-2.1",
        displayName = "E-Ishlar-Max Studio",
        badge = "Rasm Yaratish",
        description = "Matn yoki rasmdan yangi rasm yaratish va tahrirlash",
        supportsImageGen = true
    ),
    ARGON_CODER(
        id = "argon_coder",
        modelName = "gemini-4-argon",
        displayName = "E-Ishlar-Max Coder",
        badge = "STEM & GitHub",
        description = "Murakkab dasturlash, GitHub kodlar va matematika",
        supportsThinking = true
    ),
    FLASH_FAST(
        id = "flash_fast",
        modelName = "gemini-3.8-flash",
        displayName = "E-Ishlar-Max Flash",
        badge = "Tezkor",
        description = "Kunlik savollar, matn va hujjatlar uchun tezkor model"
    ),
    OFFLINE_LOCAL(
        id = "offline_local",
        modelName = "offline-local",
        displayName = "E-Ishlar-Max Offline",
        badge = "Offline",
        description = "Internetsiz ishlaydigan lokal AI (hisob-kitob, tarjima, kod)",
        isOffline = true
    );

    companion object {
        fun fromId(raw: String?): EishlarModel =
            entries.firstOrNull { it.id == raw || it.modelName == raw } ?: HYDROGEN_PRO
    }
}

enum class TranslateLanguage(val code: String, val label: String) {
    UZ("uz", "O'zbekcha"),
    EN("en", "English"),
    RU("ru", "Русский"),
    TR("tr", "Türkçe"),
    AR("ar", "العربية"),
    DE("de", "Deutsch")
}

enum class AttachmentType {
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    GENERATED_IMAGE,
    CODE;

    companion object {
        fun fromStringOrNull(raw: String?): AttachmentType? =
            entries.firstOrNull { it.name == raw }
    }
}

enum class MessageRole {
    USER,
    MODEL;

    companion object {
        fun fromString(raw: String?): MessageRole =
            entries.firstOrNull { it.name == raw } ?: MODEL
    }
}

data class PendingAttachment(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val type: AttachmentType,
    val sizeBytes: Long = 0L,
    val isImageTranslate: Boolean = false,
    val translateTarget: TranslateLanguage = TranslateLanguage.UZ
)

data class GroundingSource(
    val title: String,
    val uri: String,
    val isMaps: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class ChatConversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val modelId: String = EishlarModel.HYDROGEN_PRO.id,
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = MessageRole.MODEL.name,
    val text: String = "",
    val thinkingText: String? = null,
    val modelId: String = EishlarModel.HYDROGEN_PRO.id,
    val attachmentType: String? = null,
    val attachmentName: String? = null,
    val attachmentUri: String? = null,
    val groundingUrls: List<String> = emptyList(),
    val mapsPlaceNames: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)
