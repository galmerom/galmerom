package com.galmerom.outfittracker.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.galmerom.outfittracker.R
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.galmerom.outfittracker.data.photo.readCompressedJpeg
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch

data class PhotoActions(
    val onCamera: () -> Unit,
    val onGallery: () -> Unit,
)

@Composable
fun rememberPhotoActions(
    onJpeg: (ByteArray) -> Unit,
    onFailure: () -> Unit,
    onDenied: () -> Unit = onFailure,
): PhotoActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraUri = remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri.value
        if (!success || uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { context.readCompressedJpeg(uri) }
                .onSuccess(onJpeg)
                .onFailure { onFailure() }
        }
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCamera(context, takePicture) { cameraUri.value = it }
        } else {
            onDenied()
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { context.readCompressedJpeg(uri) }
                .onSuccess(onJpeg)
                .onFailure { onFailure() }
        }
    }
    return PhotoActions(
        onCamera = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                launchCamera(context, takePicture) { cameraUri.value = it }
            } else {
                requestCamera.launch(Manifest.permission.CAMERA)
            }
        },
        onGallery = {
            pickImage.launch(
                PickVisualMediaRequest(mediaType = PickVisualMedia.ImageOnly),
            )
        },
    )
}

@Composable
fun ClothingImage(
    jpeg: ByteArray?,
    url: String?,
    description: String,
    modifier: Modifier = Modifier,
) {
    val imageModifier = modifier
        .clip(MaterialTheme.shapes.small)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    when {
        jpeg != null -> AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(jpeg).build(),
            contentDescription = description,
            modifier = imageModifier,
            contentScale = ContentScale.Crop,
        )
        !url.isNullOrBlank() -> AsyncImage(
            model = url,
            contentDescription = description,
            modifier = imageModifier,
            contentScale = ContentScale.Crop,
        )
        else -> Box(imageModifier, contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_photo),
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun launchCamera(
    context: Context,
    launcher: ActivityResultLauncher<Uri>,
    onUri: (Uri) -> Unit,
) {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(directory, "${UUID.randomUUID()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    onUri(uri)
    launcher.launch(uri)
}
