package com.songplus.cluster.vehicle

import android.content.Context
import android.net.Uri
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build

/**
 * Read-only introspection of BYD system packages, looking for a LEGITIMATE data route: an exported ContentProvider,
 * Service or BroadcastReceiver that exposes vehicle data without a signature-level permission. If one exists, an
 * ordinary app could read it with a normal permission (or none) — no shell channel, no platform signing.
 *
 * This only queries the package manager. It opens nothing, calls no vehicle API, and changes no state.
 */
object BydComponentScan {

    enum class Kind { PROVIDER, SERVICE, RECEIVER }

    data class Comp(
        val kind: Kind,
        val pkg: String,
        val name: String,
        /** Provider authority, or receiver/service class tail. */
        val key: String,
        /** Permission guarding access, or null if none. */
        val permission: String?,
        val protection: String,
        val exported: Boolean,
        /** True when exported and the guard permission is absent or NOT signature-level — a candidate clean route. */
        val cleanCandidate: Boolean,
        /** Read-only probe result for a candidate provider (rows/columns or the exact error); null if not probed. */
        val probeResult: String? = null,
        /** Column names discovered by the provider probe, for auto-mapping to metrics. */
        val columns: List<String> = emptyList()
    )

    data class Report(val comps: List<Comp>, val candidates: Int, val packages: Int, val note: String)

    fun run(ctx: Context): Report {
        val pm = ctx.packageManager
        val pkgs = installedBydPackages(pm)
        val out = ArrayList<Comp>()
        for (pi in pkgs) {
            pi.providers?.forEach { p ->
                val perm = p.readPermission ?: p.writePermission
                out += mk(ctx, Kind.PROVIDER, p.packageName, p.name ?: "", p.authority ?: (p.name ?: ""), perm, p.exported, pm)
            }
            pi.services?.forEach { sv ->
                out += mk(ctx, Kind.SERVICE, sv.packageName, sv.name ?: "", (sv.name ?: "").substringAfterLast('.'), sv.permission, sv.exported, pm)
            }
            pi.receivers?.forEach { r ->
                out += mk(ctx, Kind.RECEIVER, r.packageName, r.name ?: "", (r.name ?: "").substringAfterLast('.'), r.permission, r.exported, pm)
            }
        }
        // Surface the most interesting first: clean candidates, then exported, then the rest.
        val sorted = out.sortedWith(compareByDescending<Comp> { it.cleanCandidate }.thenByDescending { it.exported }.thenBy { it.pkg })
        val candidates = sorted.count { it.cleanCandidate }
        val note = when {
            pkgs.isEmpty() -> "No BYD packages were visible to scan. The head unit may hide them from third-party apps."
            candidates > 0 -> "$candidates exported component(s) without a signature guard — possible legitimate data routes. These are candidates only; each still has to actually carry vehicle data."
            else -> "No exported BYD component exposes data without a signature-level permission. There is no clean IPC route on this unit; real BYD data needs platform signing."
        }
        return Report(sorted, candidates, pkgs.size, note)
    }

    private fun mk(ctx: Context, kind: Kind, pkg: String, name: String, key: String, perm: String?, exported: Boolean, pm: PackageManager): Comp {
        val prot = protectionString(perm, pm)
        val clean = exported && (perm == null || !prot.startsWith("signature"))
        // Only providers are safe to probe read-only (a query cannot trigger an action). Services/receivers are never
        // invoked here — binding or broadcasting could cause side effects, so they are listed but not called.
        val probe = if (kind == Kind.PROVIDER && clean) probeProvider(ctx, key) else Pair(null, emptyList())
        return Comp(kind, pkg, name, key, perm, prot, exported, clean, probe.first, probe.second)
    }

    /** Read-only query of an exported provider authority. Captures columns/rows or the exact failure. No writes. */
    private fun probeProvider(ctx: Context, authority: String): Pair<String, List<String>> {
        if (authority.isBlank() || authority.contains('/')) return Pair("no queryable authority", emptyList())
        return try {
            ctx.contentResolver.query(Uri.parse("content://" + authority), null, null, null, null)?.use { c ->
                val names = c.columnNames.toList()
                val cols = names.joinToString(",").take(160)
                Pair("rows=" + c.count + (if (cols.isNotBlank()) " cols=[" + cols + "]" else ""), names)
            } ?: Pair("query returned null", emptyList())
        } catch (e: SecurityException) {
            Pair("SecurityException: " + (e.message?.take(100) ?: "denied"), emptyList())
        } catch (t: Throwable) {
            Pair(t.javaClass.simpleName + ": " + (t.message?.take(100) ?: ""), emptyList())
        }
    }

    private fun installedBydPackages(pm: PackageManager): List<PackageInfo> {
        val flags = PackageManager.GET_PROVIDERS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
        val all = try {
            @Suppress("DEPRECATION") pm.getInstalledPackages(flags)
        } catch (t: Throwable) { emptyList<PackageInfo>() }
        return all.filter {
            val p = it.packageName.lowercase()
            p.contains("byd") || p.startsWith("android.hardware.bydauto") || p.contains("dilink")
        }
    }

    private fun protectionString(perm: String?, pm: PackageManager): String {
        if (perm == null) return "none"
        return try {
            val pi = pm.getPermissionInfo(perm, 0)
            val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.protection
            else @Suppress("DEPRECATION") (pi.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
            when (base) {
                PermissionInfo.PROTECTION_NORMAL -> "normal"
                PermissionInfo.PROTECTION_DANGEROUS -> "dangerous"
                PermissionInfo.PROTECTION_SIGNATURE -> "signature"
                else -> "unknown"
            }
        } catch (t: Throwable) { "undefined" }
    }
}
