package com.aistudio.pokiesvault.data.model

enum class SiteGroup(val label: String) {
    NEW("New"),
    BEST("Best"),
    UNVERIFIED("Unverified"),
    SHARE("Instant share bonuses"),
    LATEST("Latest"),
    CUSTOM("Added by you")
}

data class CatalogSite(
    val domain: String,
    val group: SiteGroup,
    val addedAt: String = ""
)

enum class SiteStatus(val label: String) {
    CHECKING("Checking"),
    LIVE("Live"),
    SLOW("Slow"),
    REDIRECT("Redirect"),
    DEAD("Down"),
    UNKNOWN("Unknown")
}

enum class ViewMode {
    GROUPS, ONE, TWO
}

enum class SortMode(val label: String) {
    DEFAULT("Featured groups"),
    AZ("Domain A–Z"),
    ZA("Domain Z–A"),
    NEW("Newest added"),
    BALANCE("Wallet balance")
}

enum class BonusMode(val label: String) {
    NONE("Nothing extra"),
    NEW("Welcome"),
    SHARE("Share"),
    COMMISSION("Commission"),
    REFERRAL("Referral"),
    REBATE("Rebate")
}

data class FallbackCred(
    val phone: String = "",
    val password: String = ""
)

data class BonusView(
    val name: String,
    val amount: Double,
    val amountDisplay: String,
    val minWithdrawal: Double? = null,
    val maxWithdrawal: Double? = null,
    val rollover: Double? = null,
    val isPercent: Boolean = false
)

data class WalletView(
    val id: String,
    val name: String,
    val balance: Double
)

sealed class AccountResult {
    abstract val domain: String

    data class Ok(
        override val domain: String,
        val hasData: Boolean,
        val cash: Double,
        val cashWalletName: String,
        val cashMinWithdrawal: Double?,
        val cashMaxWithdrawal: Double?,
        val wallets: List<WalletView>,
        val commission: BonusView?,
        val share: BonusView?,
        val referral: BonusView?,
        val newMember: BonusView?,
        val rebate: BonusView?,
        val availableBonuses: List<BonusView>
    ) : AccountResult()

    data class Err(
        override val domain: String,
        val code: String,
        val message: String
    ) : AccountResult()
}

data class LastFetch(
    val timestamp: Long,
    val rows: List<AccountResult>
)

// Public Bonus & Bonus Finder Models
enum class BonusSlot(val id: String, val label: String) {
    SIGNUP("signup", "Signup"),
    SHARE("share", "Share"),
    DAILY("daily", "Daily"),
    BIRTHDAY("birthday", "Birthday"),
    OTHER("other", "Other")
}

enum class BonusKind(val id: String, val label: String) {
    ANY("any", "Any"),
    FIXED("fixed", "Fixed amount"),
    PERCENT("percent", "Percent"),
    RANDOM("random", "Random range"),
    UNSET("unset", "Unset")
}

data class PublicBonus(
    val id: String,
    val name: String,
    val slot: BonusSlot,
    val kind: BonusKind,
    val amount: Double,
    val amountDisplay: String,
    val minWithdraw: Double? = null,
    val maxWithdraw: Double? = null,
    val rollover: Double? = null,
    val minDeposit: Double? = null
)

enum class BonusSort(val id: String, val label: String) {
    LIST("list", "List order"),
    AZ("az", "Domain A–Z"),
    AMOUNT("amount", "Highest amount"),
    ROLLOVER("rollover", "Lowest rollover"),
    CASHOUT("cashout", "Highest max cashout")
}

data class BonusFinderFilters(
    val slots: Set<BonusSlot> = emptySet(),
    val kind: BonusKind = BonusKind.ANY,
    val amountMin: String = "",
    val amountMax: String = "",
    val rollMin: String = "",
    val rollMax: String = "",
    val maxMin: String = "",
    val maxMax: String = "",
    val sort: BonusSort = BonusSort.LIST
)

data class DomainCheck(
    val status: SiteStatus,
    val ms: Long,
    val timestamp: Long
)
