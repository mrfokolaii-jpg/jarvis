package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var storageManager: StorageManager
    private lateinit var speechManager: SpeechManager
    private lateinit var jarvisAI: JarvisAI

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startFloatingWidget()
        } else {
            Toast.makeText(this, "مجوزها لازم هستند", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storageManager = StorageManager(this)
        speechManager = SpeechManager(this)
        jarvisAI = JarvisAI(this)

        setupUI()
        requestPermissions()
    }

    private fun setupUI() {
        // دکمه شروع ویجت
        findViewById<Button>(R.id.btn_start_widget).setOnClickListener {
            startFloatingWidget()
        }

        // دکمه تنظیمات
        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            showSettingsDialog()
        }

        // دکمه صادرات
        findViewById<Button>(R.id.btn_export).setOnClickListener {
            exportData()
        }

        // دکمه پاک کردن
        findViewById<Button>(R.id.btn_clear).setOnClickListener {
            clearData()
        }

        // نمایش تاریخچه
        updateHistoryDisplay()
    }

    private fun startFloatingWidget() {
        val intent = Intent(this, FloatingWidgetService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "جاویس فعال شد!", Toast.LENGTH_SHORT).show()
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )

        val needsRequest = permissions.any {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (needsRequest) {
            requestPermissionLauncher.launch(permissions)
        } else {
            startFloatingWidget()
        }
    }

    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("تنظیمات Jarvis")

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        // GitHub Token
        val tokenLabel = TextView(this).apply {
            text = "GitHub Token:"
            textSize = 14f
        }
        val tokenInput = EditText(this).apply {
            setText(storageManager.getSetting("github_token"))
            hint = "github_pat_xxxxx"
        }

        // GitHub Owner
        val ownerLabel = TextView(this).apply {
            text = "صاحب مخزن:"
            textSize = 14f
        }
        val ownerInput = EditText(this).apply {
            setText(storageManager.getSetting("github_owner"))
            hint = "نام‌کاربریتان"
        }

        // GitHub Repo
        val repoLabel = TextView(this).apply {
            text = "نام مخزن:"
            textSize = 14f
        }
        val repoInput = EditText(this).apply {
            setText(storageManager.getSetting("github_repo"))
            hint = "jarvis"
        }

        layout.addView(tokenLabel)
        layout.addView(tokenInput)
        layout.addView(ownerLabel)
        layout.addView(ownerInput)
        layout.addView(repoLabel)
        layout.addView(repoInput)

        builder.setView(layout)
        builder.setPositiveButton("ذخیره") { _, _ ->
            storageManager.saveSettings("github_token", tokenInput.text.toString())
            storageManager.saveSettings("github_owner", ownerInput.text.toString())
            storageManager.saveSettings("github_repo", repoInput.text.toString())
            Toast.makeText(this, "تنظیمات ذخیره شدند!", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("لغو") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun exportData() {
        try {
            val file = storageManager.exportConversationHistory()

            // ذخیره در GitHub اگر توکن موجود باشد
            val token = storageManager.getSetting("github_token")
            if (token.isNotEmpty()) {
                storageManager.saveToGitHub(
                    fileName = "jarvis_history_${System.currentTimeMillis()}.json",
                    content = file.readText(),
                    token = token,
                    owner = storageManager.getSetting("github_owner"),
                    repo = storageManager.getSetting("github_repo"),
                    onSuccess = {
                        Toast.makeText(this, "✅ صادرات و ارسال به GitHub موفق!", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(this, "❌ خطا: $error", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                Toast.makeText(this, "✅ فایل صادرات ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در صادرات: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearData() {
        AlertDialog.Builder(this)
            .setTitle("حذف تمام داده‌ها")
            .setMessage("آیا مطمئن هستید؟ این عمل قابل بازگشت نیست.")
            .setPositiveButton("بله") { _, _ ->
                storageManager.clearAllData()
                Toast.makeText(this, "داده‌ها پاک شدند!", Toast.LENGTH_SHORT).show()
                updateHistoryDisplay()
            }
            .setNegativeButton("خیر") { dialog, _ -> dialog.cancel() }
            .show()
    }

    private fun updateHistoryDisplay() {
        val history = storageManager.getConversationHistory()
        val historyText = if (history.isEmpty()) {
            "هیچ گفتگویی ثبت نشده است"
        } else {
            history.takeLast(5).reversed().joinToString("\n---\n") {
                "📅 ${it["timestamp"]}\n👤 شما: ${it["user"]}\n🤖 جاویس: ${it["ai"]}"
            }
        }

        findViewById<TextView>(R.id.tv_history).text = historyText
    }

    override fun onDestroy() {
        super.onDestroy()
        speechManager.destroy()
        jarvisAI.destroy()
    }
}
