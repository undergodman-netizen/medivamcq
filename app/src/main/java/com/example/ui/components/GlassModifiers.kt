package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

fun Modifier.liquidGlass(shape: RoundedCornerShape) = this
    .blur(4.dp)
    .background(Color(0x03FFFFFF), shape)
    .border(
        width = 1.4.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0x73FFFFFF),
                Color(0x26FFFFFF),
                Color(0x00FFFFFF),
                Color(0x26FFFFFF),
                Color(0x73FFFFFF)
            )
        ),
        shape = shape
    )


