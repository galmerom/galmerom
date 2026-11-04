package com.galmerom.quickdial.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galmerom.quickdial.R
import com.galmerom.quickdial.domain.DialTheme
import com.galmerom.quickdial.domain.ShortNameSize
import com.galmerom.quickdial.domain.dialableNumber
import com.galmerom.quickdial.ui.theme.LocalDialTheme
import com.galmerom.quickdial.ui.theme.QuickDialTheme
import com.galmerom.quickdial.ui.theme.themeSwatch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDialApp(viewModel: QuickDialViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var notice by remember { mutableStateOf<NoticeEvent?>(null) }
    var session by remember { mutableStateOf<HebrewSpeechSession?>(null) }
    var askedCallLog by remember { mutableStateOf(false) }
    var askedContacts by remember { mutableStateOf(false) }
    var openPickerOnGrant by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { viewModel.refresh() }
    DisposableEffect(session) { onDispose { session?.stop() } }

    LaunchedEffect(Unit) {
        viewModel.notices.collect { notice = it }
    }
    LaunchedEffect(Unit) {
        viewModel.whatsApp.collect { link ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${link.digits}")).setPackage(link.packageName)
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                viewModel.notify(NoticeKind.WhatsAppAppMissing)
            }
        }
    }

    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onCallPermissionResult(granted)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice(context, viewModel) { session = it }
        else viewModel.notify(NoticeKind.MicDenied)
    }
    val callLogPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refresh()
        if (granted && openPickerOnGrant) viewModel.openContacts()
        if (!granted) viewModel.notify(NoticeKind.ContactsDenied)
        val shouldAskLog = !openPickerOnGrant && !askedCallLog &&
            !hasPermission(context, Manifest.permission.READ_CALL_LOG)
        openPickerOnGrant = false
        if (shouldAskLog) {
            askedCallLog = true
            callLogPermission.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    LaunchedEffect(ui.ready, ui.tiles.isNotEmpty()) {
        if (!ui.ready || ui.tiles.isEmpty()) return@LaunchedEffect
        if (!askedContacts && !hasPermission(context, Manifest.permission.READ_CONTACTS)) {
            askedContacts = true
            openPickerOnGrant = false
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
        } else if (!askedCallLog && !hasPermission(context, Manifest.permission.READ_CALL_LOG)) {
            askedCallLog = true
            callLogPermission.launch(Manifest.permission.READ_CALL_LOG)
        }
    }
    LaunchedEffect(ui.callPhase) {
        val phase = ui.callPhase
        if (phase is CallPhase.NeedsPermission) {
            if (hasPermission(context, Manifest.permission.CALL_PHONE)) viewModel.onCallPermissionResult(true)
            else callPermission.launch(Manifest.permission.CALL_PHONE)
        }
    }

    BackHandler(enabled = ui.screen != Screen.Home) { viewModel.goHome() }

    CompositionLocalProvider(LocalDialTheme provides ui.theme) {
        QuickDialTheme {
            if (ui.screen == Screen.Home) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        DrawerContent(
                            ui = ui,
                            onPickContacts = {
                                scope.launch { drawerState.close() }
                                askedContacts = true
                                if (hasPermission(context, Manifest.permission.READ_CONTACTS)) {
                                    viewModel.openContacts()
                                } else {
                                    openPickerOnGrant = true
                                    contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                                }
                            },
                            onReorder = {
                                scope.launch { drawerState.close() }
                                viewModel.openReorder()
                            },
                            onColumns = viewModel::setColumns,
                            onFrozen = viewModel::setFrozen,
                            onBadge = viewModel::setBadge,
                            onSize = viewModel::setShortNameSize,
                            onTheme = viewModel::setTheme,
                        )
                    },
                ) {
                    AppScaffold(
                        title = stringResource(R.string.app_name),
                        onMenu = { scope.launch { drawerState.open() } },
                        showMenu = true,
                        onBack = null,
                    ) { padding ->
                        HomePane(
                            ui = ui,
                            onVoice = {
                                if (ui.listening) {
                                    session?.stop()
                                    session = null
                                    viewModel.setListening(false)
                                } else if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                    startVoice(context, viewModel) { session = it }
                                } else {
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onOpenMenu = { scope.launch { drawerState.open() } },
                            onCall = viewModel::requestCall,
                            onWhatsApp = viewModel::onWhatsApp,
                            modifier = Modifier.padding(padding),
                        )
                    }
                }
            } else {
                AppScaffold(
                    title = stringResource(if (ui.screen == Screen.Contacts) R.string.contacts_title else R.string.reorder_title),
                    onMenu = {},
                    showMenu = false,
                    onBack = viewModel::goHome,
                ) { padding ->
                    if (ui.screen == Screen.Contacts) {
                        ContactsPane(
                            picker = picker,
                            onQuery = viewModel::setQuery,
                            onToggle = viewModel::toggle,
                            onRename = viewModel::beginRename,
                            modifier = Modifier.padding(padding),
                        )
                    } else {
                        ReorderPane(
                            tiles = ui.tiles,
                            onSlide = viewModel::slide,
                            modifier = Modifier.padding(padding),
                        )
                    }
                }
            }

            val counting = ui.callPhase as? CallPhase.Counting
            if (counting != null) {
                CallCountdownDialog(
                    name = counting.tile.shortName.ifBlank { counting.tile.displayName },
                    onCancel = viewModel::cancelCall,
                    onFinished = {
                        val tile = viewModel.finishCountdown() ?: return@CallCountdownDialog
                        placeCall(context, tile.phoneNumber) { viewModel.notify(NoticeKind.CallFailed) }
                    },
                )
            }
            if (ui.choices.isNotEmpty() && counting == null) {
                ChoiceDialog(ui.choices, onPick = viewModel::pickChoice, onDismiss = viewModel::dismissChoices)
            }
            notice?.let { event ->
                NoticeDialog(
                    event = event,
                    onDismiss = { notice = null },
                    onSettings = {
                        notice = null
                        openAppSettings(context)
                    },
                )
            }
            picker.numberPrompt?.let { prompt ->
                NumberDialog(prompt, onDismiss = viewModel::dismissNumber, onConfirm = viewModel::confirmNumber)
            }
            picker.renamePrompt?.let { prompt ->
                RenameDialog(prompt, onDismiss = viewModel::dismissRename, onSave = viewModel::saveRename)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(
    title: String,
    showMenu: Boolean,
    onMenu: () -> Unit,
    onBack: (() -> Unit)?,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    if (showMenu) {
                        IconButton(onClick = onMenu) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu_open))
                        }
                    } else if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
            )
        },
        content = content,
    )
}

