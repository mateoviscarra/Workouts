package com.mateoviscarra.doitall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateoviscarra.doitall.ui.WorkoutApp
import com.mateoviscarra.doitall.ui.theme.ThemeMode
import com.mateoviscarra.doitall.ui.theme.ThemePreferenceStore
import com.mateoviscarra.doitall.ui.theme.WorkoutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            val themeStore = remember { ThemePreferenceStore(applicationContext) }
            val themeMode by themeStore.themeMode.collectAsStateWithLifecycle()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            WorkoutTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WorkoutApp(themeStore = themeStore)
                }
            }
        }
    }
}
