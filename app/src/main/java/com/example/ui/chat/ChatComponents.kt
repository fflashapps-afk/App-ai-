package com.example.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.model.AttachmentType
import com.example.model.ChatMessage
import com.example.model.EishlarModel
import com.example.model.MessageRole
import com.example.ui.theme.AmberThinking
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.utils.CodeBlockItem
import com.example.utils.MediaFileHelper
import com.example.utils.ParsedMarkdownSegment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorBottomSheet(
    selectedModel: EishlarModel,
    isOfflineGuestOnly: Boolean,
    onSelectModel: (EishlarModel) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Modelni almashtirish",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "E-Ishlar-Max AI Engine • Owner: Abdullojon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "EISHLARAI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 460.dp)
            ) {
                items(EishlarModel.entries) { model ->
                    val isSelected = model == selectedModel
                    val enabled = !isOfflineGuestOnly || model.isOffline
                    Surface(
                        onClick = {
                            if (enabled) {
                                onSelectModel(model)
                                onDismiss()
                            }
                        },
                        enabled = enabled,
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("model_option_${model.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = iconForModel(model),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = model.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = model.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = model.modelName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Tanlangan",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    onPickImage: () -> Unit,
    onPickImageTranslate: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickDocument: () -> Unit,
    onActivateImageGen: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Media, Fayl va AI Vositalar",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Rasm, video, audio, PDF yoki kod fayllarini yuboring va tahlil qiling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile(
                    icon = Icons.Default.Translate,
                    title = "Rasmdan tarjima",
                    subtitle = "Gemini 4 Neon",
                    onClick = { onDismiss(); onPickImageTranslate() },
                    modifier = Modifier.weight(1f).testTag("action_image_translate")
                )
                ActionTile(
                    icon = Icons.Default.Image,
                    title = "Rasm tahlili",
                    subtitle = "Foto yuklash",
                    onClick = { onDismiss(); onPickImage() },
                    modifier = Modifier.weight(1f).testTag("action_pick_image")
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile(
                    icon = Icons.Default.VideoFile,
                    title = "Video tahlil",
                    subtitle = "Gemini 4 Neon",
                    onClick = { onDismiss(); onPickVideo() },
                    modifier = Modifier.weight(1f).testTag("action_pick_video")
                )
                ActionTile(
                    icon = Icons.Default.GraphicEq,
                    title = "Audio fayl",
                    subtitle = "Gemini 3.5 Transcribe",
                    onClick = { onDismiss(); onPickAudio() },
                    modifier = Modifier.weight(1f).testTag("action_pick_audio")
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile(
                    icon = Icons.Default.AttachFile,
                    title = "Fayl & Kod",
                    subtitle = "PDF, KT, PY, TXT",
                    onClick = { onDismiss(); onPickDocument() },
                    modifier = Modifier.weight(1f).testTag("action_pick_file")
                )
                ActionTile(
                    icon = Icons.Default.Brush,
                    title = "Rasm yaratish",
                    subtitle = "Nano Banana 2.1",
                    onClick = { onDismiss(); onActivateImageGen() },
                    modifier = Modifier.weight(1f).testTag("action_image_gen")
                )
            }
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeDashboard(
    selectedModel: EishlarModel,
    onPromptSuggestion: (String, EishlarModel?) -> Unit,
    onOpenImageTranslate: () -> Unit,
    onOpenVideoPicker: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .height(155.dp)
                .clip(MaterialTheme.shapes.extraLarge)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_eishlar_hero_1795539252287),
                contentDescription = "E-Ishlar-Max Studio",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color(0x22070B12), Color(0xDD070B12)))
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "EISHLARAI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Owner: Abdullojon",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Bugun nima qilamiz?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Faol model: ${selectedModel.displayName} (${selectedModel.modelName})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SuggestionCard(
                    icon = Icons.Default.Translate,
                    title = "Rasmdan Tarjima",
                    desc = "Rasmdagi matnni o'zbek yoki boshqa tilga tarjima",
                    onClick = onOpenImageTranslate,
                    modifier = Modifier.weight(1f)
                )
                SuggestionCard(
                    icon = Icons.Default.Public,
                    title = "Google Search",
                    desc = "Internetdan eng so'nggi yangilik va faktlar",
                    onClick = {
                        onPromptSuggestion(
                            "2026-yilda sun'iy intellekt texnologiyalaridagi eng muhim yangiliklar nimalar?",
                            EishlarModel.HYDROGEN_PRO
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SuggestionCard(
                    icon = Icons.Default.LocationOn,
                    title = "Google Maps",
                    desc = "Manzillar va yaqin joylarni aniq topish",
                    onClick = {
                        onPromptSuggestion(
                            "Toshkent markazidagi eng mashhur kafe va restoranlarni xaritadan ko'rsat",
                            EishlarModel.HYDROGEN_PRO
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                SuggestionCard(
                    icon = Icons.Default.Brush,
                    title = "Rasm Yaratish",
                    desc = "Nano Banana 2.1 bilan sifatli surat chizish",
                    onClick = {
                        onPromptSuggestion(
                            "Futuristic Samarkand Registan under glowing aurora lights, ultra detailed digital art",
                            EishlarModel.NANO_BANANA_STUDIO
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SuggestionCard(
                    icon = Icons.Default.VideoFile,
                    title = "Video Tahlil",
                    desc = "Video mazmunini Gemini 4 Neon orqali tahlil",
                    onClick = onOpenVideoPicker,
                    modifier = Modifier.weight(1f)
                )
                SuggestionCard(
                    icon = Icons.Default.Code,
                    title = "GitHub & Kod",
                    desc = "GitHub repo tahlili va yuklab olinadigan kod",
                    onClick = {
                        onPromptSuggestion(
                            "Jetpack Compose da chiroyli animatsiyali tugma kodi yozib ber",
                            EishlarModel.ARGON_CODER
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = modifier.heightIn(min = 104.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    onPreviewMedia: (AttachmentType, Uri, String) -> Unit,
    onDownloadSuccess: (String) -> Unit
) {
    val isUser = MessageRole.fromString(message.role) == MessageRole.USER
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                val model = EishlarModel.fromId(message.modelId)
                Text(
                    text = "${model.displayName} • ${model.modelName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 18.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Collapsible AI Thinking Card
                if (!isUser && !message.thinkingText.isNullOrBlank()) {
                    ThinkingAccordion(thinkingText = message.thinkingText)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Attachment preview (Image, Generated Image, Video, Audio, File)
                val attachType = AttachmentType.fromStringOrNull(message.attachmentType)
                val attachUri = message.attachmentUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                if (attachType != null && attachUri != null) {
                    AttachmentMessageCard(
                        type = attachType,
                        uri = attachUri,
                        fileName = message.attachmentName ?: "Fayl",
                        onPreview = { onPreviewMedia(attachType, attachUri, message.attachmentName ?: "Fayl") },
                        onDownload = {
                            if (attachType == AttachmentType.IMAGE || attachType == AttachmentType.GENERATED_IMAGE) {
                                MediaFileHelper.saveImageToGallery(context, attachUri, message.attachmentName ?: "eishlar_image.png")
                                    .onSuccess { onDownloadSuccess("Saqlandi: $it") }
                                    .onFailure { onDownloadSuccess("Saqlashda xatolik: ${it.localizedMessage}") }
                            } else {
                                MediaFileHelper.openFileExternal(context, attachUri, "*/*")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Markdown Text + Interactive Code Blocks
                val segments = remember(message.text) { MediaFileHelper.parseMarkdownSegments(message.text) }
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        segments.forEach { segment ->
                            when (segment) {
                                is ParsedMarkdownSegment.TextSegment -> {
                                    Text(
                                        text = segment.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                is ParsedMarkdownSegment.CodeSegment -> {
                                    CodeCanvasCard(
                                        codeBlock = segment.block,
                                        onCopy = {
                                            clipboard.setText(AnnotatedString(segment.block.code))
                                            onDownloadSuccess("Kod nusxalandi")
                                        },
                                        onDownload = {
                                            val ext = MediaFileHelper.extensionForLanguage(segment.block.language)
                                            val name = "eishlar_code_${System.currentTimeMillis()}.$ext"
                                            MediaFileHelper.saveTextOrCodeToDownloads(context, name, segment.block.code)
                                                .onSuccess { onDownloadSuccess("Yuklab olindi: $it") }
                                                .onFailure { onDownloadSuccess("Xatolik: ${it.localizedMessage}") }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Grounded Google Search & Maps Sources
                if (!isUser && (message.groundingUrls.isNotEmpty() || message.mapsPlaceNames.isNotEmpty())) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Manbalar (Google Search & Maps):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        message.groundingUrls.forEach { packed ->
                            val parts = packed.split("|", limit = 2)
                            val label = parts.firstOrNull().orEmpty().ifBlank { "Manba" }
                            val url = parts.getOrNull(1) ?: label
                            AssistChip(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                },
                                label = { Text(label.take(28), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(Icons.Default.Public, null, Modifier.size(15.dp)) }
                            )
                        }
                        message.mapsPlaceNames.forEach { packed ->
                            val parts = packed.split("|", limit = 2)
                            val label = parts.firstOrNull().orEmpty().ifBlank { "Xarita" }
                            val url = parts.getOrNull(1) ?: "https://maps.google.com/?q=${Uri.encode(label)}"
                            AssistChip(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                },
                                label = { Text(label.take(28), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.secondary) }
                            )
                        }
                    }
                }

                // Quick Copy & Save as Markdown for Model replies
                if (!isUser && message.text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(message.text))
                                onDownloadSuccess("Javob nusxalandi")
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Javobni nusxalash",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                val fileName = "eishlar_javob_${System.currentTimeMillis()}.md"
                                MediaFileHelper.saveTextOrCodeToDownloads(context, fileName, message.text)
                                    .onSuccess { onDownloadSuccess("Fayl saqlandi: $it") }
                                    .onFailure { onDownloadSuccess("Xatolik: ${it.localizedMessage}") }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Javobni fayl sifatida yuklab olish",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingAccordion(
    thinkingText: String,
    initiallyExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.small,
        color = AmberThinking.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, AmberThinking.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = AmberThinking,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI Thinking jarayoni",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberThinking
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Yopish" else "Ochish",
                    tint = AmberThinking,
                    modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = thinkingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun CodeCanvasCard(
    codeBlock: CodeBlockItem,
    onCopy: () -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color(0xFF06090F),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF101827))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = codeBlock.language.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Nusxa",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onCopy)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Yuklash",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onDownload)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = codeBlock.code,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

@Composable
private fun AttachmentMessageCard(
    type: AttachmentType,
    uri: Uri,
    fileName: String,
    onPreview: () -> Unit,
    onDownload: () -> Unit
) {
    if (type == AttachmentType.IMAGE || type == AttachmentType.GENERATED_IMAGE) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onPreview)
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(210.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onPreview,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ko'rish", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onDownload,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Download, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Yuklab olish", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    } else {
        Surface(
            onClick = onPreview,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (type) {
                    AttachmentType.VIDEO -> Icons.Default.VideoFile
                    AttachmentType.AUDIO -> Icons.Default.GraphicEq
                    else -> Icons.Default.Description
                }
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(fileName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${type.name} • Bosib ko'ring", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Ochish")
                }
            }
        }
    }
}

@Composable
fun MediaPreviewDialog(
    type: AttachmentType,
    uri: Uri,
    fileName: String,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Yopish")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center
                ) {
                    if (type == AttachmentType.IMAGE || type == AttachmentType.GENERATED_IMAGE) {
                        AsyncImage(
                            model = uri,
                            contentDescription = fileName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = when (type) {
                                    AttachmentType.VIDEO -> Icons.Default.VideoFile
                                    AttachmentType.AUDIO -> Icons.Default.GraphicEq
                                    else -> Icons.Default.Description
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(fileName, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                val mime = when (type) {
                                    AttachmentType.VIDEO -> "video/*"
                                    AttachmentType.AUDIO -> "audio/*"
                                    else -> "*/*"
                                }
                                MediaFileHelper.openFileExternal(context, uri, mime)
                            }) {
                                Icon(Icons.Default.OpenInNew, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Tashqi ilovada ochish")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (type == AttachmentType.IMAGE || type == AttachmentType.GENERATED_IMAGE) {
                                MediaFileHelper.saveImageToGallery(context, uri, fileName)
                                    .onSuccess { onToast("Saqlandi: $it") }
                                    .onFailure { onToast("Xatolik: ${it.localizedMessage}") }
                            } else {
                                MediaFileHelper.openFileExternal(context, uri, "*/*")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Yuklab olish")
                    }
                    OutlinedButton(
                        onClick = {
                            val mime = when (type) {
                                AttachmentType.IMAGE, AttachmentType.GENERATED_IMAGE -> "image/*"
                                AttachmentType.VIDEO -> "video/*"
                                AttachmentType.AUDIO -> "audio/*"
                                else -> "*/*"
                            }
                            MediaFileHelper.shareFileUri(context, uri, mime)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ulashish")
                    }
                }
            }
        }
    }
}

fun iconForModel(model: EishlarModel): ImageVector = when (model) {
    EishlarModel.HYDROGEN_PRO -> Icons.Default.Public
    EishlarModel.HYDROGEN_THINKING -> Icons.Default.Psychology
    EishlarModel.NEON_VISION -> Icons.Default.Translate
    EishlarModel.AUDIO_TRANSCRIBE -> Icons.Default.GraphicEq
    EishlarModel.NANO_BANANA_STUDIO -> Icons.Default.Brush
    EishlarModel.ARGON_CODER -> Icons.Default.Code
    EishlarModel.FLASH_FAST -> Icons.Default.AutoAwesome
    EishlarModel.OFFLINE_LOCAL -> Icons.Default.CloudOff
}
