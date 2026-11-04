package com.galmerom.outfittracker.domain.repository

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
    suspend fun create(name: String, type: EntityType): Result<Entity>
}

interface MeetingRepository {
    fun observeAll(): Flow<List<Meeting>>
    suspend fun log(draft: MeetingDraft, photoJpeg: ByteArray?): Result<SaveOutcome>
}
