package com.houseofsilver.cardscanner.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands a URL to whatever app the user has for it. The scanner itself holds no internet
 * permission — this is ordinary navigation the user asked for, done by the browser or the
 * LinkedIn app, not a network call from this app.
 */
fun openUrl(context: Context, url: String) {
    val absolute = if (url.startsWith("http://") || url.startsWith("https://")) {
        url
    } else {
        "https://$url"
    }
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(absolute))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        // No browser installed — nothing useful to do
    }
}
