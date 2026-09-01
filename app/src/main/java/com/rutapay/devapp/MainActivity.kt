package com.rutapay.devapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rutapay.devapp.data.ConfigManager
import com.rutapay.devapp.ui.*
import com.rutapay.devapp.ui.theme.RutaPayDevAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val configManager = ConfigManager(this)
        
        setContent {
            RutaPayDevAppTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("splash") {
                            SplashScreen(onTimeOut = {
                                navController.navigate("mode_selection") {
                                    // Esto evita que el usuario regrese al Splash al darle atrás
                                    popUpTo("splash") { inclusive = true }
                                }
                            })
                        }
                        composable("mode_selection") {
                            ModeSelectionScreen(
                                onNavigateToTerminal = { navController.navigate("terminal") },
                                onNavigateToLogin = { navController.navigate("login") },
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(configManager, onBack = { navController.popBackStack() })
                        }
                        composable("terminal") {
                            TerminalScreen(configManager)
                        }
                        composable("login") {
                            LoginScreen(configManager, onLoginSuccess = { 
                                navController.navigate("client") {
                                    popUpTo("login") { inclusive = true }
                                }
                            })
                        }
                        composable("client") {
                            ClientScreen(configManager)
                        }
                    }
                }
            }
        }
    }
}
