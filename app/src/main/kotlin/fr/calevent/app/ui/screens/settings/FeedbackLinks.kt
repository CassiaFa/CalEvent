package fr.calevent.app.ui.screens.settings

import android.net.Uri

/** Prefilled GitHub issue forms, opened in the browser (the app itself does no networking). */
internal enum class FeedbackKind(val template: String) { BUG("bug.yml"), FEEDBACK("feedback.yml") }

internal fun variantLabel(flavor: String): String = when (flavor) {
    "play" -> "ML Kit (play)"
    "foss" -> "F-Droid (foss)"
    else -> flavor
}

internal fun feedbackUrl(kind: FeedbackKind, version: String, flavor: String, device: String, android: String): Uri =
    Uri.Builder()
        .scheme("https")
        .authority("github.com")
        .appendPath("CassiaFa").appendPath("CalEvent").appendPath("issues").appendPath("new")
        .appendQueryParameter("template", kind.template)
        .appendQueryParameter("version", version)
        .appendQueryParameter("variant", variantLabel(flavor))
        .appendQueryParameter("device", device)
        .appendQueryParameter("android", android)
        .build()
