package com.aistudio.pokiesvault.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.aistudio.pokiesvault.R
import com.aistudio.pokiesvault.data.catalog.CatalogData
import com.aistudio.pokiesvault.data.model.*
import com.aistudio.pokiesvault.util.DomainUtil
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    val themeId: String = "vault",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

class VaultRepository(
    private val db: FirebaseFirestore,
    private val prefs: SharedPreferences? = null
) {
    // Secondary constructor resolving the provisioned Firestore database ID from string resources
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        prefs = context.applicationContext.getSharedPreferences("pokies_vault_prefs", Context.MODE_PRIVATE)
    )

    private val auth = Firebase.auth
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(loadInitialLocalState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun loadInitialLocalState(): VaultState {
        val sp = prefs ?: return VaultState()
        val visited = sp.getStringSet("visited", emptySet())?.toSet() ?: emptySet()
        val mine = sp.getStringSet("mine", emptySet())?.toSet() ?: emptySet()
        val phone = sp.getString("phone", "") ?: ""
        val password = sp.getString("password", "") ?: ""
        val notVisited = sp.getBoolean("notVisited", false)
        val wallets = sp.getBoolean("wallets", false)
        val mineOnly = sp.getBoolean("mineOnly", false)
        val sortModeName = sp.getString("sortMode", SortMode.DEFAULT.name) ?: SortMode.DEFAULT.name
        val sortMode = try { SortMode.valueOf(sortModeName) } catch (_: Exception) { SortMode.DEFAULT }
        val viewModeName = sp.getString("viewMode", ViewMode.GROUPS.name) ?: ViewMode.GROUPS.name
        val viewMode = try { ViewMode.valueOf(viewModeName) } catch (_: Exception) { ViewMode.GROUPS }
        val bonusModeName = sp.getString("bonusMode", BonusMode.NONE.name) ?: BonusMode.NONE.name
        val bonusMode = try { BonusMode.valueOf(bonusModeName) } catch (_: Exception) { BonusMode.NONE }
        val themeId = sp.getString("themeId", "vault") ?: "vault"

        val customJson = sp.getString("custom", "[]") ?: "[]"
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

        val fallbacksJson = sp.getString("fallbacks", "[]") ?: "[]"
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

        val lastFetchRaw = sp.getString("last_fetch_json", null)
        val lastFetch = if (!lastFetchRaw.isNullOrBlank()) deserializeLastFetchFromPrefs(lastFetchRaw) else null

        return VaultState(
            visited = visited,
            mine = mine,
            custom = customList,
            phone = phone,
            password = password,
            fallbacks = fallbacksList.take(3),
            lastFetch = lastFetch,
            viewMode = viewMode,
            notVisited = notVisited,
            wallets = wallets,
            mineOnly = mineOnly,
            sortMode = sortMode,
            bonusMode = bonusMode,
            themeId = themeId
        )
    }

    // ==========================================
    // FIRESTORE CLOUD SYNC & REALTIME OBSERVATION
    // ==========================================

    suspend fun saveVaultToFirestore(vaultState: VaultState = _state.value): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val existingTimestamp = vaultState.createdAt

        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "visited" to vaultState.visited.take(500),
            "mine" to vaultState.mine.take(500),
            "customDomains" to vaultState.custom.take(500).map { "${it.domain}|${it.addedAt}" },
            "lastFetchTimestamp" to (vaultState.lastFetch?.timestamp ?: 0L),
            "lastFetchRows" to (vaultState.lastFetch?.rows?.take(500)?.map { serializeAccountResultMap(it) } ?: emptyList<Map<String, Any>>()),
            "themeId" to vaultState.themeId.ifBlank { "vault" }.take(32),
            "viewMode" to vaultState.viewMode.name,
            "sortMode" to vaultState.sortMode.name,
            "bonusMode" to vaultState.bonusMode.name,
            "notVisited" to vaultState.notVisited,
            "wallets" to vaultState.wallets,
            "mineOnly" to vaultState.mineOnly,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            if (existingTimestamp != null) {
                payload["createdAt"] = existingTimestamp
                docRef.set(payload).await()
            } else {
                val snap = docRef.get().await()
                if (snap.exists()) {
                    val existingCreated = snap.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: FieldValue.serverTimestamp()
                    payload["createdAt"] = existingCreated
                    docRef.set(payload).await()
                } else {
                    payload["createdAt"] = FieldValue.serverTimestamp()
                    docRef.set(payload).await()
                }
            }
            return uid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existingTimestamp == null) OperationType.CREATE else OperationType.UPDATE,
                docRef.path
            )
            throw e
        }
    }

    suspend fun getUserVault(targetUserId: String = requireUserId()): VaultState? {
        val docRef = db.collection("users").document(targetUserId)
        try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) return null
            return parseSnapshotToVaultState(snapshot, _state.value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    fun observeUserVault(targetUserId: String? = null): Flow<VaultState> = flow {
        val uid = targetUserId ?: requireUserId()
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        val merged = parseSnapshotToVaultState(snapshot, _state.value)
                        _state.value = merged
                        persistSyncedFieldsLocally(merged)
                        merged
                    } else {
                        _state.value
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    /**
     * Merges any existing local device state into the authenticated user's cloud vault on sign-in.
     * Casino credentials (phone, password, fallbacks) remain strictly local.
     */
    suspend fun mergeLocalWithCloudOnSignIn(): VaultState {
        val uid = requireUserId()
        val local = _state.value
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val cloud = parseSnapshotToVaultState(snap, local)
                val mergedVisited = (cloud.visited + local.visited).take(500).toSet()
                val mergedMine = (cloud.mine + local.mine).take(500).toSet()
                val customByDomain = LinkedHashMap<String, CatalogSite>()
                for (site in cloud.custom + local.custom) {
                    if (!customByDomain.containsKey(site.domain.lowercase())) {
                        customByDomain[site.domain.lowercase()] = site
                    }
                }
                val mergedCustom = customByDomain.values.take(500).toList()

                val mergedLastFetch = when {
                    cloud.lastFetch == null -> local.lastFetch
                    local.lastFetch == null -> cloud.lastFetch
                    local.lastFetch.timestamp > cloud.lastFetch.timestamp -> local.lastFetch
                    else -> cloud.lastFetch
                }

                val merged = cloud.copy(
                    visited = mergedVisited,
                    mine = mergedMine,
                    custom = mergedCustom,
                    lastFetch = mergedLastFetch,
                    phone = local.phone,
                    password = local.password,
                    fallbacks = local.fallbacks
                )
                _state.value = merged
                persistSyncedFieldsLocally(merged)
                saveVaultToFirestore(merged)
                merged
            } else {
                saveVaultToFirestore(local)
                local
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            local
        }
    }

    private fun pushStateAsync(nextState: VaultState) {
        if (auth.currentUser != null) {
            repoScope.launch {
                try {
                    saveVaultToFirestore(nextState)
                } catch (_: Exception) {
                    // Logged inside saveVaultToFirestore via handleFirestoreError
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSnapshotToVaultState(
        snapshot: DocumentSnapshot,
        currentLocal: VaultState
    ): VaultState {
        val data = snapshot.data ?: return currentLocal
        val visited = (data["visited"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
        val mine = (data["mine"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
        val customRaw = (data["customDomains"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val customList = customRaw.mapNotNull { entry ->
            val parts = entry.split("|", limit = 2)
            val domain = parts.getOrNull(0)?.trim().orEmpty()
            if (domain.isEmpty()) null else CatalogSite(
                domain = domain,
                group = SiteGroup.CUSTOM,
                addedAt = parts.getOrNull(1) ?: "Synced"
            )
        }

        val lastFetchTs = (data["lastFetchTimestamp"] as? Number)?.toLong() ?: 0L
        val lastFetchRowsRaw = (data["lastFetchRows"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
        val lastFetch = if (lastFetchTs > 0L && lastFetchRowsRaw.isNotEmpty()) {
            LastFetch(
                timestamp = lastFetchTs,
                rows = lastFetchRowsRaw.mapNotNull { deserializeAccountResultMap(it) }
            )
        } else {
            currentLocal.lastFetch
        }

        val themeId = (data["themeId"] as? String)?.takeIf { it.isNotBlank() } ?: currentLocal.themeId
        val viewMode = (data["viewMode"] as? String)?.let {
            try { ViewMode.valueOf(it) } catch (_: Exception) { null }
        } ?: currentLocal.viewMode
        val sortMode = (data["sortMode"] as? String)?.let {
            try { SortMode.valueOf(it) } catch (_: Exception) { null }
        } ?: currentLocal.sortMode
        val bonusMode = (data["bonusMode"] as? String)?.let {
            try { BonusMode.valueOf(it) } catch (_: Exception) { null }
        } ?: currentLocal.bonusMode

        val notVisited = (data["notVisited"] as? Boolean) ?: currentLocal.notVisited
        val wallets = (data["wallets"] as? Boolean) ?: currentLocal.wallets
        val mineOnly = (data["mineOnly"] as? Boolean) ?: currentLocal.mineOnly

        val createdAt = snapshot.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val updatedAt = snapshot.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

        return currentLocal.copy(
            visited = visited,
            mine = mine,
            custom = customList,
            lastFetch = lastFetch,
            themeId = themeId,
            viewMode = viewMode,
            sortMode = sortMode,
            bonusMode = bonusMode,
            notVisited = notVisited,
            wallets = wallets,
            mineOnly = mineOnly,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun serializeBonusViewMap(bonus: BonusView): Map<String, Any> {
        return mapOf(
            "name" to bonus.name,
            "amount" to bonus.amount,
            "amountDisplay" to bonus.amountDisplay,
            "minWithdrawal" to bonus.minWithdrawal,
            "maxWithdrawal" to bonus.maxWithdrawal,
            "rollover" to bonus.rollover,
            "isPercent" to bonus.isPercent
        ).filterValues { it != null } as Map<String, Any>
    }

    private fun deserializeBonusViewMap(map: Map<String, Any?>?): BonusView? {
        if (map == null) return null
        val name = map["name"] as? String ?: return null
        return BonusView(
            name = name,
            amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
            amountDisplay = map["amountDisplay"] as? String ?: "",
            minWithdrawal = (map["minWithdrawal"] as? Number)?.toDouble(),
            maxWithdrawal = (map["maxWithdrawal"] as? Number)?.toDouble(),
            rollover = (map["rollover"] as? Number)?.toDouble(),
            isPercent = map["isPercent"] as? Boolean ?: false
        )
    }

    private fun serializeAccountResultMap(result: AccountResult): Map<String, Any> {
        return when (result) {
            is AccountResult.Ok -> mapOf(
                "domain" to result.domain,
                "isOk" to true,
                "hasData" to result.hasData,
                "cash" to result.cash,
                "cashWalletName" to result.cashWalletName,
                "cashMinWithdrawal" to result.cashMinWithdrawal,
                "cashMaxWithdrawal" to result.cashMaxWithdrawal,
                "wallets" to result.wallets.map { w ->
                    mapOf("id" to w.id, "name" to w.name, "balance" to w.balance)
                },
                "commission" to result.commission?.let { serializeBonusViewMap(it) },
                "share" to result.share?.let { serializeBonusViewMap(it) },
                "referral" to result.referral?.let { serializeBonusViewMap(it) },
                "newMember" to result.newMember?.let { serializeBonusViewMap(it) },
                "rebate" to result.rebate?.let { serializeBonusViewMap(it) },
                "availableBonuses" to result.availableBonuses.map { serializeBonusViewMap(it) }
            ).filterValues { it != null } as Map<String, Any>

            is AccountResult.Err -> mapOf(
                "domain" to result.domain,
                "isOk" to false,
                "code" to result.code,
                "message" to result.message
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun deserializeAccountResultMap(map: Map<String, Any?>): AccountResult? {
        val domain = map["domain"] as? String ?: return null
        val isOk = map["isOk"] as? Boolean ?: false
        return if (isOk) {
            val walletsRaw = (map["wallets"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val availableRaw = (map["availableBonuses"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            AccountResult.Ok(
                domain = domain,
                hasData = map["hasData"] as? Boolean ?: true,
                cash = (map["cash"] as? Number)?.toDouble() ?: 0.0,
                cashWalletName = map["cashWalletName"] as? String ?: "Main",
                cashMinWithdrawal = (map["cashMinWithdrawal"] as? Number)?.toDouble(),
                cashMaxWithdrawal = (map["cashMaxWithdrawal"] as? Number)?.toDouble(),
                wallets = walletsRaw.map { w ->
                    WalletView(
                        id = w["id"] as? String ?: "",
                        name = w["name"] as? String ?: "",
                        balance = (w["balance"] as? Number)?.toDouble() ?: 0.0
                    )
                },
                commission = deserializeBonusViewMap(map["commission"] as? Map<String, Any?>),
                share = deserializeBonusViewMap(map["share"] as? Map<String, Any?>),
                referral = deserializeBonusViewMap(map["referral"] as? Map<String, Any?>),
                newMember = deserializeBonusViewMap(map["newMember"] as? Map<String, Any?>),
                rebate = deserializeBonusViewMap(map["rebate"] as? Map<String, Any?>),
                availableBonuses = availableRaw.mapNotNull { deserializeBonusViewMap(it) }
            )
        } else {
            AccountResult.Err(
                domain = domain,
                code = map["code"] as? String ?: "other",
                message = map["message"] as? String ?: ""
            )
        }
    }

    private fun persistSyncedFieldsLocally(vState: VaultState) {
        val sp = prefs ?: return
        val customArr = JSONArray()
        for (site in vState.custom) {
            val obj = JSONObject()
            obj.put("domain", site.domain)
            obj.put("addedAt", site.addedAt)
            customArr.put(obj)
        }
        val editor = sp.edit()
            .putStringSet("visited", vState.visited)
            .putStringSet("mine", vState.mine)
            .putString("custom", customArr.toString())
            .putString("themeId", vState.themeId)
            .putString("viewMode", vState.viewMode.name)
            .putString("sortMode", vState.sortMode.name)
            .putString("bonusMode", vState.bonusMode.name)
            .putBoolean("notVisited", vState.notVisited)
            .putBoolean("wallets", vState.wallets)
            .putBoolean("mineOnly", vState.mineOnly)

        if (vState.lastFetch != null) {
            editor.putString("last_fetch_json", serializeLastFetchForPrefs(vState.lastFetch))
        }
        editor.apply()
    }

    private fun serializeLastFetchForPrefs(lastFetch: LastFetch): String {
        val root = JSONObject()
        root.put("ts", lastFetch.timestamp)
        val arr = JSONArray()
        for (row in lastFetch.rows) {
            arr.put(JSONObject(serializeAccountResultMap(row)))
        }
        root.put("rows", arr)
        return root.toString()
    }

    private fun deserializeLastFetchFromPrefs(raw: String): LastFetch? {
        return try {
            val obj = JSONObject(raw)
            val ts = obj.optLong("ts", 0L)
            val arr = obj.optJSONArray("rows") ?: return null
            val rows = mutableListOf<AccountResult>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val domain = item.optString("domain", "")
                if (domain.isBlank()) continue
                if (item.optBoolean("isOk", false)) {
                    rows.add(
                        AccountResult.Ok(
                            domain = domain,
                            hasData = item.optBoolean("hasData", true),
                            cash = item.optDouble("cash", 0.0),
                            cashWalletName = item.optString("cashWalletName", "Main"),
                            cashMinWithdrawal = item.optDouble("cashMinWithdrawal", -1.0).takeIf { it > 0 },
                            cashMaxWithdrawal = item.optDouble("cashMaxWithdrawal", -1.0).takeIf { it > 0 },
                            wallets = emptyList(),
                            commission = null,
                            share = null,
                            referral = null,
                            newMember = null,
                            rebate = null,
                            availableBonuses = emptyList()
                        )
                    )
                } else {
                    rows.add(
                        AccountResult.Err(
                            domain = domain,
                            code = item.optString("code", "other"),
                            message = item.optString("message", "")
                        )
                    )
                }
            }
            LastFetch(ts, rows)
        } catch (_: Exception) {
            null
        }
    }

    // ==========================================
    // DOMAIN & CREDENTIAL MUTATIONS
    // ==========================================

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
        val next = _state.value.copy(themeId = themeId)
        _state.value = next
        prefs?.edit()?.putString("themeId", themeId)?.apply()
        pushStateAsync(next)
    }

    // Strictly local on-device storage for casino login credentials
    fun updateCredentials(phone: String, password: String) {
        val next = _state.value.copy(phone = phone, password = password)
        _state.value = next
        prefs?.edit()?.putString("phone", phone)?.putString("password", password)?.apply()
    }

    fun updateFallback(index: Int, phone: String, password: String) {
        val list = _state.value.fallbacks.toMutableList()
        if (index in list.indices) {
            list[index] = FallbackCred(phone, password)
            val next = _state.value.copy(fallbacks = list)
            _state.value = next
            saveFallbacksLocally(list)
        }
    }

    private fun saveFallbacksLocally(list: List<FallbackCred>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("phone", item.phone)
            obj.put("password", item.password)
            arr.put(obj)
        }
        prefs?.edit()?.putString("fallbacks", arr.toString())?.apply()
    }

    fun toggleVisited(domain: String) {
        val cur = _state.value.visited
        val nextSet = if (cur.contains(domain)) cur - domain else cur + domain
        val next = _state.value.copy(visited = nextSet)
        _state.value = next
        prefs?.edit()?.putStringSet("visited", nextSet)?.apply()
        pushStateAsync(next)
    }

    fun markVisited(domain: String) {
        if (!_state.value.visited.contains(domain)) {
            val nextSet = _state.value.visited + domain
            val next = _state.value.copy(visited = nextSet)
            _state.value = next
            prefs?.edit()?.putStringSet("visited", nextSet)?.apply()
            pushStateAsync(next)
        }
    }

    fun toggleMine(domain: String) {
        val cur = _state.value.mine
        val nextSet = if (cur.contains(domain)) cur - domain else cur + domain
        val next = _state.value.copy(mine = nextSet)
        _state.value = next
        prefs?.edit()?.putStringSet("mine", nextSet)?.apply()
        pushStateAsync(next)
    }

    fun rememberAccount(domain: String) {
        if (!_state.value.mine.contains(domain)) {
            val nextSet = _state.value.mine + domain
            val next = _state.value.copy(mine = nextSet)
            _state.value = next
            prefs?.edit()?.putStringSet("mine", nextSet)?.apply()
            pushStateAsync(next)
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
        val next = cur.copy(custom = newCustom, mine = newMine)
        _state.value = next
        saveCustomLocally(newCustom, newMine)
        pushStateAsync(next)
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
        val next = cur.copy(custom = newCustom, mine = newMine)
        _state.value = next
        saveCustomLocally(newCustom, newMine)
        pushStateAsync(next)
        return toAdd.size
    }

    fun removeCustomDomains(domains: List<String>) {
        val set = domains.toSet()
        val cur = _state.value
        val newCustom = cur.custom.filter { !set.contains(it.domain) }
        val newMine = cur.mine - set
        val next = cur.copy(custom = newCustom, mine = newMine)
        _state.value = next
        saveCustomLocally(newCustom, newMine)
        pushStateAsync(next)
    }

    private fun saveCustomLocally(custom: List<CatalogSite>, mine: Set<String>) {
        val arr = JSONArray()
        for (site in custom) {
            val obj = JSONObject()
            obj.put("domain", site.domain)
            obj.put("addedAt", site.addedAt)
            arr.put(obj)
        }
        prefs?.edit()
            ?.putString("custom", arr.toString())
            ?.putStringSet("mine", mine)
            ?.apply()
    }

    fun saveFetch(rows: List<AccountResult>) {
        val current = _state.value
        val found = rows.filterIsInstance<AccountResult.Ok>().map { it.domain }.toSet()
        val newMine = current.mine + found
        val lastFetch = LastFetch(System.currentTimeMillis(), rows)
        val next = current.copy(lastFetch = lastFetch, mine = newMine)
        _state.value = next
        prefs?.edit()
            ?.putStringSet("mine", newMine)
            ?.putString("last_fetch_json", serializeLastFetchForPrefs(lastFetch))
            ?.apply()
        pushStateAsync(next)
    }

    fun setNotVisited(value: Boolean) {
        val next = _state.value.copy(notVisited = value)
        _state.value = next
        prefs?.edit()?.putBoolean("notVisited", value)?.apply()
        pushStateAsync(next)
    }

    fun setWallets(value: Boolean) {
        val next = _state.value.copy(wallets = value)
        _state.value = next
        prefs?.edit()?.putBoolean("wallets", value)?.apply()
        pushStateAsync(next)
    }

    fun setMineOnly(value: Boolean) {
        val next = _state.value.copy(mineOnly = value)
        _state.value = next
        prefs?.edit()?.putBoolean("mineOnly", value)?.apply()
        pushStateAsync(next)
    }

    fun setViewMode(mode: ViewMode) {
        val next = _state.value.copy(viewMode = mode)
        _state.value = next
        prefs?.edit()?.putString("viewMode", mode.name)?.apply()
        pushStateAsync(next)
    }

    fun setSortMode(mode: SortMode) {
        val next = _state.value.copy(sortMode = mode)
        _state.value = next
        prefs?.edit()?.putString("sortMode", mode.name)?.apply()
        pushStateAsync(next)
    }

    fun setBonusMode(mode: BonusMode) {
        val next = _state.value.copy(bonusMode = mode)
        _state.value = next
        prefs?.edit()?.putString("bonusMode", mode.name)?.apply()
        pushStateAsync(next)
    }

    // Domain Intel Checks Cache (device-local)
    fun loadIntelChecks(): Map<String, DomainCheck> {
        val raw = prefs?.getString("intel_checks", "{}") ?: "{}"
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
        prefs?.edit()?.putString("intel_checks", obj.toString())?.apply()
    }

    // Public Bonus Finder Cache (device-local)
    fun loadBonusCache(): Map<String, List<PublicBonus>> {
        val raw = prefs?.getString("bonus_catalogue_cache", "{}") ?: "{}"
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
        prefs?.edit()?.putString("bonus_catalogue_cache", obj.toString())?.apply()
    }

    fun clearAll() {
        prefs?.edit()?.clear()?.apply()
        val empty = VaultState()
        _state.value = empty
        pushStateAsync(empty)
    }
}
