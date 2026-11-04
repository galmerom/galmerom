package com.galmerom.outfittracker.domain.logic

object AiErrorText {
    fun reason(error: Throwable): String {
        val raw = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
        val lower = raw.lowercase()
        return when {
            lower.contains("prepay") ||
                lower.contains("credits are depleted") ||
                lower.contains("quota") ||
                lower.contains("resource_exhausted") ->
                "נגמרו קרדיטי Gemini בפרויקט. צריך להוסיף יתרה ב-AI Studio"
            lower.contains("app check") || lower.contains("appcheck") ->
                "App Check דוחה את הבקשה. רשמי את אסימון הבדיקה ב-Firebase App Check"
            lower.contains("genai config") ||
                lower.contains("service_disabled") ||
                lower.contains("has not been used") ||
                lower.contains("api_key_service_blocked") ->
                "Gemini Developer API לא מופעל בפרויקט Firebase"
            lower.contains("api key not valid") || lower.contains("api_key_invalid") ->
                "מפתח ה-API לא תקין"
            lower.contains("model") && (lower.contains("not found") || lower.contains("unsupported")) ->
                "המודל לא זמין"
            lower.contains("empty model") ->
                "המודל החזיר תשובה ריקה"
            raw.isBlank() -> "שגיאה לא ידועה"
            else -> raw.replace('\n', ' ').take(120)
        }
    }
}
