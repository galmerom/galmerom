package com.galmerom.outfittracker.data.firestore

import com.galmerom.outfittracker.data.awaitCommitted
import com.galmerom.outfittracker.data.photo.FirebasePhotoStorage
import com.galmerom.outfittracker.domain.logic.Dates
import com.galmerom.outfittracker.domain.logic.errorKind
import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.model.ClothingItem
import com.galmerom.outfittracker.domain.model.Entity
import com.galmerom.outfittracker.domain.model.EntityType
import com.galmerom.outfittracker.domain.model.ErrorKind
import com.galmerom.outfittracker.domain.model.Meeting
import com.galmerom.outfittracker.domain.model.MeetingDraft
import com.galmerom.outfittracker.domain.model.SaveOutcome
import com.galmerom.outfittracker.domain.repository.ClothingRepository
import com.galmerom.outfittracker.domain.repository.EntityRepository
import com.galmerom.outfittracker.domain.repository.MeetingRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private fun FirebaseFirestore.userCollection(uid: String, name: String) =
    collection("users").document(uid).collection(name)

private fun <T> observeUserCollection(
    firestore: FirebaseFirestore,
    auth: FirebaseAuth,
    name: String,
    map: (DocumentSnapshot) -> T?,
): Flow<List<T>> = callbackFlow {
    val uid = auth.currentUser?.uid
    if (uid == null) {
        trySend(emptyList())
        awaitClose { }
        return@callbackFlow
    }
    val registration = firestore.userCollection(uid, name).addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        trySend(snapshot?.documents?.mapNotNull(map).orEmpty())
    }
    awaitClose { registration.remove() }
}

class FirestoreClothingRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val photos: FirebasePhotoStorage,
) : ClothingRepository {
    override fun observeAll(): Flow<List<ClothingItem>> =
        observeUserCollection(firestore, auth, CLOTHING, DocumentSnapshot::toClothingItem)

    override suspend fun upsert(
        item: ClothingItem,
        photoJpeg: ByteArray?,
    ): Result<Pair<ClothingItem, SaveOutcome>> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("signed out"))
        val id = item.id.ifBlank { firestore.collection("users").document().id }
        var photoUrl = item.photoUrl
        var outcome = SaveOutcome.SAVED
        if (photoJpeg != null) {
            val uploaded = runCatching { photos.upload("users/$uid/clothing/$id.jpg", photoJpeg) }
            if (uploaded.isSuccess) {
                photoUrl = uploaded.getOrThrow()
            } else {
                val error = uploaded.exceptionOrNull()
                if (error?.errorKind() == ErrorKind.PERMISSION) return Result.failure(error)
                outcome = SaveOutcome.SAVED_WITHOUT_PHOTO
            }
        }
        val saved = item.copy(id = id, description = item.description.trim(), photoUrl = photoUrl)
        return firestore.userCollection(uid, CLOTHING)
            .document(id)
            .set(saved.toMap())
            .awaitCommitted()
            .map { saved to outcome }
    }

    override suspend fun setHidden(id: String, hidden: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("signed out"))
        return firestore.userCollection(uid, CLOTHING)
            .document(id)
            .update("hidden", hidden)
            .awaitCommitted()
    }

    private companion object {
        const val CLOTHING = "clothingItems"
    }
}

class FirestoreEntityRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : EntityRepository {
    override fun observeAll(): Flow<List<Entity>> =
        observeUserCollection(firestore, auth, ENTITIES, DocumentSnapshot::toEntity)

    override suspend fun create(name: String, type: EntityType): Result<Entity> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("signed out"))
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("empty name"))
        val id = firestore.collection("users").document().id
        val entity = Entity(id = id, name = trimmed, type = type)
        return firestore.userCollection(uid, ENTITIES)
            .document(id)
            .set(mapOf("name" to entity.name, "type" to entity.type.wire))
            .awaitCommitted()
            .map { entity }
    }

    private companion object {
        const val ENTITIES = "entities"
    }
}

class FirestoreMeetingRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val photos: FirebasePhotoStorage,
) : MeetingRepository {
    override fun observeAll(): Flow<List<Meeting>> =
        observeUserCollection(firestore, auth, MEETINGS, DocumentSnapshot::toMeeting)

    override suspend fun log(draft: MeetingDraft, photoJpeg: ByteArray?): Result<SaveOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("signed out"))
        val id = firestore.collection("users").document().id
        var photoUrl: String? = null
        var outcome = SaveOutcome.SAVED
        if (photoJpeg != null) {
            val uploaded = runCatching { photos.upload("users/$uid/meetings/$id.jpg", photoJpeg) }
            if (uploaded.isSuccess) {
                photoUrl = uploaded.getOrThrow()
            } else {
                val error = uploaded.exceptionOrNull()
                if (error?.errorKind() == ErrorKind.PERMISSION) return Result.failure(error)
                outcome = SaveOutcome.SAVED_WITHOUT_PHOTO
            }
        }
        val fields = mapOf(
            "date" to Dates.formatIso(draft.date),
            "entityId" to draft.entityId,
            "entityName" to draft.entityName,
            "clothingItemIds" to draft.clothingItemIds,
            "photoUrl" to photoUrl,
            "note" to draft.note.trim(),
        )
        return firestore.userCollection(uid, MEETINGS)
            .document(id)
            .set(fields)
            .awaitCommitted()
            .map { outcome }
    }

    private companion object {
        const val MEETINGS = "meetings"
    }
}

private fun ClothingItem.toMap(): Map<String, Any?> = mapOf(
    "description" to description,
    "category" to category.wire,
    "photoUrl" to photoUrl,
    "hidden" to hidden,
)

private fun DocumentSnapshot.toClothingItem(): ClothingItem? {
    val category = ClothingCategory.fromWire(getString("category")) ?: return null
    return ClothingItem(
        id = id,
        description = getString("description").orEmpty(),
        category = category,
        photoUrl = getString("photoUrl")?.takeIf { it.isNotBlank() },
        hidden = getBoolean("hidden") ?: false,
    )
}

private fun DocumentSnapshot.toEntity(): Entity? {
    val type = EntityType.fromWire(getString("type")) ?: return null
    val name = getString("name")?.trim().orEmpty()
    if (name.isEmpty()) return null
    return Entity(id = id, name = name, type = type)
}

private fun DocumentSnapshot.toMeeting(): Meeting? {
    val date = Dates.parseIso(getString("date")) ?: return null
    val entityId = getString("entityId").orEmpty()
    if (entityId.isEmpty()) return null
    val ids = (get("clothingItemIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    return Meeting(
        id = id,
        date = date,
        entityId = entityId,
        entityName = getString("entityName").orEmpty(),
        clothingItemIds = ids,
        photoUrl = getString("photoUrl")?.takeIf { it.isNotBlank() },
        note = getString("note").orEmpty(),
    )
}
