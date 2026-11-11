package com.aistudio.pokiesvault.data.network

import com.aistudio.pokiesvault.data.model.*
import com.aistudio.pokiesvault.util.DomainUtil
import com.aistudio.pokiesvault.util.FormatUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.random.Random

class CasinoApiService {

    private data class ResolvedMerchant(
        val host: String,
        val merchantId: String,
        val supported: Boolean,
        val resolvedAt: Long = System.currentTimeMillis()
    )

    // In-memory cookie store per host so Cloudflare (__cf_bm) and PHP session cookies
    // persist across GET / -> POST /users/login -> POST /users/syncData
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val sessionCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostKey = DomainUtil.normalizeDomain(url.host)
            val list = cookieStore.getOrPut(hostKey) { mutableListOf() }
            synchronized(list) {
                for (cookie in cookies) {
                    list.removeAll { it.name == cookie.name }
                    list.add(cookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val hostKey = DomainUtil.normalizeDomain(url.host)
            val list = cookieStore[hostKey] ?: return emptyList()
            val now = System.currentTimeMillis()
            synchronized(list) {
                list.removeAll { it.expiresAt < now }
                return list.toList()
            }
        }
    }

    // Cache resolved (host, merchantId) per domain so fallback credentials and bonus checks
    // do not re-download the homepage HTML multiple times for the same site
    private val merchantCache = ConcurrentHashMap<String, ResolvedMerchant>()

    // Per-host pacing locks to prevent simultaneous worker bursts against the same backend host
    private val hostMutexes = ConcurrentHashMap<String, Mutex>()
    private val hostLastRequestTs = ConcurrentHashMap<String, Long>()

    private val client = OkHttpClient.Builder()
        .cookieJar(sessionCookieJar)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private val secChUa = "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\""

    private val merchantPattern = Pattern.compile("MERCHANTID\\s*=\\s*['\"]?(\\d+)['\"]?", Pattern.CASE_INSENSITIVE)

    /**
     * Applies realistic Mobile Chrome document navigation headers + Client Hints for initial GET / requests.
     * Uses Android 10+ Privacy Sandbox Reduced User-Agent ("K") so no hardware model or device serial is leaked.
     */
    private fun Request.Builder.applyDocumentNavigationHeaders(): Request.Builder = this
        .header("User-Agent", userAgent)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        .header("Accept-Language", "en-AU,en;q=0.9")
        .header("Sec-CH-UA", secChUa)
        .header("Sec-CH-UA-Mobile", "?1")
        .header("Sec-CH-UA-Platform", "\"Android\"")
        .header("Sec-Fetch-Dest", "document")
        .header("Sec-Fetch-Mode", "navigate")
        .header("Sec-Fetch-Site", "none")
        .header("Sec-Fetch-User", "?1")
        .header("Upgrade-Insecure-Requests", "1")

    /**
     * Applies realistic Mobile Chrome same-origin XHR/Fetch headers + Client Hints for POST /api/v1/index.php requests.
     */
    private fun Request.Builder.applySameOriginAjaxHeaders(host: String): Request.Builder = this
        .header("User-Agent", userAgent)
        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        .header("Accept", "application/json, text/plain, */*")
        .header("Accept-Language", "en-AU,en;q=0.9")
        .header("Origin", "https://$host")
        .header("Referer", "https://$host/")
        .header("X-Requested-With", "XMLHttpRequest")
        .header("Sec-CH-UA", secChUa)
        .header("Sec-CH-UA-Mobile", "?1")
        .header("Sec-CH-UA-Platform", "\"Android\"")
        .header("Sec-Fetch-Dest", "empty")
        .header("Sec-Fetch-Mode", "cors")
        .header("Sec-Fetch-Site", "same-origin")

    /**
     * Queries the device's active public egress IP, ISP/carrier, and country directly from the Android client.
     * Because all fetches run directly on-device via OkHttpClient, this is the exact IP seen by target domains.
     */
    suspend fun fetchEgressIpInfo(): EgressIpInfo = withContext(Dispatchers.IO) {
        // Primary: ipwho.is (returns IP, ISP/ASN org, country_code, city, and hosting flag)
        try {
            val req = Request.Builder()
                .url("https://ipwho.is/")
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()
            probeClient.newCall(req).execute().use { res ->
                val body = res.body?.string()
                if (res.isSuccessful && !body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    if (json.optBoolean("success", true)) {
                        val ip = json.optString("ip", "").trim()
                        if (ip.isNotEmpty()) {
                            val country = json.optString("country_code", "").trim()
                            val city = json.optString("city", "").trim()
                            val conn = json.optJSONObject("connection")
                            val ispRaw = conn?.optString("isp", "")?.ifBlank { conn.optString("org", "") }.orEmpty().trim()
                            val orgRaw = conn?.optString("org", "").orEmpty().trim()
                            val ispLabel = ispRaw.ifBlank { orgRaw }.ifBlank { "Direct Network" }
                            val security = json.optJSONObject("security")
                            val hostingFlag = security?.optBoolean("hosting", false) == true
                            val isCloud = hostingFlag || isCloudProviderName("$ispLabel $orgRaw")
                            return@withContext EgressIpInfo(
                                ip = ip,
                                isp = ispLabel,
                                countryCode = country,
                                city = city,
                                isCloudOrHosting = isCloud,
                                isLoading = false,
                                error = null
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback 1: ipinfo.io/json
        try {
            val req = Request.Builder()
                .url("https://ipinfo.io/json")
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()
            probeClient.newCall(req).execute().use { res ->
                val body = res.body?.string()
                if (res.isSuccessful && !body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val ip = json.optString("ip", "").trim()
                    if (ip.isNotEmpty()) {
                        val orgRaw = json.optString("org", "").replace(Regex("^AS\\d+\\s*"), "").trim()
                        val country = json.optString("country", "").trim()
                        val city = json.optString("city", "").trim()
                        val ispLabel = orgRaw.ifBlank { "Direct Network" }
                        return@withContext EgressIpInfo(
                            ip = ip,
                            isp = ispLabel,
                            countryCode = country,
                            city = city,
                            isCloudOrHosting = isCloudProviderName(ispLabel),
                            isLoading = false,
                            error = null
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback 2: api.ipify.org
        try {
            val req = Request.Builder()
                .url("https://api.ipify.org?format=json")
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()
            probeClient.newCall(req).execute().use { res ->
                val body = res.body?.string()
                if (res.isSuccessful && !body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val ip = json.optString("ip", "").trim()
                    if (ip.isNotEmpty()) {
                        return@withContext EgressIpInfo(
                            ip = ip,
                            isp = "Direct Egress",
                            countryCode = "",
                            city = "",
                            isCloudOrHosting = false,
                            isLoading = false,
                            error = null
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        EgressIpInfo(
            ip = "Unavailable",
            isp = "Check network connection",
            isLoading = false,
            error = "Could not resolve public IP"
        )
    }

    private fun isCloudProviderName(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("google") ||
                lower.contains("gcp") ||
                lower.contains("amazon") ||
                lower.contains("aws") ||
                lower.contains("microsoft") ||
                lower.contains("azure") ||
                lower.contains("digitalocean") ||
                lower.contains("linode") ||
                lower.contains("vultr") ||
                lower.contains("hetzner") ||
                lower.contains("ovh") ||
                lower.contains("choopa") ||
                lower.contains("datacenter") ||
                lower.contains("hosting")
    }

    /**
     * Paces requests targeting the same host so concurrent workers or rapid login->syncData
     * sequences have a slight human-like stagger (140-260ms) to avoid WAF/rate-limit triggers.
     */
    private suspend fun paceHost(host: String, minGapMs: Long = 160L) {
        val key = DomainUtil.normalizeDomain(host)
        val mutex = hostMutexes.getOrPut(key) { Mutex() }
        mutex.withLock {
            val now = System.currentTimeMillis()
            val last = hostLastRequestTs[key] ?: 0L
            val elapsed = now - last
            if (elapsed < minGapMs) {
                val jitter = Random.nextLong(25L, 95L)
                delay((minGapMs - elapsed) + jitter)
            }
            hostLastRequestTs[key] = System.currentTimeMillis()
        }
    }

    /**
     * Executes an OkHttp request with automatic exponential backoff + Retry-After support
     * for HTTP 429 (Rate Limit), 502, 503, 504, and transient network/socket timeouts.
     */
    private suspend fun <T> executeWithRetry(
        hostForPacing: String,
        maxAttempts: Int = 3,
        requestFactory: () -> Request,
        block: (Response) -> T
    ): T {
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            paceHost(hostForPacing)
            try {
                val req = requestFactory()
                client.newCall(req).execute().use { response ->
                    val code = response.code
                    if (code == 429 || code == 502 || code == 503 || code == 504) {
                        if (attempt < maxAttempts) {
                            val retryAfterSec = response.header("Retry-After")?.trim()?.toLongOrNull()
                            val backoffMs = when {
                                retryAfterSec != null && retryAfterSec in 1..15 -> retryAfterSec * 1000L
                                code == 429 -> 1200L * attempt + Random.nextLong(150L, 450L)
                                else -> 650L * attempt + Random.nextLong(100L, 300L)
                            }
                            delay(backoffMs)
                            return@use
                        } else if (code == 429) {
                            throw IOException("rate-limited")
                        }
                    }
                    return block(response)
                }
            } catch (e: SocketTimeoutException) {
                lastException = e
                if (attempt < maxAttempts) {
                    delay(500L * attempt + Random.nextLong(100L, 250L))
                }
            } catch (e: IOException) {
                lastException = e
                if (e.message == "rate-limited") throw e
                if (attempt < maxAttempts) {
                    delay(450L * attempt + Random.nextLong(100L, 250L))
                }
            }
        }
        throw lastException ?: IOException("timeout")
    }

    /**
     * Resolves and caches the canonical host and MERCHANTID for a domain (TTL: 30 minutes).
     * Eliminates redundant homepage scrapes when testing fallback credentials on the same domain.
     */
    private suspend fun resolveMerchantEndpoint(domain: String): ResolvedMerchant {
        val now = System.currentTimeMillis()
        val cached = merchantCache[domain]
        if (cached != null && (now - cached.resolvedAt) < 30 * 60 * 1000L) {
            return cached
        }

        val resolved = executeWithRetry(
            hostForPacing = domain,
            maxAttempts = 2,
            requestFactory = {
                Request.Builder()
                    .url("https://$domain/")
                    .applyDocumentNavigationHeaders()
                    .get()
                    .build()
            }
        ) { response ->
            val finalUrl = response.request.url
            var host = DomainUtil.normalizeDomain(finalUrl.host)
            if (!DomainUtil.isPublicDomain(host)) host = domain

            val html = response.body?.string() ?: ""
            var merchantId = ""
            val matcher = merchantPattern.matcher(html)
            if (matcher.find()) {
                merchantId = matcher.group(1) ?: ""
            }
            val supported = merchantId.isNotEmpty() ||
                    html.contains("api/v1/index.php") ||
                    html.contains("wallet/app.js")
            ResolvedMerchant(
                host = host,
                merchantId = merchantId,
                supported = supported
            )
        }

        merchantCache[domain] = resolved
        return resolved
    }

    suspend fun checkSiteStatus(rawDomain: String): SiteStatus = withContext(Dispatchers.IO) {
        checkSiteStatusDetailed(rawDomain).first
    }

    suspend fun checkSiteStatusDetailed(rawDomain: String): Pair<SiteStatus, Long> = withContext(Dispatchers.IO) {
        val domain = DomainUtil.normalizeDomain(rawDomain)
        if (!DomainUtil.isPublicDomain(domain)) return@withContext Pair(SiteStatus.DEAD, 0L)

        val started = System.currentTimeMillis()
        try {
            paceHost(domain, minGapMs = 80L)
            val request = Request.Builder()
                .url("https://$domain/")
                .applyDocumentNavigationHeaders()
                .get()
                .build()

            probeClient.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - started
                val code = response.code
                val status = when {
                    code in 300..399 -> SiteStatus.REDIRECT
                    code in 200..399 || code == 403 || code == 429 -> if (elapsed > 2800) SiteStatus.SLOW else SiteStatus.LIVE
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
            val endpoint = resolveMerchantEndpoint(domain)
            if (endpoint.merchantId.isEmpty()) return@withContext emptyList()

            val host = endpoint.host
            val body = FormBody.Builder()
                .add("module", "/merchants/getPublicInfo")
                .add("merchantId", endpoint.merchantId)
                .add("accessId", "")
                .add("accessToken", "")
                .add("walletIsAdmin", "")
                .build()

            executeWithRetry(
                hostForPacing = host,
                maxAttempts = 2,
                requestFactory = {
                    Request.Builder()
                        .url("https://$host/api/v1/index.php")
                        .applySameOriginAjaxHeaders(host)
                        .post(body)
                        .build()
                }
            ) { res ->
                val jsonStr = res.body?.string() ?: return@executeWithRetry emptyList()
                val json = JSONObject(jsonStr)
                if (json.optString("status") != "SUCCESS") return@executeWithRetry emptyList()
                val dataObj = json.optJSONObject("data") ?: return@executeWithRetry emptyList()
                val promoArr = dataObj.optJSONArray("promotions") ?: return@executeWithRetry emptyList()
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
        val phone = phoneRaw.replace("[\\s\\-()]".toRegex(), "")
        if (!Regex("^[0-9+]{6,20}$").matches(phone)) {
            return@withContext AccountResult.Err(domain, "other", "Enter the mobile number you use on these sites")
        }
        if (passwordRaw.length < 4 || passwordRaw.length > 64) {
            return@withContext AccountResult.Err(domain, "other", "Password should be 4–64 characters")
        }

        try {
            // Step 0: Resolve (or reuse cached) merchantId & canonical host
            val endpoint = resolveMerchantEndpoint(domain)
            if (!endpoint.supported) {
                return@withContext AccountResult.Err(domain, "unsupported", "Different login system")
            }

            val host = endpoint.host
            val merchantId = endpoint.merchantId

            // Step 1: Login with retry & rate-limit backoff
            val loginBody = FormBody.Builder()
                .add("module", "/users/login")
                .add("merchantId", merchantId)
                .add("accessId", "")
                .add("accessToken", "")
                .add("walletIsAdmin", "")
                .add("mobile", phone)
                .add("password", passwordRaw)
                .build()

            val loginJson = executeWithRetry(
                hostForPacing = host,
                maxAttempts = 3,
                requestFactory = {
                    Request.Builder()
                        .url("https://$host/api/v1/index.php")
                        .applySameOriginAjaxHeaders(host)
                        .post(loginBody)
                        .build()
                }
            ) { res ->
                val str = res.body?.string()?.trim() ?: throw IOException("empty-login")
                if (!str.startsWith("{")) throw IOException("bad-json")
                JSONObject(str)
            }

            val loginStatus = loginJson.optString("status")
            if (loginStatus != "SUCCESS") {
                val dataObj = loginJson.optJSONObject("data")
                val msg = dataObj?.optString("message") ?: loginJson.optString("message", "Could not sign in")
                return@withContext classifyFailure(domain, msg)
            }

            val dataObj = loginJson.optJSONObject("data") ?: throw IOException("no-data")
            val accessId = dataObj.optString("id", "")
            val accessToken = dataObj.optString("token", "")
            if (accessId.isEmpty() || accessToken.isEmpty()) {
                return@withContext AccountResult.Err(domain, "other", "Sign-in did not return a session")
            }

            // Step 2: syncData with retry & rate-limit backoff
            val syncBody = FormBody.Builder()
                .add("module", "/users/syncData")
                .add("merchantId", merchantId)
                .add("accessId", accessId)
                .add("accessToken", accessToken)
                .add("walletIsAdmin", "")
                .add("background", "1")
                .build()

            val syncJson = executeWithRetry(
                hostForPacing = host,
                maxAttempts = 3,
                requestFactory = {
                    Request.Builder()
                        .url("https://$host/api/v1/index.php")
                        .applySameOriginAjaxHeaders(host)
                        .post(syncBody)
                        .build()
                }
            ) { res ->
                val str = res.body?.string()?.trim() ?: throw IOException("empty-sync")
                if (!str.startsWith("{")) throw IOException("bad-json")
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
            lower.contains("invalid login") ||
                    lower.contains("invalid password") ||
                    lower.contains("wrong password") ||
                    lower.contains("user not found") ||
                    lower.contains("account not found") ||
                    lower.contains("mobile not found") -> AccountResult.Err(domain, "invalid_login", "Not your account")
            lower.contains("rate-limited") || lower.contains("too many requests") || lower.contains("429") ->
                AccountResult.Err(domain, "rate_limited", "Rate limited by site")
            lower.contains("captcha") || lower.contains("cloudflare") ->
                AccountResult.Err(domain, "captcha", "Site asked for a captcha")
            lower.contains("bad-json") || lower.contains("empty-") ->
                AccountResult.Err(domain, "unreachable", "Site did not return account data")
            lower.contains("timeout") || lower.contains("abort") || lower.contains("timed out") ->
                AccountResult.Err(domain, "timeout", "Timed out")
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
