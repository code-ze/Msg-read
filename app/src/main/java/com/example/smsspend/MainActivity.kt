package com.example.smsspend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.smsspend.ui.AppRoot
import com.example.smsspend.ui.MainViewModel
import com.example.smsspend.ui.theme.SmsSpendTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmsSpendTheme {
                AppRoot(vm)
            }
        }
    }
}
