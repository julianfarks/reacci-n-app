package com.instantreact.service

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnóstico del servicio de accesibilidad. Guarda en archivos privados: la última excepción no capturada,
 * los eventos del ciclo de vida del servicio y los motivos con los que Android cerró el proceso. Todo se
 * puede copiar desde la pantalla principal («Copiar informe») para saber qué pasa en un móvil concreto.
 */
object Diagnostics {
    const val TAG = "InstantReact"
    private const val CRASH = "last_crash.txt"
    private const val EVENTS = "service_events.txt"

    @Volatile private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                record(CRASH, "Hilo ${t.name}\n" + Log.getStackTraceString(e), append = false)
                record(EVENTS, "CRASH del proceso: ${e.javaClass.name}: ${e.message}")
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    /** Anota un evento (con hora) en el archivo y en Logcat. */
    fun event(msg: String, error: Throwable? = null) {
        if (error != null) Log.e(TAG, msg, error) else Log.i(TAG, msg)
        record(EVENTS, msg + (error?.let { "\n" + Log.getStackTraceString(it) } ?: ""))
    }

    private fun record(name: String, text: String, append: Boolean = true) {
        val ctx = appContext ?: return
        try {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val f = File(ctx.filesDir, name)
            val line = "[$stamp] $text\n"
            if (append) {
                if (f.exists() && f.length() > 20_000) f.writeText(f.readText().takeLast(8_000))
                f.appendText(line)
            } else {
                f.writeText(line)
            }
        } catch (_: Throwable) {
        }
    }

    fun isEnabledInSettings(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val id = "${context.packageName}/${ReactAccessibilityService::class.java.name}"
        return flat.split(':').any { it.equals(id, true) || it.equals("${context.packageName}/.service.ReactAccessibilityService", true) }
    }

    fun report(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("Instant React · informe de diagnóstico")
        sb.appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Throwable) { "?" }
        sb.appendLine("App: ${context.packageName} v$version")
        sb.appendLine("Activado en Ajustes: ${isEnabledInSettings(context)}")
        sb.appendLine("onServiceConnected ejecutado (servicio vivo en este proceso): ${ReactAccessibilityService.instance != null}")
        sb.appendLine("Última conexión: ${ReactAccessibilityService.lastConnected?.let { Date(it) } ?: "nunca en este proceso"}")
        val svc = ReactAccessibilityService.instance
        if (svc != null) {
            sb.appendLine("canPerformGestures (capacidad concedida): ${svc.serviceInfo?.capabilities?.and(android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0}")
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, 6)
                sb.appendLine("Últimos cierres del proceso (Android):")
                if (exits.isEmpty()) sb.appendLine("  ninguno")
                for (e in exits) {
                    sb.appendLine("  ${Date(e.timestamp)} · ${reasonName(e.reason)} · ${e.description ?: ""} · estado ${e.status}")
                }
            } catch (t: Throwable) {
                sb.appendLine("Cierres del proceso: no disponible (${t.javaClass.simpleName})")
            }
        }
        sb.appendLine("--- Última excepción no capturada ---")
        sb.appendLine(read(context, CRASH) ?: "ninguna")
        sb.appendLine("--- Eventos del servicio ---")
        sb.appendLine(read(context, EVENTS)?.takeLast(3000) ?: "ninguno")
        return sb.toString()
    }

    private fun read(context: Context, name: String): String? = try {
        File(context.filesDir, name).takeIf { it.exists() }?.readText()?.trim()
    } catch (_: Throwable) { null }

    private fun reasonName(r: Int): String = if (Build.VERSION.SDK_INT < 30) "$r" else when (r) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED (parada forzosa)"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED (matado por el sistema)"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "$r"
    }
}
