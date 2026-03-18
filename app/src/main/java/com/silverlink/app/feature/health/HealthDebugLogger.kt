package com.silverlink.app.feature.health

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

object HealthDebugLogger {
    const val TAG_AUTH = "SL-HealthAuth"
    const val TAG_DATA = "SL-HealthData"
    const val TAG_SDK = "SL-HealthSdk"
    const val TAG_SIGNATURE = "SL-Signature"

    fun logCurrentSigningSha1(context: Context) {
        runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray() }
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures.orEmpty().map { it.toByteArray() }
            }

            if (signatures.isEmpty()) {
                Log.w(TAG_SIGNATURE, "No signatures found for package=${context.packageName}")
                return
            }

            signatures.forEachIndexed { index, bytes ->
                val sha1 = bytes.toSha1Fingerprint()
                Log.i(
                    TAG_SIGNATURE,
                    "package=${context.packageName} signer[$index] SHA1=$sha1"
                )
            }
        }.onFailure {
            Log.e(TAG_SIGNATURE, "Failed to print signing SHA1", it)
        }
    }

    private fun ByteArray.toSha1Fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(this)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
