package com.aistudio.pokiesvault.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object DomainUtil {
    fun normalizeDomain(value: String): String {
        var domain = value.trim().lowercase()
        domain = domain.replace(Regex("^https?://"), "")
        domain = domain.substringBefore("/").substringBefore("?").substringBefore(":")
        domain = domain.removePrefix("www.")
        return domain
    }

    fun isPublicDomain(domain: String): Boolean {
        if (!Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$").matches(domain)) {
            return false
        }
        if (domain.length > 253) return false
        if (Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$").matches(domain)) return false
        if (domain == "localhost" || domain.endsWith(".local") || domain.endsWith(".internal") || domain.endsWith(".localhost")) {
            return false
        }
        return true
    }
}

object FormatUtil {
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "AU"))

    fun money(amount: Double?): String {
        val value = amount ?: 0.0
        return currencyFormat.format(value)
    }

    fun moneyCompact(amount: Double): String {
        val rounded = Math.round(amount * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) {
            "$" + rounded.toLong()
        } else {
            String.format(Locale.US, "$%.2f", rounded)
        }
    }

    fun formatAdded(isoString: String): String {
        if (isoString.isBlank()) return ""
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val date = inputFormat.parse(isoString.substringBefore("+")) ?: return ""
            val outputFormat = SimpleDateFormat("dd/MM/yyyy · h:mm a", Locale("en", "AU")).apply {
                timeZone = TimeZone.getTimeZone("Australia/Melbourne")
            }
            outputFormat.format(date)
        } catch (_: Exception) {
            ""
        }
    }

    fun initials(domain: String): String {
        val letters = domain.replace(Regex("[^a-zA-Z0-9]"), "")
        val sub = if (letters.length >= 2) letters.substring(0, 2) else "PK"
        return sub.uppercase(Locale.US)
    }
}
