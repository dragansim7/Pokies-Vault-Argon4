package com.aistudio.pokiesvault.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.pokiesvault.data.model.SiteStatus
import com.aistudio.pokiesvault.ui.components.ResponsibleGamblingFooter
import com.aistudio.pokiesvault.ui.theme.*
import com.aistudio.pokiesvault.ui.viewmodel.VaultUiState
import com.aistudio.pokiesvault.ui.viewmodel.VaultViewModel

@Composable
fun DomainIntelScreen(
    viewModel: VaultViewModel,
    uiState: VaultUiState,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVaultColors.current
    val context = LocalContext.current
    var addInput by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                var displayName = ""
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIndex) ?: ""
                    }
                }
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""
                viewModel.addBulkDomainsIntel(content, displayName)
            } catch (e: Exception) {
                viewModel.addBulkDomainsIntel("", "")
            }
        }
    }

    val allSites = remember(uiState) { viewModel.getFilteredCatalogSites() }
    val allDomains = remember(allSites) { allSites.map { it.domain } }

    val q = uiState.intelFilter.trim().lowercase()
    val shownDomains = remember(allDomains, q) {
        if (q.isEmpty()) allDomains else allDomains.filter { it.contains(q) }
    }

    val checks = uiState.intelChecks
    val counts = remember(allDomains, checks) {
        var up = 0
        var slow = 0
        var redirect = 0
        var down = 0
        for (d in allDomains) {
            when (checks[d]?.status) {
                SiteStatus.LIVE -> up++
                SiteStatus.SLOW -> slow++
                SiteStatus.REDIRECT -> redirect++
                SiteStatus.DEAD -> down++
                else -> {}
            }
        }
        Counts(allDomains.size, up, slow, redirect, down)
    }

    val allSelected = shownDomains.isNotEmpty() && shownDomains.all { uiState.selectedIntelDomains.contains(it) }

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
                modifier = Modifier.testTag("intel_back_to_list")
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = colors.cyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back to the list", color = colors.cyan, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Domain Intel",
                style = TextStyle(
                    brush = colors.gradient,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "This is the same list as the main page and the fetcher. After you add or remove a domain, that exact list is what comes back when you reopen the app.",
                color = colors.muted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Status Counters
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colors.panel,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${counts.total} total", color = colors.fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${counts.up} up", color = colors.live, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${counts.slow} slow", color = colors.slow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${counts.redirect} redirect", color = colors.violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${counts.down} down", color = colors.dead, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Control Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colors.panel,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Add single domain row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = addInput,
                            onValueChange = { addInput = it },
                            placeholder = { Text("yeah96.com or https://yeah96.com", color = colors.faint, fontSize = 12.sp) },
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
                                .testTag("intel_domain_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val err = viewModel.addSingleDomainIntel(addInput)
                                if (err == null) addInput = ""
                            },
                            enabled = addInput.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.cyan),
                            modifier = Modifier.testTag("intel_add_domain_btn")
                        ) {
                            Text("Add", color = colors.ink, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Filter row
                    OutlinedTextField(
                        value = uiState.intelFilter,
                        onValueChange = { viewModel.setIntelFilter(it) },
                        placeholder = { Text("Find a domain", color = colors.faint, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.muted) },
                        trailingIcon = {
                            if (uiState.intelFilter.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setIntelFilter("") }) {
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
                            .testTag("intel_filter_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                filePickerLauncher.launch(
                                    arrayOf("text/*", "text/plain", "text/csv", "application/octet-stream", "*/*")
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.panel2),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.1f).testTag("intel_bulk_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Import file",
                                tint = colors.cyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import file", color = colors.fg, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { viewModel.runIntelCheck() },
                            enabled = !uiState.isIntelChecking && allDomains.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.cyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.3f).testTag("intel_check_status_btn")
                        ) {
                            if (uiState.isIntelChecking) {
                                CircularProgressIndicator(color = colors.ink, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "${uiState.intelProgressDone}/${uiState.intelProgressTotal}",
                                    color = colors.ink,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                val label = if (uiState.selectedIntelDomains.isNotEmpty()) {
                                    "Check ${uiState.selectedIntelDomains.size}"
                                } else {
                                    "Check status"
                                }
                                Text(label, color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        if (uiState.isIntelChecking) {
                            Button(
                                onClick = { viewModel.stopIntelCheck() },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.panel2),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(0.8f).testTag("intel_stop_btn")
                            ) {
                                Text("Stop", color = colors.dead, fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.removeSelectedIntelDomains() },
                                enabled = uiState.selectedIntelDomains.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.panel2),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("intel_remove_btn")
                            ) {
                                Text(
                                    "Remove" + if (uiState.selectedIntelDomains.isNotEmpty()) " (${uiState.selectedIntelDomains.size})" else "",
                                    color = if (uiState.selectedIntelDomains.isNotEmpty()) colors.dead else colors.muted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap Import file to select a .txt or .csv domain list from device storage.",
                        color = colors.faint,
                        fontSize = 11.sp
                    )

                    if (uiState.intelError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(uiState.intelError, color = colors.dead, fontSize = 12.sp)
                    }
                    if (uiState.intelNote.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(uiState.intelNote, color = colors.cyan, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Select all row
            if (shownDomains.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleAllIntelDomains(shownDomains) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { viewModel.toggleAllIntelDomains(shownDomains) },
                        colors = CheckboxDefaults.colors(checkedColor = colors.cyan)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (uiState.intelFilter.isNotBlank()) "Select ${shownDomains.size} shown" else "Select all",
                        color = colors.fg,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (shownDomains.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.panel
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (allDomains.isNotEmpty()) "No domains match that search." else "No domains yet. Add one above or import a file.",
                            color = colors.muted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(shownDomains, key = { it }) { domain ->
                val isSelected = uiState.selectedIntelDomains.contains(domain)
                val check = checks[domain]
                val status = check?.status ?: SiteStatus.UNKNOWN
                val statusColor = when (status) {
                    SiteStatus.LIVE -> colors.live
                    SiteStatus.SLOW -> colors.slow
                    SiteStatus.REDIRECT -> colors.violet
                    SiteStatus.DEAD -> colors.dead
                    SiteStatus.CHECKING -> colors.muted
                    SiteStatus.UNKNOWN -> colors.faint
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { viewModel.toggleIntelDomain(domain) },
                    shape = RoundedCornerShape(10.dp),
                    color = colors.panel,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) colors.cyan.copy(alpha = 0.5f) else colors.border
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleIntelDomain(domain) },
                                colors = CheckboxDefaults.colors(checkedColor = colors.cyan),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = domain,
                                color = colors.fg,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val label = when (status) {
                                SiteStatus.LIVE -> "Up"
                                SiteStatus.SLOW -> "Slow"
                                SiteStatus.REDIRECT -> "Redirect"
                                SiteStatus.DEAD -> "Down"
                                SiteStatus.CHECKING -> "Checking"
                                SiteStatus.UNKNOWN -> "Not checked"
                            }
                            val latencyText = if (check != null && check.ms > 0L) " · ${check.ms} ms" else ""
                            Text(
                                text = "$label$latencyText",
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
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

private data class Counts(val total: Int, val up: Int, val slow: Int, val redirect: Int, val down: Int)

