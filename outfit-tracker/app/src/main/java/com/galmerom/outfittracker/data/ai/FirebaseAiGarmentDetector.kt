package com.galmerom.outfittracker.data.ai

import android.util.Log
import com.galmerom.outfittracker.data.photo.ImageCompressor
import com.galmerom.outfittracker.domain.GarmentDetector
import com.galmerom.outfittracker.domain.WardrobeDetection
import com.galmerom.outfittracker.domain.logic.GarmentResponseParser
import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.model.ClothingItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class FirebaseAiGarmentDetector : GarmentDetector {
    override suspend fun detect(
        jpeg: ByteArray,
        items: List<ClothingItem>,
        categories: List<ClothingCategory>,
    ): Result<WardrobeDetection> {
        val names = categories.associate { it.id to it.name }
        val allowed = items.map { it.id }.toSet()
        val allowedCategories = categories.map { it.id }.toSet()
        return try {
            val thumbs = loadThumbs(items)
            val schema = Schema.obj(
                mapOf(
                    "itemIds" to Schema.array(Schema.string()),
                    "unmatched" to Schema.array(
                        Schema.obj(mapOf("category" to Schema.string())),
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
                    text("תמונת המפגש, הפריטים שעליה צריך לזהות:")
                    inlineData(jpeg, "image/jpeg")
                    text(prompt(items, categories, names))
                    items.forEach { item ->
                        text("פריט id=${item.id}")
                        thumbs[item.id]?.let { inlineData(it, "image/jpeg") }
                    }
                },
            )
            val text = response.text
            if (text.isNullOrBlank()) {
                return Result.failure(IllegalStateException("empty model response"))
            }
            val detection = WardrobeDetection(
                matchedIds = GarmentResponseParser.parseIds(text, allowed),
                unmatchedCategories = GarmentResponseParser.parseUnmatched(text, allowedCategories),
            )
            Log.i(TAG, "garment detection matched ${detection.matchedIds.size}, unmatched ${detection.unmatchedCategories.size}")
            Result.success(detection)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "garment detection failed", error)
            Result.failure(error)
        }
    }

    private suspend fun loadThumbs(items: List<ClothingItem>): Map<String, ByteArray> = coroutineScope {
        val gate = Semaphore(4)
        items.map { item ->
            async {
                val url = item.photoUrl ?: return@async null
                val bytes = gate.withPermit { thumb(url) } ?: return@async null
                item.id to bytes
            }
        }.awaitAll().filterNotNull().toMap()
    }

    private suspend fun thumb(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = URL(url).openStream().use { it.readBytes() }
            val jpeg = ImageCompressor.jpeg(bytes, THUMB_EDGE)
            jpeg.takeIf { it.size >= 3 && it[0] == 0xFF.toByte() && it[1] == 0xD8.toByte() }
        }.getOrNull()
    }

    private fun prompt(
        items: List<ClothingItem>,
        categories: List<ClothingCategory>,
        categoryNames: Map<String, String>,
    ): String {
        val catalog = items.joinToString("\n") { item ->
            val category = categoryNames[item.categoryId] ?: item.categoryId
            "- id=${item.id} | ${item.description} | $category"
        }.ifBlank { "(הארון ריק)" }
        val categoryCatalog = categories.joinToString("\n") { "- ${it.id}: ${it.name}" }.ifBlank { "(אין קטגוריות)" }
        return """
            החזירי JSON בלבד בלי שמות ובלי תיאור חופשי.
            itemIds: רק מזהים מהארון שנראים על האדם. אם שני פריטים דומים ושניהם אפשריים, החזירי את שניהם.
            unmatched: פריט לבוש שנראה בתמונה ואין לו התאמה בארון. לכל פריט כזה אובייקט עם category בלבד, מזהה מהקטגוריות, או מחרוזת ריקה אם הקטגוריה לא ברורה.
            אל תמציאי פריט ארון חדש בתוך itemIds.
            קטגוריות:
            $categoryCatalog
            פריטים קיימים בארון:
            $catalog
        """.trimIndent()
    }

    private companion object {
        const val MODEL = "gemini-3.8-flash"
        const val TAG = "OutfitTracker"
        const val THUMB_EDGE = 256
    }
}
