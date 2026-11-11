package com.aistudio.pokiesvault.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.pokiesvault.data.model.*
import com.aistudio.pokiesvault.ui.theme.*
import com.aistudio.pokiesvault.util.FormatUtil

@Composable
fun SiteAvatar(domain: String, modifier: Modifier = Modifier) {
    val colors = LocalVaultColors.current
    val tones = listOf(colors.cyan, colors.violet, colors.pink, colors.live)
    val color = tones[domain.firstOrNull()?.code?.mod(tones.size) ?: 0]
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = FormatUtil.initials(domain),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun SiteCard(
    site: CatalogSite,
    status: SiteStatus,
    isBookmarked: Boolean,
    isVisited: Boolean,
    walletText: String?,
    bonusText: String?,
    onCardClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVaultColors.current
    val context = LocalContext.current
    val statusColor = when (status) {
        SiteStatus.LIVE -> colors.live
        SiteStatus.SLOW -> colors.slow
        SiteStatus.REDIRECT -> colors.violet
        SiteStatus.DEAD -> colors.dead
        SiteStatus.CHECKING -> colors.muted
        SiteStatus.UNKNOWN -> colors.faint
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                onCardClick()
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://${site.domain}/"))
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
            .testTag("site_card_${site.domain}"),
        shape = RoundedCornerShape(14.dp),
        color = colors.panel,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SiteAvatar(domain = site.domain)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = site.domain,
                    color = colors.fg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = status.label + if (site.addedAt.isNotBlank()) " · ${FormatUtil.formatAdded(site.addedAt)}" else "",
                        color = colors.muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!walletText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = walletText,
                        color = colors.cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (!bonusText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = bonusText,
                        color = colors.pink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            IconButton(
                onClick = onBookmarkToggle,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("bookmark_button_${site.domain}")
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Save account",
                    tint = if (isBookmarked) colors.cyan else colors.muted
                )
            }
        }
    }
}

