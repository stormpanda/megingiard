package com.stormpanda.megingiard

import android.app.Activity
import android.app.ActivityOptions
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Display
import com.stormpanda.megingiard.catalog.DisplayDetector

private const val TAG = "LaunchTrampolineAct"

/**
 * Builds the forwarded [MainActivity] intent from [sourceIntent], preserving URI/MIME pairs via
 * [Intent.setDataAndType] and synthesizing [ClipData] + read permission when `ACTION_SEND` delivers
 * a stream only in [Intent.EXTRA_STREAM].
 */
internal fun buildMainActivityForwardIntent(
    context: Context,
    sourceIntent: Intent?,
): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = sourceIntent?.action
        setDataAndType(sourceIntent?.data, sourceIntent?.type)
        val incomingClip = sourceIntent?.clipData
        if (incomingClip != null) {
            clipData = incomingClip
        } else if (sourceIntent?.action == Intent.ACTION_SEND) {
            val streamUri = sourceIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            if (streamUri != null) {
                clipData = ClipData.newRawUri("", streamUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        sourceIntent?.extras?.let { putExtras(it) }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        sourceIntent?.flags?.let { originalFlags ->
            if ((originalFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if ((originalFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
    }

/**
 * Invisible entry point Activity that routes launches of Megingiard to the secondary
 * (bottom) display on dual-screen devices such as the AYN Thor.
 */
class LaunchTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val secondaryDisplay = DisplayDetector.findSecondaryDisplay(this)
            val targetDisplayId = secondaryDisplay?.displayId ?: Display.DEFAULT_DISPLAY

            AppLog.i(
                TAG,
                "Routing launch to display $targetDisplayId (action=${intent?.action}, data=${intent?.data}, secondary=${secondaryDisplay?.displayId})",
            )

            val options =
                ActivityOptions.makeBasic().apply {
                    setLaunchDisplayId(targetDisplayId)
                }
            val targetIntent = buildMainActivityForwardIntent(this, intent)
            startActivity(targetIntent, options.toBundle())
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to route launch intent to MainActivity", e)
        } finally {
            finish()
        }
    }
}
