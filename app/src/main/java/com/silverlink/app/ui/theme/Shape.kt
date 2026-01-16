package com.silverlink.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Global unified large rounded corners (20dp - 24dp)
// "Soft, safe, and friendly" feel
val Shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp), // Main shape for Cards, Input Fields
    large = RoundedCornerShape(24.dp),  // Chat Bubbles, Large Buttons
    extraLarge = RoundedCornerShape(32.dp) // FABs, Pills
)
