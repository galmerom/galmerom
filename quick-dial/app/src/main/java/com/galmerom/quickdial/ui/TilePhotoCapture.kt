package com.galmerom.quickdial.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

data class TilePhotoActions(
    val onGallery: (contactId: String) -> Unit,
    val onCamera: (contactId: String) -> Unit,
)

@Composable
fun rememberTilePhotoActions(
    onPicked: (contactId: String, uri: Uri) -> Unit,
    onDenied: () -> Unit,
    onCaptureActive: (Boolean) -> Unit = {},
): TilePhotoActions {
    val context = LocalContext.current
    // The camera app recreates this screen. The file is created first and the
    // URI is remembered so the picture can still be copied when we come back.
    var cameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraContactId by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val contactId = cameraContactId
        val uri = cameraUriString?.let(Uri::parse)
        onCaptureActive(false)
        if (!success || contactId.isNullOrBlank() || uri == null) return@rememberLauncherForActivityResult
        onPicked(contactId, uri)
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val contactId = cameraContactId
        if (!granted || contactId.isNullOrBlank()) {
            onCaptureActive(false)
            if (!granted) onDenied()
            return@rememberLauncherForActivityResult
        }
        launchCamera(context, contactId, takePicture) { uri ->
            cameraUriString = uri
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val contactId = cameraContactId
        onCaptureActive(false)
        if (uri == null || contactId.isNullOrBlank()) return@rememberLauncherForActivityResult
        onPicked(contactId, uri)
    }

    return TilePhotoActions(
        onGallery = { contactId ->
            cameraContactId = contactId
            onCaptureActive(true)
            pickImage.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        },
        onCamera = { contactId ->
            cameraContactId = contactId
            onCaptureActive(true)
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                launchCamera(context, contactId, takePicture) { uri ->
                    cameraUriString = uri
                }
            } else {
                requestCamera.launch(Manifest.permission.CAMERA)
            }
        },
    )
}

private fun launchCamera(
    context: Context,
    contactId: String,
    launcher: androidx.activity.result.ActivityResultLauncher<Uri>,
    rememberUri: (String) -> Unit,
) {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(directory, "shot-$contactId-${System.currentTimeMillis()}.jpg")
    file.createNewFile()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val capture = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    val handlers = context.packageManager.queryIntentActivities(capture, PackageManager.MATCH_DEFAULT_ONLY)
    for (handler in handlers) {
        context.grantUriPermission(handler.activityInfo.packageName, uri, flags)
    }
    rememberUri(uri.toString())
    launcher.launch(uri)
}
