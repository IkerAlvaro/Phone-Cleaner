package com.ikeralvaro.phonecleaner.core.util

import android.text.format.DateUtils

/** "hace 5 minutos" en minúscula para usarlo dentro de una frase ("Usada hace 5 minutos"). */
fun relativeTime(time: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
        .replaceFirstChar { it.lowercase() }
