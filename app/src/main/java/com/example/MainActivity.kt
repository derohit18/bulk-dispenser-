package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.BulkSmsScreen
import com.example.ui.BulkSmsViewModel
import com.example.ui.BulkSmsViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BulkSmsViewModel by viewModels {
        val app = application as BulkSmsApp
        BulkSmsViewModelFactory(app.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                BulkSmsScreen(viewModel = viewModel)
            }
        }
    }
}

