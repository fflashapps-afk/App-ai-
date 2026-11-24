package com.example.data.ai

import com.example.model.ChatMessage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

data class OfflineAiResult(
    val thinkingTrace: String,
    val responseText: String
)

object OfflineAiEngine {

    private val dictionaryUzEn = mapOf(
        "salom" to "Hello / Hi",
        "rahmat" to "Thank you",
        "xayr" to "Goodbye",
        "kitob" to "Book",
        "suv" to "Water",
        "uy" to "House / Home",
        "maktab" to "School",
        "dastur" to "Program / Software",
        "kod" to "Code",
        "ish" to "Work / Job",
        "vaqt" to "Time",
        "yordam" to "Help",
        "telefon" to "Phone",
        "kompyuter" to "Computer",
        "do'st" to "Friend",
        "shahri" to "City",
        "bugun" to "Today",
        "ertaga" to "Tomorrow",
        "kecha" to "Yesterday",
        "yaxshi" to "Good / Well"
    )

    private val dictionaryEnUz = dictionaryUzEn.entries.associate { (k, v) ->
        v.lowercase().split(" / ").first() to k.replaceFirstChar { it.uppercase() }
    } + mapOf(
        "hello" to "Salom",
        "hi" to "Salom",
        "thanks" to "Rahmat",
        "thank you" to "Katta rahmat",
        "good morning" to "Xayrli tong",
        "how are you" to "Qalaysiz?",
        "artificial intelligence" to "Sun'iy intellekt",
        "code" to "Kod / Dastur kodi",
        "translate" to "Tarjima qilish"
    )

