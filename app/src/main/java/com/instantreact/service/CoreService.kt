package com.instantreact.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.Image
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.widget.Toast
import com.instantreact.R
import com.instantreact.core.ColorClassifier
import com.instantreact.core.DetectorConfig
import com.instantreact.core.Engine
import com.instantreact.core.FrameProcessor
import com.instantreact.core.TriggerListener
import com.instantreact.ui.CalibrationOverlay
import com.instantreact.ui.DebugOverlay
import com.instantreact.ui.FloatingPanel
import com.instantreact.MainActivity

/** Captura reducida de pantalla (rejilla ARGB) usada solo por el autodetector de círculos. */
class Snapshot(val pixels: IntArray, val w: Int, val h: Int, val step: Int)

/**
 * Servicio en primer plano: mantiene la captura de pantalla (MediaProjection), procesa cada
 * fotograma y dispara los toques. También aloja la ventana flotante y la calibración.
 */
class CoreService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var captureThread: HandlerThread
    private lateinit var tapThread: HandlerThread
    private lateinit var captureHandler: Handler
    private lateinit var tapHandler: Handler

    private var capture: ScreenCapture? = null
    private var panel: FloatingPanel? = null
    private var calibration: CalibrationOverlay? = null
    private var debugOverlay: DebugOverlay? = null
    private lateinit var processor: FrameProcessor

    private var appliedCfg: DetectorConfig? = null
    private var needWarm = false
    private var frameTime = 0L

    private val snapLock = Any()
    private var snapBuf: IntArray? = null
    private var snapW = 0
    private var snapH = 0
    @Volatile
    private var snapshotEnabled = false

    private val triggerListener = TriggerListener { index -> onTrigger(index, frameTime) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startEngine(intent)
            ACTION_STOP -> stopSelf()
            ACTION_CALIBRATE -> showCalibration()
            else -> if (capture == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun startEngine(intent: Intent) {
        if (capture != null) return
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        val code = intent.getIntExtra(EXTRA_CODE, 0)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_DATA)
        }
        if (data == null) {
            stopSelf()
            return
        }

        Prefs.apply(this)
        processor = FrameProcessor(Engine.config)

        captureThread = HandlerThread("ir-capture", Process.THREAD_PRIORITY_URGENT_DISPLAY).also { it.start() }
        tapThread = HandlerThread("ir-tap", Process.THREAD_PRIORITY_URGENT_DISPLAY).also { it.start() }
        captureHandler = Handler(captureThread.looper)
        tapHandler = Handler(tapThread.looper)

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = try {
            mpm.getMediaProjection(code, data)
        } catch (e: Exception) {
            null
        }
        if (projection == null) {
            Toast.makeText(this, "No se pudo iniciar la captura de pantalla", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        val cap = ScreenCapture(
            this,
            projection,
            captureHandler,
            { image -> onFrame(image) },
            { main.post { stopSelf() } },
        )
        capture = cap
        try {
            cap.start()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al iniciar la captura: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        Engine.captureActive = true
        Engine.running = false
        instance = this

        panel = FloatingPanel(this, object : FloatingPanel.Callbacks {
            override fun onStart() {
                if (!Engine.config.calibrated) {
                    Toast.makeText(this@CoreService, "Primero calibra los puntos", Toast.LENGTH_SHORT).show()
                    return
                }
                if (ReactAccessibilityService.instance == null) {
                    Toast.makeText(
                        this@CoreService,
                        "Activa el servicio de accesibilidad de Instant React para poder pulsar",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                // Sin reset: el estado de cada punto se mantiene también con la detección parada, así que un
                // círculo ya azul al pulsar INICIAR no dispara; solo reacciona al siguiente cambio gris -> azul.
                Engine.running = true
            }

            override fun onStop() {
                Engine.running = false
            }

            override fun onOpenSettings() = openMain()

            override fun onToggleDebug() = toggleDebug()

            override fun onCalibrate() = showCalibration()
        }).also { it.show() }
    }

    private fun toggleDebug() {
        Engine.debugMode = !Engine.debugMode
        if (Engine.debugMode) {
            if (debugOverlay == null) debugOverlay = DebugOverlay(this) { processor.debug }.also { it.show() }
        } else {
            debugOverlay?.hide()
            debugOverlay = null
        }
        panel?.refresh()
    }

    private fun openMain() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(i)
    }

    // ---------------------------------------------------------------- frames

    private fun onFrame(img: Image) {
        frameTime = SystemClock.elapsedRealtimeNanos()
        val cfg = Engine.config
        if (cfg !== appliedCfg) {
            processor.updateConfig(cfg)
            appliedCfg = cfg
            needWarm = true
        }
        val plane = img.planes[0]
        processor.process(
            plane.buffer,
            plane.rowStride,
            plane.pixelStride,
            img.width,
            img.height,
            Engine.running,
            triggerListener,
        )
        Engine.orientationMismatch = processor.orientationMismatch
        if (needWarm && cfg.calibrated && !processor.orientationMismatch) {
            TapExecutor.warmUp(processor.tapX, processor.tapY, cfg.tapDurationMs)
            needWarm = false
        }
        if (snapshotEnabled) writeSnapshot(plane, img.width, img.height)
    }

    private fun onTrigger(index: Int, tFrame: Long) {
        Engine.stats.onDetected()
        val cfg = Engine.config
        val x = processor.tapX[index]
        val y = processor.tapY[index]
        val delay = cfg.delayMs
        val dur = cfg.tapDurationMs
        if (delay <= 0L) {
            doTap(x, y, tFrame, 0L, dur, index)
        } else {
            tapHandler.postDelayed({ doTap(x, y, tFrame, delay, dur, index) }, delay)
        }
    }

    private fun doTap(x: Int, y: Int, tFrame: Long, delayMs: Long, durMs: Long, index: Int) {
        val tDispatch = SystemClock.elapsedRealtimeNanos()
        val reaction = (tDispatch - tFrame) / 1_000_000.0
        val system = maxOf(0.0, reaction - delayMs)
        val stats = Engine.stats
        stats.onDispatched(reaction, system, delayMs, index)
        processor.markPressed(index, reaction)
        val ok = TapExecutor.tap(x, y, durMs, object : TapExecutor.Result {
            override fun onCompleted() {
                stats.onTapCompleted((SystemClock.elapsedRealtimeNanos() - tFrame) / 1_000_000.0)
            }

            override fun onFailed() {
                stats.onGestureCancelled()
            }
        })
        if (!ok) stats.onGestureCancelled()
    }

    private fun writeSnapshot(plane: Image.Plane, w: Int, h: Int) {
        val step = SNAP_STEP
        val gw = w / step
        val gh = h / step
        val buf = plane.buffer
        val rs = plane.rowStride
        val ps = plane.pixelStride
        synchronized(snapLock) {
            var out = snapBuf
            if (out == null || out.size != gw * gh) {
                out = IntArray(gw * gh)
                snapBuf = out
            }
            snapW = gw
            snapH = gh
            var o = 0
            for (gy in 0 until gh) {
                val base = gy * step * rs
                for (gx in 0 until gw) {
                    val idx = base + gx * step * ps
                    out[o++] = ColorClassifier.pack(
                        buf.get(idx).toInt() and 0xFF,
                        buf.get(idx + 1).toInt() and 0xFF,
                        buf.get(idx + 2).toInt() and 0xFF,
                    )
                }
            }
        }
    }

    private fun snapshot(): Snapshot? = synchronized(snapLock) {
        val b = snapBuf ?: return null
        Snapshot(b.clone(), snapW, snapH, SNAP_STEP)
    }

    // ----------------------------------------------------------- calibración

    private fun showCalibration() {
        val cap = capture ?: run {
            Toast.makeText(this, "Activa primero la captura desde la app", Toast.LENGTH_SHORT).show()
            return
        }
        if (calibration != null) return
        Engine.running = false
        panel?.hide()
        snapshotEnabled = true
        calibration = CalibrationOverlay(this, Engine.config, Engine.profileName, cap.width, cap.height, object : CalibrationOverlay.Callbacks {
            override fun onSave(cfg: DetectorConfig) {
                Prefs.saveCalibration(this@CoreService, cfg)
                Toast.makeText(this@CoreService, "Calibración guardada", Toast.LENGTH_SHORT).show()
                closeCalibration()
            }

            override fun onCancel() = closeCalibration()

            override fun grabSnapshot(): Snapshot? = snapshot()
        }).also { it.show() }
    }

    private fun closeCalibration() {
        snapshotEnabled = false
        calibration?.hide()
        calibration = null
        panel?.show()
    }

    // ------------------------------------------------------------- ciclo vida

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        capture?.resizeToDisplay()
        needWarm = true
    }

    override fun onDestroy() {
        Engine.running = false
        Engine.captureActive = false
        instance = null
        calibration?.hide()
        calibration = null
        debugOverlay?.hide()
        debugOverlay = null
        Engine.debugMode = false
        panel?.hide()
        panel = null
        capture?.stop()
        capture = null
        if (::captureThread.isInitialized) captureThread.quitSafely()
        if (::tapThread.isInitialized) tapThread.quitSafely()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Instant React", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, CoreService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Instant React activo")
            .setContentText("Captura de pantalla y panel flotante en marcha")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat), "Detener", stop).build())
            .build()
    }

    companion object {
        const val ACTION_START = "com.instantreact.START"
        const val ACTION_STOP = "com.instantreact.STOP"
        const val ACTION_CALIBRATE = "com.instantreact.CALIBRATE"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL_ID = "instant_react"
        private const val NOTIF_ID = 1001
        private const val SNAP_STEP = 4

        @Volatile
        var instance: CoreService? = null

        fun start(ctx: Context, resultCode: Int, data: Intent) {
            val i = Intent(ctx, CoreService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, CoreService::class.java).setAction(ACTION_STOP))
        }

        fun calibrate(ctx: Context) {
            ctx.startService(Intent(ctx, CoreService::class.java).setAction(ACTION_CALIBRATE))
        }
    }
}
