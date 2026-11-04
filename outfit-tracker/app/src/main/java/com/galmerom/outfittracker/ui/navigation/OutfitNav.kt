package com.galmerom.outfittracker.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galmerom.outfittracker.data.FontSize
import com.galmerom.outfittracker.ui.categories.CategoryListScreen
import com.galmerom.outfittracker.ui.entities.EntityEditScreen
import com.galmerom.outfittracker.ui.entities.EntityEditViewModel
import com.galmerom.outfittracker.ui.entities.EntityListScreen
import com.galmerom.outfittracker.ui.entities.HiddenEntitiesScreen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.galmerom.outfittracker.R
import com.galmerom.outfittracker.data.AppContainer
import com.galmerom.outfittracker.data.FirebaseStatus
import com.galmerom.outfittracker.domain.repository.SignedInUser
import com.galmerom.outfittracker.ui.LocalAppContainer
import com.galmerom.outfittracker.ui.UserMessage
import com.galmerom.outfittracker.ui.auth.SignInScreen
import com.galmerom.outfittracker.ui.meeting.LogMeetingScreen
import com.galmerom.outfittracker.ui.search.MeetingDetailScreen
import com.galmerom.outfittracker.ui.search.SearchScreen
import com.galmerom.outfittracker.ui.setup.SetupRequiredScreen
import com.galmerom.outfittracker.ui.textFor
import com.galmerom.outfittracker.ui.wardrobe.ClothingEditScreen
import com.galmerom.outfittracker.ui.wardrobe.ClothingEditViewModel
import com.galmerom.outfittracker.ui.wardrobe.HiddenItemsScreen
import com.galmerom.outfittracker.ui.wardrobe.WardrobeScreen
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val LOG = "log"
    const val LOG_EDIT = "log/{meetingId}"
    const val SEARCH = "search"
    const val WARDROBE = "wardrobe"
    const val HIDDEN = "wardrobe/hidden"
    const val ENTITIES = "entities"
    const val ENTITIES_HIDDEN = "entities/hidden"
    const val ENTITY_EDIT = "entities/edit/{entityId}"
    const val CATEGORIES = "categories"
    const val EDIT = "wardrobe/edit/{itemId}"
    const val DETAIL = "meeting/{meetingId}"

    fun edit(itemId: String) = "wardrobe/edit/$itemId"
    fun detail(meetingId: String) = "meeting/$meetingId"
    fun entity(entityId: String) = "entities/edit/$entityId"
}

private sealed interface Gate {
    data object Loading : Gate
    data object SignedOut : Gate
    data class SignedIn(val user: SignedInUser) : Gate
}

@Composable
fun OutfitTrackerRoot() {
    val container = LocalAppContainer.current
    when (container.status) {
        FirebaseStatus.MissingConfig -> SetupRequiredScreen()
        FirebaseStatus.MissingWebClient, FirebaseStatus.Ready -> AuthGate(container)
    }
}

@Composable
private fun AuthGate(container: AppContainer) {
    val auth = container.authRepository
    if (auth == null) {
        SetupRequiredScreen()
        return
    }
    val gate by produceState<Gate>(Gate.Loading, auth) {
        auth.observeUser().collect { user ->
            value = if (user == null) Gate.SignedOut else Gate.SignedIn(user)
        }
    }
    when (val current = gate) {
        Gate.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        Gate.SignedOut -> SignedOutScreen(container)
        is Gate.SignedIn -> AppScaffold(current.user)
    }
}

