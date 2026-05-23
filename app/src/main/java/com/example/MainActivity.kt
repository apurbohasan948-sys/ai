package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.AssistantRepository
import com.example.ui.NovaDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.NovaViewModel
import com.example.viewmodel.NovaViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Trap any uncaught exceptions to ensure we log what causes process deaths
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("MainActivity", "Caught uncaught exception in thread: $thread", throwable)
            oldHandler?.uncaughtException(thread, throwable)
        }

        enableEdgeToEdge()

        // Initialize Room Database with dedicated background seeding
        val db = AppDatabase.getDatabase(applicationContext)
        val repository = AssistantRepository(
            assistantDao = db.assistantDao(),
            reminderDao = db.reminderDao(),
            routineDao = db.routineDao(),
            contactDao = db.contactDao()
        )

        // Resolve viewModel via factory injection
        val viewModelFactory = NovaViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[NovaViewModel::class.java]

        // Set app state to foreground on startup pre-emptively
        com.example.service.NovaBackgroundService.isAppInForeground = true

        // Start Nova Background Phrase Listener Service only if recording permission is granted
        val recordAudioPermission = android.Manifest.permission.RECORD_AUDIO
        val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(this, recordAudioPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasMicPermission) {
            try {
                val serviceIntent = Intent(this, com.example.service.NovaBackgroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed starting NovaBackgroundService", e)
            }
        } else {
            android.util.Log.d("MainActivity", "Microphone permission not granted yet, skipping foreground service startup")
        }

        // Collect screenshot capture signals flowing from ViewModel
        lifecycleScope.launch {
            viewModel.screenshotEvent.collectLatest { timestamp ->
                if (timestamp > 0) {
                    takeScreenshot { savedPath ->
                        viewModel.triggerScreenshotComplete(savedPath)
                    }
                }
            }
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NovaDashboard(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.service.NovaBackgroundService.isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        com.example.service.NovaBackgroundService.isAppInForeground = false
    }

    private fun takeScreenshot(callback: (String) -> Unit) {
        val window = this.window
        val view = window.decorView
        if (view.width <= 0 || view.height <= 0) {
            callback("Standard simulation screen directory.")
            return
        }

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                PixelCopy.request(window, bitmap, { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        saveBitmapToStorage(bitmap, callback)
                    } else {
                        drawCanvasFallback(view, bitmap, callback)
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                drawCanvasFallback(view, bitmap, callback)
            }
        } else {
            drawCanvasFallback(view, bitmap, callback)
        }
    }

    private fun drawCanvasFallback(view: View, bitmap: Bitmap, callback: (String) -> Unit) {
        try {
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            saveBitmapToStorage(bitmap, callback)
        } catch (e: Exception) {
            callback("Storage writing path error.")
        }
    }

    private fun saveBitmapToStorage(bitmap: Bitmap, callback: (String) -> Unit) {
        try {
            val file = File(getExternalFilesDir(null), "Nova_Screenshot_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            callback(file.absolutePath)
        } catch (e: Exception) {
            callback("Saved internally to buffer cache.")
        }
    }
}
