package com.aistudio.pokiesvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.pokiesvault.data.model.*
import com.aistudio.pokiesvault.ui.components.*
import com.aistudio.pokiesvault.ui.theme.*
import com.aistudio.pokiesvault.ui.viewmodel.VaultUiState
import com.aistudio.pokiesvault.ui.viewmodel.VaultViewModel
import com.aistudio.pokiesvault.util.FormatUtil

@Composable
fun DirectoryScreen(
    viewModel: VaultViewModel,
    uiState: VaultUiState,
    modifier: Modifier = Modifier
) {
    val colors = LocalVaultColors.current
    val sites = remember(uiState) { viewModel.getFilteredCatalogSites() }
    val vState = uiState.vaultState
    val fetchMap = remember(uiState.currentFetchRows) {
        uiState.currentFetchRows.associateBy { it.domain }
    }

    val isGroupedView = vState.viewMode == ViewMode.GROUPS &&
            uiState.searchQuery.isBlank() &&
            !vState.mineOnly &&
            !vState.notVisited &&
            vState.sortMode == SortMode.DEFAULT

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Toolbar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.panel,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                // Tool buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterToolButton(
                        text = if (vState.notVisited) "Not visited: On" else "Not visited: Off",
                        isActive = vState.notVisited,
                        icon = Icons.Default.Visibility,
                        onClick = { viewModel.toggleNotVisited() },
                        modifier = Modifier.weight(1f).testTag("not_visited_filter_btn")
                    )
                    FilterToolButton(
                        text = when (vState.viewMode) {
                            ViewMode.GROUPS -> "View: Groups"
                            ViewMode.ONE -> "View: One"
                            ViewMode.TWO -> "View: Two"
                        },
                        isActive = false,
                        icon = Icons.Default.GridView,
                        onClick = { viewModel.cycleViewMode() },
                        modifier = Modifier.weight(1f).testTag("view_mode_btn")
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterToolButton(
                        text = if (vState.wallets) "Wallets: On" else "Wallets: Off",
                        isActive = vState.wallets,
                        icon = Icons.Default.AccountBalanceWallet,
                        onClick = { viewModel.toggleWallets() },
                        modifier = Modifier.weight(1f).testTag("wallets_toggle_btn")
                    )
                    FilterToolButton(
                        text = "Sort / filter",
                        isActive = vState.sortMode != SortMode.DEFAULT || vState.mineOnly,
                        icon = Icons.Default.Sort,
                        onClick = { viewModel.setSortFilterOpen(true) },
                        modifier = Modifier.weight(1f).testTag("sort_filter_btn")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                // Search Input
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search casinos or domains", color = colors.faint, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.muted) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = colors.muted)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.fg,
                        unfocusedTextColor = colors.fg,
                        focusedBorderColor = colors.cyan,
                        unfocusedBorderColor = colors.border,
                        focusedContainerColor = colors.panel2,
                        unfocusedContainerColor = colors.panel2
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("search_field")
                )

                Spacer(modifier = Modifier.height(6.dp))
                StatsBar(
                    siteCount = sites.size,
                    visitedCount = sites.count { vState.visited.contains(it.domain) },
                    myCount = vState.mine.size
                )
            }
        }

        // Main content list / grid
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HowAccountsWorkBanner()
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (sites.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No matching sites found.", color = colors.muted, fontSize = 14.sp)
                    }
                }
            } else if (isGroupedView) {
                // Grouped presentation
                val groups = listOf(
                    SiteGroup.NEW,
                    SiteGroup.BEST,
                    SiteGroup.UNVERIFIED,
                    SiteGroup.SHARE,
                    SiteGroup.CUSTOM,
                    SiteGroup.LATEST
                )

                groups.forEach { group ->
                    val groupSites = sites.filter { it.group == group }
                    if (groupSites.isNotEmpty()) {
                        item {
                            Text(
                                text = "${group.label} · ${groupSites.size}",
                                color = colors.cyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(groupSites, key = { it.domain }) { site ->
                            LaunchedEffect(site.domain) {
                                viewModel.checkStatusIfNeeded(site.domain)
                            }
                            val status = uiState.siteStatuses[site.domain] ?: SiteStatus.CHECKING
                            val acc = fetchMap[site.domain] as? AccountResult.Ok
                            val walletText = if (vState.wallets && acc != null) {
                                "${FormatUtil.money(acc.cash)} ${acc.cashWalletName}"
                            } else null
                            val bonusText = if (acc != null) {
                                when (vState.bonusMode) {
                                    BonusMode.NEW -> acc.newMember?.let { "${it.name} ${it.amountDisplay}" }
                                    BonusMode.SHARE -> acc.share?.let { "${it.name} ${it.amountDisplay}" }
                                    BonusMode.COMMISSION -> acc.commission?.let { "${it.name} ${it.amountDisplay}" }
                                    BonusMode.REFERRAL -> acc.referral?.let { "${it.name} ${it.amountDisplay}" }
                                    BonusMode.REBATE -> acc.rebate?.let { "${it.name} ${it.amountDisplay}" }
                                    BonusMode.NONE -> null
                                }
                            } else null

                            SiteCard(
                                site = site,
                                status = status,
                                isBookmarked = vState.mine.contains(site.domain),
                                isVisited = vState.visited.contains(site.domain),
                                walletText = walletText,
                                bonusText = bonusText,
                                onCardClick = { viewModel.markVisited(site.domain) },
                                onBookmarkToggle = { viewModel.toggleMine(site.domain) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            } else if (vState.viewMode == ViewMode.TWO) {
                val chunkedSites = sites.chunked(2)
                items(chunkedSites) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { site ->
                            LaunchedEffect(site.domain) {
                                viewModel.checkStatusIfNeeded(site.domain)
                            }
                            val status = uiState.siteStatuses[site.domain] ?: SiteStatus.CHECKING
                            val acc = fetchMap[site.domain] as? AccountResult.Ok
                            val walletText = if (vState.wallets && acc != null) {
                                "${FormatUtil.money(acc.cash)} ${acc.cashWalletName}"
                            } else null

                            SiteCard(
                                site = site,
                                status = status,
                                isBookmarked = vState.mine.contains(site.domain),
                                isVisited = vState.visited.contains(site.domain),
                                walletText = walletText,
                                bonusText = null,
                                onCardClick = { viewModel.markVisited(site.domain) },
                                onBookmarkToggle = { viewModel.toggleMine(site.domain) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(sites, key = { it.domain }) { site ->
                    LaunchedEffect(site.domain) {
                        viewModel.checkStatusIfNeeded(site.domain)
                    }
                    val status = uiState.siteStatuses[site.domain] ?: SiteStatus.CHECKING
                    val acc = fetchMap[site.domain] as? AccountResult.Ok
                    val walletText = if (vState.wallets && acc != null) {
                        "${FormatUtil.money(acc.cash)} ${acc.cashWalletName}"
                    } else null

                    SiteCard(
                        site = site,
                        status = status,
                        isBookmarked = vState.mine.contains(site.domain),
                        isVisited = vState.visited.contains(site.domain),
                        walletText = walletText,
                        bonusText = null,
                        onCardClick = { viewModel.markVisited(site.domain) },
                        onBookmarkToggle = { viewModel.toggleMine(site.domain) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                StorySection()
                ResponsibleGamblingFooter()
            }
        }
    }
}

@Composable
fun FilterToolButton(
    text: String,
    isActive: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVaultColors.current
    Surface(
        modifier = modifier
            .height(34.dp)
            .testTag("tool_btn_$text"),
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) colors.cyan.copy(alpha = 0.15f) else colors.panel2,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) colors.cyan else colors.border
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) colors.cyan else colors.muted,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = if (isActive) colors.cyan else colors.fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
