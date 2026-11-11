package com.aistudio.pokiesvault.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.pokiesvault.Screen
import com.aistudio.pokiesvault.ui.theme.*

@Composable
fun AppDrawer(
    currentScreen: Screen,
    activeThemeId: String,
    savedAccountsCount: Int,
    userEmail: String?,
    isLocalOnly: Boolean = false,
    onNavigate: (Screen) -> Unit,
    onThemeSelect: (String) -> Unit,
    onMyAccountsClick: () -> Unit,
    onAddDomainClick: () -> Unit,
    onClearDeviceClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onSignInClick: () -> Unit = {},
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVaultColors.current
    var showConfirmClear by remember { mutableStateOf(false) }

    ModalDrawerSheet(
        modifier = modifier.width(310.dp),
        drawerContainerColor = colors.panel,
        drawerContentColor = colors.fg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Menu",
                        color = colors.fg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Password stays on this device.",
                        color = colors.muted,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onCloseDrawer) {
                    Icon(Icons.Default.Close, contentDescription = "Close menu", tint = colors.muted)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cloud / Local Mode Status Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_cloud_status"),
                shape = RoundedCornerShape(12.dp),
                color = colors.panel2,
                border = BorderStroke(
                    1.dp,
                    if (isLocalOnly) colors.violet.copy(alpha = 0.55f) else colors.cyan.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isLocalOnly) Icons.Default.Smartphone else Icons.Default.CloudDone,
                        contentDescription = if (isLocalOnly) "Local-Only Device Mode" else "Cloud Vault Synced",
                        tint = if (isLocalOnly) colors.violet else colors.cyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLocalOnly) "Local-Only Mode Active" else "Cloud Vault Active",
                            color = if (isLocalOnly) colors.violet else colors.cyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (isLocalOnly) "Device storage · Direct local IP" else (userEmail ?: "Google Account Connected"),
                            color = colors.muted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 1: GO
            Text("GO", color = colors.cyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(6.dp))

            DrawerItemRow(
                icon = Icons.Default.Home,
                title = "Main list",
                subtitle = "Domains and account cards",
                isSelected = currentScreen == Screen.DIRECTORY,
                onClick = {
                    onNavigate(Screen.DIRECTORY)
                    onCloseDrawer()
                },
                modifier = Modifier.testTag("drawer_main_list")
            )

            DrawerItemRow(
                icon = Icons.Default.Bolt,
                title = "Fetcher",
                subtitle = "Balances, share, referral, rebate",
                isSelected = currentScreen == Screen.FETCHER,
                onClick = {
                    onNavigate(Screen.FETCHER)
                    onCloseDrawer()
                },
                modifier = Modifier.testTag("drawer_fetcher")
            )

            DrawerItemRow(
                icon = Icons.Default.Radar,
                title = "Domain Intel",
                subtitle = "Add, remove, and check domains",
                isSelected = currentScreen == Screen.INTEL,
                onClick = {
                    onNavigate(Screen.INTEL)
                    onCloseDrawer()
                },
                modifier = Modifier.testTag("drawer_domain_intel")
            )

            DrawerItemRow(
                icon = Icons.Default.CardGiftcard,
                title = "Bonus Finder",
                subtitle = "Signup, share, daily, birthday",
                isSelected = currentScreen == Screen.BONUSES,
                onClick = {
                    onNavigate(Screen.BONUSES)
                    onCloseDrawer()
                },
                modifier = Modifier.testTag("drawer_bonus_finder")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 2: LIST
            Text("LIST", color = colors.cyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(6.dp))

            DrawerItemRow(
                icon = Icons.Default.Person,
                title = "My accounts",
                subtitle = "$savedAccountsCount site${if (savedAccountsCount == 1) "" else "s"} saved",
                onClick = {
                    onMyAccountsClick()
                    onCloseDrawer()
                },
                modifier = Modifier.testTag("drawer_my_accounts")
            )

            DrawerItemRow(
                icon = Icons.Default.Add,
                title = "Add a domain",
                subtitle = "Save a site into your accounts",
                onClick = {
                    onAddDomainClick()
                    onCloseDrawer()
                },
                modifier = Modifier.testTag("drawer_add_domain")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 3: THEME
            Text("THEMES", color = colors.cyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ALL_THEMES.forEach { theme ->
                    val isSelected = theme.id == activeThemeId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelect(theme.id) }
                            .testTag("theme_btn_${theme.id}"),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) colors.panel2 else colors.panel,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) colors.cyan else colors.border
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(theme.gradient)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(theme.name, color = if (isSelected) colors.cyan else colors.fg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(theme.hint, color = colors.muted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 4: ACCOUNT & DEVICE
            Text("ACCOUNT", color = colors.cyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(6.dp))

            if (isLocalOnly) {
                DrawerItemRow(
                    icon = Icons.Default.CloudSync,
                    title = "Sign in with Google",
                    subtitle = "Enable Cloud Vault backup & sync",
                    titleColor = colors.cyan,
                    onClick = {
                        onCloseDrawer()
                        onSignInClick()
                    },
                    modifier = Modifier.testTag("drawer_sign_in")
                )
            } else {
                DrawerItemRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "Sign out",
                    subtitle = "Disconnect Google account on this device",
                    onClick = {
                        onCloseDrawer()
                        onSignOutClick()
                    },
                    modifier = Modifier.testTag("drawer_sign_out")
                )
            }

            DrawerItemRow(
                icon = Icons.Default.DeleteOutline,
                title = "Clear this device",
                subtitle = if (isLocalOnly) "Removes local vault data" else "Removes local & cloud vault data",
                titleColor = colors.dead,
                onClick = { showConfirmClear = true },
                modifier = Modifier.testTag("drawer_clear_device")
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showConfirmClear) {
        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            title = { Text("Clear vault data?", color = colors.fg) },
            text = { Text("Clear saved sites, domain intel, visits, and the password stored on this device?", color = colors.muted) },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmClear = false
                        onClearDeviceClick()
                        onCloseDrawer()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.dead)
                ) {
                    Text("Clear", color = colors.fg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear = false }) {
                    Text("Cancel", color = colors.muted)
                }
            },
            containerColor = colors.panel2,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

@Composable
private fun DrawerItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    titleColor: androidx.compose.ui.graphics.Color? = null,
    clickable: Boolean = true
) {
    val colors = LocalVaultColors.current
    val effectiveColor = titleColor ?: if (isSelected) colors.cyan else colors.fg

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) colors.cyan.copy(alpha = 0.12f) else colors.panel2,
        border = BorderStroke(
            1.dp,
            if (isSelected) colors.cyan else colors.border
        ),
        onClick = if (clickable) onClick else ({})
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (titleColor != null) titleColor else if (isSelected) colors.cyan else colors.muted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    color = effectiveColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = subtitle,
                    color = colors.muted,
                    fontSize = 10.sp
                )
            }
        }
    }
}
