package com.galmerom.outfittracker.data.photo

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await

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
}
