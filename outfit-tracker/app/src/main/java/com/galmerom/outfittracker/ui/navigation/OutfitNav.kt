package com.galmerom.outfittracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
    const val LOG = "log"
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
    val topLevel = route == Routes.LOG || route == Routes.SEARCH || route == Routes.WARDROBE
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val fontSize by container.fontSettings.choice.collectAsStateWithLifecycle(FontSize.REGULAR)
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
                    RowFontSizes(fontSize) { size ->
                        scope.launch { container.fontSettings.set(size) }
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
            if (topLevel) {
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
                        selected = route == Routes.LOG,
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
            startDestination = Routes.LOG,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LOG) { LogMeetingScreen() }
            composable(Routes.SEARCH) { SearchScreen(onOpenMeeting = { navController.navigate(Routes.detail(it)) }) }
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
                )
            }
            composable(Routes.ENTITIES) {
                EntityListScreen(
                    onAdd = { navController.navigate(Routes.entity(EntityEditViewModel.NEW)) },
                    onEdit = { navController.navigate(Routes.entity(it)) },
                    onHidden = { navController.navigate(Routes.ENTITIES_HIDDEN) },
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
