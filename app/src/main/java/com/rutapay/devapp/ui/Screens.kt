package com.rutapay.devapp.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.rutapay.devapp.data.ConfigManager
import com.rutapay.devapp.network.KtorClient
import com.rutapay.devapp.network.LoginRequest
import com.rutapay.devapp.network.SignalRManager
import com.rutapay.devapp.util.QrUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ModeSelectionScreen(onNavigateToTerminal: () -> Unit, onNavigateToLogin: () -> Unit, onNavigateToSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("RutaPay Dev App", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onNavigateToTerminal, modifier = Modifier.fillMaxWidth()) {
            Text("Modo Terminal")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToLogin, modifier = Modifier.fillMaxWidth()) {
            Text("Modo Cliente")
        }
        Spacer(modifier = Modifier.height(32.dp))
        IconButton(onClick = onNavigateToSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
fun SettingsScreen(configManager: ConfigManager, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val initialApi by configManager.apiUrl.collectAsState(initial = "")
    val initialSignalR by configManager.signalrUrl.collectAsState(initial = "")
    
    var api by remember(initialApi) { mutableStateOf(initialApi) }
    var signalr by remember(initialSignalR) { mutableStateOf(initialSignalR) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Configuración", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = api,
            onValueChange = { api = it },
            label = { Text("API URL") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = signalr,
            onValueChange = { signalr = it },
            label = { Text("SignalR Hub URL") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = {
            scope.launch {
                configManager.saveConfig(api, signalr)
                onBack()
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Guardar y Volver")
        }
    }
}

@Composable
fun TerminalScreen(configManager: ConfigManager) {
    val signalrUrl by configManager.signalrUrl.collectAsState(initial = "")
    val scope = rememberCoroutineScope()
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusMessage by remember { mutableStateOf("Esperando pago...") }

    LaunchedEffect(Unit) {
        qrBitmap = withContext(Dispatchers.Default) {
            QrUtils.generateQrCode("PAYMENT_ID_12345")
        }
    }

    LaunchedEffect(signalrUrl) {
        if (signalrUrl.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val manager = SignalRManager(signalrUrl)
                manager.start()
                manager.paymentConfirmations.collect { message ->
                    statusMessage = "Pago Confirmado: $message"
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Modo Terminal", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        qrBitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "QR Code",
                modifier = Modifier.size(256.dp),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(statusMessage, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun LoginScreen(configManager: ConfigManager, onLoginSuccess: () -> Unit) {
    val apiUrl by configManager.apiUrl.collectAsState(initial = "")
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Inicio de Sesión Cliente", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Usuario") })
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(onClick = {
                scope.launch {
                    isLoading = true
                    val client = KtorClient(apiUrl)
                    val response = client.login(LoginRequest(username, password))
                    isLoading = false
                    if (response.success) {
                        onLoginSuccess()
                    }
                }
            }) {
                Text("Ingresar")
            }
        }
    }
}

@Composable
fun ClientScreen(configManager: ConfigManager) {
    var scanResult by remember { mutableStateOf("Nada escaneado aún") }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            scanResult = "QR Escaneado: ${result.contents}"
            // Aquí podrías llamar a la API para procesar el pago
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Modo Cliente", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { 
            val options = ScanOptions()
            options.setBeepEnabled(true)
            options.setOrientationLocked(false)
            scanLauncher.launch(options)
        }) {
            Text("Escanear QR")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(scanResult)
    }
}
