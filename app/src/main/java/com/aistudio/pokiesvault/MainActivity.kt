package com.aistudio.pokiesvault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.pokiesvault.data.catalog.CatalogData
import com.aistudio.pokiesvault.ui.components.*
import com.aistudio.pokiesvault.ui.screens.BonusFinderScreen
import com.aistudio.pokiesvault.ui.screens.DirectoryScreen
import com.aistudio.pokiesvault.ui.screens.DomainIntelScreen
import com.aistudio.pokiesvault.ui.screens.FetcherScreen
import com.aistudio.pokiesvault.ui.theme.*
import com.aistudio.pokiesvault.ui.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

enum class Screen {
    DIRECTORY, FETCHER, INTEL, BONUSES
}

class MainActivity : ComponentActivity() {
    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val themeId = uiState.vaultState.themeId

            PokiesVaultTheme(themeId = themeId) {
                val colors = LocalVaultColors.current
                var currentScreen by remember { mutableStateOf(Screen.DIRECTORY) }
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                // BackHandler returns to DIRECTORY if on sub-screen, or closes drawer
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
