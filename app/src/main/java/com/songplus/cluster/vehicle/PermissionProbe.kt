package com.songplus.cluster.vehicle

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build

/**
 * Honest, read-only diagnostic for the BYDAUTO_* permissions the app declares.
 *
 * For each declared permission it reports whether the permission is defined on this device, its protection level,
 * and whether it is currently granted. It NEVER changes a grant state and NEVER makes [checkSelfPermission] lie:
 * it only reads what the platform reports, so we can tell whether a legitimate runtime grant is even possible.
 */
object PermissionProbe {

    data class Entry(
        val name: String,
        /** True if some package on this device defines this permission. */
        val defined: Boolean,
        /** Human-readable protection level, e.g. "signature", "dangerous", "signatureOrSystem|privileged". */
        val protection: String,
        /** null when the permission is not defined on this device. */
        val granted: Boolean?,
        /** True only when the platform could grant this to a normal app (normal or dangerous, and defined). */
        val grantable: Boolean
    )

    data class Report(
        val entries: List<Entry>,
        /** Permissions that are defined, not yet granted, and grantable by a runtime request or Settings. */
        val requestable: List<String>,
        /** True when at least one needed permission can be obtained without manufacturer signing. */
        val anyGrantable: Boolean,
        val verdict: String
    )

    /** The permission prefix that matters for vehicle data. */
    private const val PREFIX = "android.permission.BYDAUTO"

    fun run(ctx: Context): Report {
        val pm = ctx.packageManager
        val declared: List<String> = try {
            val info = pm.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
            (info.requestedPermissions ?: emptyArray()).filter { it.startsWith(PREFIX) }.sorted()
        } catch (t: Throwable) {
            emptyList()
        }

        val entries = declared.map { name ->
            val pi: PermissionInfo? = try { pm.getPermissionInfo(name, 0) } catch (t: PackageManager.NameNotFoundException) { null } catch (t: Throwable) { null }
            if (pi == null) {
                Entry(name, defined = false, protection = "not defined on this device", granted = null, grantable = false)
            } else {
                val prot = protectionString(pi)
                val granted = ctx.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
                val base = baseProtection(pi)
                val grantable = base == PermissionInfo.PROTECTION_NORMAL || base == PermissionInfo.PROTECTION_DANGEROUS
                Entry(name, defined = true, protection = prot, granted = granted, grantable = grantable)
            }
        }

        val requestable = entries.filter { it.defined && it.grantable && it.granted == false }.map { it.name }
        val anyGrantable = entries.any { it.defined && it.grantable }
        val definedCount = entries.count { it.defined }
        val verdict = when {
            entries.isEmpty() -> "No BYDAUTO permissions are declared in the manifest."
            definedCount == 0 -> "None of these permissions are defined on this device — the BYD framework may be absent or named differently. Runtime requests cannot help."
            anyGrantable -> "${requestable.size} of $definedCount permission(s) can be requested at runtime. Tap \"Request grantable permissions\". Anything still denied is signature-protected."
            else -> "All defined BYDAUTO permissions are signature / privileged level. A normal installed app cannot be granted them by a runtime request — they require the head unit's own provisioning (engineering/factory menu, or an app signed with the platform key). Use OBD2 for real speed instead."
        }
        return Report(entries, requestable, anyGrantable, verdict)
    }

    private fun baseProtection(pi: PermissionInfo): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.protection
        else @Suppress("DEPRECATION") (pi.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)

    private fun protectionString(pi: PermissionInfo): String {
        val base = when (baseProtection(pi)) {
            PermissionInfo.PROTECTION_NORMAL -> "normal"
            PermissionInfo.PROTECTION_DANGEROUS -> "dangerous"
            PermissionInfo.PROTECTION_SIGNATURE -> "signature"
            else -> "unknown"
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.protectionFlags
        else @Suppress("DEPRECATION") (pi.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE.inv())
        val parts = buildList {
            if (flags and PermissionInfo.PROTECTION_FLAG_PRIVILEGED != 0) add("privileged")
            if (flags and PermissionInfo.PROTECTION_FLAG_SYSTEM != 0) add("system")
            if (flags and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT != 0) add("development")
            if (flags and PermissionInfo.PROTECTION_FLAG_APPOP != 0) add("appop")
            if (flags and PermissionInfo.PROTECTION_FLAG_PRE23 != 0) add("pre23")
            if (flags and PermissionInfo.PROTECTION_FLAG_INSTALLER != 0) add("installer")
        }
        return if (parts.isEmpty()) base else base + "|" + parts.joinToString("|")
    }
}