    suspend fun respond(
        prompt: String,
        history: List<ChatMessage>
    ): OfflineAiResult {
        delay(280)
        val trimmed = prompt.trim()
        val lower = trimmed.lowercase(Locale.getDefault())

        // 1. Identity / Owner
        if (lower.contains("kim yaratgan") || lower.contains("owner") || lower.contains("egasi") ||
            lower.contains("isming") || lower.contains("who made") || lower.contains("e-ishlar") ||
            lower.contains("eishlar")
        ) {
            return OfflineAiResult(
                thinkingTrace = "Offline bilimlar bazasi tekshirilmoqda -> E-Ishlar-Max (EISHLARAI) haqidagi ma'lumot aniqlandi.",
                responseText = """
                    Men **E-Ishlar-Max AI** (**EISHLARAI**) — ko'p funksiyali intellektual yordamchiman.
                    
                    - **Egasi (Owner):** Abdullojon
                    - **Joriy rejim:** Offline Lokal AI (internetsiz ishlayapti)
                    - **Imkoniyatlar:** Matematika, birliklar konvertatsiyasi, kod shablonlari (Kotlin/Python/HTML), va tezkor lug'at tarjimasi.
                    
                    Internetga ulanganingizda **E-Ishlar-Max Pro** (`gemini-4-hydrogen`), **Vision** (`gemini-4-neon`), **Audio** (`gemini-3.5-transcribe`) va **Studio** (`gemini-nano-banana-2.1`) modellariga o'tishingiz mumkin!
                """.trimIndent()
            )
        }

        // 2. Date / Time
        if (lower.contains("soat") || lower.contains("vaqt") || lower.contains("sana") ||
            lower.contains("bugun") || lower.contains("time") || lower.contains("date")
        ) {
            val now = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            return OfflineAiResult(
                thinkingTrace = "Qurilma tizim soati o'qildi (Offline rejim).",
                responseText = "Qurilma vaqti bo'yicha hozirgi sana va vaqt: **$now**."
            )
        }

        // 3. Math Expression Evaluator
        val mathCandidate = extractMathExpression(trimmed)
        if (mathCandidate != null) {
            val evaluated = runCatching { evalMath(mathCandidate) }.getOrNull()
            if (evaluated != null && !evaluated.isNaN() && !evaluated.isInfinite()) {
                val formatted = if (evaluated % 1.0 == 0.0) evaluated.toLong().toString()
                else String.format(Locale.US, "%.6f", evaluated).trimEnd('0').trimEnd('.')
                return OfflineAiResult(
                    thinkingTrace = "Matematik ifoda aniqlandi: `$mathCandidate` -> rekursiv arifmetik parser ishga tushirildi.",
                    responseText = "Natija:\n\n`$mathCandidate` = **$formatted**"
                )
            }
        }

        // 4. Unit conversion
        val unitResult = tryUnitConversion(lower)
        if (unitResult != null) {
            return OfflineAiResult(
                thinkingTrace = "Birlik konvertatsiyasi so'rovi aniqlandi -> lokal formula qo'llanildi.",
                responseText = unitResult
            )
        }

        // 5. Offline Dictionary / Translation
        if (lower.contains("tarjima") || lower.contains("translate") || lower.contains("inglizcha") || lower.contains("o'zbekcha")) {
            val found = dictionaryUzEn.entries.firstOrNull { lower.contains(it.key) }
                ?: dictionaryEnUz.entries.firstOrNull { lower.contains(it.key) }
            if (found != null) {
                return OfflineAiResult(
                    thinkingTrace = "Offline lug'at bazasidan '${found.key}' so'zi topildi.",
                    responseText = "**Offline Tarjima:**\n`${found.key}` → **${found.value}**\n\n*To'liq matn yoki rasmdan tarjima qilish uchun internetga ulanganda `E-Ishlar-Max Vision` modelidan foydalaning.*"
                )
            }
        }

        // 6. Code template generation offline
        if (lower.contains("kotlin") || lower.contains("compose") || lower.contains("python") ||
            lower.contains("html") || lower.contains("kod") || lower.contains("code")
        ) {
            return buildOfflineCodeResponse(lower)
        }

        // 7. Greeting
        if (lower.startsWith("salom") || lower.startsWith("assalom") || lower.startsWith("hello") ||
            lower.startsWith("hi") || lower.startsWith("privet")
        ) {
            return OfflineAiResult(
                thinkingTrace = "Salomlashish aniqlandi. Foydalanuvchiga offline imkoniyatlar ko'rsatilmoqda.",
                responseText = """
                    Assalomu alaykum! Men **E-Ishlar-Max Offline AI** man (Owner: Abdullojon).
                    
                    Hozir internetsiz rejimda quyidagilarni bajara olaman:
                    1. **Matematika:** masalan, `(25 * 4) + sqrt(144)` deb yozing
                    2. **Konvertatsiya:** masalan, `15 km necha mil` yoki `25 c necha f`
                    3. **Kod shablonlari:** `kotlin compose button`, `python script`, `html page`
                    4. **Tezkor lug'at:** `salom tarjima`, `hello tarjima`
                    5. **Sana va vaqt:** `hozir soat necha?`
                    
                    Qanday yordam bera olaman?
                """.trimIndent()
            )
        }

        // 8. Conversation context fallback
        val turnCount = history.size
        return OfflineAiResult(
            thinkingTrace = "Offline rejimda savol tahlil qilindi (tarixda $turnCount ta xabar). Jonli qidiruv mavjud emas.",
            responseText = """
                **Offline rejim javobi (E-Ishlar-Max):**
                
                Sizning so'rovingiz: *"$trimmed"*
                
                Hozir qurilma **Offline AI** rejimida ishlamoqda, shuning uchun Google Search, Google Maps, GitHub jonli qidiruv yoki bulutli Gemini modellari faol emas.
                
                **Offline rejimda sinab ko'rishingiz mumkin:**
                - Istalgan matematik hisob-kitob: `(128 / 4) + 15^2`
                - O'lchov birliklari: `100 km necha metr` yoki `36.6 c necha f`
                - Dasturlash kodi: `kotlin compose kod ber` yoki `python kod ber`
                
                Internet yoqilganda yuqoridagi model tugmasi orqali **E-Ishlar-Max Pro** modeliga o'tib eng so'nggi ma'lumotlarni olishingiz mumkin.
            """.trimIndent()
        )
    }

