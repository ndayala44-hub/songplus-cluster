package com.songplus.cluster.vehicle

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** Reads this app's own signing certificate. Read-only; used to verify a platform-signed install. */
object SigningInfo {
    /** Returns (subjectDN, sha256-hex) for the first signer, or a short error pair. */
    fun of(ctx: Context): Pair<String, String> {
        return try {
            val pm = ctx.packageManager
            @Suppress("DEPRECATION")
            val sigs = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners ?: emptyArray()
            if (sigs.isEmpty()) return "unknown" to "no signer"
            val der = sigs[0].toByteArray()
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
            val dn = cert.subjectDN.name
            val sha = MessageDigest.getInstance("SHA-256").digest(der)
                .joinToString(":") { "%02X".format(it) }
            dn to sha
        } catch (t: Throwable) {
            "unknown" to (t.message ?: t.javaClass.simpleName)
        }
    }
}