@Composable
private fun DrawerContent(
    ui: HomeUi,
    onPickContacts: () -> Unit,
    onReorder: () -> Unit,
    onColumns: (Int) -> Unit,
    onFrozen: (Boolean) -> Unit,
    onBadge: (Boolean) -> Unit,
    onSize: (ShortNameSize) -> Unit,
    onTheme: (DialTheme) -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.fillMaxHeight()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.columns), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = ui.columns == 3,
                    onClick = { onColumns(3) },
                    label = { Text(stringResource(R.string.columns_3), style = MaterialTheme.typography.titleMedium) },
                )
                FilterChip(
                    selected = ui.columns == 4,
                    onClick = { onColumns(4) },
                    label = { Text(stringResource(R.string.columns_4), style = MaterialTheme.typography.titleMedium) },
                )
            }
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.pick_contacts), style = MaterialTheme.typography.titleLarge) },
                selected = false,
                onClick = onPickContacts,
            )
            SettingSwitch(stringResource(R.string.freeze), ui.frozen, onFrozen)
            Text(
                stringResource(R.string.freeze_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ui.frozen) {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.reorder), style = MaterialTheme.typography.titleLarge) },
                    selected = false,
                    onClick = onReorder,
                )
            }
            SettingSwitch(stringResource(R.string.badge), ui.showBadge, onBadge)
            Text(stringResource(R.string.short_name_size), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShortNameSize.entries.forEach { size ->
                    FilterChip(
                        selected = ui.shortNameSp == size.sp,
                        onClick = { onSize(size) },
                        label = { Text(size.label(), style = MaterialTheme.typography.titleMedium) },
                    )
                }
            }
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DialTheme.entries.forEach { theme ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(themeSwatch(theme))
                                .border(
                                    width = if (theme == ui.theme) 3.dp else 1.dp,
                                    color = if (theme == ui.theme) {
                                        MaterialTheme.colorScheme.onBackground
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { onTheme(theme) },
                        )
                        Text(theme.label(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).clickable { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun ShortNameSize.label(): String = when (this) {
    ShortNameSize.Small -> stringResource(R.string.size_small)
    ShortNameSize.Medium -> stringResource(R.string.size_medium)
    ShortNameSize.Large -> stringResource(R.string.size_large)
}

@Composable
private fun DialTheme.label(): String = when (this) {
    DialTheme.Blue -> stringResource(R.string.theme_blue)
    DialTheme.Ocean -> stringResource(R.string.theme_ocean)
    DialTheme.Sage -> stringResource(R.string.theme_sage)
    DialTheme.Lilac -> stringResource(R.string.theme_lilac)
    DialTheme.Warm -> stringResource(R.string.theme_warm)
}

private fun startVoice(context: Context, viewModel: QuickDialViewModel, hold: (HebrewSpeechSession) -> Unit) {
    viewModel.setListening(true)
    val speech = HebrewSpeechSession(
        context = context,
        onResult = { phrases ->
            viewModel.setListening(false)
            viewModel.onSpeech(phrases)
        },
        onError = { problem ->
            viewModel.setListening(false)
            viewModel.notify(
                when (problem) {
                    SpeechProblem.Unavailable -> NoticeKind.SpeechUnavailable
                    SpeechProblem.Permission -> NoticeKind.MicDenied
                    SpeechProblem.Failed -> NoticeKind.SpeechFailed
                },
            )
        },
    )
    hold(speech)
    speech.start()
}

private fun placeCall(context: Context, number: String, onFailed: () -> Unit) {
    val dialable = dialableNumber(number)
    if (dialable.isBlank()) {
        onFailed()
        return
    }
    val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", dialable, null))
    try {
        context.startActivity(intent)
    } catch (_: SecurityException) {
        onFailed()
    } catch (_: ActivityNotFoundException) {
        onFailed()
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
