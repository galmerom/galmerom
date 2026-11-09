package com.galmerom.quickdial.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.galmerom.quickdial.R
import kotlinx.coroutines.delay

@Composable
fun CallCountdownDialog(name: String, onCancel: () -> Unit, onFinished: () -> Unit) {
    var remaining by remember(name) { mutableIntStateOf(3) }
    LaunchedEffect(name) {
        repeat(3) { step ->
            remaining = 3 - step
            delay(1000)
        }
        onFinished()
    }
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.calling, name),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = remaining.toString(),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 84.sp,
                    lineHeight = 92.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().height(76.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
fun NoticeDialog(event: NoticeEvent, onDismiss: () -> Unit, onSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(event.kind.titleRes()), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(stringResource(event.kind.bodyRes()), style = MaterialTheme.typography.bodyLarge)
        },
        confirmButton = {
            if (event.kind.offersSettings) {
                TextButton(onClick = onSettings) {
                    Text(stringResource(R.string.open_settings), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok), style = MaterialTheme.typography.titleMedium)
            }
        },
    )
}

private fun NoticeKind.titleRes(): Int = when (this) {
    NoticeKind.WhatsAppMissing, NoticeKind.WhatsAppAppMissing -> R.string.whatsapp_missing_title
    NoticeKind.CallFailed -> R.string.call_failed_title
    NoticeKind.NoNumber -> R.string.no_number_title
    NoticeKind.CallDenied -> R.string.call_denied_title
    NoticeKind.ContactsDenied -> R.string.contacts_denied_title
    NoticeKind.CameraDenied -> R.string.camera_denied_title
    NoticeKind.PhotoFailed -> R.string.photo_failed_title
}

private fun NoticeKind.bodyRes(): Int = when (this) {
    NoticeKind.WhatsAppMissing -> R.string.whatsapp_missing_body
    NoticeKind.WhatsAppAppMissing -> R.string.whatsapp_app_missing_body
    NoticeKind.CallFailed -> R.string.call_failed_body
    NoticeKind.NoNumber -> R.string.no_number_body
    NoticeKind.CallDenied -> R.string.call_denied_body
    NoticeKind.ContactsDenied -> R.string.contacts_denied_body
    NoticeKind.CameraDenied -> R.string.camera_denied_body
    NoticeKind.PhotoFailed -> R.string.photo_failed_body
}

private val NoticeKind.offersSettings: Boolean
    get() = this == NoticeKind.CallDenied ||
        this == NoticeKind.ContactsDenied ||
        this == NoticeKind.CameraDenied
