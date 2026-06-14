package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.db.DocDatabase
import com.example.db.DocRepository
import com.example.ui.DocEditorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DocViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = DocDatabase.getDatabase(this)
        val repository = DocRepository(database.docDao())
        
        val viewModel: DocViewModel by viewModels {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DocViewModel(repository) as T
                }
            }
        }

        setContent {
            MyApplicationTheme {
                DocEditorScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
