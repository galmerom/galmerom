package com.galmerom.outfittracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
    const val EDIT = "wardrobe/edit/{itemId}"
    const val DETAIL = "meeting/{meetingId}"

    fun edit(itemId: String) = "wardrobe/edit/$itemId"
    fun detail(meetingId: String) = "meeting/$meetingId"
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
    when (gate) {
        Gate.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        Gate.SignedOut -> SignedOutScreen(container)
        is Gate.SignedIn -> AppScaffold()
    }
}

@Composable
private fun SignedOutScreen(container: AppContainer) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val auth = container.authRepository ?: return
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
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
private fun AppScaffold() {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val topLevel = route == Routes.LOG || route == Routes.SEARCH || route == Routes.WARDROBE
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            if (topLevel) {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        TextButton(
                            onClick = {
                                scope.launch { container.authRepository?.signOut() }
                            },
                        ) {
                            Text(stringResource(R.string.sign_out))
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (topLevel) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == Routes.LOG,
                        onClick = { navController.openTopLevel(Routes.LOG) },
                        icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_log_meeting)) },
                    )
                    NavigationBarItem(
                        selected = route == Routes.SEARCH,
                        onClick = { navController.openTopLevel(Routes.SEARCH) },
                        icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_search)) },
                    )
                    NavigationBarItem(
                        selected = route == Routes.WARDROBE,
                        onClick = { navController.openTopLevel(Routes.WARDROBE) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
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
        }
    }
}

private fun androidx.navigation.NavController.openTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
