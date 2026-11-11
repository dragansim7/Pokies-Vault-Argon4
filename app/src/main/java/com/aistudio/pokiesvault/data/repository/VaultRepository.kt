package com.aistudio.pokiesvault.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.aistudio.pokiesvault.data.catalog.CatalogData
import com.aistudio.pokiesvault.data.model.*
import com.aistudio.pokiesvault.util.DomainUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class VaultState(
    val visited: Set<String> = emptySet(),
    val mine: Set<String> = emptySet(),
    val custom: List<CatalogSite> = emptyList(),
    val phone: String = "",
    val password: String = "",
    val fallbacks: List<FallbackCred> = listOf(FallbackCred(), FallbackCred(), FallbackCred()),
    val lastFetch: LastFetch? = null,
    val viewMode: ViewMode = ViewMode.GROUPS,
    val notVisited: Boolean = false,
    val wallets: Boolean = false,
    val mineOnly: Boolean = false,
    val sortMode: SortMode = SortMode.DEFAULT,
    val bonusMode: BonusMode = BonusMode.NONE,
    val themeId: String = "vault"
)

class VaultRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pokies_vault_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private fun loadInitialState(): VaultState {
        val visited = prefs.getStringSet("visited", emptySet()) ?: emptySet()
        val mine = prefs.getStringSet("mine", emptySet()) ?: emptySet()
        val phone = prefs.getString("phone", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        val notVisited = prefs.getBoolean("notVisited", false)
        val wallets = prefs.getBoolean("wallets", false)
        val mineOnly = prefs.getBoolean("mineOnly", false)
        val sortModeName = prefs.getString("sortMode", SortMode.DEFAULT.name) ?: SortMode.DEFAULT.name
        val sortMode = try { SortMode.valueOf(sortModeName) } catch (_: Exception) { SortMode.DEFAULT }
        val viewModeName = prefs.getString("viewMode", ViewMode.GROUPS.name) ?: ViewMode.GROUPS.name
        val viewMode = try { ViewMode.valueOf(viewModeName) } catch (_: Exception) { ViewMode.GROUPS }
        val bonusModeName = prefs.getString("bonusMode", BonusMode.NONE.name) ?: BonusMode.NONE.name
        val bonusMode = try { BonusMode.valueOf(bonusModeName) } catch (_: Exception) { BonusMode.NONE }
        val themeId = prefs.getString("themeId", "vault") ?: "vault"

        val customJson = prefs.getString("custom", "[]") ?: "[]"
        val customList = mutableListOf<CatalogSite>()
        try {
            val arr = JSONArray(customJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                customList.add(
                    CatalogSite(
                        domain = obj.getString("domain"),
                        group = SiteGroup.CUSTOM,
                        addedAt = obj.optString("addedAt", "")
                    )
                )
            }
        } catch (_: Exception) {}

        val fallbacksJson = prefs.getString("fallbacks", "[]") ?: "[]"
        val fallbacksList = mutableListOf<FallbackCred>()
        try {
            val arr = JSONArray(fallbacksJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                fallbacksList.add(FallbackCred(obj.optString("phone", ""), obj.optString("password", "")))
            }
        } catch (_: Exception) {}
        while (fallbacksList.size < 3) {
            fallbacksList.add(FallbackCred())
        }

        return VaultState(
            visited = visited,
            mine = mine,
            custom = customList,
            phone = phone,
            password = password,
            fallbacks = fallbacksList.take(3),
            viewMode = viewMode,
            notVisited = notVisited,
            wallets = wallets,
            mineOnly = mineOnly,
            sortMode = sortMode,
            bonusMode = bonusMode,
            themeId = themeId
        )
    }

    fun getAllSites(): List<CatalogSite> {
        val current = _state.value
        val map = LinkedHashMap<String, CatalogSite>()
        for (site in CatalogData.SITES) {
            map[site.domain] = site
        }
        for (site in current.custom) {
            if (!map.containsKey(site.domain)) {
                map[site.domain] = site
            }
        }
        return map.values.toList()
    }

    fun setThemeId(themeId: String) {
        _state.value = _state.value.copy(themeId = themeId)
        prefs.edit().putString("themeId", themeId).apply()
    }

    fun updateCredentials(phone: String, password: String) {
        val next = _state.value.copy(phone = phone, password = password)
        _state.value = next
        prefs.edit().putString("phone", phone).putString("password", password).apply()
    }

