package com.aistudio.pokiesvault.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.pokiesvault.data.model.*
import com.aistudio.pokiesvault.data.network.CasinoApiService
import com.aistudio.pokiesvault.data.repository.VaultRepository
import com.aistudio.pokiesvault.data.repository.VaultState
import com.aistudio.pokiesvault.util.DomainUtil
import com.aistudio.pokiesvault.util.FormatUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VaultRepository(application)
    private val apiService = CasinoApiService()

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null
    private var intelJob: Job? = null
    private var bonusJob: Job? = null
    private val statusCheckJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            val initialChecks = repository.loadIntelChecks()
            val initialBonuses = repository.loadBonusCache()
            _uiState.update { it.copy(intelChecks = initialChecks, bonusCache = initialBonuses) }

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
            val status = apiService.checkSiteStatus(domain)
            _uiState.update { it.copy(siteStatuses = it.siteStatuses + (domain to status)) }
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
        val validCreds = creds.filter { it.first.isNotBlank() && it.second.isNotBlank() }
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
            _uiState.update {
                it.copy(
                    isFetching = true,
                    fetchProgressDone = 0,
                    fetchProgressTotal = uniqueDomains.size,
                    fetchStatusMessage = if (uniqueDomains.size == 1) "Checking ${uniqueDomains[0]}…" else "Checking ${uniqueDomains.size} sites…",
                    currentFetchRows = emptyList(),
                    fetchDiffs = emptyList(),
                    showRejectedSites = false
                )
            }

            val previousMap = mutableMapOf<String, AccountResult.Ok>()
            vState.lastFetch?.rows?.filterIsInstance<AccountResult.Ok>()?.forEach {
                previousMap[it.domain] = it
            }

            val collected = mutableListOf<AccountResult>()
            val mutex = java.lang.Object()

            val queue = java.util.concurrent.ConcurrentLinkedQueue(uniqueDomains)
            val workers = (1..3).map {
                launch {
                    while (isActive) {
                        val domain = queue.poll() ?: break
                        var result: AccountResult = AccountResult.Err(domain, "other", "No credentials")

                        for ((phone, password) in validCreds) {
                            if (!isActive) break
                            val res = apiService.fetchCasinoAccount(domain, phone, password)
                            if (res is AccountResult.Ok) {
                                repository.rememberAccount(domain)
                                result = res
                                break
                            } else if (res is AccountResult.Err) {
                                result = res
                                if (res.code != "invalid_login") break
                            }
                        }

                        synchronized(mutex) {
                            collected.add(result)
                            val done = collected.size
                            val sorted = collected.sortedBy { it.domain }
                            _uiState.update {
                                it.copy(
                                    fetchProgressDone = done,
                                    currentFetchRows = sorted
                                )
                            }
                        }
                    }
                }
            }
            workers.joinAll()

            val finalRows = collected.sortedBy { it.domain }
            repository.saveFetch(finalRows)

            val diffNotes = mutableListOf<String>()
            for (r in finalRows) {
                if (r is AccountResult.Ok) {
                    val prev = previousMap[r.domain]
                    if (prev != null && Math.abs(prev.cash - r.cash) >= 0.01) {
                        diffNotes.add("${r.domain} balance ${FormatUtil.money(prev.cash)} → ${FormatUtil.money(r.cash)}")
                    }
                }
            }

            val okCount = finalRows.count { it is AccountResult.Ok }
            _uiState.update {
                it.copy(
                    isFetching = false,
                    fetchDiffs = diffNotes,
                    fetchStatusMessage = "Done. $okCount of ${finalRows.size} signed in. Successful accounts are saved in your vault."
                )
            }
        }
    }

    fun stopFetch() {
        fetchJob?.cancel()
        _uiState.update {
            it.copy(
                isFetching = false,
                fetchStatusMessage = "Stopped. Successful sign-ins stay in My accounts."
            )
        }
    }

    fun viewLastFetch() {
        val last = _uiState.value.vaultState.lastFetch
        if (last != null) {
            _uiState.update {
                it.copy(
                    currentFetchRows = last.rows,
                    fetchStatusMessage = "Showing the last fetch saved on this device."
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
                intelNote = "Added $domain. It is on the main list and in the fetcher."
            )
        }
        return null
    }

    fun addBulkDomainsIntel(text: String): Int {
        val lines = text.split("\n", ",", " ", "\t")
        val parsed = lines.map { DomainUtil.normalizeDomain(it) }.filter { DomainUtil.isPublicDomain(it) }.distinct()
        if (parsed.isEmpty()) {
            _uiState.update { it.copy(intelError = "No valid domains found in text.", intelNote = "") }
            return 0
        }
        val added = repository.addCustomDomains(parsed)
        _uiState.update {
            it.copy(
                intelError = "",
                intelNote = "Imported $added new domain${if (added == 1) "" else "s"} from ${parsed.size} found."
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

            // If more than 40 domains, check live status batch first
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
