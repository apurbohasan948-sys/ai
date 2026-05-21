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
        enableEdgeToEdge()

        // Initialize Room Database using application lifecycle scope for background seeding
        val db = AppDatabase.getDatabase(applicationContext, lifecycleScope)
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