@Composable
fun ResultCard(
    row: AccountResult.Ok,
    modifier: Modifier = Modifier
) {
    val colors = LocalVaultColors.current
    val context = LocalContext.current
    var expandedBonuses by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.panel,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.domain,
                    color = colors.fg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://${row.domain}/"))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                ) {
                    Text("Open", color = colors.cyan, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = FormatUtil.money(row.cash),
                    color = colors.live,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                val limits = buildString {
                    append(row.cashWalletName)
                    if (row.cashMinWithdrawal != null) append(" · Min ${FormatUtil.money(row.cashMinWithdrawal)}")
                    if (row.cashMaxWithdrawal != null) append(" · Max ${FormatUtil.money(row.cashMaxWithdrawal)}")
                    else append(" · No max shown")
                }
                Text(
                    text = limits,
                    color = colors.muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            // Bonus Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BonusGridItem(label = "Commission", bonus = row.commission, modifier = Modifier.weight(1f))
                BonusGridItem(label = "Share", bonus = row.share, modifier = Modifier.weight(1f))
                BonusGridItem(label = "Referral", bonus = row.referral, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BonusGridItem(label = "Welcome", bonus = row.newMember, modifier = Modifier.weight(1f))
                BonusGridItem(label = "Rebate", bonus = row.rebate, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.weight(1f))
            }

            if (row.availableBonuses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedBonuses = !expandedBonuses },
                    shape = RoundedCornerShape(8.dp),
                    color = colors.panel2
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "All bonuses on this account (${row.availableBonuses.size})",
                                color = colors.fg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = if (expandedBonuses) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = colors.muted
                            )
                        }
                        AnimatedVisibility(visible = expandedBonuses) {
                            Column(modifier = Modifier.padding(top = 6.dp)) {
                                row.availableBonuses.forEach { bonus ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = bonus.name + if (bonus.rollover != null) " · ${bonus.rollover.toInt()}×" else "",
                                            color = colors.muted,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = bonus.amountDisplay,
                                            color = colors.cyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BonusGridItem(label: String, bonus: BonusView?, modifier: Modifier = Modifier) {
    val colors = LocalVaultColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = colors.panel2
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                color = colors.faint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (bonus != null) {
                Text(
                    text = "Yes (${bonus.amountDisplay})",
                    color = colors.live,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "No",
                    color = colors.muted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun StatsBar(
    siteCount: Int,
    visitedCount: Int,
    myCount: Int,
    modifier: Modifier = Modifier
) {
    val colors = LocalVaultColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        Text(
            text = "Sites $siteCount",
            color = colors.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Visited $visitedCount",
            color = colors.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "My accounts $myCount",
            color = colors.cyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HowAccountsWorkBanner(modifier: Modifier = Modifier) {
    val colors = LocalVaultColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.panel,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "How your accounts work",
                color = colors.fg,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Bookmark a site, then open the fetcher and sign in with the phone number and password you already use. Only sites that accept that login are kept. Nothing here is paid — no fetch packs and no rented cards.",
                color = colors.muted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun StorySection(modifier: Modifier = Modifier) {
    val colors = LocalVaultColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "One place for the sites you actually use",
            color = colors.fg,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val items = listOf(
            "The list" to "New, best, unverified, share, and the long tail stay grouped. Search, hide sites you have already opened, and bookmark only the ones that are yours.",
            "The fetch" to "The same phone and password is tried on the wallet login these sites share. A hit keeps the site. A rejection does not.",
            "What you see" to "Cash, commission, share, referral, welcome, and rebate — plus the rest of the bonuses sitting on the account. Unlimited checks. No credits to buy."
        )

        items.forEach { (title, desc) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = colors.panel,
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(title, color = colors.cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(desc, color = colors.muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
fun ResponsibleGamblingFooter(modifier: Modifier = Modifier) {
    val colors = LocalVaultColors.current
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.bg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = colors.dead.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, colors.dead)
                ) {
                    Text(
                        "18+",
                        color = colors.dead,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Gamble responsibly.",
                    color = colors.fg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Need help? Gambling Help Online — 1800 858 858 · BetStop",
                color = colors.cyan,
                fontSize = 11.sp,
                modifier = Modifier.clickable {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gamblinghelponline.org.au/"))
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Pokies Vault checks sites you already use. It does not take bets, sell fetches, or rent listings. Passwords stay on this device.",
                color = colors.faint,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun AddDomainDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> String?
) {
    val colors = LocalVaultColors.current
    var domainInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a domain", color = colors.fg, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "It is saved as one of your accounts and can be fetched with the same phone and password.",
                    color = colors.muted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = {
                        domainInput = it
                        errorMessage = null
                    },
                    placeholder = { Text("e.g. 9au.com", color = colors.faint) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.fg,
                        unfocusedTextColor = colors.fg,
                        focusedBorderColor = colors.cyan,
                        unfocusedBorderColor = colors.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("domain_input")
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(errorMessage!!, color = colors.dead, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val err = onAdd(domainInput)
                    if (err != null) errorMessage = err
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.cyan),
                modifier = Modifier.testTag("save_domain_button")
            ) {
                Text("Save site", color = colors.ink, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.muted)
            }
        },
        containerColor = colors.panel,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun SortFilterDialog(
    currentSort: SortMode,
    currentBonus: BonusMode,
    mineOnly: Boolean,
    onSortSelected: (SortMode) -> Unit,
    onBonusSelected: (BonusMode) -> Unit,
    onMineOnlyToggled: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalVaultColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort and filter", color = colors.fg, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Sort order", color = colors.cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                SortMode.values().forEach { mode ->
                    val selected = currentSort == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSortSelected(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSortSelected(mode) },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.cyan)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(mode.label, color = if (selected) colors.cyan else colors.fg, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Show on cards", color = colors.cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                BonusMode.values().forEach { mode ->
                    val selected = currentBonus == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBonusSelected(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onBonusSelected(mode) },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.cyan)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(mode.label, color = if (selected) colors.cyan else colors.fg, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMineOnlyToggled() }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = mineOnly,
                        onCheckedChange = { onMineOnlyToggled() },
                        colors = CheckboxDefaults.colors(checkedColor = colors.cyan)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("My accounts only", color = colors.fg, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = colors.cyan)
            ) {
                Text("Done", color = colors.ink, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = colors.panel,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun ConfirmScanDialog(
    totalCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = LocalVaultColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan every listed site?", color = colors.fg, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "This tries your password on $totalCount domains and keeps only the ones that sign in. It can take several minutes. You can stop it at any time.",
                color = colors.muted,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.cyan),
                modifier = Modifier.testTag("confirm_scan_button")
            ) {
                Text("Scan my login", color = colors.ink, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.muted)
            }
        },
        containerColor = colors.panel,
        shape = RoundedCornerShape(16.dp)
    )
}
