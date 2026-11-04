package com.galmerom.outfittracker.domain.repository

import com.galmerom.outfittracker.domain.model.ClothingCategory
import com.galmerom.outfittracker.domain.model.ClothingItem
import com.galmerom.outfittracker.domain.model.Entity
import com.galmerom.outfittracker.domain.model.EntityType
import com.galmerom.outfittracker.domain.model.Meeting
import com.galmerom.outfittracker.domain.model.MeetingDraft
import com.galmerom.outfittracker.domain.model.SaveOutcome
import kotlinx.coroutines.flow.Flow

data class SignedInUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
)

interface AuthRepository {
    fun observeUser(): Flow<SignedInUser?>
    suspend fun signIn(activityContext: android.content.Context): Result<Unit>
    suspend fun signOut()
}

interface ClothingRepository {
    fun observeAll(): Flow<List<ClothingItem>>
    suspend fun upsert(item: ClothingItem, photoJpeg: ByteArray?): Result<Pair<ClothingItem, SaveOutcome>>
    suspend fun setHidden(id: String, hidden: Boolean): Result<Unit>
}

interface EntityRepository {
    fun observeAll(): Flow<List<Entity>>
    suspend fun create(
        name: String,
        type: EntityType,
        description: String = "",
        photoJpeg: ByteArray? = null,
    ): Result<Entity>
    suspend fun update(
        id: String,
        name: String,
        type: EntityType,
        description: String = "",
        photoJpeg: ByteArray? = null,
        keepPhotoUrl: String? = null,
    ): Result<Unit>
    suspend fun setHidden(id: String, hidden: Boolean): Result<Unit>
    suspend fun setMembers(forumId: String, memberIds: List<String>): Result<Unit>
}

interface CategoryRepository {
    fun observeAll(): Flow<List<ClothingCategory>>
    suspend fun add(name: String): Result<ClothingCategory>
    suspend fun rename(id: String, name: String): Result<Unit>
    suspend fun reorder(ids: List<String>): Result<Unit>
}

interface MeetingRepository {
    fun observeAll(): Flow<List<Meeting>>
    suspend fun log(draft: MeetingDraft, photoJpeg: ByteArray?): Result<SaveOutcome>
    suspend fun update(id: String, draft: MeetingDraft, photoJpeg: ByteArray?, keepPhotoUrl: String?): Result<SaveOutcome>
    suspend fun delete(id: String): Result<Unit>
    suspend fun setHidden(id: String, hidden: Boolean): Result<Unit>
}
