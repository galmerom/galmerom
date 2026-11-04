package com.galmerom.outfittracker.data.ai

import com.galmerom.outfittracker.domain.GarmentDetector
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

class FirebaseAiGarmentDetector : GarmentDetector {
    override suspend fun detect(
        jpeg: ByteArray,
        categories: List<ClothingCategory>,
    ): Result<List<DetectedGarment>> {
        val known = categories.ifEmpty { DefaultCategories.seeds }
        val allowed = known.map { it.id }.toSet()
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
            Result.success(GarmentResponseParser.parse(response.text.orEmpty(), allowed))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
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
    }
}
