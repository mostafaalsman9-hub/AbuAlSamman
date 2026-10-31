package com.abualsamman.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.IBinder
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * خدمة مقدمة (Foreground Service من نوع microphone).
 * - تبدأ من داخل التطبيق وهو ظاهر (شرط أندرويد 14/15/16 لاستخدام الميكروفون).
 * - تراقب حالة المكالمة؛ عند بدء المكالمة (OFFHOOK) تبدأ التسجيل، وعند انتهائها (IDLE) توقفه.
 * - تظهر إشعاراً دائماً يوضح للمستخدم أن الحماية فعّالة وأن التسجيل جارٍ.
 */
class RecordingService : Service() {

    private var recorder: MediaRecorder? = null
    private var tm: TelephonyManager? = null

    private val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> startRecording()
                TelephonyManager.CALL_STATE_IDLE -> stopRecording()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        startForeground(
            NOTIF_ID, buildNotification("في انتظار مكالمة…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        if (tm == null) {
            tm = getSystemService(TelephonyManager::class.java)
            try {
                tm?.registerTelephonyCallback(mainExecutor, callback)
            } catch (e: SecurityException) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        running = true
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (recorder != null) return
        val dir = File(getExternalFilesDir(null), "recordings").apply { mkdirs() }
        val name = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(dir, "call_$name.m4a")
        try {
            recorder = MediaRecorder(this).apply {
                // المصدر MIC هو المتاح للتطبيقات العادية في أندرويد 10+
                // (مصدر VOICE_CALL محجوب على التطبيقات غير النظامية).
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            updateNotification("🔴 جارٍ تسجيل المكالمة…")
        } catch (e: Exception) {
            recorder?.release(); recorder = null
            updateNotification("تعذر بدء التسجيل على هذا الجهاز")
        }
    }

    private fun stopRecording() {
        recorder?.let {
            try { it.stop() } catch (_: RuntimeException) { }
            it.release()
            updateNotification("تم حفظ التسجيل — في انتظار مكالمة…")
        }
        recorder = null
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ابو السمان")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "إيقاف الحماية", stop).build())
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "تسجيل المكالمات", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    override fun onDestroy() {
        stopRecording()
        tm?.unregisterTelephonyCallback(callback)
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.abualsamman.recorder.STOP"
        private const val CHANNEL_ID = "call_rec"
        private const val NOTIF_ID = 1001
        @Volatile var running = false

        fun start(ctx: Context) { ctx.startForegroundService(Intent(ctx, RecordingService::class.java)) }
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, RecordingService::class.java).setAction(ACTION_STOP)) }
    }
}
