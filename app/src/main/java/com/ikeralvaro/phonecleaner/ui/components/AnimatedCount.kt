package com.ikeralvaro.phonecleaner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/** Cuenta animada de 0 al valor (p. ej. los bytes liberados). */
@Composable
fun animatedCount(target: Long, durationMs: Int = 1_200): Long {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
    return (target * anim.value).toLong()
}
