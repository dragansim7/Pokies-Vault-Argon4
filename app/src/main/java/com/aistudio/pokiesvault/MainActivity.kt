package com.aistudio.pokiesvault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aistudio.pokiesvault.data.catalog.CatalogData
import com.aistudio.pokiesvault.data.repository.VaultRepository
import com.aistudio.pokiesvault.ui.components.*
import com.aistudio.pokiesvault.ui.screens.BonusFinderScreen
import com.aistudio.pokiesvault.ui.screens.DirectoryScreen
import com.aistudio.pokiesvault.ui.screens.DomainIntelScreen
import com.aistudio.pokiesvault.ui.screens.FetcherScreen
import com.aistudio.pokiesvault.ui.screens.VaultAuthScreen
import com.aistudio.pokiesvault.ui.screens.signOut
import com.aistudio.pokiesvault.ui.theme.*
import com.aistudio.pokiesvault.ui.viewmodel.VaultViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class Screen {
    DIRECTORY, FETCHER, INTEL, BONUSES
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppContent()
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences("pokies_vault_prefs", android.content.Context.MODE_PRIVATE)
    }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isLocalOnlyMode by remember {
        mutableStateOf(prefs.getBoolean("local_only_mode", false))
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val u = auth.currentUser
            currentUser = u
            if (u != null) {
                prefs.edit().putBoolean("local_only_mode", false).apply()
                isLocalOnlyMode = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    when {
        user != null -> {
            val uid = user.uid
            key(uid) {
                MainVaultFeatureScreen(
                    currentUserId = uid,
                    userEmail = user.email ?: user.displayName,
                    isLocalOnly = false
                )
            }
        }
        isLocalOnlyMode -> {
            key("local_device") {
                MainVaultFeatureScreen(
                    currentUserId = "local_device",
                    userEmail = null,
                    isLocalOnly = true,
                    onRequestSignIn = {
                        prefs.edit().putBoolean("local_only_mode", false).apply()
                        isLocalOnlyMode = false
                    }
                )
            }
        }
        else -> {
            PokiesVaultTheme(themeId = "vault") {
                VaultAuthScreen(
                    onAuthSuccess = {
                        prefs.edit().putBoolean("local_only_mode", false).apply()
                        isLocalOnlyMode = false
                        currentUser = Firebase.auth.currentUser
                    },
                    onContinueLocalOnly = {
                        prefs.edit().putBoolean("local_only_mode", true).apply()
                        isLocalOnlyMode = true
                    }
                )
            }
        }
    }
}

@Composable
fun MainVaultFeatureScreen(
    currentUserId: String,
    userEmail: String?,
    isLocalOnly: Boolean = false,
    onRequestSignIn: () -> Unit = {}
) {
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()

    val viewModel: VaultViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val prefs = app.getSharedPreferences("pokies_vault_prefs", android.content.Context.MODE_PRIVATE)
                VaultViewModel(
                    repository = VaultRepository(db, prefs),
                    currentUserId = currentUserId,
                    isLocalOnly = isLocalOnly
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    val themeId = uiState.vaultState.themeId

    PokiesVaultTheme(themeId = themeId) {
        val colors = LocalVaultColors.current
        var currentScreen by remember { mutableStateOf(Screen.DIRECTORY) }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

        BackHandler(enabled = drawerState.isOpen || currentScreen != Screen.DIRECTORY) {
            if (drawerState.isOpen) {
                scope.launch { drawerState.close() }
            } else {
                currentScreen = Screen.DIRECTORY
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawer(
                    currentScreen = currentScreen,
                    activeThemeId = themeId,
                    savedAccountsCount = uiState.vaultState.mine.size,
                    userEmail = userEmail,
                    isLocalOnly = isLocalOnly,
                    onNavigate = { screen ->
                        currentScreen = screen
                    },
                    onThemeSelect = { newThemeId ->
                        viewModel.setTheme(newThemeId)
                    },
                    onMyAccountsClick = {
                        viewModel.toggleMineOnly()
                        currentScreen = Screen.DIRECTORY
                    },
                    onAddDomainClick = {
                        viewModel.setAddDomainOpen(true)
                    },
                    onClearDeviceClick = {
                        viewModel.clearAll()
                        currentScreen = Screen.DIRECTORY
                    },
                    onSignOutClick = {
                        signOut(
                            credentialManager = credentialManager,
                            scope = scope
                        )
                    },
                    onSignInClick = onRequestSignIn,
                    onCloseDrawer = {
                        scope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBarContent(
                        currentScreen = currentScreen,
                        onFetcherClick = {
                            currentScreen = if (currentScreen == Screen.FETCHER) Screen.DIRECTORY else Screen.FETCHER
                        },
                        onMenuClick = {
                            scope.launch { drawerState.open() }
                        },
                        onTitleClick = {
                            currentScreen = Screen.DIRECTORY
                        }
                    )
                },
                containerColor = colors.bg,
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentScreen) {
                        Screen.DIRECTORY -> {
                            DirectoryScreen(
                                viewModel = viewModel,
                                uiState = uiState
                            )
                        }
                        Screen.FETCHER -> {
                            FetcherScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                onBackToList = { currentScreen = Screen.DIRECTORY }
                            )
                        }
                        Screen.INTEL -> {
                            DomainIntelScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                onBackToList = { currentScreen = Screen.DIRECTORY }
                            )
                        }
                        Screen.BONUSES -> {
                            BonusFinderScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                onBackToList = { currentScreen = Screen.DIRECTORY }
                            )
                        }
                    }
                }
            }
        }

        // Dialogs
        if (uiState.isAddDomainOpen) {
            AddDomainDialog(
                onDismiss = { viewModel.setAddDomainOpen(false) },
                onAdd = { raw -> viewModel.addCustomDomain(raw) }
            )
        }

        if (uiState.isSortFilterOpen) {
            SortFilterDialog(
                currentSort = uiState.vaultState.sortMode,
                currentBonus = uiState.vaultState.bonusMode,
                mineOnly = uiState.vaultState.mineOnly,
                onSortSelected = { mode -> viewModel.setSortMode(mode) },
                onBonusSelected = { bMode -> viewModel.setBonusMode(bMode) },
                onMineOnlyToggled = { viewModel.toggleMineOnly() },
                onDismiss = { viewModel.setSortFilterOpen(false) }
            )
        }

        if (uiState.isConfirmScanOpen) {
            ConfirmScanDialog(
                totalCount = CatalogData.SITES.size,
                onDismiss = { viewModel.setConfirmScanOpen(false) },
                onConfirm = {
                    viewModel.startFetch(CatalogData.SITES.map { it.domain })
                }
            )
        }
    }
}

@Composable
fun TopAppBarContent(
    currentScreen: Screen,
    onFetcherClick: () -> Unit,
    onMenuClick: () -> Unit,
    onTitleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVaultColors.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = colors.bg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Title with Gradient
            Text(
                text = "POKIES VAULT",
                style = TextStyle(
                    brush = colors.gradient,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier
                    .clickable { onTitleClick() }
                    .testTag("brand_title")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Fetcher Action Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.ink,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (currentScreen == Screen.FETCHER) colors.cyan else colors.violet.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .clickable { onFetcherClick() }
                        .testTag("nav_fetcher_btn")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (currentScreen == Screen.FETCHER) colors.cyan else colors.fg,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "FETCHER",
                            color = if (currentScreen == Screen.FETCHER) colors.cyan else colors.fg,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Menu Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.ink,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.violet.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .clickable { onMenuClick() }
                        .testTag("nav_menu_btn")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open menu",
                            tint = colors.fg,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "MENU",
                            color = colors.fg,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