    fun updateFallback(index: Int, phone: String, password: String) {
        val list = _state.value.fallbacks.toMutableList()
        if (index in list.indices) {
            list[index] = FallbackCred(phone, password)
            val next = _state.value.copy(fallbacks = list)
            _state.value = next
            saveFallbacks(list)
        }
    }

    private fun saveFallbacks(list: List<FallbackCred>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("phone", item.phone)
            obj.put("password", item.password)
            arr.put(obj)
        }
        prefs.edit().putString("fallbacks", arr.toString()).apply()
    }

    fun toggleVisited(domain: String) {
        val cur = _state.value.visited
        val nextSet = if (cur.contains(domain)) cur - domain else cur + domain
        _state.value = _state.value.copy(visited = nextSet)
        prefs.edit().putStringSet("visited", nextSet).apply()
    }

    fun markVisited(domain: String) {
        if (!_state.value.visited.contains(domain)) {
            val nextSet = _state.value.visited + domain
            _state.value = _state.value.copy(visited = nextSet)
            prefs.edit().putStringSet("visited", nextSet).apply()
        }
    }

    fun toggleMine(domain: String) {
        val cur = _state.value.mine
        val nextSet = if (cur.contains(domain)) cur - domain else cur + domain
        _state.value = _state.value.copy(mine = nextSet)
        prefs.edit().putStringSet("mine", nextSet).apply()
    }

    fun rememberAccount(domain: String) {
        if (!_state.value.mine.contains(domain)) {
            val nextSet = _state.value.mine + domain
            _state.value = _state.value.copy(mine = nextSet)
            prefs.edit().putStringSet("mine", nextSet).apply()
        }
    }

    fun addCustomDomain(domain: String): String? {
        val cur = _state.value
        if (cur.custom.any { it.domain.equals(domain, ignoreCase = true) } ||
            CatalogData.SITES.any { it.domain.equals(domain, ignoreCase = true) }) {
            rememberAccount(domain)
            return null
        }
        val newCustom = listOf(CatalogSite(domain, SiteGroup.CUSTOM, "Now")) + cur.custom
        val newMine = cur.mine + domain
        _state.value = cur.copy(custom = newCustom, mine = newMine)
        saveCustom(newCustom, newMine)
        return null
    }

    fun addCustomDomains(domains: List<String>): Int {
        val cur = _state.value
        val valid = domains.map { DomainUtil.normalizeDomain(it) }.filter { DomainUtil.isPublicDomain(it) }.distinct()
        val existingDomains = (CatalogData.SITES.map { it.domain } + cur.custom.map { it.domain }).toSet()
        val toAdd = valid.filter { !existingDomains.contains(it) }

        if (toAdd.isEmpty()) {
            return 0
        }

        val addedSites = toAdd.map { CatalogSite(it, SiteGroup.CUSTOM, "Imported") }
        val newCustom = addedSites + cur.custom
        val newMine = cur.mine + toAdd
        _state.value = cur.copy(custom = newCustom, mine = newMine)
        saveCustom(newCustom, newMine)
        return toAdd.size
    }

    fun removeCustomDomains(domains: List<String>) {
        val set = domains.toSet()
        val cur = _state.value
        val newCustom = cur.custom.filter { !set.contains(it.domain) }
        val newMine = cur.mine - set
        _state.value = cur.copy(custom = newCustom, mine = newMine)
        saveCustom(newCustom, newMine)
    }

    private fun saveCustom(custom: List<CatalogSite>, mine: Set<String>) {
        val arr = JSONArray()
        for (site in custom) {
            val obj = JSONObject()
            obj.put("domain", site.domain)
            obj.put("addedAt", site.addedAt)
            arr.put(obj)
        }
        prefs.edit()
            .putString("custom", arr.toString())
            .putStringSet("mine", mine)
            .apply()
    }

    fun saveFetch(rows: List<AccountResult>) {
        val current = _state.value
        val found = rows.filterIsInstance<AccountResult.Ok>().map { it.domain }.toSet()
        val newMine = current.mine + found
        val lastFetch = LastFetch(System.currentTimeMillis(), rows)
        _state.value = current.copy(lastFetch = lastFetch, mine = newMine)
        prefs.edit().putStringSet("mine", newMine).apply()
    }

    fun setNotVisited(value: Boolean) {
        _state.value = _state.value.copy(notVisited = value)
        prefs.edit().putBoolean("notVisited", value).apply()
    }

    fun setWallets(value: Boolean) {
        _state.value = _state.value.copy(wallets = value)
        prefs.edit().putBoolean("wallets", value).apply()
    }

    fun setMineOnly(value: Boolean) {
        _state.value = _state.value.copy(mineOnly = value)
        prefs.edit().putBoolean("mineOnly", value).apply()
    }

    fun setViewMode(mode: ViewMode) {
        _state.value = _state.value.copy(viewMode = mode)
        prefs.edit().putString("viewMode", mode.name).apply()
    }

    fun setSortMode(mode: SortMode) {
        _state.value = _state.value.copy(sortMode = mode)
        prefs.edit().putString("sortMode", mode.name).apply()
    }

    fun setBonusMode(mode: BonusMode) {
        _state.value = _state.value.copy(bonusMode = mode)
        prefs.edit().putString("bonusMode", mode.name).apply()
    }

    // Domain Intel Checks Cache
    fun loadIntelChecks(): Map<String, DomainCheck> {
        val raw = prefs.getString("intel_checks", "{}") ?: "{}"
        val map = mutableMapOf<String, DomainCheck>()
        try {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val domain = keys.next()
                val item = obj.getJSONObject(domain)
                val statusName = item.optString("status", SiteStatus.UNKNOWN.name)
                val status = try { SiteStatus.valueOf(statusName) } catch (_: Exception) { SiteStatus.UNKNOWN }
                val ms = item.optLong("ms", 0L)
                val ts = item.optLong("ts", 0L)
                map[domain] = DomainCheck(status, ms, ts)
            }
        } catch (_: Exception) {}
        return map
    }

    fun saveIntelChecks(checks: Map<String, DomainCheck>) {
        val obj = JSONObject()
        for ((domain, check) in checks) {
            val item = JSONObject()
            item.put("status", check.status.name)
            item.put("ms", check.ms)
            item.put("ts", check.timestamp)
            obj.put(domain, item)
        }
        prefs.edit().putString("intel_checks", obj.toString()).apply()
    }

    // Public Bonus Finder Cache
    fun loadBonusCache(): Map<String, List<PublicBonus>> {
        val raw = prefs.getString("bonus_catalogue_cache", "{}") ?: "{}"
        val map = mutableMapOf<String, List<PublicBonus>>()
        try {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val domain = keys.next()
                val arr = obj.getJSONArray(domain)
                val bonuses = mutableListOf<PublicBonus>()
                for (i in 0 until arr.length()) {
                    val b = arr.getJSONObject(i)
                    bonuses.add(
                        PublicBonus(
                            id = b.optString("id", ""),
                            name = b.optString("name", ""),
                            slot = try { BonusSlot.valueOf(b.optString("slot", BonusSlot.OTHER.name)) } catch (_: Exception) { BonusSlot.OTHER },
                            kind = try { BonusKind.valueOf(b.optString("kind", BonusKind.UNSET.name)) } catch (_: Exception) { BonusKind.UNSET },
                            amount = b.optDouble("amount", 0.0),
                            amountDisplay = b.optString("amountDisplay", ""),
                            minWithdraw = b.optDouble("minWithdraw", -1.0).takeIf { it > 0.0 },
                            maxWithdraw = b.optDouble("maxWithdraw", -1.0).takeIf { it > 0.0 },
                            rollover = b.optDouble("rollover", -1.0).takeIf { it > 0.0 },
                            minDeposit = b.optDouble("minDeposit", -1.0).takeIf { it > 0.0 }
                        )
                    )
                }
                map[domain] = bonuses
            }
        } catch (_: Exception) {}
        return map
    }

    fun saveBonusCache(cache: Map<String, List<PublicBonus>>) {
        val obj = JSONObject()
        for ((domain, list) in cache) {
            val arr = JSONArray()
            for (b in list) {
                val item = JSONObject()
                item.put("id", b.id)
                item.put("name", b.name)
                item.put("slot", b.slot.name)
                item.put("kind", b.kind.name)
                item.put("amount", b.amount)
                item.put("amountDisplay", b.amountDisplay)
                b.minWithdraw?.let { item.put("minWithdraw", it) }
                b.maxWithdraw?.let { item.put("maxWithdraw", it) }
                b.rollover?.let { item.put("rollover", it) }
                b.minDeposit?.let { item.put("minDeposit", it) }
                arr.put(item)
            }
            obj.put(domain, arr)
        }
        prefs.edit().putString("bonus_catalogue_cache", obj.toString()).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _state.value = VaultState()
    }
}
