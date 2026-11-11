package com.aistudio.pokiesvault.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.pokiesvault.data.model.*
import com.aistudio.pokiesvault.ui.components.ResponsibleGamblingFooter
import com.aistudio.pokiesvault.ui.components.SiteAvatar
import com.aistudio.pokiesvault.ui.theme.*
import com.aistudio.pokiesvault.ui.viewmodel.VaultUiState
import com.aistudio.pokiesvault.ui.viewmodel.VaultViewModel
import com.aistudio.pokiesvault.util.FormatUtil
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private const val PAGE_SIZE = 15

@Composable
fun BonusFinderScreen(
    viewModel: VaultViewModel,
    uiState: VaultUiState,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVaultColors.current
    var showFilterPanel by remember { mutableStateOf(false) }

    val filteredDomains = remember(uiState) { viewModel.getFilteredBonusDomains() }
    val pageCount = remember(filteredDomains) { max(1, ceil(filteredDomains.size.toDouble() / PAGE_SIZE).toInt()) }
    val currentPage = min(uiState.bonusPage, pageCount - 1).coerceAtLeast(0)
    val pageSlice = remember(filteredDomains, currentPage) {
        val start = currentPage * PAGE_SIZE
        val end = min(start + PAGE_SIZE, filteredDomains.size)
        if (start < end) filteredDomains.subList(start, end) else emptyList()
    }

    val loadedCount = remember(filteredDomains, uiState.bonusCache) {
        filteredDomains.count { uiState.bonusCache.containsKey(it) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = onBackToList,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.testTag("finder_back_to_list")
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = colors.cyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back to the list", color = colors.cyan, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Bonus Finder",
                style = TextStyle(
                    brush = colors.gradient,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "The same domains as the main list. Bonuses are the public signup, share, daily, and birthday offers. No sign-in required.",
                color = colors.muted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Main Control Panel
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colors.panel,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Search input
                    OutlinedTextField(
                        value = uiState.bonusQuery,
                        onValueChange = { viewModel.setBonusQuery(it) },
                        placeholder = { Text("Search this list", color = colors.faint, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.muted) },
                        trailingIcon = {
                            if (uiState.bonusQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setBonusQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = colors.muted)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
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
                            .height(46.dp)
                            .testTag("bonus_search_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Slot Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            BonusSlot.SIGNUP,
                            BonusSlot.SHARE,
                            BonusSlot.DAILY,
                            BonusSlot.BIRTHDAY
                        ).forEach { slot ->
                            val active = uiState.bonusFilters.slots.contains(slot)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) colors.cyan.copy(alpha = 0.2f) else colors.panel2,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (active) colors.cyan else colors.border
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clickable { viewModel.toggleBonusSlotFilter(slot) }
                                    .testTag("slot_btn_${slot.id}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = slot.label,
                                        color = if (active) colors.cyan else colors.fg,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Expandable advanced filters toggle
                    TextButton(
                        onClick = { showFilterPanel = !showFilterPanel },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (showFilterPanel) Icons.Default.ExpandLess else Icons.Default.Tune,
                            contentDescription = null,
                            tint = colors.cyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showFilterPanel) "Hide filters & sort" else "Filters & sort options",
                            color = colors.cyan,
                            fontSize = 12.sp
                        )
                    }

                    AnimatedVisibility(visible = showFilterPanel) {
                        BonusFiltersContent(
                            filters = uiState.bonusFilters,
                            onUpdate = { updater -> viewModel.updateBonusFilters(updater) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.loadBonuses(pageSlice) },
                            enabled = !uiState.isBonusLoading && pageSlice.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.cyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.2f).testTag("load_page_btn")
                        ) {
                            Text("Load this page", color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { viewModel.loadBonuses(filteredDomains) },
                            enabled = !uiState.isBonusLoading && filteredDomains.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.panel2),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("load_list_btn")
                        ) {
                            Text("Load all (${filteredDomains.size})", color = colors.fg, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { viewModel.stopBonusLoad() },
                            enabled = uiState.isBonusLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.panel2),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.7f).testTag("stop_bonus_btn")
                        ) {
                            Text("Stop", color = if (uiState.isBonusLoading) colors.dead else colors.muted, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = buildString {
                            append(uiState.bonusStatusMessage)
                            append(" ${filteredDomains.size} domains · $loadedCount loaded")
                            if (uiState.isBonusLoading) {
                                append(" · ${uiState.bonusProgressDone} / ${uiState.bonusProgressTotal}")
                            }
                        },
                        color = colors.cyan,
                        fontSize = 11.sp
                    )

                    if (uiState.isBonusLoading && uiState.bonusProgressTotal > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val progress = uiState.bonusProgressDone.toFloat() / uiState.bonusProgressTotal.toFloat()
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            color = colors.cyan,
                            trackColor = colors.panel2,
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        if (filteredDomains.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.panel
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No domains match the current search or filters.", color = colors.muted, fontSize = 13.sp)
                    }
                }
            }
        } else {
            itemsIndexed(pageSlice, key = { _, domain -> domain }) { index, domain ->
                val rank = currentPage * PAGE_SIZE + index + 1
                val bonuses = uiState.bonusCache[domain]
                DomainBonusCard(
                    domain = domain,
                    rank = rank,
                    bonuses = bonuses,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Pagination Controls
            if (pageCount > 1) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.setBonusPage(currentPage - 1) },
                            enabled = currentPage > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.panel2),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Previous", color = if (currentPage > 0) colors.fg else colors.muted, fontSize = 12.sp)
                        }

                        Text(
                            text = "Page ${currentPage + 1} / $pageCount",
                            color = colors.fg,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )

                        Button(
                            onClick = { viewModel.setBonusPage(currentPage + 1) },
                            enabled = currentPage < pageCount - 1,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.panel2),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Next", color = if (currentPage < pageCount - 1) colors.fg else colors.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            ResponsibleGamblingFooter()
        }
    }
}