    private fun buildOfflineCodeResponse(lower: String): OfflineAiResult {
        return when {
            lower.contains("python") -> OfflineAiResult(
                thinkingTrace = "Offline kod generatori -> Python shabloni tanlandi.",
                responseText = """
                    Mana offline Python shabloni (uni fayl sifatida yuklab olishingiz mumkin):
                    
                    ```python
                    def eishlar_assistant(query: str) -> str:
                        return f"E-Ishlar-Max AI javobi: {query.strip().capitalize()}"

                    if __name__ == "__main__":
                        user_input = input("Savol kiriting: ")
                        print(eishlar_assistant(user_input))
                    ```
                """.trimIndent()
            )
            lower.contains("html") -> OfflineAiResult(
                thinkingTrace = "Offline kod generatori -> HTML5 shabloni tanlandi.",
                responseText = """
                    Mana offline HTML5 sahifa shabloni:
                    
                    ```html
                    <!DOCTYPE html>
                    <html lang="uz">
                    <head>
                      <meta charset="UTF-8" />
                      <title>E-Ishlar-Max</title>
                      <style>
                        body { font-family: sans-serif; background: #070b12; color: #f1f5f9; padding: 2rem; }
                        .card { background: #0e1522; border-radius: 16px; padding: 24px; border: 1px solid #283854; }
                      </style>
                    </head>
                    <body>
                      <div class="card">
                        <h1>E-Ishlar-Max AI</h1>
                        <p>Owner: Abdullojon</p>
                      </div>
                    </body>
                    </html>
                    ```
                """.trimIndent()
            )
            else -> OfflineAiResult(
                thinkingTrace = "Offline kod generatori -> Kotlin Jetpack Compose shabloni tanlandi.",
                responseText = """
                    Mana offline Kotlin / Jetpack Compose shabloni:
                    
                    ```kotlin
                    @Composable
                    fun EishlarGreetingCard(owner: String = "Abdullojon") {
                        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("E-Ishlar-Max AI", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(4.dp))
                                Text("Owner: ${'$'}owner", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    ```
                """.trimIndent()
            )
        }
    }

    private fun tryUnitConversion(lower: String): String? {
        val numberRegex = Regex("""(\d+(?:\.\d+)?)\s*(km|m|kg|g|gb|mb|c|f)\b""")
        val match = numberRegex.find(lower) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2]
        return when (unit) {
            "km" -> "$value km = **${value * 1000} metr** = **${String.format(Locale.US, "%.2f", value * 0.621371)} mil**"
            "m" -> "$value m = **${value / 1000.0} km** = **${value * 100} sm**"
            "kg" -> "$value kg = **${value * 1000} gramm** = **${String.format(Locale.US, "%.2f", value * 2.20462)} funt (lb)**"
            "gb" -> "$value GB = **${value * 1024} MB** = **${value * 1024 * 1024} KB**"
            "mb" -> "$value MB = **${String.format(Locale.US, "%.3f", value / 1024.0)} GB** = **${value * 1024} KB**"
            "c" -> "$value °C = **${String.format(Locale.US, "%.1f", (value * 9.0 / 5.0) + 32.0)} °F**"
            "f" -> "$value °F = **${String.format(Locale.US, "%.1f", (value - 32.0) * 5.0 / 9.0)} °C**"
            else -> null
        }
    }

    private fun extractMathExpression(input: String): String? {
        val cleaned = input.replace(" ", "")
        val hasOperator = cleaned.any { it in charArrayOf('+', '-', '*', '/', '^') } || cleaned.contains("sqrt(")
        if (!hasOperator) return null
        val mathMatch = Regex("""[0-9()+*/.^%\-]+|sqrt\([0-9.+*/^\-]+\)""").findAll(cleaned)
            .joinToString("") { it.value }
        return if (mathMatch.length >= 3 && mathMatch.any { it.isDigit() }) mathMatch else null
    }

    private fun evalMath(expr: String): Double {
        return object {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < expr.length) expr[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < expr.length) throw IllegalArgumentException("Unexpected: ${ch.toChar()}")
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    x = when {
                        eat('+'.code) -> x + parseTerm()
                        eat('-'.code) -> x - parseTerm()
                        else -> return x
                    }
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    x = when {
                        eat('*'.code) -> x * parseFactor()
                        eat('/'.code) -> x / parseFactor()
                        eat('%'.code) -> x % parseFactor()
                        else -> return x
                    }
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()
                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                    while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                    x = expr.substring(startPos, pos).toDouble()
                } else if (ch in 'a'.code..'z'.code) {
                    while (ch in 'a'.code..'z'.code) nextChar()
                    val func = expr.substring(startPos, pos)
                    x = parseFactor()
                    x = if (func == "sqrt") sqrt(x) else throw IllegalArgumentException("Unknown func: $func")
                } else {
                    throw IllegalArgumentException("Unexpected char: ${ch.toChar()}")
                }
                if (eat('^'.code)) x = x.pow(parseFactor())
                return x
            }
        }.parse()
    }
}
