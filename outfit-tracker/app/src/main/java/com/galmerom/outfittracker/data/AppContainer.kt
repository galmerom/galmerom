package com.galmerom.outfittracker.data

import android.content.Context
import com.galmerom.outfittracker.data.ai.FirebaseAiGarmentDetector
import com.galmerom.outfittracker.data.auth.FirebaseAuthRepository
import com.galmerom.outfittracker.data.firestore.FirestoreCategoryRepository
import com.galmerom.outfittracker.data.firestore.FirestoreClothingRepository
import com.galmerom.outfittracker.data.firestore.FirestoreEntityRepository
import com.galmerom.outfittracker.data.firestore.FirestoreMeetingRepository
import com.galmerom.outfittracker.data.photo.FirebasePhotoStorage
import com.galmerom.outfittracker.domain.GarmentDetector
import com.galmerom.outfittracker.domain.repository.AuthRepository
import com.galmerom.outfittracker.domain.repository.CategoryRepository
import com.galmerom.outfittracker.domain.repository.ClothingRepository
import com.galmerom.outfittracker.domain.repository.EntityRepository
import com.galmerom.outfittracker.domain.repository.MeetingRepository
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.storage.storage

enum class FirebaseStatus {
    MissingConfig,
    MissingWebClient,
    Ready,
}

class AppContainer(context: Context) {
    val status: FirebaseStatus
    val authRepository: AuthRepository?
    val clothingRepository: ClothingRepository?
    val entityRepository: EntityRepository?
    val meetingRepository: MeetingRepository?
    val categoryRepository: CategoryRepository?
    val detector: GarmentDetector?
    val preferences: UserPreferences
    private var firestoreRef: FirebaseFirestore? = null
    private var authRef: FirebaseAuth? = null

    fun syncPreferences() {
        val firestore = firestoreRef ?: return
        val auth = authRef ?: return
        preferences.pushToAccount(firestore, auth)
    }

    init {
        val appContext = context.applicationContext
        preferences = UserPreferences(appContext)
        if (!FirebaseConfig.isConfigured(appContext)) {
            status = FirebaseStatus.MissingConfig
            authRepository = null
            clothingRepository = null
            entityRepository = null
            meetingRepository = null
            categoryRepository = null
            detector = null
        } else {
            val firebaseApp = FirebaseApp.getApps(appContext).firstOrNull()
                ?: FirebaseApp.initializeApp(appContext)
            if (firebaseApp == null) {
                status = FirebaseStatus.MissingConfig
                authRepository = null
                clothingRepository = null
                entityRepository = null
                meetingRepository = null
                categoryRepository = null
                detector = null
            } else {
                val firestore = Firebase.firestore
                firestoreRef = firestore
                runCatching {
                    firestore.firestoreSettings = firestoreSettings {
                        setLocalCacheSettings(persistentCacheSettings {})
                    }
                }
                val auth = Firebase.auth
                authRef = auth
                val photos = FirebasePhotoStorage(Firebase.storage)
                val webClientId = FirebaseConfig.webClientId(appContext)
                status = if (webClientId == null) FirebaseStatus.MissingWebClient else FirebaseStatus.Ready
                authRepository = FirebaseAuthRepository(auth, appContext, webClientId)
                clothingRepository = FirestoreClothingRepository(firestore, auth, photos)
                entityRepository = FirestoreEntityRepository(firestore, auth, photos)
                AppCheckInstaller.install(appContext)
                meetingRepository = FirestoreMeetingRepository(firestore, auth, photos)
                categoryRepository = FirestoreCategoryRepository(firestore, auth)
                detector = FirebaseAiGarmentDetector()
                auth.addAuthStateListener { firebaseAuth ->
                    if (firebaseAuth.currentUser != null) {
                        preferences.startAccountSync(firestore, auth)
                    }
                }
            }
        }
    }
}
