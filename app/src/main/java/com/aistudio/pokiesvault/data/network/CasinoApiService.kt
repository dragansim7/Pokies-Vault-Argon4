package com.aistudio.pokiesvault.data.network

import com.aistudio.pokiesvault.data.model.*
import com.aistudio.pokiesvault.util.DomainUtil
import com.aistudio.pokiesvault.util.FormatUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class CasinoApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val userAgent = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

    suspend fun checkSiteStatus(rawDomain: String): SiteStatus = withContext(Dispatchers.IO) {
        checkSiteStatusDetailed(rawDomain).first
    }

    suspend fun checkSiteStatusDetailed(rawDomain: String): Pair<SiteStatus, Long> = withContext(Dispatchers.IO) {
        val domain = DomainUtil.normalizeDomain(rawDomain)
        if (!DomainUtil.isPublicDomain(domain)) return@withContext Pair(SiteStatus.DEAD, 0L)

        val started = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url("https://$domain/")
                .header("User-Agent", userAgent)
                .header("Accept", "text/html")
                .get()
                .build()

            probeClient.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - started
                val code = response.code
                val status = when {
                    code in 300..399 -> SiteStatus.REDIRECT
                    code in 200..399 -> if (elapsed > 2800) SiteStatus.SLOW else SiteStatus.LIVE
                    else -> SiteStatus.DEAD
                }
                Pair(status, elapsed)
            }
        } catch (_: Exception) {
            Pair(SiteStatus.DEAD, System.currentTimeMillis() - started)
        }
    }

    suspend fun fetchAllLiveStatuses(): Map<String, SiteStatus> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, SiteStatus>()
        try {
            val request = Request.Builder()
                .url("https://intel.bestpokieslist.online/public/statuses")
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val str = response.body?.string() ?: return@withContext emptyMap()
                val json = try {
                    JSONArray(str)
                } catch (_: Exception) {
                    val obj = JSONObject(str)
                    obj.optJSONArray("domains") ?: obj.optJSONArray("statuses") ?: JSONArray()
                }

                for (i in 0 until json.length()) {
                    val item = json.optJSONObject(i) ?: continue
                    val domain = DomainUtil.normalizeDomain(item.optString("domain", ""))
                    if (!DomainUtil.isPublicDomain(domain)) continue
                    val raw = item.optString("status", "").lowercase()
                    val status = when (raw) {
                        "live", "up", "ok" -> SiteStatus.LIVE
                        "slow" -> SiteStatus.SLOW
                        "redirect" -> SiteStatus.REDIRECT
                        "dead", "down" -> SiteStatus.DEAD
                        else -> SiteStatus.UNKNOWN
                    }
                    map[domain] = status
                }
            }
        } catch (_: Exception) {
            // Graceful fallback to empty map
        }
        map
    }

    suspend fun loadPublicPromotions(rawDomain: String): List<PublicBonus> = withContext(Dispatchers.IO) {
        val domain = DomainUtil.normalizeDomain(rawDomain)
        if (!DomainUtil.isPublicDomain(domain)) return@withContext emptyList()

        try {
            val homeRequest = Request.Builder()
                .url("https://$domain/")
                .header("User-Agent", userAgent)
                .header("Accept", "text/html")
                .get()
                .build()

            var merchantId = ""
            var host = domain

            client.newCall(homeRequest).execute().use { response ->
                val finalUrl = response.request.url
                host = DomainUtil.normalizeDomain(finalUrl.host)
                if (!DomainUtil.isPublicDomain(host)) host = domain

                val html = response.body?.string() ?: ""
                val matcher = Pattern.compile("MERCHANTID\\s*=\\s*(\\d+)").matcher(html)
                if (matcher.find()) {
                    merchantId = matcher.group(1) ?: ""
                }
            }

            if (merchantId.isEmpty()) return@withContext emptyList()

            val body = FormBody.Builder()
                .add("module", "/merchants/getPublicInfo")
                .add("merchantId", merchantId)
                .add("accessId", "")
                .add("accessToken", "")
                .add("walletIsAdmin", "")
                .build()

            val req = Request.Builder()
                .url("https://$host/api/v1/index.php")
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json,text/plain,*/*")
                .header("Origin", "https://$host")
                .header("Referer", "https://$host/")
                .post(body)
                .build()

            client.newCall(req).execute().use { res ->
                val jsonStr = res.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(jsonStr)
                if (json.optString("status") != "SUCCESS") return@withContext emptyList()
                val dataObj = json.optJSONObject("data") ?: return@withContext emptyList()
                val promoArr = dataObj.optJSONArray("promotions") ?: return@withContext emptyList()
                parsePublicPromotions(promoArr)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parsePublicPromotions(promoArr: JSONArray): List<PublicBonus> {
        val list = mutableListOf<PublicBonus>()
        for (i in 0 until promoArr.length()) {
            val p = promoArr.optJSONObject(i) ?: continue
            val rawName = p.optString("name", "")
            val name = cleanName(rawName)
            if (name.isBlank()) continue

            val claimConfig = parseClaimConfig(p.opt("claimConfig"))
            val custom = claimConfig.optString("customBonus", "")
            val slot = slotOf(name, custom)

            val fixed = p.optDouble("bonusFixed", 0.0)
            val percent = p.optDouble("bonus", 0.0)
            val random = p.optJSONObject("bonusRandom")
            val rmin = random?.optDouble("min", 0.0)
            val rmax = random?.optDouble("max", 0.0)

            var kind = BonusKind.UNSET
            var amount = 0.0
            var amountDisplay = "—"

            if (fixed > 0.0) {
                kind = BonusKind.FIXED
                amount = fixed
                amountDisplay = FormatUtil.moneyCompact(fixed)
            } else if (percent > 0.0) {
                kind = BonusKind.PERCENT
                amount = percent
                val pctStr = if (percent % 1.0 == 0.0) percent.toInt().toString() else String.format("%.0f", percent)
                amountDisplay = "$pctStr%"
            } else if (rmin != null && rmax != null && (rmin > 0.0 || rmax > 0.0)) {
                kind = BonusKind.RANDOM
                amount = rmax
                amountDisplay = "${FormatUtil.moneyCompact(rmminSafe(rmin))}–${FormatUtil.moneyCompact(rmax)}"
            }

            val minWithdraw = p.optDouble("minWithdraw", -1.0).takeIf { it > 0.0 }
            val maxWithdraw = p.optDouble("maxWithdraw", -1.0).takeIf { it > 0.0 }
            val rollover = p.optDouble("rollover", -1.0).takeIf { it > 0.0 }
            val minDeposit = p.optDouble("minTopup", -1.0).takeIf { it > 0.0 }

            list.add(
                PublicBonus(
                    id = p.optString("id", name),
                    name = name,
                    slot = slot,
                    kind = kind,
                    amount = amount,
                    amountDisplay = amountDisplay,
                    minWithdraw = minWithdraw,
                    maxWithdraw = maxWithdraw,
                    rollover = rollover,
                    minDeposit = minDeposit
                )
            )
        }
        return list
    }

    private fun rmminSafe(min: Double): Double = if (min < 0.0) 0.0 else min

    private fun slotOf(name: String, custom: String): BonusSlot {
        val text = "$name $custom".lowercase()
        return when {
            text.contains("birth") || text.contains("bday") || text.contains("b-day") -> BonusSlot.BIRTHDAY
            text.contains("share") || text.contains("refer") || text.contains("invite") || text.contains("downline") -> BonusSlot.SHARE
            text.contains("daily") || text.contains("every day") -> BonusSlot.DAILY
            text.contains("signup") || text.contains("sign up") || text.contains("register") || text.contains("no deposit") || text.contains("welcome") -> BonusSlot.SIGNUP
            else -> BonusSlot.OTHER
        }
    }

    suspend fun fetchCasinoAccount(
        rawDomain: String,
        phoneRaw: String,
        passwordRaw: String
    ): AccountResult = withContext(Dispatchers.IO) {
        val domain = DomainUtil.normalizeDomain(rawDomain)
        if (!DomainUtil.isPublicDomain(domain)) {
            return@withContext AccountResult.Err(domain, "other", "Enter a real site domain")
        }
        val phone = phoneRaw.replace("\\s+".toRegex(), "")
        if (!Regex("^[0-9+]{6,20}$").matches(phone)) {
            return@withContext AccountResult.Err(domain, "other", "Enter the mobile number you use on these sites")
        }
        if (passwordRaw.length < 4 || passwordRaw.length > 64) {
            return@withContext AccountResult.Err(domain, "other", "Password should be 4–64 characters")
        }

        try {
            val homeRequest = Request.Builder()
                .url("https://$domain/")
                .header("User-Agent", userAgent)
                .header("Accept", "text/html")
                .get()
                .build()

            var merchantId = ""
            var host = domain

            client.newCall(homeRequest).execute().use { response ->
                val finalUrl = response.request.url
                host = DomainUtil.normalizeDomain(finalUrl.host)
                if (!DomainUtil.isPublicDomain(host)) host = domain

                val html = response.body?.string() ?: ""
                val matcher = Pattern.compile("MERCHANTID\\s*=\\s*(\\d+)").matcher(html)
                if (matcher.find()) {
                    merchantId = matcher.group(1) ?: ""
                }
                if (merchantId.isEmpty() && !html.contains("api/v1/index.php") && !html.contains("wallet/app.js")) {
                    return@withContext AccountResult.Err(domain, "unsupported", "Different login system")
                }
            }

            // Step 1: Login
            val loginBody = FormBody.Builder()
                .add("module", "/users/login")
                .add("merchantId", merchantId)
                .add("accessId", "")
                .add("accessToken", "")
                .add("walletIsAdmin", "")
                .add("mobile", phone)
                .add("password", passwordRaw)
                .build()

            val loginRequest = Request.Builder()
                .url("https://$host/api/v1/index.php")
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json,text/plain,*/*")
                .header("Origin", "https://$host")
                .header("Referer", "https://$host/")
                .post(loginBody)
                .build()

            val loginJson = client.newCall(loginRequest).execute().use { res ->
                val str = res.body?.string() ?: throw Exception("empty-login")
                JSONObject(str)
            }

            val loginStatus = loginJson.optString("status")
            if (loginStatus != "SUCCESS") {
                val dataObj = loginJson.optJSONObject("data")
                val msg = dataObj?.optString("message") ?: loginJson.optString("message", "Could not sign in")
                return@withContext classifyFailure(domain, msg)
            }

            val dataObj = loginJson.optJSONObject("data") ?: throw Exception("no-data")
            val accessId = dataObj.optString("id", "")
            val accessToken = dataObj.optString("token", "")
            if (accessId.isEmpty() || accessToken.isEmpty()) {
                return@withContext AccountResult.Err(domain, "other", "Sign-in did not return a session")
            }

            // Step 2: syncData
            val syncBody = FormBody.Builder()
                .add("module", "/users/syncData")
                .add("merchantId", merchantId)
                .add("accessId", accessId)
                .add("accessToken", accessToken)
                .add("walletIsAdmin", "")
                .add("background", "1")
                .build()

            val syncRequest = Request.Builder()
                .url("https://$host/api/v1/index.php")
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json,text/plain,*/*")
                .header("Origin", "https://$host")
                .header("Referer", "https://$host/")
                .post(syncBody)
                .build()

            val syncJson = client.newCall(syncRequest).execute().use { res ->
                val str = res.body?.string() ?: throw Exception("empty-sync")
                JSONObject(str)
            }

            if (syncJson.optString("status") != "SUCCESS") {
                val sData = syncJson.optJSONObject("data")
                val msg = sData?.optString("message") ?: "Signed in, but balances did not load"
                return@withContext classifyFailure(domain, msg)
            }

            val userObj = syncJson.optJSONObject("data") ?: JSONObject()
            parseSyncUser(domain, userObj)
        } catch (e: Exception) {
            classifyFailure(domain, e.message ?: "Could not reach site")
        }
    }

    private fun classifyFailure(domain: String, message: String): AccountResult {
        val lower = message.lowercase()
        return when {
            lower.contains("invalid login") || lower.contains("invalid password") -> AccountResult.Err(domain, "invalid_login", "Not your account")
            lower.contains("captcha") -> AccountResult.Err(domain, "captcha", "Site asked for a captcha")
            lower.contains("bad-json") || lower.contains("empty-") -> AccountResult.Err(domain, "unreachable", "Site did not return account data")
            lower.contains("timeout") || lower.contains("abort") -> AccountResult.Err(domain, "timeout", "Timed out")
            else -> AccountResult.Err(domain, "other", message.ifBlank { "Could not sign in" })
        }
    }

    private fun parseSyncUser(domain: String, user: JSONObject): AccountResult {
        val wallets = mutableListOf<WalletView>()
        val walletArr = user.optJSONArray("wallet") ?: JSONArray()
        for (i in 0 until walletArr.length()) {
            val w = walletArr.optJSONObject(i) ?: continue
            val id = w.optString("id", "")
            val name = cleanName(w.optString("name", "Balance"))
            val balance = w.optDouble("balance", 0.0)
            wallets.add(WalletView(id, name.ifBlank { "Balance" }, balance))
        }

        var cash = user.optDouble("cash", 0.0)
        var cashWalletName = "Balance"
        for (w in wallets) {
            if (w.balance > 0.001) {
                cash = w.balance
                cashWalletName = w.name
            }
        }

        val minWithdraw = user.optDouble("minWithdraw", -1.0).takeIf { it > 0.0 }
        val maxWithdraw = user.optDouble("maxWithdraw", -1.0).takeIf { it > 0.0 }

        val taken = user.optJSONObject("timebonus") ?: JSONObject()
        val commissionList = mutableListOf<BonusView>()
        val shareList = mutableListOf<BonusView>()
        val referralList = mutableListOf<BonusView>()
        val newList = mutableListOf<BonusView>()
        val rebateList = mutableListOf<BonusView>()
        val availableBonuses = mutableListOf<BonusView>()

        val bonusArr = user.optJSONArray("bonus") ?: JSONArray()
        for (i in 0 until bonusArr.length()) {
            val b = bonusArr.optJSONObject(i) ?: continue
            val id = b.optString("id", "")
            val maxRound = b.optInt("maxRound", 0)
            val used = taken.optInt(id, 0)
            if (maxRound > 0 && used >= maxRound) continue

            val claimConfig = parseClaimConfig(b.opt("claimConfig"))
            val creditLessThan = claimConfig.optDouble("creditLessThan", -1.0)
            if (creditLessThan > 0.0 && cash >= creditLessThan) continue

            val name = cleanName(b.optString("name", "Bonus")).ifBlank { "Bonus" }
            val custom = claimConfig.optString("customBonus", "")
            val fixed = b.optDouble("bonusFixed", 0.0)
            val percent = b.optDouble("bonus", 0.0)
            val random = b.optJSONObject("bonusRandom")
            val rmin = random?.optDouble("min", 0.0)
            val rmax = random?.optDouble("max", 0.0)
            val rollover = b.optDouble("rollover", -1.0).takeIf { it > 0.0 }

            var amount = 0.0
            var amountDisplay = ""
            var isPercent = false

            if (fixed > 0.0) {
                amount = fixed
                amountDisplay = FormatUtil.moneyCompact(fixed)
            } else if (rmin != null && rmax != null && rmax > 0.0) {
                amount = rmax
                amountDisplay = "${FormatUtil.moneyCompact(rmminSafe(rmin))}–${FormatUtil.moneyCompact(rmax)}"
            } else if (percent > 0.0) {
                amount = 0.0
                val pctStr = if (percent % 1.0 == 0.0) percent.toInt().toString() else String.format("%.0f", percent)
                amountDisplay = "$pctStr% match"
                isPercent = true
            } else {
                continue
            }

            val minW = claimConfig.optDouble("minWithdraw", claimConfig.optDouble("minWithdrawal", -1.0)).takeIf { it > 0.0 }
            val maxW = claimConfig.optDouble("maxWithdraw", claimConfig.optDouble("maxWithdrawal", -1.0)).takeIf { it > 0.0 }

            val view = BonusView(
                name = name,
                amount = amount,
                amountDisplay = amountDisplay,
                minWithdrawal = minW,
                maxWithdrawal = maxW,
                rollover = rollover,
                isPercent = isPercent
            )
            availableBonuses.add(view)

            when (categoryOf(name, custom)) {
                "commission" -> commissionList.add(view)
                "share" -> shareList.add(view)
                "referral" -> referralList.add(view)
                "new" -> newList.add(view)
                "rebate" -> rebateList.add(view)
            }
        }

        availableBonuses.sortWith(compareByDescending<BonusView> { it.amount }.thenBy { it.name })

        fun pick(list: List<BonusView>): BonusView? {
            if (list.isEmpty()) return null
            val dollars = list.filter { !it.isPercent }
            val pool = if (dollars.isNotEmpty()) dollars else list
            return pool.maxByOrNull { it.amount }
        }

        val commission = pick(commissionList)
        val share = pick(shareList)
        val referral = pick(referralList)
        val fresh = pick(newList)
        val rebate = pick(rebateList)

        val hasData = cash > 0.001 || commission != null || share != null || referral != null || fresh != null || rebate != null || availableBonuses.isNotEmpty()

        return AccountResult.Ok(
            domain = domain,
            hasData = hasData,
            cash = cash,
            cashWalletName = cashWalletName,
            cashMinWithdrawal = minWithdraw,
            cashMaxWithdrawal = maxWithdraw,
            wallets = wallets,
            commission = commission,
            share = share,
            referral = referral,
            newMember = fresh,
            rebate = rebate,
            availableBonuses = availableBonuses
        )
    }

    private fun parseClaimConfig(raw: Any?): JSONObject {
        if (raw is String) {
            return try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
        }
        if (raw is JSONObject) return raw
        return JSONObject()
    }

    private fun cleanName(str: String): String {
        return str
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun categoryOf(name: String, custom: String): String? {
        val c = custom.lowercase()
        val n = name.lowercase()
        return when {
            c.contains("share") || n.contains("share") -> "share"
            c.contains("commission") || n.contains("commission") -> "commission"
            c.contains("rebate") || n.contains("rebate") || n.contains("cashback") || n.contains("cash back") -> "rebate"
            c.contains("refer") || c.contains("downline") || c.contains("invite") || n.contains("refer") || n.contains("invite") || n.contains("mate") -> "referral"
            c.contains("welcome") || c.contains("signup") || c.contains("newmember") || n.contains("welcome") || n.contains("sign up") || n.contains("new member") -> "new"
            else -> null
        }
    }
}
