package com.example

import android.os.Bundle
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
}
