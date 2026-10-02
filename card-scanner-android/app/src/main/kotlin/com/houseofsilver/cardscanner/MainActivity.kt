package com.houseofsilver.cardscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.houseofsilver.cardscanner.ui.navigation.AppNavigation
import com.houseofsilver.cardscanner.ui.theme.CardScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CardScannerTheme {
                AppNavigation()
            }
        }
    }
}
