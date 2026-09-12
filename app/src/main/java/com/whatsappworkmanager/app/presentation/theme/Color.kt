package com.whatsappworkmanager.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * A deliberately complete Material3 palette — every token below is hand-picked, none left to
 * Material3's auto-derivation from a single seed color. Auto-derived tokens are a reasonable
 * starting point but tend to feel slightly mismatched once a screen mixes several of them
 * (a card here, a chip there) — specifying every one intentionally is what makes the whole
 * app read as one cohesive, considered design instead of a patchwork of "close enough" colors.
 *
 * The palette itself: a deep, confident emerald/teal as the primary (a nod to WhatsApp's own
 * color language without directly copying it, since this app lives *alongside* WhatsApp, not
 * inside it), a warm amber for "needs attention," a clear red reserved only for genuinely
 * urgent state, and soft, slightly warm neutrals for surfaces rather than stark black/white —
 * easier on the eyes for something meant to be checked often throughout a workday.
 */

// Primary — emerald/teal
val PrimaryTeal = Color(0xFF0E7A63)
val PrimaryTealLight = Color(0xFF4FAE94)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFB9F0DE)
val OnPrimaryContainerLight = Color(0xFF002014)
val PrimaryContainerDark = Color(0xFF0B5B49)
val OnPrimaryContainerDark = Color(0xFFB9F0DE)

// Secondary — muted sage, for supporting text/icons that shouldn't compete with primary
val SecondaryGreen = Color(0xFF4C6359)
val SecondaryContainerLight = Color(0xFFCEE9DA)
val OnSecondaryContainerLight = Color(0xFF0A1F16)
val SecondaryContainerDark = Color(0xFF334A40)
val OnSecondaryContainerDark = Color(0xFFCEE9DA)

// Tertiary — a distinct warm accent, used sparingly (e.g. group vs. person distinction)
val TertiaryAmberDeep = Color(0xFF8C5000)
val TertiaryContainerLight = Color(0xFFFFDDB3)
val OnTertiaryContainerLight = Color(0xFF2B1700)
val TertiaryContainerDark = Color(0xFF6B3D00)
val OnTertiaryContainerDark = Color(0xFFFFDDB3)

// Neutrals — warm, soft, not stark
val BackgroundLight = Color(0xFFF6FAF7)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFE4EEE8)
val OnSurfaceLight = Color(0xFF191C1B)
val OnSurfaceVariantLight = Color(0xFF41493F)

val BackgroundDark = Color(0xFF0E1412)
val SurfaceDark = Color(0xFF171E1B)
val SurfaceVariantDark = Color(0xFF2A332E)
val OnSurfaceDark = Color(0xFFE1E3DF)
val OnSurfaceVariantDark = Color(0xFFC0CAC2)

val OutlineLight = Color(0xFFD8E1DB)
val OutlineDark = Color(0xFF3A453F)
val OutlineVariantLight = Color(0xFFC0CBC4)
val OutlineVariantDark = Color(0xFF2E3934)

// Semantic — kept consistent across the app for the same meaning everywhere
val UrgentRed = Color(0xFFD6484A)
val OnUrgentRed = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD8)
val OnErrorContainerLight = Color(0xFF410004)
val ErrorContainerDark = Color(0xFF8C1D1F)
val OnErrorContainerDark = Color(0xFFFFDAD8)

val WarnAmber = Color(0xFFE0A72E)
val OkGreen = Color(0xFF3E9C6D)
