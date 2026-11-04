package com.galmerom.outfittracker.data.photo

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebasePhotoStorage(
    private val storage: FirebaseStorage,
) {
    suspend fun upload(path: String, jpeg: ByteArray): String {
        val ref = storage.reference.child(path)
        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()
        ref.putBytes(jpeg, metadata).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        URL(url).openStream().use { it.readBytes() }
    }
}
