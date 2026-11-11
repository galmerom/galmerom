package com.galmerom.botbekalut.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galmerom.botbekalut.BotBekalutViewModel
import com.galmerom.botbekalut.Gate
import com.galmerom.botbekalut.LaunchArgs
import com.galmerom.botbekalut.domain.FontSizeId
import com.galmerom.botbekalut.domain.ThemeId
import com.galmerom.botbekalut.ui.theme.BotBekalutTheme
import kotlinx.coroutines.launch

@Composable
fun BotBekalutApp(launch: LaunchArgs) {
    val viewModel: BotBekalutViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scroll by viewModel.scroll.collectAsStateWithLifecycle()
    BotBekalutTheme(
        theme = ThemeId.fromId(state.settings.themeId),
        font = FontSizeId.fromId(state.settings.fontScale),
    ) {
        when (val gate = state.gate) {
            Gate.Loading -> Centered { CircularProgressIndicator() }
            Gate.SignedOut -> SignInPane(
                signingIn = state.signingIn,
                error = state.authError,
                onSignIn = { viewModel.signIn(it) },
            )
            is Gate.Denied -> DeniedPane(email = gate.email, onSignOut = viewModel::signOut)
            is Gate.Ready -> {
                NotificationPermission(state.settings.askedNotificationPermission, viewModel::markNotificationAsked)
                ReadyPane(
                    state = state,
                    scroll = scroll,
                    launch = launch,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun ReadyPane(
    state: com.galmerom.botbekalut.UiState,
    scroll: com.galmerom.botbekalut.ScrollCommand?,
    launch: LaunchArgs,
    viewModel: BotBekalutViewModel,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showPages by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(launch.botId, launch.openUnread, launch.issuedAt) {
        viewModel.consumeLaunch(launch.botId, launch.openUnread, launch.issuedAt)
    }
    LaunchedEffect(Unit) {
        viewModel.toasts.collect { snackbar.showSnackbar(it) }
    }
    BackHandler(enabled = showPages) { showPages = false }
    val notificationsGranted = if (Build.VERSION.SDK_INT < 33) {
        true
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                SideMenu(
                    settings = state.settings,
                    notificationsGranted = notificationsGranted,
                    onTheme = viewModel::setTheme,
                    onFont = viewModel::setFont,
                    onMute = viewModel::setMuted,
                    onMove = viewModel::moveBot,
                    onPages = {
                        showPages = true
                        scope.launch { drawerState.close() }
                    },
                    onSignOut = {
                        scope.launch { drawerState.close() }
                        viewModel.signOut()
                    },
                )
            }
        },
    ) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                ChatPane(
                    state = state,
                    scroll = scroll,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onRefresh = { viewModel.refresh(userInitiated = true) },
                    onBotSettled = viewModel::onBotSettled,
                    onRevealUnread = viewModel::revealUnread,
                    onSaveAnchor = viewModel::saveAnchor,
                    onMarkRead = viewModel::markReadToEnd,
                )
                if (showPages) {
                    PagesScreen(
                        pages = state.pages,
                        hidden = state.hiddenPageIds,
                        order = state.settings.botOrder,
                        onBack = { showPages = false },
                        onHidden = viewModel::setPageHidden,
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    )
                }
            }
        }
    }
}

@Composable
private fun SignInPane(
    signingIn: Boolean,
    error: String?,
    onSignIn: (android.content.Context) -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("בוט בקלות", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "הודעות מהבוטים, 24 השעות האחרונות.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
        )
        Button(onClick = { onSignIn(context) }, enabled = !signingIn, modifier = Modifier.fillMaxWidth()) {
            Text(if (signingIn) "מתחבר…" else "כניסה עם Google", style = MaterialTheme.typography.titleMedium)
        }
        if (!error.isNullOrBlank()) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun DeniedPane(email: String?, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("לחשבון הזה אין גישה", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            "האפליקציה זמינה רק לחשבון המורשה.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (!email.isNullOrBlank()) {
            Text(email, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
        Button(onClick = onSignOut, modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) {
            Text("יציאה", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun NotificationPermission(asked: Boolean, onAsked: () -> Unit) {
    if (Build.VERSION.SDK_INT < 33 || asked) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onAsked() }
    LaunchedEffect(asked) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) onAsked() else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