@Composable
private fun BonusFiltersContent(
    filters: BonusFinderFilters,
    onUpdate: ((BonusFinderFilters) -> BonusFinderFilters) -> Unit
) {
    val colors = LocalVaultColors.current

    Column(modifier = Modifier.padding(top = 8.dp)) {
        // Sort option
        Text("Sort by", color = colors.cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BonusSort.values().forEach { sort ->
                val active = filters.sort == sort
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (active) colors.cyan.copy(alpha = 0.2f) else colors.panel2,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (active) colors.cyan else colors.border),
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clickable { onUpdate { it.copy(sort = sort) } }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = sort.label.replace("Highest ", "High ").replace("Lowest ", "Low "),
                            color = if (active) colors.cyan else colors.fg,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Kind
        Text("Amount type", color = colors.cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                BonusKind.ANY to "Any",
                BonusKind.FIXED to "Fixed $",
                BonusKind.PERCENT to "Percent %",
                BonusKind.RANDOM to "Range"
            ).forEach { (kind, label) ->
                val active = filters.kind == kind
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (active) colors.cyan.copy(alpha = 0.2f) else colors.panel2,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (active) colors.cyan else colors.border),
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clickable { onUpdate { it.copy(kind = kind) } }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = if (active) colors.cyan else colors.fg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Amount & Rollover ranges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = filters.amountMin,
                onValueChange = { v -> onUpdate { it.copy(amountMin = v) } },
                placeholder = { Text("Amount min", color = colors.faint, fontSize = 10.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(42.dp)
            )
            OutlinedTextField(
                value = filters.amountMax,
                onValueChange = { v -> onUpdate { it.copy(amountMax = v) } },
                placeholder = { Text("Amount max", color = colors.faint, fontSize = 10.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = filters.rollMin,
                onValueChange = { v -> onUpdate { it.copy(rollMin = v) } },
                placeholder = { Text("Rollover min", color = colors.faint, fontSize = 10.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(42.dp)
            )
            OutlinedTextField(
                value = filters.rollMax,
                onValueChange = { v -> onUpdate { it.copy(rollMax = v) } },
                placeholder = { Text("Rollover max", color = colors.faint, fontSize = 10.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(42.dp)
            )
        }
    }
}

@Composable
fun DomainBonusCard(
    domain: String,
    rank: Int,
    bonuses: List<PublicBonus>?,
    modifier: Modifier = Modifier
) {
    val colors = LocalVaultColors.current
    val context = LocalContext.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bonus_card_$domain"),
        shape = RoundedCornerShape(14.dp),
        color = colors.panel,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SiteAvatar(domain = domain, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = domain,
                            color = colors.fg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "#$rank in list",
                            color = colors.muted,
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://$domain/"))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.panel2),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Open", color = colors.cyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (bonuses == null) {
                Text(
                    text = "Bonuses not loaded yet. Tap 'Load this page' or 'Load all' to fetch promotions.",
                    color = colors.faint,
                    fontSize = 12.sp
                )
            } else if (bonuses.isEmpty()) {
                Text(
                    text = "No public promotions published by this site.",
                    color = colors.muted,
                    fontSize = 12.sp
                )
            } else {
                bonuses.forEach { bonus ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = colors.panel2
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = when (bonus.slot) {
                                            BonusSlot.SIGNUP -> colors.cyan.copy(alpha = 0.2f)
                                            BonusSlot.SHARE -> colors.live.copy(alpha = 0.2f)
                                            BonusSlot.DAILY -> colors.slow.copy(alpha = 0.2f)
                                            BonusSlot.BIRTHDAY -> colors.pink.copy(alpha = 0.2f)
                                            BonusSlot.OTHER -> colors.panel
                                        }
                                    ) {
                                        Text(
                                            text = bonus.slot.label,
                                            color = when (bonus.slot) {
                                                BonusSlot.SIGNUP -> colors.cyan
                                                BonusSlot.SHARE -> colors.live
                                                BonusSlot.DAILY -> colors.slow
                                                BonusSlot.BIRTHDAY -> colors.pink
                                                BonusSlot.OTHER -> colors.muted
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = bonus.name,
                                        color = colors.fg,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = bonus.amountDisplay,
                                    color = colors.live,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Bonus terms row
                            val terms = buildString {
                                if (bonus.rollover != null) append("${bonus.rollover.toInt()}× rollover · ")
                                if (bonus.minDeposit != null) append("Min deposit ${FormatUtil.money(bonus.minDeposit)} · ")
                                if (bonus.minWithdraw != null) append("Min cashout ${FormatUtil.money(bonus.minWithdraw)} · ")
                                if (bonus.maxWithdraw != null) append("Max cashout ${FormatUtil.money(bonus.maxWithdraw)}")
                                else append("No max limit")
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = terms,
                                color = colors.muted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
