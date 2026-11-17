package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.FirebaseAuthManager
import com.example.data.local.LocalPreferencesStore
import com.example.data.repository.LifeMateRepository
import com.example.ui.components.QuickAddModalSheet
import com.example.ui.components.QuickAddTab
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.GoalsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PlanScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.theme.LifeMateTheme
import com.example.ui.viewmodel.LifeMateUiState
import com.example.ui.viewmodel.LifeMateViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

object NavRoutes {
    const val HOME = "home"
    const val PLAN = "plan"
    const val EXPENSES = "expenses"
    const val GOALS = "goals"
    const val PROFILE = "profile"
}

private data class BottomNavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val Destinations = listOf(
    BottomNavDestination(NavRoutes.HOME, "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    BottomNavDestination(NavRoutes.PLAN, "Plan", Icons.Rounded.CheckCircle, Icons.Outlined.CheckCircle),
    BottomNavDestination(NavRoutes.EXPENSES, "Expenses", Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    BottomNavDestination(NavRoutes.GOALS, "Goals", Icons.Rounded.TrackChanges, Icons.Outlined.TrackChanges),
    BottomNavDestination(NavRoutes.PROFILE, "Profile", Icons.Rounded.Person, Icons.Outlined.Person)
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LifeMateAppRoot()
        }
    }
}

@Composable
fun LifeMateAppRoot(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val prefsStore = remember(context) { LocalPreferencesStore(context.applicationContext) }
    val lastDarkMode by prefsStore.lastDarkModeFlow.collectAsStateWithLifecycle(initialValue = false)
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val authManager = remember(context, auth, credentialManager) {
        FirebaseAuthManager(
            context = context.applicationContext,
            auth = auth,
            credentialManager = credentialManager
        )
    }
    val currentUser by authManager.observeAuthUser().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(authManager) {
        if (auth.currentUser == null) {
            authManager.attemptSilentSignIn()
        }
    }

    val user = currentUser
    if (user == null) {
        LifeMateTheme(darkTheme = lastDarkMode) {
            LoginScreen(
                authManager = authManager,
                onAuthSuccess = {}
            )
        }
    } else {
        key(user.uid) {
            AuthenticatedLifeMateSession(
                user = user,
                authManager = authManager,
                fallbackDarkMode = lastDarkMode
            )
        }
    }
}

@Composable
private fun AuthenticatedLifeMateSession(
    user: FirebaseUser,
    authManager: FirebaseAuthManager,
    fallbackDarkMode: Boolean
) {
    val coroutineScope = rememberCoroutineScope()

    val viewModel: LifeMateViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                LifeMateViewModel(
                    repository = LifeMateRepository(db),
                    prefsStore = LocalPreferencesStore(app),
                    currentUserId = user.uid,
                    googleDisplayName = user.displayName ?: "",
                    googleEmail = user.email ?: "",
                    googlePhotoUrl = user.photoUrl?.toString() ?: ""
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val isDark = if (uiState.isLoading) fallbackDarkMode else uiState.darkModeEnabled

    LifeMateTheme(darkTheme = isDark) {
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.needsOnboarding -> {
                ProfileSetupScreen(
                    initialName = user.displayName ?: "",
                    initialAvatarModel = uiState.avatarModel,
                    isSaving = isSaving,
                    onPickPhoto = viewModel::savePickedPhoto,
                    onSaveProfile = viewModel::saveOnboardingProfile
                )
            }

            else -> {
                LifeMateMainScaffold(
                    uiState = uiState,
                    statusMessage = statusMessage,
                    onClearStatusMessage = viewModel::clearStatusMessage,
                    viewModel = viewModel,
                    onLogout = {
                        coroutineScope.launch {
                            authManager.signOut()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LifeMateMainScaffold(
    uiState: LifeMateUiState,
    statusMessage: String?,
    onClearStatusMessage: () -> Unit,
    viewModel: LifeMateViewModel,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavRoutes.HOME
    val snackbarHostState = remember { SnackbarHostState() }

    var showQuickAddSheet by remember { mutableStateOf(false) }

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusMessage)
            onClearStatusMessage()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        if (!uiState.errorMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(uiState.errorMessage)
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val quickAddInitialTab = when (currentRoute) {
        NavRoutes.EXPENSES -> QuickAddTab.EXPENSE
        NavRoutes.GOALS -> QuickAddTab.GOAL
        else -> QuickAddTab.TASK
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 640.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                if (currentRoute != NavRoutes.PROFILE) {
                    ExtendedFloatingActionButton(
                        onClick = { showQuickAddSheet = true },
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text("Quick Add") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("quick_add_fab")
                    )
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        Destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = NavRoutes.HOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(NavRoutes.HOME) {
                        HomeScreen(
                            uiState = uiState,
                            onDismissWelcomeBanner = viewModel::dismissWelcomeBanner,
                            onToggleTask = viewModel::toggleTaskCompletion,
                            onNavigateToPlan = { navigateToTab(NavRoutes.PLAN) },
                            onNavigateToExpenses = { navigateToTab(NavRoutes.EXPENSES) },
                            onNavigateToGoals = { navigateToTab(NavRoutes.GOALS) },
                            onNavigateToProfile = { navigateToTab(NavRoutes.PROFILE) },
                            onQuickAddTask = {
                                showQuickAddSheet = true
                            },
                            onQuickAddGoal = {
                                navigateToTab(NavRoutes.GOALS)
                            }
                        )
                    }
                    composable(NavRoutes.PLAN) {
                        PlanScreen(
                            uiState = uiState,
                            onAddTask = viewModel::addTask,
                            onUpdateTask = viewModel::updateTask,
                            onToggleTask = viewModel::toggleTaskCompletion,
                            onDeleteTask = viewModel::deleteTask
                        )
                    }
                    composable(NavRoutes.EXPENSES) {
                        ExpensesScreen(
                            uiState = uiState,
                            onAddExpense = viewModel::addExpense,
                            onUpdateExpense = viewModel::updateExpense,
                            onDeleteExpense = viewModel::deleteExpense
                        )
                    }
                    composable(NavRoutes.GOALS) {
                        GoalsScreen(
                            uiState = uiState,
                            onAddGoal = viewModel::addGoal,
                            onUpdateGoal = viewModel::updateGoal,
                            onDeleteGoal = viewModel::deleteGoal
                        )
                    }
                    composable(NavRoutes.PROFILE) {
                        ProfileScreen(
                            uiState = uiState,
                            onPickPhoto = viewModel::savePickedPhoto,
                            onUpdateProfile = viewModel::updateProfileDetails,
                            onToggleDarkMode = viewModel::toggleDarkMode,
                            onToggleNotifications = viewModel::toggleNotifications,
                            onResetLocalData = viewModel::resetLocalData,
                            onLogout = onLogout
                        )
                    }
                }
            }
        }
    }

    if (showQuickAddSheet) {
        QuickAddModalSheet(
            initialTab = quickAddInitialTab,
            todayKey = uiState.todayKey,
            onDismiss = { showQuickAddSheet = false },
            onAddTask = viewModel::addTask,
            onAddExpense = viewModel::addExpense,
            onAddGoal = viewModel::addGoal
        )
    }
}
