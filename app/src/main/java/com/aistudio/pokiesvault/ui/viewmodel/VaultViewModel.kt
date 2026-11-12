package com.aistudio.pokiesvault.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.pokiesvault.data.model.*
import com.aistudio.pokiesvault.data.network.CasinoApiService
import com.aistudio.pokiesvault.data.repository.VaultRepository
import com.aistudio.pokiesvault.data.repository.VaultState
import com.aistudio.pokiesvault.util.DomainUtil
import com.aistudio.pokiesvault.util.FormatUtil
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class VaultUiState(
    val vaultState: VaultState = VaultState(),
    val searchQuery: String = "",
    val siteStatuses: Map<String, SiteStatus> = emptyMap(),
    val isFetching: Boolean = false,
    val fetchProgressDone: Int = 0,
    val fetchProgressTotal: Int = 0,
    val fetchStatusMessage: String = "Enter the phone number and password you use on these sites.",
    val currentFetchRows: List<AccountResult> = emptyList(),
    val fetchDiffs: List<String> = emptyList(),
    val isAddDomainOpen: Boolean = false,
    val isSortFilterOpen: Boolean = false,
    val isConfirmScanOpen: Boolean = false,
    val showRejectedSites: Boolean = false,
    val cloudSyncError: String? = null,
    val egressIpInfo: EgressIpInfo = EgressIpInfo(),
    val merchantCacheCount: Int = 0,
    val workerTelemetry: WorkerBatchTelemetry = WorkerBatchTelemetry(),

    // Domain Intel State
    val intelChecks: Map<String, DomainCheck> = emptyMap(),
    val selectedIntelDomains: Set<String> = emptySet(),
    val intelFilter: String = "",
    val isIntelChecking: Boolean = false,
    val intelProgressDone: Int = 0,
    val intelProgressTotal: Int = 0,
    val intelNote: String = "",
    val intelError: String = "",

    // Bonus Finder State
    val bonusQuery: String = "",
    val bonusFilters: BonusFinderFilters = BonusFinderFilters(),
    val bonusCache: Map<String, List<PublicBonus>> = emptyMap(),
    val bonusPage: Int = 0,
    val isBonusLoading: Boolean = false,
    val bonusProgressDone: Int = 0,
    val bonusProgressTotal: Int = 0,
    val bonusStatusMessage: String = "Same domains as the main list. Load bonuses to fill the cards."
)

