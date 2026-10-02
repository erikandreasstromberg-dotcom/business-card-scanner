package com.houseofsilver.cardscanner.email

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import java.net.URLEncoder

/**
 * Hands off a pre-populated draft to whichever email app the user has installed
 * (Outlook, Gmail, etc.). We never send anything ourselves — the user hits Send
 * in their own client, so the email lives in their real Sent folder.
 */
object MailtoSender {

    data class Result(val launched: Boolean, val error: String? = null)

    data class EmailApp(val label: String, val packageName: String)

    /**
     * Payment and messaging apps register as `mailto:` handlers so they can offer
     * "send money to an email address". They are never what we want here.
     */
    private val NON_EMAIL_PACKAGE_HINTS = listOf(
        "paypal", "venmo", "cash.app", "squareup", "revolut", "wise.android",
        "skype", "whatsapp", "telegram", "messenger"
    )

    /** Returns installed apps that handle `mailto:` intents (Outlook, Gmail, etc.). */
    fun installedEmailApps(context: Context): List<EmailApp> {
        val probe = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:test@example.com"))
        val pm = context.packageManager
        val infos: List<ResolveInfo> = pm.queryIntentActivities(probe, 0)
        return infos
            .map { EmailApp(label = it.loadLabel(pm).toString(), packageName = it.activityInfo.packageName) }
            .filter { app ->
                val pkg = app.packageName.lowercase()
                NON_EMAIL_PACKAGE_HINTS.none { hint -> pkg.contains(hint) }
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun send(
        context: Context,
        to: String,
        subject: String,
        body: String,
        preferredPackage: String? = null
    ): Result {
        val uri = buildMailto(to, subject, body)
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // If the user picked a preferred app AND it's still installed AND still handles mailto:,
        // skip the chooser and go straight there. Otherwise fall back to the chooser.
        val pkg = preferredPackage?.takeIf { it.isNotBlank() }
        val useDirect = pkg != null && installedEmailApps(context).any { it.packageName == pkg }

        return try {
            if (useDirect) {
                intent.setPackage(pkg)
                context.startActivity(intent)
            } else {
                val chooser = Intent.createChooser(intent, "Send email with").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
            Result(launched = true)
        } catch (e: ActivityNotFoundException) {
            Result(launched = false, error = "No email app installed")
        } catch (e: Exception) {
            Result(launched = false, error = e.message ?: "Failed to open email app")
        }
    }

    /**
     * Opens the email app with a file already attached. Uses ACTION_SEND rather than a
     * `mailto:` URI because attachments can't ride on a URI, and a long CSV body would be
     * truncated by most clients anyway.
     */
    fun sendAttachment(
        context: Context,
        attachment: Uri,
        mimeType: String,
        subject: String,
        body: String,
        preferredPackage: String? = null
    ): Result {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, attachment)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pkg = preferredPackage?.takeIf { it.isNotBlank() }
        // The preferred app is only usable here if it also accepts attachments of this type.
        val directOk = pkg != null &&
            context.packageManager.queryIntentActivities(Intent(intent).setPackage(pkg), 0).isNotEmpty()

        return try {
            if (directOk) {
                context.startActivity(Intent(intent).setPackage(pkg))
            } else {
                context.startActivity(
                    Intent.createChooser(intent, "Send export with")
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            }
            Result(launched = true)
        } catch (e: ActivityNotFoundException) {
            Result(launched = false, error = "No app can send this attachment")
        } catch (e: Exception) {
            Result(launched = false, error = e.message ?: "Failed to open email app")
        }
    }

    private fun buildMailto(to: String, subject: String, body: String): Uri {
        val q = buildString {
            append("subject=").append(encode(subject))
            append("&body=").append(encode(body))
        }
        return Uri.parse("mailto:${Uri.encode(to)}?$q")
    }

    private fun encode(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