@Composable
private fun SignedOutScreen(container: AppContainer) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val auth = container.authRepository ?: return
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            SignInScreen(
                missingWebClient = container.status == FirebaseStatus.MissingWebClient,
                onSignIn = { auth.signIn(context) },
                onFailure = {
                    scope.launch { snackbar.showSnackbar(context.textFor(UserMessage.SIGN_IN_FAILED)) }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(user: SignedInUser) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val topLevel = route == Routes.LOG || route == Routes.LOG_EDIT || route == Routes.SEARCH || route == Routes.WARDROBE
    val showHomeButton = topLevel && route != Routes.HOME
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val prefs by container.preferences.prefs.collectAsStateWithLifecycle(com.galmerom.outfittracker.data.UserPrefs())
    val entityLabel = stringResource(R.string.entity_label)
    val barColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Text(
                        user.displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.signed_in_unknown),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(user.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.menu_entities, entityLabel)) },
                        selected = route == Routes.ENTITIES,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(Routes.ENTITIES)
                        },
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.menu_categories)) },
                        selected = route == Routes.CATEGORIES,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(Routes.CATEGORIES)
                        },
                    )
                    Text(stringResource(R.string.font_size), style = MaterialTheme.typography.titleSmall)
                    RowFontSizes(prefs.fontSize) { size ->
                        container.preferences.setFontSize(size)
                        container.syncPreferences()
                    }
                    ThemePicker(prefs.theme) { theme ->
                        container.preferences.setTheme(theme)
                        container.syncPreferences()
                    }
                    TextButton(onClick = { scope.launch { container.authRepository?.signOut() } }) {
                        Text(stringResource(R.string.sign_out))
                    }
                }
            }
        },
    ) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (topLevel || route == Routes.HOME) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu_open))
                        }
                    },
                    title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        if (showHomeButton) {
                            TextButton(
                                onClick = {
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(Routes.HOME) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.back_home))
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (topLevel) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    NavigationBarItem(
                        selected = route == Routes.LOG || route == Routes.LOG_EDIT,
                        onClick = { navController.openTopLevel(Routes.LOG) },
                        colors = barColors,
                        icon = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_log_meeting)) },
                    )
                    NavigationBarItem(
                        selected = route == Routes.SEARCH,
                        onClick = { navController.openTopLevel(Routes.SEARCH) },
                        colors = barColors,
                        icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_search)) },
                    )
                    NavigationBarItem(
                        selected = route == Routes.WARDROBE,
                        onClick = { navController.openTopLevel(Routes.WARDROBE) },
                        colors = barColors,
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_hanger),
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(R.string.nav_wardrobe)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNewMeeting = { navController.navigate(Routes.LOG) },
                    onSearch = { navController.navigate(Routes.SEARCH) },
                    onWardrobe = { navController.navigate(Routes.WARDROBE) },
                )
            }
            composable(Routes.LOG) { LogMeetingScreen() }
            composable(
                route = Routes.LOG_EDIT,
                arguments = listOf(navArgument("meetingId") { type = NavType.StringType }),
            ) { entry ->
                LogMeetingScreen(meetingId = entry.arguments?.getString("meetingId"))
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenMeeting = { navController.navigate(Routes.detail(it)) },
                    onEditMeeting = { navController.navigate("log/$it") },
                )
            }
            composable(Routes.WARDROBE) {
                WardrobeScreen(
                    onAdd = { navController.navigate(Routes.edit(ClothingEditViewModel.NEW_ID)) },
                    onEdit = { navController.navigate(Routes.edit(it)) },
                    onHidden = { navController.navigate(Routes.HIDDEN) },
                )
            }
            composable(Routes.HIDDEN) { HiddenItemsScreen(onBack = { navController.popBackStack() }) }
            composable(
                route = Routes.EDIT,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { entry ->
                ClothingEditScreen(
                    itemId = entry.arguments?.getString("itemId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("meetingId") { type = NavType.StringType }),
            ) { entry ->
                MeetingDetailScreen(
                    meetingId = entry.arguments?.getString("meetingId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("log/$it") },
                )
            }
            composable(Routes.ENTITIES) { entry ->
                val saved by entry.savedStateHandle
                    .getStateFlow("entitySaved", false)
                    .collectAsStateWithLifecycle()
                EntityListScreen(
                    onAdd = { navController.navigate(Routes.entity(EntityEditViewModel.NEW)) },
                    onEdit = { navController.navigate(Routes.entity(it)) },
                    onHidden = { navController.navigate(Routes.ENTITIES_HIDDEN) },
                    onHome = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    showSaved = saved,
                    onSavedShown = { entry.savedStateHandle["entitySaved"] = false },
                )
            }
            composable(Routes.ENTITIES_HIDDEN) {
                HiddenEntitiesScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.ENTITY_EDIT,
                arguments = listOf(navArgument("entityId") { type = NavType.StringType }),
            ) { entry ->
                EntityEditScreen(
                    entityId = entry.arguments?.getString("entityId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        navController.previousBackStackEntry?.savedStateHandle?.set("entitySaved", true)
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.CATEGORIES) {
                CategoryListScreen(onBack = { navController.popBackStack() })
            }
        }
    }
    }
}

@Composable
private fun HomeScreen(onNewMeeting: () -> Unit, onSearch: () -> Unit, onWardrobe: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.hello), style = MaterialTheme.typography.headlineMedium)
        HomeButton(stringResource(R.string.home_new_meeting), onNewMeeting)
        HomeButton(stringResource(R.string.nav_wardrobe), onWardrobe)
        HomeButton(stringResource(R.string.nav_search), onSearch)
    }
}

