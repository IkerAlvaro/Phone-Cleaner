package com.ikeralvaro.phonecleaner.ui.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val APPS = "apps"
    const val FILES = "files"
    const val SYSTEM = "system"
    const val CLEANER = "cleaner?autoStart={autoStart}"
    const val BOOST = "boost"
    const val BATTERY = "battery"
    const val COMPRESS = "compress"
    const val SETTINGS = "settings"
    const val APP_DETAIL = "app/{packageName}"
    const val MEDIA_REVIEW = "media/{category}"

    fun cleaner(autoStart: Boolean) = "cleaner?autoStart=$autoStart"
    fun appDetail(packageName: String) = "app/$packageName"
    fun mediaReview(category: String) = "media/$category"

    val topLevel = listOf(HOME, APPS, FILES, SYSTEM)
}
