package com.aistudio.pokiesvault.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.pokiesvault.data.catalog.CatalogData
import com.aistudio.pokiesvault.data.model.AccountResult
import com.aistudio.pokiesvault.data.model.EgressIpInfo
import com.aistudio.pokiesvault.data.model.WorkerBatchTelemetry
import com.aistudio.pokiesvault.data.model.WorkerPhase
import com.aistudio.pokiesvault.data.model.WorkerStatus
import com.aistudio.pokiesvault.ui.components.ResponsibleGamblingFooter
import com.aistudio.pokiesvault.ui.components.ResultCard
import com.aistudio.pokiesvault.ui.theme.*
import com.aistudio.pokiesvault.ui.viewmodel.VaultUiState
import com.aistudio.pokiesvault.ui.viewmodel.VaultViewModel
import com.aistudio.pokiesvault.util.DomainUtil
import com.aistudio.pokiesvault.util.FormatUtil
import java.util.Locale

@Composable
fun FetcherScreen(
    viewModel: VaultViewModel,
    uiState: VaultUiState,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVaultColors.current
    val vState = uiState.vaultState
    var showPassword by remember { mutableStateOf(false) }
    var showAdvancedFallbacks by remember { mutableStateOf(false) }
    var singleDomainInput by remember { mutableStateOf("") }

    var phoneInput by remember(vState.phone) { mutableStateOf(vState.phone) }
    var passwordInput by remember(vState.password) { mutableStateOf(vState.password) }

    val rows = uiState.currentFetchRows
    val validAccounts = remember(rows) {
        rows.filterIsInstance<AccountResult.Ok>().filter { it.hasData }
    }
    val emptyAccounts = remember(rows) {
        rows.filterIsInstance<AccountResult.Ok>().filter { !it.hasData }
    }
    val rejectedAccounts = remember(rows) {
        rows.filterIsInstance<AccountResult.Err>()
    }
    val totalCash = remember(validAccounts) {
        validAccounts.sumOf { it.cash }
    }

    val mineTargets = remember(vState.mine, vState.visited) {
        if (vState.mine.isNotEmpty()) {
            vState.mine.toList()
        } else {
            CatalogData.SITES.filter { vState.visited.contains(it.domain) }.map { it.domain }
        }
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
                modifier = Modifier.testTag("back_to_list_btn")
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = colors.cyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back to the list", color = colors.cyan, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Fetch bonus data",
                color = colors.fg,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Uses the same phone and password across sites that share this login. It signs in, reads your wallet and bonuses, and drops everything else. No fetch limit.",
                color = colors.muted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Outgoing Egress IP & Mask Box
            OutgoingIpTelemetryCard(
                info = uiState.egressIpInfo,
                merchantCacheCount = uiState.merchantCacheCount,
                onRefresh = { viewModel.refreshEgressIp() },
                onClearMerchantCache = { viewModel.clearMerchantCache() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Credentials Panel
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colors.panel,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Phone Number
                    Text("Phone number", color = colors.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it
                            viewModel.updateCredentials(it, passwordInput)
                        },
                        placeholder = { Text("04…", color = colors.faint) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
                            .testTag("phone_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Password
                    Text("Password", color = colors.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            viewModel.updateCredentials(phoneInput, it)
                        },
                        placeholder = { Text("Enter account password", color = colors.faint) },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                    tint = colors.muted
                                )
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
                            .testTag("password_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showAdvancedFallbacks = !showAdvancedFallbacks },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (showAdvancedFallbacks) "Hide fallback logins" else "Fallback logins",
                            color = colors.cyan,
                            fontSize = 12.sp
                        )
                    }

                    AnimatedVisibility(visible = showAdvancedFallbacks) {
                        Column {
                            vState.fallbacks.forEachIndexed { index, fb ->
                                var fbPhone by remember(fb.phone) { mutableStateOf(fb.phone) }
                                var fbPass by remember(fb.password) { mutableStateOf(fb.password) }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Fallback ${index + 2} phone", color = colors.faint, fontSize = 11.sp)
                                OutlinedTextField(
                                    value = fbPhone,
                                    onValueChange = {
                                        fbPhone = it
                                        viewModel.updateFallback(index, it, fbPass)
                                    },
                                    placeholder = { Text("04…", color = colors.faint) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = colors.fg,
                                        unfocusedTextColor = colors.fg,
                                        focusedBorderColor = colors.cyan,
                                        unfocusedBorderColor = colors.border
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Fallback ${index + 2} password", color = colors.faint, fontSize = 11.sp)
                                OutlinedTextField(
                                    value = fbPass,
                                    onValueChange = {
                                        fbPass = it
                                        viewModel.updateFallback(index, fbPhone, it)
                                    },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = colors.fg,
                                        unfocusedTextColor = colors.fg,
                                        focusedBorderColor = colors.cyan,
                                        unfocusedBorderColor = colors.border
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Saved only on this device. The server uses them to sign in and does not keep them.",
                        color = colors.faint,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startFetch(mineTargets) },
                            enabled = !uiState.isFetching,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.cyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("fetch_my_accounts_btn")
                        ) {
                            if (uiState.isFetching) {
                                CircularProgressIndicator(
                                    color = colors.ink,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                "Fetch (${mineTargets.size})",
                                color = colors.ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.stopFetch() },
                            enabled = uiState.isFetching,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.panel2),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(0.8f)
                                .testTag("stop_fetch_btn")
                        ) {
                            Text("Stop", color = if (uiState.isFetching) colors.dead else colors.muted, fontSize = 13.sp)
                        }

                        Button(
                            onClick = { viewModel.viewLastFetch() },
                            enabled = !uiState.isFetching && vState.lastFetch != null,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.panel2),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("view_last_fetch_btn")
                        ) {
                            Text("Last", color = colors.fg, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Check Single Domain
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = singleDomainInput,
                            onValueChange = { singleDomainInput = it },
                            placeholder = { Text("Check one site, e.g. 9au.com", color = colors.faint, fontSize = 12.sp) },
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
                                .weight(1f)
                                .height(46.dp)
                                .testTag("single_domain_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val d = DomainUtil.normalizeDomain(singleDomainInput)
                                if (DomainUtil.isPublicDomain(d)) {
                                    viewModel.startFetch(listOf(d))
                                }
                            },
                            enabled = !uiState.isFetching && singleDomainInput.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.cyan),
                            modifier = Modifier.testTag("check_single_btn")
                        ) {
                            Text("Check", color = colors.ink, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = { viewModel.setConfirmScanOpen(true) },
                        enabled = !uiState.isFetching,
                        modifier = Modifier.fillMaxWidth().testTag("scan_all_btn")
                    ) {
                        Text("Scan the full list to find my accounts", color = colors.violet, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.fetchStatusMessage,
                        color = colors.cyan,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Live Worker Statistical Box (always visible)
            WorkerStatsTelemetryCard(
                telemetry = uiState.workerTelemetry,
                isFetching = uiState.isFetching
            )

            // Diffs
            if (uiState.fetchDiffs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.panel,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Changes since the previous fetch",
                            color = colors.live,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        uiState.fetchDiffs.forEach { diff ->
                            Text("• $diff", color = colors.fg, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 1: Valid Accounts
            Text(
                text = "Your accounts · ${validAccounts.size}" + if (validAccounts.isNotEmpty()) " · ${FormatUtil.money(totalCash)} cash" else "",
                color = colors.cyan,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (validAccounts.isEmpty()) {
            item {
                Text("Nothing with a balance or bonus yet.", color = colors.muted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(14.dp))
            }
        } else {
            items(validAccounts, key = { it.domain }) { row ->
                ResultCard(row = row)
            }
        }

        // Section 2: Empty Accounts
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Signed in, nothing waiting · ${emptyAccounts.size}",
                color = colors.muted,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (emptyAccounts.isEmpty()) {
            item {
                Text("None.", color = colors.faint, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(14.dp))
            }
        } else {
            items(emptyAccounts, key = { it.domain }) { row ->
                ResultCard(row = row)
            }
        }

        // Section 3: Rejected Accounts
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Not your account or unreachable · ${rejectedAccounts.size}",
                color = colors.dead,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (rejectedAccounts.isNotEmpty()) {
                Button(
                    onClick = { viewModel.setShowRejectedSites(!uiState.showRejectedSites) },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.panel2),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (uiState.showRejectedSites) "Hide rejected sites" else "Show rejected sites",
                        color = colors.fg,
                        fontSize = 12.sp
                    )
                }
            } else {
                Text("Rejected sites are not saved.", color = colors.faint, fontSize = 12.sp)
            }
        }

        if (uiState.showRejectedSites) {
            items(rejectedAccounts, key = { it.domain }) { err ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.panel2
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(err.domain, color = colors.fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(err.message, color = colors.muted, fontSize = 11.sp)
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
fun OutgoingIpTelemetryCard(
    info: EgressIpInfo,
    merchantCacheCount: Int,
    onRefresh: () -> Unit,
    onClearMerchantCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVaultColors.current
    val statusColor = when {
        info.isLoading -> colors.cyan
        info.error != null -> colors.dead
        info.isCloudOrHosting -> colors.slow
        else -> colors.live
    }

    val badgeLabel = when {
        info.isLoading -> "RESOLVING…"
        info.error != null -> "OFFLINE"
        info.isCloudOrHosting -> "CLOUD / VPN"
        else -> "CARRIER / LOCAL"
    }

    val locationSuffix = buildString {
        if (info.city.isNotBlank()) append(info.city)
        if (info.countryCode.isNotBlank()) {
            if (isNotEmpty()) append(", ")
            append(info.countryCode)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("outgoing_ip_telemetry_card"),
        shape = RoundedCornerShape(14.dp),
        color = colors.panel,
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Top Row: Public Outgoing IP + Ping (ms) + One-Tap Refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (info.isLoading) "Resolving egress IP…" else info.ip,
                                color = colors.fg,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                            if (!info.isLoading && info.pingMs > 0L) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = colors.panel2,
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border)
                                ) {
                                    Text(
                                        text = "${info.pingMs} ms",
                                        color = colors.cyan,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = statusColor.copy(alpha = 0.16f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, statusColor.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = badgeLabel,
                                    color = statusColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        // ISP, ASN & Geolocation line
                        val ispGeoText = if (info.isLoading) {
                            "Querying ipwho.is / ipapi.co / ipify.org…"
                        } else {
                            buildString {
                                append(info.isp.ifBlank { "Direct Network" })
                                if (info.asn.isNotBlank()) {
                                    append(" · ")
                                    append(info.asn)
                                }
                                if (locationSuffix.isNotBlank()) {
                                    append(" · ")
                                    append(locationSuffix)
                                }
                            }
                        }
                        Text(
                            text = ispGeoText,
                            color = colors.muted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !info.isLoading,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("refresh_egress_ip_btn")
                ) {
                    if (info.isLoading) {
                        CircularProgressIndicator(
                            color = colors.cyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(15.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh public IP",
                            tint = colors.cyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Active Mask & Merchant ID Cache Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Active Browser Mask Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.panel2,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = colors.cyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Mask: ${info.activeMask}",
                            color = colors.fg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Tappable Merchant ID Cache Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.panel2,
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        if (merchantCacheCount > 0) colors.cyan.copy(alpha = 0.5f) else colors.border
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClearMerchantCache() }
                        .testTag("clear_merchant_cache_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Clear cached Merchant IDs",
                            tint = if (merchantCacheCount > 0) colors.cyan else colors.muted,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "IDs: $merchantCacheCount · Clear",
                            color = if (merchantCacheCount > 0) colors.cyan else colors.muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkerStatsTelemetryCard(
    telemetry: WorkerBatchTelemetry,
    isFetching: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalVaultColors.current
    val rawProgress = if (telemetry.totalCount > 0) {
        (telemetry.doneCount.toFloat() / telemetry.totalCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 180),
        label = "batch_progress"
    )

    val elapsedSecondsText = String.format(Locale.US, "%.1fs", telemetry.elapsedMs / 1000.0)
    val speedText = String.format(Locale.US, "%.1f/s", telemetry.speedHostsPerSec)
    val avgPingText = if (telemetry.avgPingMs > 0L) "${telemetry.avgPingMs} ms" else "— ms"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("worker_stats_telemetry_card"),
        shape = RoundedCornerShape(16.dp),
        color = colors.panel,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isFetching) colors.cyan.copy(alpha = 0.5f) else colors.border
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header + Live 100ms Batch Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isFetching) colors.cyan else colors.muted)
                    )
                    Text(
                        text = if (isFetching) "LIVE WORKER TELEMETRY (3 WORKERS)" else "WORKER TELEMETRY (3 WORKERS)",
                        color = if (isFetching) colors.cyan else colors.fg,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.panel2,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border)
                ) {
                    Text(
                        text = "⏱ $elapsedSecondsText",
                        color = if (isFetching) colors.cyan else colors.muted,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Animated Batch Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                color = colors.cyan,
                trackColor = colors.panel2,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Key Stat Tiles: Done, Queue, Avg Ping, Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TelemetryStatTile(
                    label = "Done",
                    value = "${telemetry.doneCount}/${telemetry.totalCount}",
                    valueColor = colors.fg,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "Queue",
                    value = "${telemetry.queueCount}",
                    valueColor = if (telemetry.queueCount > 0) colors.cyan else colors.muted,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "Avg Ping",
                    value = avgPingText,
                    valueColor = colors.live,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "Speed",
                    value = speedText,
                    valueColor = colors.violet,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Per-Worker Live Status Rows
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                telemetry.workers.forEach { worker ->
                    WorkerLiveRow(worker = worker)
                }
            }
        }
    }
}

@Composable
private fun TelemetryStatTile(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalVaultColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = colors.panel2,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label.uppercase(Locale.US),
                color = colors.faint,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = valueColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WorkerLiveRow(
    worker: WorkerStatus
) {
    val colors = LocalVaultColors.current
    val (phaseLabel, phaseColor) = when (worker.phase) {
        WorkerPhase.IDLE -> "IDLE" to colors.muted
        WorkerPhase.RESOLVING -> "RESOLVE" to colors.cyan
        WorkerPhase.AUTHENTICATING -> "AUTH" to colors.violet
        WorkerPhase.SYNCING -> "SYNC" to colors.live
        WorkerPhase.BACKOFF -> "BACKOFF" to colors.slow
        WorkerPhase.DONE -> "DONE" to colors.live
    }

    val hostText = worker.currentHost.ifBlank { "Standing by" }
    val timerText = if (worker.phase == WorkerPhase.IDLE && worker.hostElapsedMs == 0L) {
        if (worker.lastHostPingMs > 0L) "${worker.lastHostPingMs}ms" else "0.0s"
    } else {
        String.format(Locale.US, "%.1fs", worker.hostElapsedMs / 1000.0)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("worker_row_${worker.workerId}"),
        shape = RoundedCornerShape(8.dp),
        color = colors.panel2,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "W#${worker.workerId}",
                    color = colors.muted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = phaseColor.copy(alpha = 0.16f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, phaseColor.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = phaseLabel,
                        color = phaseColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Text(
                    text = hostText,
                    color = if (worker.currentHost.isNotBlank()) colors.fg else colors.faint,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = timerText,
                color = if (worker.phase != WorkerPhase.IDLE && worker.phase != WorkerPhase.DONE) colors.cyan else colors.muted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}


