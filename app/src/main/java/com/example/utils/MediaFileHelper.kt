package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class CodeBlockItem(
    val language: String,
    val code: String
)

sealed interface ParsedMarkdownSegment {
    data class TextSegment(val text: String) : ParsedMarkdownSegment
    data class CodeSegment(val block: CodeBlockItem) : ParsedMarkdownSegment
}

object MediaFileHelper {

    private const val MAX_INLINE_BYTES = 18 * 1024 * 1024L

    fun getFileName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") {
            return File(uri.path.orEmpty()).name.ifBlank { "file" }
        }
        var name = "biriktirilgan_fayl"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(index) ?: name
            }
        }
        return name
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            return File(uri.path.orEmpty()).length()
        }
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) {
                size = cursor.getLong(index)
            }
        }
        return size
    }

    fun readBytesWithinLimit(context: Context, uri: Uri): ByteArray {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Faylni o'qib bo'lmadi.")
        if (raw.size > MAX_INLINE_BYTES) {
            throw IllegalArgumentException("Fayl hajmi 18 MB dan oshmasligi kerak.")
        }
        return raw
    }

    fun decodeScaledBitmap(context: Context, uri: Uri, maxDim: Int = 1536): Bitmap? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
    }

    fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 88): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    fun saveGeneratedBitmapLocally(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.filesDir, "generated_images").apply { mkdirs() }
        val file = File(dir, "eishlar_img_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return Uri.fromFile(file)
    }

    fun saveImageToGallery(context: Context, sourceUri: Uri, fileName: String): Result<String> {
        return runCatching {
            val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: error("Rasm topilmadi")
            val cleanName = if (fileName.endsWith(".png", true) || fileName.endsWith(".jpg", true)) {
                fileName
            } else {
                "$fileName.png"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/E-Ishlar-Max")
                }
                val targetUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Galereyaga yozish imkoni bo'lmadi")
                context.contentResolver.openOutputStream(targetUri)?.use { it.write(bytes) }
                "Pictures/E-Ishlar-Max/$cleanName"
            } else {
                val outFile = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), cleanName)
                FileOutputStream(outFile).use { it.write(bytes) }
                outFile.absolutePath
            }
        }
    }

    fun saveTextOrCodeToDownloads(context: Context, fileName: String, content: String): Result<String> {
        return runCatching {
            val bytes = content.toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/E-Ishlar-Max")
                }
                val targetUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Downloads papkasiga saqlab bo'lmadi")
                context.contentResolver.openOutputStream(targetUri)?.use { it.write(bytes) }
                "Downloads/E-Ishlar-Max/$fileName"
            } else {
                val outFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                FileOutputStream(outFile).use { it.write(bytes) }
                outFile.absolutePath
            }
        }
    }

    fun shareFileUri(context: Context, uri: Uri, mimeType: String) {
        val shareableUri = if (uri.scheme == "file") {
            val file = File(uri.path.orEmpty())
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            uri
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareableUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Ulashish"))
    }

    fun openFileExternal(context: Context, uri: Uri, mimeType: String) {
        val openableUri = if (uri.scheme == "file") {
            val file = File(uri.path.orEmpty())
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            uri
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(openableUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Ochish"))
        }
    }

    fun parseMarkdownSegments(markdown: String): List<ParsedMarkdownSegment> {
        val segments = mutableListOf<ParsedMarkdownSegment>()
        val codeFenceRegex = Regex("""```([a-zA-Z0-9_+-]*)\n([\s\S]*?)```""")
        var lastIndex = 0
        for (match in codeFenceRegex.findAll(markdown)) {
            if (match.range.first > lastIndex) {
                val textPart = markdown.substring(lastIndex, match.range.first).trim()
                if (textPart.isNotEmpty()) {
                    segments.add(ParsedMarkdownSegment.TextSegment(textPart))
                }
            }
            val lang = match.groupValues[1].ifBlank { "text" }
            val code = match.groupValues[2].trimEnd()
            segments.add(ParsedMarkdownSegment.CodeSegment(CodeBlockItem(lang, code)))
            lastIndex = match.range.last + 1
        }
        if (lastIndex < markdown.length) {
            val remaining = markdown.substring(lastIndex).trim()
            if (remaining.isNotEmpty()) {
                segments.add(ParsedMarkdownSegment.TextSegment(remaining))
            }
        }
        if (segments.isEmpty() && markdown.isNotBlank()) {
            segments.add(ParsedMarkdownSegment.TextSegment(markdown))
        }
        return segments
    }

    fun extensionForLanguage(language: String): String = when (language.lowercase()) {
        "kotlin", "kt" -> "kt"
        "python", "py" -> "py"
        "javascript", "js" -> "js"
        "typescript", "ts" -> "ts"
        "html" -> "html"
        "css" -> "css"
        "json" -> "json"
        "sql" -> "sql"
        "bash", "sh" -> "sh"
        "java" -> "java"
        "cpp", "c++" -> "cpp"
        "markdown", "md" -> "md"
        else -> "txt"
    }
}

class VoiceAudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): File {
        stopSilently()
        val dir = File(context.cacheDir, "voice_recordings").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96000)
            setAudioSamplingRate(44100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        return file
    }

    fun stopRecording(): File? {
        val file = outputFile
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            file?.takeIf { it.exists() && it.length() > 0L }
        } catch (_: Exception) {
            stopSilently()
            null
        }
    }

    fun stopSilently() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        } finally {
            recorder = null
        }
    }
}
