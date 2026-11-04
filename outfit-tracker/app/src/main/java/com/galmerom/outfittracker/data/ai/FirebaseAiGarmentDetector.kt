package com.galmerom.outfittracker.data.ai

import android.util.Log
import com.galmerom.outfittracker.domain.GarmentDetector
import com.galmerom.outfittracker.domain.logic.AiErrorText
import com.galmerom.outfittracker.domain.logic.GarmentResponseParser
import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.model.DefaultCategories
import com.galmerom.outfittracker.domain.model.DetectedGarment
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlin.coroutines.cancellation.CancellationException

class FirebaseAiGarmentDetector(
    private val debugSecret: () -> String? = { null },
) : GarmentDetector {
    override suspend fun detect(
        jpeg: ByteArray,
        categories: List<ClothingCategory>,
    ): Result<List<DetectedGarment>> {
        val known = categories.ifEmpty { DefaultCategories.seeds }
        val allowed = known.map { it.id }.toSet()
        val names = known.associate { it.name to it.id }
        return try {
            val schema = Schema.obj(
                mapOf(
                    "garments" to Schema.array(
                        Schema.obj(
                            mapOf(
                                "category" to Schema.enumeration(known.map { it.id }),
                                "description" to Schema.string(),
                            ),
                        ),
                    ),
                ),
            )
            val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(
                    modelName = MODEL,
                    generationConfig = generationConfig {
                        responseMimeType = "application/json"
                        responseSchema = schema
                    },
                )
            val response = model.generateContent(
                content {
                    inlineData(jpeg, "image/jpeg")
                    text(prompt(known))
                },
            )
            val text = response.text
            if (text.isNullOrBlank()) {
                return Result.failure(IllegalStateException("empty model response"))
            }
            val garments = GarmentResponseParser.parse(text, allowed, names)
            if (garments.isEmpty()) {
                Log.w(TAG, "garment detection parsed nothing: ${text.take(400)}")
            } else {
                Log.i(TAG, "garment detection found ${garments.size}")
            }
            Result.success(garments)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "garment detection failed", error)
            Result.failure(IllegalStateException(shownReason(error), error))
        }
    }

    private fun shownReason(error: Throwable): String {
        val reason = AiErrorText.reason(error)
        val token = runCatching { debugSecret() }.getOrNull()
        return if (!token.isNullOrBlank() && reason.contains("App Check")) {
            "$reason. אסימון: $token"
        } else {
            reason
        }
    }

    private fun prompt(categories: List<ClothingCategory>): String {
        val catalog = categories.joinToString("\n") { "- ${it.id}: ${it.name}" }
        return """
            הסתכלי על האדם בתמונה ורשמי את פרטי הלבוש שנראים עליו.
            החזירי JSON בלבד לפי הסכמה: מערך garments, ובכל פריט category ו-description.
            category חייב להיות אחד מהמזהים הבאים:
            $catalog
            description הוא תיאור קצר בעברית של הפריט, כולל צבע או דוגמה אם רואים.
            דלגי על פנים, שיער ורקע. כללי נעליים ושכבה עליונה אם הם נראים.
        """.trimIndent()
    }

    private companion object {
        const val MODEL = "gemini-3.8-flash"
        const val TAG = "OutfitTracker"
    }
}
