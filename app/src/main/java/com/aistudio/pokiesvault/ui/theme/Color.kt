package com.aistudio.pokiesvault.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class VaultColors(
    val id: String,
    val name: String,
    val hint: String,
    val bg: Color,
    val panel: Color,
    val panel2: Color,
    val fg: Color,
    val muted: Color,
    val faint: Color,
    val cyan: Color,
    val violet: Color,
    val pink: Color,
    val ink: Color,
    val live: Color = Color(0xFF3DDC97),
    val slow: Color = Color(0xFFE2B657),
    val dead: Color = Color(0xFFFF6C82),
    val border: Color = Color(0x22F5F5F7)
) {
    val gradient: Brush
        get() = Brush.linearGradient(listOf(cyan, violet, pink))
}

val ThemeVault = VaultColors(
    id = "vault",
    name = "Vault",
    hint = "Cyan, violet, pink",
    bg = Color(0xFF050507),
    panel = Color(0xFF0C0C0E),
    panel2 = Color(0xFF141418),
    fg = Color(0xFFF5F5F7),
    muted = Color(0xFF92929E),
    faint = Color(0xFF6F6F79),
    cyan = Color(0xFF00E5FF),
    violet = Color(0xFF7B5CFF),
    pink = Color(0xFFFF2FB0),
    ink = Color(0xFF070708)
)

val ThemeEmerald = VaultColors(
    id = "emerald",
    name = "Emerald",
    hint = "Mint and gold",
    bg = Color(0xFF06110C),
    panel = Color(0xFF0C1A14),
    panel2 = Color(0xFF12241C),
    fg = Color(0xFFF3F7F4),
    muted = Color(0xFF8EA398),
    faint = Color(0xFF6D8074),
    cyan = Color(0xFF3DFFC5),
    violet = Color(0xFF1FA87A),
    pink = Color(0xFFD6B25E),
    ink = Color(0xFF04110C)
)

val ThemeArctic = VaultColors(
    id = "arctic",
    name = "Arctic",
    hint = "Ice and steel",
    bg = Color(0xFF070B12),
    panel = Color(0xFF101722),
    panel2 = Color(0xFF172033),
    fg = Color(0xFFF4F7FB),
    muted = Color(0xFF93A0B5),
    faint = Color(0xFF6E7C90),
    cyan = Color(0xFF8FD6FF),
    violet = Color(0xFF6AA6FF),
    pink = Color(0xFFD7E4F5),
    ink = Color(0xFF05080E)
)

val ThemeEmber = VaultColors(
    id = "ember",
    name = "Ember",
    hint = "Amber and copper",
    bg = Color(0xFF100806),
    panel = Color(0xFF1A100C),
    panel2 = Color(0xFF261812),
    fg = Color(0xFFF8F3EF),
    muted = Color(0xFFB5A197),
    faint = Color(0xFF8A786C),
    cyan = Color(0xFFFFB15A),
    violet = Color(0xFFFF6A3D),
    pink = Color(0xFFFF4D6A),
    ink = Color(0xFF0C0604)
)

val ThemeRoyal = VaultColors(
    id = "royal",
    name = "Royal",
    hint = "Gold and violet",
    bg = Color(0xFF0A0812),
    panel = Color(0xFF141022),
    panel2 = Color(0xFF1C1730),
    fg = Color(0xFFF6F4FB),
    muted = Color(0xFFA39BB8),
    faint = Color(0xFF7C748F),
    cyan = Color(0xFFE6C36A),
    violet = Color(0xFF9B6BFF),
    pink = Color(0xFFF0A3FF),
    ink = Color(0xFF07060E)
)

val ThemeAmber = VaultColors(
    id = "amber",
    name = "Amber",
    hint = "Dark gold",
    bg = Color(0xFF070605),
    panel = Color(0xFF12100D),
    panel2 = Color(0xFF1B1814),
    fg = Color(0xFFF4EFE8),
    muted = Color(0xFFA89886),
    faint = Color(0xFF7D6E5E),
    cyan = Color(0xFFF6D7A2),
    violet = Color(0xFFE0A04A),
    pink = Color(0xFF8D5A22),
    ink = Color(0xFF0B0907)
)

val ALL_THEMES = listOf(ThemeVault, ThemeEmerald, ThemeArctic, ThemeEmber, ThemeRoyal, ThemeAmber)

fun getThemeById(id: String): VaultColors = ALL_THEMES.find { it.id == id } ?: ThemeVault

val LocalVaultColors = staticCompositionLocalOf { ThemeVault }

// Backward compatibility constants pointing to default ThemeVault
val VaultBg get() = ThemeVault.bg
val VaultPanel get() = ThemeVault.panel
val VaultPanel2 get() = ThemeVault.panel2
val VaultFg get() = ThemeVault.fg
val VaultMuted get() = ThemeVault.muted
val VaultFaint get() = ThemeVault.faint
val VaultCyan get() = ThemeVault.cyan
val VaultViolet get() = ThemeVault.violet
val VaultPink get() = ThemeVault.pink
val VaultLive get() = ThemeVault.live
val VaultSlow get() = ThemeVault.slow
val VaultDead get() = ThemeVault.dead
val VaultInk get() = ThemeVault.ink
val VaultBorder get() = ThemeVault.border
val VaultGradient get() = ThemeVault.gradient
