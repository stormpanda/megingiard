package com.stormpanda.megingiard.gamefocus.domain

import android.app.ActivityOptions
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.StrictMode
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.EMULATOR_ID_YUZU
import com.stormpanda.megingiard.catalog.RomLauncher
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.catalog.SafPathResolver
import java.io.File

private const val TAG = "YuzuLauncher"
private const val MIME_TYPE_OCTET_STREAM = "application/octet-stream"
private const val EMULATION_ACTIVITY_SUFFIX = ".activities.EmulationActivity"
private const val CLIP_DATA_LABEL_ROM = "rom"

/**
 * ROM launcher implementation for Nintendo Switch emulators in the Yuzu family
 * (Citron, Yuzu, Sudachi, Suyu).
 */
class YuzuLauncher : RomLauncher {
    override val id: String = EMULATOR_ID_YUZU
    override val displayName: String = "Yuzu"

    companion object {
        val supportedPackages: List<String> =
            listOf(
                "org.citron.citron_emu",
                "org.citron.citron_emu.debug",
                "org.yuzu.yuzu_emu",
                "org.yuzu.yuzu_emu.ea",
                "org.sudachi.sudachi_emu",
                "org.sudachi.sudachi_emu.ea",
                "com.suyu.suyu",
            )
    }

    override suspend fun launchGame(
        context: Context,
        romPath: String,
        systemId: String,
        displayId: Int,
        retroArchCore: String?,
        romUri: String?,
    ): Boolean {
        val packageName = getInstalledYuzuPackage(context)
        if (packageName == null) {
            AppLog.e(
                TAG,
                "No supported Yuzu-compatible emulator installed (checked ${supportedPackages.joinToString()})",
            )
            return false
        }

        val uri = resolveRomUri(romPath, romUri)
        val emulationActivityName = "$packageName$EMULATION_ACTIVITY_SUFFIX"
        val hasExplicitActivity = isActivityAvailable(context, packageName, emulationActivityName)

        AppLog.i(
            TAG,
            "Launching Switch ROM '$romPath' (uri=$uri) via $packageName (activity=$emulationActivityName, explicit=$hasExplicitActivity) on display $displayId",
        )

        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, MIME_TYPE_OCTET_STREAM)
                clipData = ClipData.newRawUri(CLIP_DATA_LABEL_ROM, uri)
                if (hasExplicitActivity) {
                    component = ComponentName(packageName, emulationActivityName)
                } else {
                    setPackage(packageName)
                }
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
                )
            }

        val options =
            ActivityOptions.makeBasic().apply {
                setLaunchDisplayId(displayId)
            }

        val oldPolicy = StrictMode.getVmPolicy()
        return try {
            // Temporarily relax StrictMode VmPolicy file URI exposure checks on file:// URIs
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
            context.startActivity(intent, options.toBundle())
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to launch Switch game via $packageName: ${e.message}", e)
            false
        } finally {
            StrictMode.setVmPolicy(oldPolicy)
        }
    }

    private fun resolveRomUri(
        romPath: String,
        romUri: String? = null,
    ): Uri {
        if (!romUri.isNullOrBlank() && romUri.startsWith("content://")) {
            return Uri.parse(romUri)
        }
        if (romPath.startsWith("content://")) {
            return Uri.parse(romPath)
        }
        val treeUris = RomManager.romFolders.value.map { it.uriString }
        val resolvedSafUri = SafPathResolver.resolveContentUri(romPath, treeUris)
        if (resolvedSafUri != null) {
            return resolvedSafUri
        }
        return Uri.fromFile(File(romPath))
    }

    private fun getInstalledYuzuPackage(context: Context): String? {
        val pm = context.packageManager
        return supportedPackages.firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (e: Exception) {
                AppLog.w(TAG, "Error checking package info for $pkg: ${e.message}")
                false
            }
        }
    }

    private fun isActivityAvailable(
        context: Context,
        packageName: String,
        activityName: String,
    ): Boolean {
        val pm = context.packageManager
        return try {
            pm.getActivityInfo(ComponentName(packageName, activityName), 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            AppLog.w(TAG, "Error checking activity info for $activityName: ${e.message}")
            false
        }
    }
}
