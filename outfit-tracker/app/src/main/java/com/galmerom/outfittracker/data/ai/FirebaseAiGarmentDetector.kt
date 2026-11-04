package com.galmerom.outfittracker.data.ai

import com.galmerom.outfittracker.domain.GarmentDetector
import com.galmerom.outfittracker.domain.logic.GarmentResponseParser
import com.galmerom.outfittracker.domain.model.DetectedGarment
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlin.coroutines.cancellation.CancellationException

class FirebaseAiGarmentDetector : GarmentDetector {
    override suspend fun detect(jpeg: ByteArray): Result<List<DetectedGarment>> {
        return try {
            val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(MODEL)
            val response = model.generateContent(
                content {
                    inlineData(jpeg, "image/jpeg")
                    text(PROMPT)
                },
            )
            Result.success(GarmentResponseParser.parse(response.text.orEmpty()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private companion object {
        const val MODEL = "gemini-3.8-flash"
        val PROMPT = """
            Look at the person in the photo and list the garments they are wearing.
            Reply with ONLY a JSON array. No markdown.
            Each element is an object with keys "category" and "description".
            category is exactly one of: shirt, pants, skirt, dress, jacket, shoes, accessory, other.
            description is a short Hebrew phrase naming the garment and a visible color or pattern.
            Skip face, hair, and background. Include shoes and outer layers when they are visible.
        """.trimIndent()
    }
}
