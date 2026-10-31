package com.abualsamman.recorder

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var permsText: TextView
    private lateinit var toggleBtn: Button
    private lateinit var list: ListView
    private var files: List<File> = emptyList()
    private var player: MediaPlayer? = null

    private val permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refresh()
        if (allGranted()) toggleService()
        else Toast.makeText(this, "يلزم منح كل الصلاحيات ليعمل التسجيل", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (!prefs().getBoolean("consent", false)) showConsent()
    }

    override fun onResume() { super.onResume(); refresh() }

    override fun onDestroy() { player?.release(); super.onDestroy() }

    // ---------------- UI ----------------
    private fun buildUi() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(TextView(this).apply {
            text = "ابو السمان"; textSize = 28f; setTextColor(Color.parseColor("#0B5D3B"))
            gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "تسجيل المكالمات"; gravity = Gravity.CENTER; textSize = 14f
        })
        statusText = TextView(this).apply { textSize = 16f; setPadding(0, pad, 0, pad / 2) }
        permsText = TextView(this).apply { textSize = 14f }
        toggleBtn = Button(this).apply { setOnClickListener { onToggleClicked() } }
        val infoBtn = Button(this).apply {
            text = "ما هي الصلاحيات ولماذا؟"; setOnClickListener { showPermissionsExplained() }
        }
        val tip = TextView(this).apply {
            text = "💡 لالتقاط صوت الطرفين: شغّل مكبّر الصوت أثناء المكالمة، " +
                "فالنظام يمنع التطبيقات العادية من تسجيل صوت الطرف الآخر مباشرة."
            textSize = 13f; setPadding(0, pad / 2, 0, pad / 2)
        }
        list = ListView(this)
        root.addView(statusText); root.addView(permsText)
        root.addView(toggleBtn); root.addView(infoBtn); root.addView(tip)
        root.addView(TextView(this).apply { text = "التسجيلات:"; textSize = 16f })
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        list.setOnItemClickListener { _, _, pos, _ -> play(files[pos]) }
        list.setOnItemLongClickListener { _, _, pos, _ -> fileActions(files[pos]); true }
    }

    private fun refresh() {
        val on = RecordingService.running
        statusText.text = if (on) "✅ الحماية تعمل — سيبدأ التسجيل تلقائياً مع أي مكالمة"
        else "⛔ الحماية متوقفة"
        toggleBtn.text = if (on) "إيقاف التسجيل التلقائي" else "تشغيل التسجيل التلقائي"
        permsText.text = permissions.joinToString("\n") {
            (if (granted(it)) "✔ " else "✘ ") + permLabel(it)
        }
        loadRecordings()
    }

    private fun loadRecordings() {
        val dir = File(getExternalFilesDir(null), "recordings")
        files = dir.listFiles { f -> f.extension == "m4a" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        val fmt = SimpleDateFormat("yyyy/MM/dd  HH:mm:ss", Locale.getDefault())
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            files.map { "${fmt.format(Date(it.lastModified()))}   (${it.length() / 1024} KB)" })
    }

    // ---------------- Permissions ----------------
    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun allGranted() = permissions.all { granted(it) }

    private fun permLabel(p: String) = when (p) {
        Manifest.permission.RECORD_AUDIO -> "الميكروفون"
        Manifest.permission.READ_PHONE_STATE -> "حالة الهاتف"
        else -> "الإشعارات"
    }

    private fun showPermissionsExplained() {
        AlertDialog.Builder(this).setTitle("الصلاحيات المطلوبة")
            .setMessage(
                "🎤 الميكروفون:\nلالتقاط صوتك وصوت الطرف الآخر (عند تشغيل مكبّر الصوت) أثناء المكالمة.\n\n" +
                "📞 حالة الهاتف:\nلمعرفة وقت بدء المكالمة وانتهائها كي يبدأ التسجيل ويتوقف تلقائياً. " +
                "التطبيق لا يقرأ أرقامك ولا سجل المكالمات.\n\n" +
                "🔔 الإشعارات:\nلإظهار إشعار دائم يُعلمك أن التسجيل يعمل — وهو إلزامي من النظام.\n\n" +
                "📁 التخزين:\nلا يحتاج صلاحية؛ التسجيلات تُحفظ في مجلد التطبيق الخاص فقط ولا تُرسل لأي جهة."
            ).setPositiveButton("حسناً", null).show()
    }

    private fun showConsent() {
        AlertDialog.Builder(this).setTitle("تنبيه قانوني مهم")
            .setMessage(
                "تسجيل المكالمات قد يتطلب موافقة جميع الأطراف بحسب قوانين بلدك.\n\n" +
                "بالمتابعة فأنت تتعهد بإبلاغ الطرف الآخر واستخدام التسجيلات بشكل قانوني فقط."
            ).setCancelable(false)
            .setPositiveButton("أوافق") { _, _ -> prefs().edit().putBoolean("consent", true).apply() }
            .setNegativeButton("خروج") { _, _ -> finish() }.show()
    }

    // ---------------- Service ----------------
    private fun onToggleClicked() {
        if (!allGranted()) { permLauncher.launch(permissions); return }
        toggleService()
    }

    private fun toggleService() {
        if (RecordingService.running) RecordingService.stop(this)
        else RecordingService.start(this) // يجب أن يكون التطبيق ظاهراً (شرط أندرويد 14+)
        toggleBtn.postDelayed({ refresh() }, 500)
    }

    // ---------------- Playback / actions ----------------
    private fun play(f: File) {
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(f.absolutePath); prepare(); start()
        }
        Toast.makeText(this, "جارٍ التشغيل… (اضغط مطولاً للمشاركة أو الحذف)", Toast.LENGTH_SHORT).show()
    }

    private fun fileActions(f: File) {
        AlertDialog.Builder(this).setItems(arrayOf("مشاركة", "حذف")) { _, i ->
            if (i == 0) {
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "audio/mp4"; putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "مشاركة التسجيل"))
            } else { f.delete(); loadRecordings() }
        }.show()
    }

    private fun prefs() = getSharedPreferences("abu_alsamman", Context.MODE_PRIVATE)
}