class VaultViewModel(
    private val repository: VaultRepository,
    private val currentUserId: String,
    private val isLocalOnly: Boolean = false
) : ViewModel() {
    private val apiService = CasinoApiService()

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null
    private var intelJob: Job? = null
    private var bonusJob: Job? = null
    private val statusCheckJobs = mutableMapOf<String, Job>()
    private val statusProbeSemaphore = kotlinx.coroutines.sync.Semaphore(2)

    init {
        viewModelScope.launch {
            val initialChecks = repository.loadIntelChecks()
            val initialBonuses = repository.loadBonusCache()
            val cachedStatuses = initialChecks.mapValues { it.value.status }
            _uiState.update {
                it.copy(
                    intelChecks = initialChecks,
                    bonusCache = initialBonuses,
                    siteStatuses = cachedStatuses
                )
            }

            // Load bulk status feed in a single call to avoid per-card status probe storms
            launch {
                val bulkStatuses = apiService.fetchAllLiveStatuses()
                if (bulkStatuses.isNotEmpty()) {
                    _uiState.update { current ->
                        current.copy(siteStatuses = current.siteStatuses + bulkStatuses)
                    }
                }
            }

            // Load active egress public IP & ISP info seen by target domains
            launch {
                val info = apiService.fetchEgressIpInfo()
                _uiState.update { it.copy(egressIpInfo = info) }
            }

            launch {
                repository.state.collect { vState ->
                    _uiState.update { current ->
                        current.copy(
                            vaultState = vState,
                            currentFetchRows = if (current.currentFetchRows.isEmpty() && vState.lastFetch != null) {
                                vState.lastFetch.rows
                            } else {
                                current.currentFetchRows
                            }
                        )
                    }
                }
            }

            if (!isLocalOnly && currentUserId != "local_device") {
                // Merge local device data into cloud vault on sign-in, then start live listener
                repository.mergeLocalWithCloudOnSignIn()

                repository.observeUserVault(currentUserId)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w("VaultViewModel", "Error observing user vault", rootError)
                        _uiState.update {
                            it.copy(cloudSyncError = rootError.message ?: "Cloud vault sync error")
                        }
                    }
                    .collect { vState ->
                        _uiState.update { current ->
                            current.copy(
                                vaultState = vState,
                                cloudSyncError = null,
                                currentFetchRows = if (current.currentFetchRows.isEmpty() && vState.lastFetch != null) {
                                    vState.lastFetch.rows
                                } else {
                                    current.currentFetchRows
                                }
                            )
                        }
                    }
            }
        }
    }

    fun refreshEgressIp() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(egressIpInfo = it.egressIpInfo.copy(isLoading = true, error = null))
            }
            val info = apiService.fetchEgressIpInfo()
            _uiState.update {
                it.copy(
                    egressIpInfo = info,
                    merchantCacheCount = apiService.getMerchantCacheCount()
                )
            }
        }
    }

    fun clearMerchantCache() {
        apiService.clearMerchantCache()
        _uiState.update { it.copy(merchantCacheCount = 0) }
    }

    // Theme
    fun setTheme(themeId: String) {
        repository.setThemeId(themeId)
    }

    // Search and Filters
    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleNotVisited() {
        repository.setNotVisited(!_uiState.value.vaultState.notVisited)
    }

    fun toggleWallets() {
        repository.setWallets(!_uiState.value.vaultState.wallets)
    }

    fun toggleMineOnly() {
        repository.setMineOnly(!_uiState.value.vaultState.mineOnly)
    }

    fun cycleViewMode() {
        val next = when (_uiState.value.vaultState.viewMode) {
            ViewMode.GROUPS -> ViewMode.ONE
            ViewMode.ONE -> ViewMode.TWO
            ViewMode.TWO -> ViewMode.GROUPS
        }
        repository.setViewMode(next)
    }

    fun setSortMode(mode: SortMode) {
        repository.setSortMode(mode)
    }

    fun setBonusMode(mode: BonusMode) {
        repository.setBonusMode(mode)
    }

    fun toggleVisited(domain: String) {
        repository.toggleVisited(domain)
    }

    fun markVisited(domain: String) {
        repository.markVisited(domain)
    }

    fun toggleMine(domain: String) {
        repository.toggleMine(domain)
    }

    fun setAddDomainOpen(open: Boolean) {
        _uiState.update { it.copy(isAddDomainOpen = open) }
    }

    fun setSortFilterOpen(open: Boolean) {
        _uiState.update { it.copy(isSortFilterOpen = open) }
    }

    fun setConfirmScanOpen(open: Boolean) {
        _uiState.update { it.copy(isConfirmScanOpen = open) }
    }

    fun setShowRejectedSites(show: Boolean) {
        _uiState.update { it.copy(showRejectedSites = show) }
    }

    fun addCustomDomain(raw: String): String? {
        val domain = DomainUtil.normalizeDomain(raw)
        if (!DomainUtil.isPublicDomain(domain)) return "Enter a valid domain like 9au.com"
        val error = repository.addCustomDomain(domain)
        if (error == null) {
            setAddDomainOpen(false)
        }
        return error
    }

    fun updateCredentials(phone: String, pass: String) {
        repository.updateCredentials(phone, pass)
    }

    fun updateFallback(index: Int, phone: String, pass: String) {
        repository.updateFallback(index, phone, pass)
    }

    fun clearAll() {
        repository.clearAll()
        _uiState.update {
            it.copy(
                siteStatuses = emptyMap(),
                currentFetchRows = emptyList(),
                fetchDiffs = emptyList(),
                intelChecks = emptyMap(),
                selectedIntelDomains = emptySet(),
                bonusCache = emptyMap()
            )
        }
    }

    fun checkStatusIfNeeded(domain: String) {
        if (_uiState.value.siteStatuses.containsKey(domain) || statusCheckJobs.containsKey(domain)) return

        _uiState.update { it.copy(siteStatuses = it.siteStatuses + (domain to SiteStatus.CHECKING)) }
        val job = viewModelScope.launch {
            statusProbeSemaphore.withPermit {
                val status = apiService.checkSiteStatus(domain)
                _uiState.update { it.copy(siteStatuses = it.siteStatuses + (domain to status)) }
            }
        }
        statusCheckJobs[domain] = job
    }

    // Account Fetching
    fun startFetch(domainsToFetch: List<String>) {
        val vState = _uiState.value.vaultState
        val creds = mutableListOf(vState.phone.trim() to vState.password)
        for (f in vState.fallbacks) {
            if (f.phone.isNotBlank() && f.password.isNotBlank()) {
                creds.add(f.phone.trim() to f.password)
            }
        }
        val validCreds = creds.filter { it.first.isNotBlank() && it.second.isNotBlank() }.distinct()
        if (validCreds.isEmpty()) {
            _uiState.update { it.copy(fetchStatusMessage = "Enter your phone number and password before fetching.") }
            return
        }

        val uniqueDomains = domainsToFetch.distinct().filter { DomainUtil.isPublicDomain(it) }
        if (uniqueDomains.isEmpty()) {
            _uiState.update { it.copy(fetchStatusMessage = "No sites to check. Save accounts from the list, add a domain, or scan.") }
            return
        }

        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val batchStartMs = System.currentTimeMillis()
            val workerStates = java.util.concurrent.ConcurrentHashMap<Int, WorkerStatus>()
            for (idx in 1..3) {
                workerStates[idx] = WorkerStatus(workerId = idx, phase = WorkerPhase.IDLE, currentHost = "—")
            }

            _uiState.update {
                it.copy(
                    isFetching = true,
                    fetchProgressDone = 0,
                    fetchProgressTotal = uniqueDomains.size,
                    fetchStatusMessage = if (uniqueDomains.size == 1) "Checking ${uniqueDomains[0]}…" else "Checking ${uniqueDomains.size} sites…",
                    currentFetchRows = emptyList(),
                    fetchDiffs = emptyList(),
                    showRejectedSites = false,
                    workerTelemetry = WorkerBatchTelemetry(
                        doneCount = 0,
                        totalCount = uniqueDomains.size,
                        queueCount = uniqueDomains.size,
                        avgPingMs = 0L,
                        speedHostsPerSec = 0.0,
                        elapsedMs = 0L,
                        workers = (1..3).map { id -> workerStates[id]!! }
                    )
                )
            }

            val previousMap = mutableMapOf<String, AccountResult.Ok>()
            vState.lastFetch?.rows?.filterIsInstance<AccountResult.Ok>()?.forEach {
                previousMap[it.domain] = it
            }

            val collected = mutableListOf<AccountResult>()
            val hostPings = mutableListOf<Long>()
            val mutex = java.lang.Object()
            val queue = java.util.concurrent.ConcurrentLinkedQueue(uniqueDomains)

            fun buildTelemetrySnapshot(nowMs: Long): WorkerBatchTelemetry {
                val elapsed = (nowMs - batchStartMs).coerceAtLeast(1L)
                val done: Int
                val avgPing: Long
                synchronized(mutex) {
                    done = collected.size
                    avgPing = if (hostPings.isNotEmpty()) hostPings.sum() / hostPings.size else 0L
                }
                val remaining = (uniqueDomains.size - done).coerceAtLeast(0)
                val speed = if (elapsed >= 100L && done > 0) {
                    (done.toDouble() * 1000.0) / elapsed.toDouble()
                } else {
                    0.0
                }
                val liveWorkers = (1..3).map { id ->
                    val ws = workerStates[id] ?: WorkerStatus(workerId = id)
                    val hostElapsed = if (ws.phase != WorkerPhase.IDLE && ws.phase != WorkerPhase.DONE && ws.hostStartedAtMs > 0L) {
                        (nowMs - ws.hostStartedAtMs).coerceAtLeast(0L)
                    } else {
                        ws.hostElapsedMs
                    }
                    ws.copy(hostElapsedMs = hostElapsed)
                }
                return WorkerBatchTelemetry(
                    doneCount = done,
                    totalCount = uniqueDomains.size,
                    queueCount = remaining,
                    avgPingMs = avgPing,
                    speedHostsPerSec = speed,
                    elapsedMs = elapsed,
                    workers = liveWorkers
                )
            }

            // 100ms live telemetry ticker for smooth batch & per-host timers
            val tickerJob = launch {
                while (isActive) {
                    delay(100L)
                    val now = System.currentTimeMillis()
                    _uiState.update { current ->
                        current.copy(
                            workerTelemetry = buildTelemetrySnapshot(now),
                            merchantCacheCount = apiService.getMerchantCacheCount()
                        )
                    }
                }
            }

            val workers = (1..3).map { workerIdx ->
                launch {
                    // Slight initial worker stagger so all 3 workers do not fire at t=0ms simultaneously
                    delay((workerIdx - 1) * 140L)
                    while (isActive) {
                        val domain = queue.poll() ?: break
                        val hostStartMs = System.currentTimeMillis()
                        workerStates[workerIdx] = WorkerStatus(
                            workerId = workerIdx,
                            phase = WorkerPhase.RESOLVING,
                            currentHost = domain,
                            hostStartedAtMs = hostStartMs,
                            hostElapsedMs = 0L
                        )

                        var result: AccountResult = AccountResult.Err(domain, "other", "No credentials")

                        for ((phone, password) in validCreds) {
                            if (!isActive) break
                            val res = apiService.fetchCasinoAccount(
                                rawDomain = domain,
                                phoneRaw = phone,
                                passwordRaw = password,
                                onPhaseChange = { newPhase ->
                                    val prev = workerStates[workerIdx]
                                    if (prev != null) {
                                        workerStates[workerIdx] = prev.copy(
                                            phase = newPhase,
                                            hostElapsedMs = (System.currentTimeMillis() - hostStartMs).coerceAtLeast(0L)
                                        )
                                    }
                                }
                            )
                            if (res is AccountResult.Ok) {
                                repository.rememberAccount(domain)
                                result = res
                                break
                            } else if (res is AccountResult.Err) {
                                result = res
                                // Only retry fallback credentials when the site explicitly reports invalid_login
                                if (res.code != "invalid_login") break
                            }
                        }

                        val hostDurationMs = (System.currentTimeMillis() - hostStartMs).coerceAtLeast(1L)
                        workerStates[workerIdx] = WorkerStatus(
                            workerId = workerIdx,
                            phase = WorkerPhase.IDLE,
                            currentHost = domain,
                            hostStartedAtMs = 0L,
                            hostElapsedMs = hostDurationMs,
                            lastHostPingMs = hostDurationMs
                        )

                        synchronized(mutex) {
                            collected.add(result)
                            hostPings.add(hostDurationMs)
                            val done = collected.size
                            val sorted = collected.sortedBy { it.domain }
                            val now = System.currentTimeMillis()
                            _uiState.update {
                                it.copy(
                                    fetchProgressDone = done,
                                    currentFetchRows = sorted,
                                    merchantCacheCount = apiService.getMerchantCacheCount(),
                                    workerTelemetry = buildTelemetrySnapshot(now)
                                )
                            }
                        }
                    }

                    val lastWorker = workerStates[workerIdx]
                    workerStates[workerIdx] = WorkerStatus(
                        workerId = workerIdx,
                        phase = WorkerPhase.DONE,
                        currentHost = lastWorker?.currentHost ?: "—",
                        hostStartedAtMs = 0L,
                        hostElapsedMs = lastWorker?.hostElapsedMs ?: 0L,
                        lastHostPingMs = lastWorker?.lastHostPingMs ?: 0L
                    )
                }
            }
            workers.joinAll()
            tickerJob.cancel()

            val finalRows = collected.sortedBy { it.domain }
            repository.saveFetch(finalRows)

            val diffNotes = mutableListOf<String>()
            for (r in finalRows) {
                if (r is AccountResult.Ok) {
                    val prev = previousMap[r.domain]
                    if (prev != null && kotlin.math.abs(prev.cash - r.cash) >= 0.01) {
                        diffNotes.add("${r.domain} balance ${FormatUtil.money(prev.cash)} → ${FormatUtil.money(r.cash)}")
                    }
                }
            }

            val okCount = finalRows.count { it is AccountResult.Ok }
            val storageLabel = if (isLocalOnly || currentUserId == "local_device") {
                "Saved on this device."
            } else {
                "Synced to your cloud vault."
            }
            val endSnapshot = buildTelemetrySnapshot(System.currentTimeMillis()).copy(
                queueCount = 0,
                workers = (1..3).map { id ->
                    val ws = workerStates[id] ?: WorkerStatus(workerId = id)
                    ws.copy(phase = WorkerPhase.DONE)
                }
            )
            _uiState.update {
                it.copy(
                    isFetching = false,
                    fetchDiffs = diffNotes,
                    merchantCacheCount = apiService.getMerchantCacheCount(),
                    workerTelemetry = endSnapshot,
                    fetchStatusMessage = "Done. $okCount of ${finalRows.size} signed in. $storageLabel"
                )
            }
        }
    }

    fun stopFetch() {
        fetchJob?.cancel()
        val partialRows = _uiState.value.currentFetchRows
        if (partialRows.isNotEmpty()) {
            repository.saveFetch(partialRows)
        }
        _uiState.update { current ->
            val resetWorkers = current.workerTelemetry.workers.map { w ->
                w.copy(phase = WorkerPhase.IDLE, hostStartedAtMs = 0L)
            }
            current.copy(
                isFetching = false,
                merchantCacheCount = apiService.getMerchantCacheCount(),
                workerTelemetry = current.workerTelemetry.copy(workers = resetWorkers),
                fetchStatusMessage = "Stopped. ${partialRows.count { r -> r is AccountResult.Ok }} successful sign-ins saved to your vault."
            )
        }
    }

    fun viewLastFetch() {
        val last = _uiState.value.vaultState.lastFetch
        if (last != null) {
            _uiState.update {
                it.copy(
                    currentFetchRows = last.rows,
                    fetchStatusMessage = "Showing the last fetch saved in your vault."
                )
            }
        }
    }

    fun getFilteredCatalogSites(): List<CatalogSite> {
        val state = _uiState.value
        val allSites = repository.getAllSites()
        val q = state.searchQuery.trim().lowercase()
        val vState = state.vaultState
        val fetchMap = state.currentFetchRows.associateBy { it.domain }

        var list = allSites.filter { site ->
            if (vState.mineOnly && !vState.mine.contains(site.domain)) return@filter false
            if (vState.notVisited && vState.visited.contains(site.domain)) return@filter false
            if (q.isNotEmpty() && !site.domain.contains(q)) return@filter false
            true
        }

        list = when (vState.sortMode) {
            SortMode.AZ -> list.sortedBy { it.domain }
            SortMode.ZA -> list.sortedByDescending { it.domain }
            SortMode.NEW -> list.sortedByDescending { it.addedAt }
            SortMode.BALANCE -> list.sortedWith(
                compareByDescending<CatalogSite> { (fetchMap[it.domain] as? AccountResult.Ok)?.cash ?: -1.0 }
                    .thenBy { it.domain }
            )
            SortMode.DEFAULT -> list
        }

        return list
    }

    // ==========================================
    // DOMAIN INTEL OPERATIONS
    // ==========================================

    fun setIntelFilter(query: String) {
        _uiState.update { it.copy(intelFilter = query) }
    }

    fun toggleIntelDomain(domain: String) {
        _uiState.update { state ->
            val set = state.selectedIntelDomains.toMutableSet()
            if (set.contains(domain)) set.remove(domain) else set.add(domain)
            state.copy(selectedIntelDomains = set)
        }
    }

    fun toggleAllIntelDomains(domainsToToggle: List<String>) {
        _uiState.update { state ->
            val set = state.selectedIntelDomains.toMutableSet()
            val allSelected = domainsToToggle.isNotEmpty() && domainsToToggle.all { set.contains(it) }
            if (allSelected) {
                set.removeAll(domainsToToggle.toSet())
            } else {
                set.addAll(domainsToToggle)
            }
            state.copy(selectedIntelDomains = set)
        }
    }

    fun addSingleDomainIntel(raw: String): String? {
        val domain = DomainUtil.normalizeDomain(raw)
        if (!DomainUtil.isPublicDomain(domain)) {
            _uiState.update { it.copy(intelError = "Enter a valid domain like yeah96.com", intelNote = "") }
            return "Enter a valid domain"
        }
        val allSites = repository.getAllSites()
        if (allSites.any { it.domain.equals(domain, ignoreCase = true) }) {
            _uiState.update { it.copy(intelError = "That domain is already in the list.", intelNote = "") }
            return "Domain already in list"
        }
        repository.addCustomDomain(domain)
        _uiState.update {
            it.copy(
                intelError = "",
                intelNote = "Added $domain. Synced to your cloud vault."
            )
        }
        return null
    }

    fun addBulkDomainsIntel(text: String, sourceLabel: String = ""): Int {
        val lines = text.split("\n", "\r", ",", ";", " ", "\t")
        val parsed = lines.map { DomainUtil.normalizeDomain(it) }.filter { DomainUtil.isPublicDomain(it) }.distinct()
        if (parsed.isEmpty()) {
            val errSource = if (sourceLabel.isNotBlank()) " in $sourceLabel" else " in file"
            _uiState.update { it.copy(intelError = "No valid domains found$errSource.", intelNote = "") }
            return 0
        }
        val added = repository.addCustomDomains(parsed)
        val suffix = if (sourceLabel.isNotBlank()) " from $sourceLabel" else ""
        _uiState.update {
            it.copy(
                intelError = "",
                intelNote = "Imported $added new domain${if (added == 1) "" else "s"} (${parsed.size} total valid)$suffix."
            )
        }
        return added
    }

    fun removeSelectedIntelDomains() {
        val selected = _uiState.value.selectedIntelDomains.toList()
        if (selected.isEmpty()) return

        repository.removeCustomDomains(selected)
        val newChecks = _uiState.value.intelChecks.toMutableMap()
        for (d in selected) {
            newChecks.remove(d)
        }
        repository.saveIntelChecks(newChecks)

        _uiState.update {
            it.copy(
                intelChecks = newChecks,
                selectedIntelDomains = emptySet(),
                intelNote = "Removed ${selected.size} domain${if (selected.size == 1) "" else "s"}.",
                intelError = ""
            )
        }
    }

    fun runIntelCheck() {
        val state = _uiState.value
        val allDomains = repository.getAllSites().map { it.domain }
        val targets = if (state.selectedIntelDomains.isNotEmpty()) {
            allDomains.filter { state.selectedIntelDomains.contains(it) }
        } else {
            allDomains
        }

        if (targets.isEmpty()) {
            _uiState.update { it.copy(intelError = "No domains to check.") }
            return
        }

        intelJob?.cancel()
        intelJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isIntelChecking = true,
                    intelProgressDone = 0,
                    intelProgressTotal = targets.size,
                    intelError = "",
                    intelNote = "Checking ${targets.size} domains…"
                )
            }

            if (targets.size > 40) {
                val liveBatch = apiService.fetchAllLiveStatuses()
                if (liveBatch.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val nextChecks = _uiState.value.intelChecks.toMutableMap()
                    for (d in targets) {
                        val status = liveBatch[d] ?: SiteStatus.UNKNOWN
                        nextChecks[d] = DomainCheck(status, 0L, now)
                    }
                    repository.saveIntelChecks(nextChecks)
                    _uiState.update {
                        it.copy(
                            isIntelChecking = false,
                            intelChecks = nextChecks,
                            intelProgressDone = targets.size,
                            intelNote = "Checked ${targets.size} domains via status feed."
                        )
                    }
                    return@launch
                }
            }

            val queue = java.util.concurrent.ConcurrentLinkedQueue(targets)
            val mutex = java.lang.Object()
            val checksMap = _uiState.value.intelChecks.toMutableMap()
            var doneCount = 0

            val workers = (1..4).map {
                launch {
                    while (isActive) {
                        val domain = queue.poll() ?: break
                        val (status, ms) = apiService.checkSiteStatusDetailed(domain)
                        val check = DomainCheck(status, ms, System.currentTimeMillis())
                        synchronized(mutex) {
                            checksMap[domain] = check
                            doneCount++
                            _uiState.update {
                                it.copy(
                                    intelChecks = HashMap(checksMap),
                                    intelProgressDone = doneCount
                                )
                            }
                        }
                    }
                }
            }
            workers.joinAll()
            repository.saveIntelChecks(checksMap)

            _uiState.update {
                it.copy(
                    isIntelChecking = false,
                    intelChecks = checksMap,
                    intelNote = "Checked ${targets.size} domain${if (targets.size == 1) "" else "s"}."
                )
            }
        }
    }

    fun stopIntelCheck() {
        intelJob?.cancel()
        _uiState.update {
            it.copy(
                isIntelChecking = false,
                intelNote = "Stopped. Finished checks stay in the list."
            )
        }
    }

    // ==========================================
    // BONUS FINDER OPERATIONS
    // ==========================================

    fun setBonusQuery(query: String) {
        _uiState.update { it.copy(bonusQuery = query, bonusPage = 0) }
    }

    fun setBonusPage(page: Int) {
        _uiState.update { it.copy(bonusPage = page) }
    }

    fun updateBonusFilters(update: (BonusFinderFilters) -> BonusFinderFilters) {
        _uiState.update { state ->
            state.copy(bonusFilters = update(state.bonusFilters), bonusPage = 0)
        }
    }

    fun toggleBonusSlotFilter(slot: BonusSlot) {
        updateBonusFilters { current ->
            val set = current.slots.toMutableSet()
            if (set.contains(slot)) set.remove(slot) else set.add(slot)
            current.copy(slots = set)
        }
    }

    fun getFilteredBonusDomains(): List<String> {
        val state = _uiState.value
        val allDomains = repository.getAllSites().map { it.domain }
        val q = state.bonusQuery.trim().lowercase()
        val filters = state.bonusFilters
        val cache = state.bonusCache

        var list = allDomains.filter { q.isEmpty() || it.contains(q) }

        if (isBonusFilterActive(filters)) {
            list = list.filter { domain ->
                val bonuses = cache[domain] ?: return@filter false
                bonuses.any { passesBonusFilter(it, filters) }
            }
        }

        return when (filters.sort) {
            BonusSort.LIST -> list
            BonusSort.AZ -> list.sorted()
            BonusSort.AMOUNT -> list.sortedWith(
                compareByDescending<String> { domain ->
                    scoreBonus(cache[domain] ?: emptyList(), filters)
                }.thenBy { it }
            )
            BonusSort.ROLLOVER -> list.sortedWith(
                compareBy<String> { domain ->
                    scoreBonusRollover(cache[domain] ?: emptyList(), filters)
                }.thenBy { it }
            )
            BonusSort.CASHOUT -> list.sortedWith(
                compareByDescending<String> { domain ->
                    scoreBonusCashout(cache[domain] ?: emptyList(), filters)
                }.thenBy { it }
            )
        }
    }

    private fun isBonusFilterActive(f: BonusFinderFilters): Boolean {
        return f.slots.isNotEmpty() ||
                f.kind != BonusKind.ANY ||
                f.amountMin.isNotBlank() ||
                f.amountMax.isNotBlank() ||
                f.rollMin.isNotBlank() ||
                f.rollMax.isNotBlank() ||
                f.maxMin.isNotBlank() ||
                f.maxMax.isNotBlank()
    }

    private fun passesBonusFilter(b: PublicBonus, f: BonusFinderFilters): Boolean {
        if (f.slots.isNotEmpty() && !f.slots.contains(b.slot)) return false
        if (f.kind != BonusKind.ANY && b.kind != f.kind) return false

        val amtMin = f.amountMin.toDoubleOrNull()
        val amtMax = f.amountMax.toDoubleOrNull()
        val rollMin = f.rollMin.toDoubleOrNull()
        val rollMax = f.rollMax.toDoubleOrNull()
        val maxMin = f.maxMin.toDoubleOrNull()
        val maxMax = f.maxMax.toDoubleOrNull()

        if (amtMin != null && b.amount < amtMin) return false
        if (amtMax != null && b.amount > amtMax) return false
        if (rollMin != null && (b.rollover == null || b.rollover < rollMin)) return false
        if (rollMax != null && (b.rollover == null || b.rollover > rollMax)) return false
        if (maxMin != null && (b.maxWithdraw == null || b.maxWithdraw < maxMin)) return false
        if (maxMax != null && (b.maxWithdraw == null || b.maxWithdraw > maxMax)) return false

        return true
    }

    private fun scoreBonus(bonuses: List<PublicBonus>, f: BonusFinderFilters): Double {
        val pool = bonuses.filter { passesBonusFilter(it, f) }
        return pool.maxOfOrNull { it.amount } ?: 0.0
    }

    private fun scoreBonusRollover(bonuses: List<PublicBonus>, f: BonusFinderFilters): Double {
        val pool = bonuses.filter { passesBonusFilter(it, f) }
        val rolls = pool.mapNotNull { it.rollover }
        return if (rolls.isNotEmpty()) rolls.minOrNull() ?: Double.MAX_VALUE else Double.MAX_VALUE
    }

    private fun scoreBonusCashout(bonuses: List<PublicBonus>, f: BonusFinderFilters): Double {
        val pool = bonuses.filter { passesBonusFilter(it, f) }
        val maxes = pool.mapNotNull { it.maxWithdraw }
        return if (maxes.isNotEmpty()) maxes.maxOrNull() ?: -1.0 else -1.0
    }

    fun loadBonuses(domains: List<String>) {
        val unique = domains.distinct().filter { DomainUtil.isPublicDomain(it) }
        if (unique.isEmpty()) {
            _uiState.update { it.copy(bonusStatusMessage = "No domains to load.") }
            return
        }

        bonusJob?.cancel()
        bonusJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBonusLoading = true,
                    bonusProgressDone = 0,
                    bonusProgressTotal = unique.size,
                    bonusStatusMessage = "Loading public bonuses for ${unique.size} domains…"
                )
            }

            val queue = java.util.concurrent.ConcurrentLinkedQueue(unique)
            val mutex = java.lang.Object()
            val cacheMap = _uiState.value.bonusCache.toMutableMap()
            var done = 0

            val workers = (1..3).map {
                launch {
                    while (isActive) {
                        val domain = queue.poll() ?: break
                        val bonuses = apiService.loadPublicPromotions(domain)
                        synchronized(mutex) {
                            cacheMap[domain] = bonuses
                            done++
                            _uiState.update {
                                it.copy(
                                    bonusCache = HashMap(cacheMap),
                                    bonusProgressDone = done
                                )
                            }
                        }
                    }
                }
            }
            workers.joinAll()
            repository.saveBonusCache(cacheMap)

            _uiState.update {
                it.copy(
                    isBonusLoading = false,
                    bonusCache = cacheMap,
                    merchantCacheCount = apiService.getMerchantCacheCount(),
                    bonusStatusMessage = "Public bonuses loaded. No password was used."
                )
            }
        }
    }

    fun stopBonusLoad() {
        bonusJob?.cancel()
        _uiState.update {
            it.copy(
                isBonusLoading = false,
                bonusStatusMessage = "Stopped."
            )
        }
    }
}
