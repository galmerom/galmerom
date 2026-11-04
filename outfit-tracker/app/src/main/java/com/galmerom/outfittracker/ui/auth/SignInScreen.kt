package com.galmerom.outfittracker.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galmerom.outfittracker.R

@Composable
fun SignInScreen(
    missingWebClient: Boolean,
    onSignIn: suspend () -> Result<Unit>,
    onFailure: () -> Unit,
) {
    var busy by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(R.string.hello), style = MaterialTheme.typography.headlineMedium)
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        Text(text = stringResource(R.string.sign_in_body), style = MaterialTheme.typography.bodyLarge)
        if (missingWebClient) {
            Text(
                text = stringResource(R.string.sign_in_missing_client),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = {
                busy = true
            },
            enabled = !missingWebClient && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator()
            } else {
                Text(stringResource(R.string.sign_in_google))
            }
        }
    }
    if (busy && !missingWebClient) {
        androidx.compose.runtime.LaunchedEffect(busy) {
            val result = onSignIn()
            busy = false
            if (result.isFailure) onFailure()
        }
    }
}
