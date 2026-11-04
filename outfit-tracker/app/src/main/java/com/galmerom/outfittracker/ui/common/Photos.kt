package com.galmerom.outfittracker.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.galmerom.outfittracker.domain.logic.ImageSizing
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
    maxEdge: Int = ImageSizing.MAX_EDGE_PX,
): PhotoActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The camera activity, especially the front camera, often recreates this screen.
    // The output URI has to survive that, or the picture is thrown away before it can be saved.
    var cameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUriString?.let(Uri::parse)
        if (!success) return@rememberLauncherForActivityResult
        if (uri == null) {
            onFailure()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching { context.readCompressedJpeg(uri, maxEdge) }
                .onSuccess(onJpeg)
                .onFailure { onFailure() }
        }
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraUriString = launchCamera(context, takePicture)
        } else {
            onDenied()
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { context.readCompressedJpeg(uri, maxEdge) }
                .onSuccess(onJpeg)
                .onFailure { onFailure() }
        }
    }
    return PhotoActions(
        onCamera = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                cameraUriString = launchCamera(context, takePicture)
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

@Composable
fun EntityAvatar(
    photoUrl: String?,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    jpeg: ByteArray? = null,
) {
    ClothingImage(
        jpeg = jpeg,
        url = photoUrl,
        description = label,
        modifier = modifier.size(size).clip(CircleShape),
    )
}

private fun launchCamera(
    context: Context,
    launcher: ActivityResultLauncher<Uri>,
): String {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(directory, "${UUID.randomUUID()}.jpg")
    file.createNewFile()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val capture = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    val handlers = context.packageManager.queryIntentActivities(capture, PackageManager.MATCH_DEFAULT_ONLY)
    for (handler in handlers) {
        context.grantUriPermission(handler.activityInfo.packageName, uri, flags)
    }
    launcher.launch(uri)
    return uri.toString()
}
