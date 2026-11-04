package com.galmerom.outfittracker.data

import android.content.Context
import com.galmerom.outfittracker.data.ai.FirebaseAiGarmentDetector
import com.galmerom.outfittracker.data.auth.FirebaseAuthRepository
import com.galmerom.outfittracker.data.firestore.FirestoreClothingRepository
import com.galmerom.outfittracker.data.firestore.FirestoreEntityRepository
import com.galmerom.outfittracker.data.firestore.FirestoreMeetingRepository
import com.galmerom.outfittracker.data.photo.FirebasePhotoStorage
import com.galmerom.outfittracker.domain.GarmentDetector
import com.galmerom.outfittracker.domain.repository.AuthRepository
import com.galmerom.outfittracker.domain.repository.ClothingRepository
import com.galmerom.outfittracker.domain.repository.EntityRepository
import com.galmerom.outfittracker.domain.repository.MeetingRepository
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
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
    val detector: GarmentDetector?

    init {
        val appContext = context.applicationContext
        if (!FirebaseConfig.isConfigured(appContext)) {
            status = FirebaseStatus.MissingConfig
            authRepository = null
            clothingRepository = null
            entityRepository = null
            meetingRepository = null
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
                detector = null
            } else {
                val firestore = Firebase.firestore
                runCatching {
                    firestore.firestoreSettings = firestoreSettings {
                        setLocalCacheSettings(persistentCacheSettings {})
                    }
                }
                val auth = Firebase.auth
                val photos = FirebasePhotoStorage(Firebase.storage)
                val webClientId = FirebaseConfig.webClientId(appContext)
                status = if (webClientId == null) FirebaseStatus.MissingWebClient else FirebaseStatus.Ready
                authRepository = FirebaseAuthRepository(auth, appContext, webClientId)
                clothingRepository = FirestoreClothingRepository(firestore, auth, photos)
                entityRepository = FirestoreEntityRepository(firestore, auth)
                meetingRepository = FirestoreMeetingRepository(firestore, auth, photos)
                detector = FirebaseAiGarmentDetector()
            }
        }
    }
}
