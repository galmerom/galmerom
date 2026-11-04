package com.galmerom.outfittracker.data.firestore

import com.galmerom.outfittracker.data.awaitCommitted
import com.galmerom.outfittracker.data.photo.FirebasePhotoStorage
import com.galmerom.outfittracker.data.photo.ImageCompressor
import com.galmerom.outfittracker.domain.logic.CategoryOrder
import com.galmerom.outfittracker.domain.logic.Dates
import com.galmerom.outfittracker.domain.logic.ImageSizing
import com.galmerom.outfittracker.domain.logic.errorKind
import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.model.DefaultCategories
import com.galmerom.outfittracker.domain.model.ClothingItem
import com.galmerom.outfittracker.domain.model.Entity
import com.galmerom.outfittracker.domain.model.EntityType
import com.galmerom.outfittracker.domain.model.ErrorKind
import com.galmerom.outfittracker.domain.model.Meeting
import com.galmerom.outfittracker.domain.model.MeetingDraft
import com.galmerom.outfittracker.domain.model.SaveOutcome
import com.galmerom.outfittracker.domain.repository.CategoryRepository
import com.galmerom.outfittracker.domain.repository.ClothingRepository
import com.galmerom.outfittracker.domain.repository.EntityRepository
import com.galmerom.outfittracker.domain.repository.MeetingRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

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
    override fun observeAll(): Flow<List<ClothingItem>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = firestore.userCollection(uid, CLOTHING).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            snapshot?.documents?.forEach { document ->
                val legacy = document.getString("category")
                val categoryId = document.getString("categoryId")
                val migrated = DefaultCategories.fromLegacy(legacy)
                if (categoryId.isNullOrBlank() && migrated != null) {
                    document.reference.update("categoryId", migrated)
                }
            }
            trySend(snapshot?.documents?.mapNotNull { it.toClothingItem() }.orEmpty())
        }
        awaitClose { registration.remove() }
    }

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
    private val photos: FirebasePhotoStorage,
) : EntityRepository {
    override fun observeAll(): Flow<List<Entity>> =
        observeUserCollection(firestore, auth, ENTITIES, DocumentSnapshot::toEntity)

    override suspend fun create(
        name: String,
        type: EntityType,
        description: String,
        photoJpeg: ByteArray?,
    ): Result<Entity> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("signed out"))
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("empty name"))
        val id = firestore.collection("users").document().id
        val photoUrl = uploadAvatar(uid, id, photoJpeg).getOrElse { return Result.failure(it) }
        val entity = Entity(
            id = id,
            name = trimmed,
            type = type,
            description = description.trim(),
            photoUrl = photoUrl,
        )
        val fields = mutableMapOf<String, Any>(
            "name" to entity.name,
            "type" to entity.type.wire,
            "hidden" to false,
            "memberIds" to emptyList<String>(),
            "description" to entity.description,
        )
        entity.photoUrl?.let { fields["photoUrl"] = it }
        return firestore.userCollection(uid, ENTITIES)
            .document(id)
            .set(fields)
            .awaitCommitted()
            .map { entity }
    }

    override suspend fun update(
        id: String,
        name: String,
        type: EntityType,
        description: String,
        photoJpeg: ByteArray?,
        keepPhotoUrl: String?,
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("signed out"))
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("empty name"))
        val photoUrl = if (photoJpeg != null) {
            uploadAvatar(uid, id, photoJpeg).getOrElse { return Result.failure(it) }
        } else {
            keepPhotoUrl
        }
        val updates = mutableMapOf<String, Any>(
            "name" to trimmed,
            "type" to type.wire,
            "description" to description.trim(),
        )
        if (photoUrl != null) updates["photoUrl"] = photoUrl
        if (type != EntityType.FORUM) updates["memberIds"] = emptyList<String>()
        return firestore.userCollection(uid, ENTITIES).document(id).update(updates).awaitCommitted()
    }

    private suspend fun uploadAvatar(uid: String, id: String, photoJpeg: ByteArray?): Result<String?> {
        if (photoJpeg == null) return Result.success(null)
        val jpeg = ImageCompressor.jpeg(photoJpeg, ImageSizing.AVATAR_EDGE_PX)
        return runCatching { photos.upload("users/$uid/avatars/$id.jpg", jpeg) }
    }

    override suspend fun setHidden(id: String, hidden: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("signed out"))
        return firestore.userCollection(uid, ENTITIES).document(id).update("hidden", hidden).awaitCommitted()
    }

    override suspend fun setMembers(forumId: String, memberIds: List<String>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("signed out"))
        return firestore.userCollection(uid, ENTITIES)
            .document(forumId)
            .update("memberIds", memberIds.distinct())
            .awaitCommitted()
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
        return firestore.userCollection(uid, MEETINGS)
            .document(id)
            .set(draft.toFields(photoUrl, hidden = false))
            .awaitCommitted()
            .map { outcome }
    }

    override suspend fun update(
        id: String,
        draft: MeetingDraft,
        photoJpeg: ByteArray?,
        keepPhotoUrl: String?,
    ): Result<SaveOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("signed out"))
        var photoUrl = keepPhotoUrl
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
        return firestore.userCollection(uid, MEETINGS)
            .document(id)
            .set(draft.toFields(photoUrl, hidden = null), com.google.firebase.firestore.SetOptions.merge())
            .awaitCommitted()
            .map { outcome }
    }

    override suspend fun delete(id: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("signed out"))
        return firestore.userCollection(uid, MEETINGS).document(id).delete().awaitCommitted()
    }

    override suspend fun setHidden(id: String, hidden: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("signed out"))
        return firestore.userCollection(uid, MEETINGS).document(id).update("hidden", hidden).awaitCommitted()
    }

    private companion object {
        const val MEETINGS = "meetings"
    }
}

class FirestoreCategoryRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : CategoryRepository {
    override fun observeAll(): Flow<List<ClothingCategory>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val collection = firestore.userCollection(uid, CATEGORIES)
        val existing = runCatching { collection.limit(1).get().await() }.getOrNull()
        if (existing != null && existing.isEmpty) {
            val batch = firestore.batch()
            DefaultCategories.seeds.forEach { seed ->
                batch.set(
                    collection.document(seed.id),
                    mapOf("name" to seed.name, "sortOrder" to seed.sortOrder),
                )
            }
            runCatching { batch.commit().await() }
        }
        val registration = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val stored = snapshot?.documents?.mapNotNull { it.toCategory() }.orEmpty()
            val migration = CategoryOrder.migrationUpdates(stored)
            val categories = if (migration == null) {
                stored
            } else {
                val batch = firestore.batch()
                migration.forEach { (id, order) ->
                    batch.update(collection.document(id), "sortOrder", order)
                }
                batch.commit()
                stored.map { category ->
                    migration[category.id]?.let { category.copy(sortOrder = it) } ?: category
                }
            }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
            trySend(categories)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun add(name: String): Result<ClothingCategory> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("signed out"))
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("empty name"))
        val collection = firestore.userCollection(uid, CATEGORIES)
        val nextOrder = runCatching {
            collection.get().await().documents.mapNotNull { it.getLong("sortOrder")?.toInt() }.maxOrNull() ?: -1
        }.getOrDefault(DefaultCategories.seeds.size - 1) + 1
        val id = collection.document().id
        val category = ClothingCategory(id, trimmed, nextOrder)
        return collection.document(id)
            .set(mapOf("name" to trimmed, "sortOrder" to nextOrder))
            .awaitCommitted()
            .map { category }
    }

    override suspend fun rename(id: String, name: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("signed out"))
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("empty name"))
        return firestore.userCollection(uid, CATEGORIES).document(id).update("name", trimmed).awaitCommitted()
    }

    override suspend fun reorder(ids: List<String>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("signed out"))
        if (ids.isEmpty()) return Result.success(Unit)
        val collection = firestore.userCollection(uid, CATEGORIES)
        val batch = firestore.batch()
        CategoryOrder.sortOrders(ids).forEach { (id, order) ->
            batch.update(collection.document(id), "sortOrder", order)
        }
        return batch.commit().awaitCommitted()
    }

    private companion object {
        const val CATEGORIES = "categories"
    }
}

private fun ClothingItem.toMap(): Map<String, Any?> = mapOf(
    "description" to description,
    "categoryId" to categoryId,
    "photoUrl" to photoUrl,
    "hidden" to hidden,
)

private fun DocumentSnapshot.toClothingItem(): ClothingItem? {
    val categoryId = getString("categoryId")?.takeIf { it.isNotBlank() }
        ?: DefaultCategories.fromLegacy(getString("category"))
        ?: return null
    return ClothingItem(
        id = id,
        description = getString("description").orEmpty(),
        categoryId = categoryId,
        photoUrl = getString("photoUrl")?.takeIf { it.isNotBlank() },
        hidden = getBoolean("hidden") ?: false,
    )
}

private fun DocumentSnapshot.toCategory(): ClothingCategory? {
    val name = getString("name")?.trim().orEmpty()
    if (name.isEmpty()) return null
    return ClothingCategory(
        id = id,
        name = name,
        sortOrder = getLong("sortOrder")?.toInt() ?: 0,
    )
}

private fun DocumentSnapshot.toEntity(): Entity? {
    val type = EntityType.fromWire(getString("type")) ?: return null
    val name = getString("name")?.trim().orEmpty()
    if (name.isEmpty()) return null
    val members = (get("memberIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    return Entity(
        id = id,
        name = name,
        type = type,
        hidden = getBoolean("hidden") ?: false,
        memberIds = members,
        description = getString("description").orEmpty(),
        photoUrl = getString("photoUrl")?.takeIf { it.isNotBlank() },
    )
}

private fun MeetingDraft.toFields(photoUrl: String?, hidden: Boolean?): Map<String, Any?> = buildMap {
    put("date", Dates.formatIso(date))
    put("entityIds", entityIds)
    put("entityNames", entityNames)
    put("entityId", entityIds.firstOrNull().orEmpty())
    put("entityName", entityNames.firstOrNull().orEmpty())
    put("clothingItemIds", clothingItemIds)
    put("photoUrl", photoUrl)
    put("note", note.trim())
    if (hidden != null) put("hidden", hidden)
}

private fun DocumentSnapshot.toMeeting(): Meeting? {
    val date = Dates.parseIso(getString("date")) ?: return null
    val listedIds = (get("entityIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    val legacyId = getString("entityId")?.takeIf { it.isNotBlank() }
    val entityIds = listedIds.ifEmpty { listOfNotNull(legacyId) }
    if (entityIds.isEmpty()) return null
    val listedNames = (get("entityNames") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    val legacyName = getString("entityName").orEmpty()
    val entityNames = when {
        listedNames.isNotEmpty() -> entityIds.mapIndexed { index, _ -> listedNames.getOrNull(index).orEmpty() }
        else -> entityIds.mapIndexed { index, _ -> if (index == 0) legacyName else "" }
    }
    val clothingIds = (get("clothingItemIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    return Meeting(
        id = id,
        date = date,
        entityIds = entityIds,
        entityNames = entityNames,
        clothingItemIds = clothingIds,
        photoUrl = getString("photoUrl")?.takeIf { it.isNotBlank() },
        note = getString("note").orEmpty(),
        hidden = getBoolean("hidden") ?: false,
    )
}
