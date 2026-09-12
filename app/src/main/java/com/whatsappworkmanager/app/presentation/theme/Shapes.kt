package com.whatsappworkmanager.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One consistent corner-radius scale for the whole app, referenced via `MaterialTheme.shapes.*`
 * instead of each screen picking its own `RoundedCornerShape(20.dp)` ad hoc. Small differences
 * like that — one card at 16dp, another at 20dp, a dialog at 12dp, all in the same screen —
 * are a big part of what makes a UI read as "random" rather than considered, even when no
 * single choice looks wrong on its own.
 */
val WwmShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