@Composable
private fun HomeButton(label: String, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ThemePicker(selected: com.galmerom.outfittracker.data.AppTheme, onSelect: (com.galmerom.outfittracker.data.AppTheme) -> Unit) {
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        com.galmerom.outfittracker.data.AppTheme.entries.forEach { theme ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(themeSwatch(theme))
                        .border(
                            width = if (theme == selected) 3.dp else 1.dp,
                            color = if (theme == selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        )
                        .clickable { onSelect(theme) },
                )
                Text(themeLabel(theme), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun themeLabel(theme: com.galmerom.outfittracker.data.AppTheme): String = when (theme) {
    com.galmerom.outfittracker.data.AppTheme.WARM -> stringResource(R.string.theme_warm)
    com.galmerom.outfittracker.data.AppTheme.OCEAN -> stringResource(R.string.theme_ocean)
    com.galmerom.outfittracker.data.AppTheme.SAGE -> stringResource(R.string.theme_sage)
    com.galmerom.outfittracker.data.AppTheme.LILAC -> stringResource(R.string.theme_lilac)
    com.galmerom.outfittracker.data.AppTheme.NIGHT -> stringResource(R.string.theme_night)
}

private fun themeSwatch(theme: com.galmerom.outfittracker.data.AppTheme) = when (theme) {
    com.galmerom.outfittracker.data.AppTheme.WARM -> com.galmerom.outfittracker.ui.theme.Accent
    com.galmerom.outfittracker.data.AppTheme.OCEAN -> com.galmerom.outfittracker.ui.theme.Ocean
    com.galmerom.outfittracker.data.AppTheme.SAGE -> com.galmerom.outfittracker.ui.theme.Sage
    com.galmerom.outfittracker.data.AppTheme.LILAC -> com.galmerom.outfittracker.ui.theme.Lilac
    com.galmerom.outfittracker.data.AppTheme.NIGHT -> com.galmerom.outfittracker.ui.theme.NightBackground
}

@Composable
private fun RowFontSizes(selected: FontSize, onSelect: (FontSize) -> Unit) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == FontSize.REGULAR,
            onClick = { onSelect(FontSize.REGULAR) },
            label = { Text(stringResource(R.string.font_regular)) },
        )
        FilterChip(
            selected = selected == FontSize.LARGE,
            onClick = { onSelect(FontSize.LARGE) },
            label = { Text(stringResource(R.string.font_large)) },
        )
        FilterChip(
            selected = selected == FontSize.EXTRA_LARGE,
            onClick = { onSelect(FontSize.EXTRA_LARGE) },
            label = { Text(stringResource(R.string.font_extra_large)) },
        )
    }
}

private fun androidx.navigation.NavController.openTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
