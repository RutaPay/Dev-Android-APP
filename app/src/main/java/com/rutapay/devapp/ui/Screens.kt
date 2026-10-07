package com.rutapay.devapp.ui

import com.rutapay.devapp.R
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.rutapay.devapp.data.ConfigManager
import com.rutapay.devapp.network.KtorClient
import com.rutapay.devapp.network.LoginRequest
import com.rutapay.devapp.network.SignalRManager
import com.rutapay.devapp.util.QrUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ModeSelectionScreen(onNavigateToTerminal: () -> Unit, onNavigateToLogin: () -> Unit, onNavigateToSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
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

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
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
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
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
fun LoginScreen(
    configManager: ConfigManager,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit = {}
) {
    val apiUrl by configManager.apiUrl.collectAsState(initial = "")
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF082B48)
    val buttonBlue = Color(0xFF3898EC)
    val lightBorder = Color(0xFFCBD5E1)
    val darkText = Color(0xFF0F172A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, lightBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Título
                Text(
                    text = "Inicia sesión para usar\nRutaPay",
                    style = TextStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = darkBlue,
                        lineHeight = 32.sp
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Subtítulo
                Text(
                    text = "Accede para usar nuestra plataforma de pagos para el transporte público.",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = darkBlue,
                        lineHeight = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Campo Correo Electrónico
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("Correo Electrónico", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = buttonBlue,
                        unfocusedBorderColor = lightBorder,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Campo Contraseña
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Contraseña", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = buttonBlue,
                        unfocusedBorderColor = lightBorder,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Link Olvidaste tu contraseña
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "¿Olvidaste tu contraseña? ",
                        style = TextStyle(fontSize = 13.sp, color = darkText, fontWeight = FontWeight.Normal)
                    )
                    Text(
                        text = "Restablecer contraseña",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = buttonBlue,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.clickable {
                            // Acción para restablecer contraseña
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Botón Iniciar Sesión
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            val client = KtorClient(apiUrl)
                            val response = client.login(LoginRequest(email, password))
                            isLoading = false
                            if (response.success) {
                                onLoginSuccess()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBlue,
                        contentColor = Color.White
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = "Iniciar Sesión",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Link No tienes una cuenta
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "¿No tienes una cuenta? ",
                        style = TextStyle(fontSize = 13.sp, color = darkText, fontWeight = FontWeight.Normal)
                    )
                    Text(
                        text = "Regístrate aquí",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = buttonBlue,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.clickable {
                            onNavigateToRegister()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(
    configManager: ConfigManager,
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val apiUrl by configManager.apiUrl.collectAsState(initial = "")
    var nombre by remember { mutableStateOf("") }
    var apellidoPaterno by remember { mutableStateOf("") }
    var apellidoMaterno by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var acceptTerms by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF082B48)
    val buttonBlue = Color(0xFF3898EC)
    val lightBorder = Color(0xFFCBD5E1)
    val darkText = Color(0xFF0F172A)
    val grayDot = Color(0xFF94A3B8)

    // Validaciones de contraseña
    val has8Chars = password.length >= 8
    val hasNumber = password.any { it.isDigit() }
    val hasUpper = password.any { it.isUpperCase() }
    val hasLower = password.any { it.isLowerCase() }
    val hasSymbol = password.any { !it.isLetterOrDigit() }
    val passwordsMatch = password.isNotEmpty() && password == confirmPassword

    val isFormValid = nombre.isNotBlank() &&
            apellidoPaterno.isNotBlank() &&
            email.isNotBlank() &&
            telefono.isNotBlank() &&
            has8Chars && hasNumber && hasUpper && hasLower && hasSymbol &&
            passwordsMatch && acceptTerms

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, lightBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Título
                Text(
                    text = "Empieza a usar RutaPay",
                    style = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = darkBlue
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtítulo
                Text(
                    text = "Regístrate para comenzar a usar nuestra plataforma de pagos.",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = darkBlue,
                        lineHeight = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Nombre(s)
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    placeholder = { Text("Nombre(s)", color = grayDot) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = buttonBlue,
                        unfocusedBorderColor = lightBorder,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Apellido Paterno
                OutlinedTextField(
                    value = apellidoPaterno,
                    onValueChange = { apellidoPaterno = it },
                    placeholder = { Text("Apellido Paterno", color = grayDot) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = buttonBlue,
                        unfocusedBorderColor = lightBorder,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Apellido Materno
                OutlinedTextField(
                    value = apellidoMaterno,
                    onValueChange = { apellidoMaterno = it },
                    placeholder = { Text("Apellido Materno", color = grayDot) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = buttonBlue,
                        unfocusedBorderColor = lightBorder,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Correo Electrónico
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("Correo Electrónico", color = grayDot) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = buttonBlue,
                        unfocusedBorderColor = lightBorder,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Número de Teléfono
                OutlinedTextField(
                    value = telefono,
                    onValueChange = { telefono = it },
                    placeholder = { Text("Número de Teléfono", color = grayDot) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = buttonBlue,
                        unfocusedBorderColor = lightBorder,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Contraseña
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Contraseña", color = grayDot) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = buttonBlue,
                        unfocusedBorderColor = lightBorder,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Requisitos de Contraseña
                Text(
                    text = "La contraseña debe contener:",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = darkBlue
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                PasswordRequirementItem(text = "Al menos 8 caracteres", fulfilled = has8Chars)
                PasswordRequirementItem(text = "Al menos 1 número (0...9)", fulfilled = hasNumber)
                PasswordRequirementItem(text = "Al menos 1 mayúscula (A...Z)", fulfilled = hasUpper)
                PasswordRequirementItem(text = "Al menos 1 minúscula (a...z)", fulfilled = hasLower)
                PasswordRequirementItem(text = "Al menos 1 símbolo (!...$)", fulfilled = hasSymbol)

                Spacer(modifier = Modifier.height(12.dp))

                // Confirmar Contraseña
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    placeholder = { Text("Confirmar Contraseña", color = grayDot) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = buttonBlue,
                        unfocusedBorderColor = lightBorder,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                PasswordRequirementItem(text = "Las contraseñas coinciden", fulfilled = passwordsMatch)

                Spacer(modifier = Modifier.height(14.dp))

                // Términos y Condiciones
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = acceptTerms,
                        onCheckedChange = { acceptTerms = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = buttonBlue,
                            uncheckedColor = grayDot
                        )
                    )
                    Text(
                        text = "Acepto los ",
                        style = TextStyle(fontSize = 12.sp, color = darkText)
                    )
                    Text(
                        text = "Términos y Condiciones",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = buttonBlue,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.clickable {
                            // Ver términos y condiciones
                        }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Botón Registrarse
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            delay(1000)
                            isLoading = false
                            onRegisterSuccess()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = isFormValid && !isLoading,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBlue,
                        disabledContainerColor = buttonBlue.copy(alpha = 0.5f),
                        contentColor = Color.White
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = "Registrarse",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Enlace a Iniciar Sesión
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "¿Ya tienes una cuenta? ",
                        style = TextStyle(fontSize = 13.sp, color = darkText)
                    )
                    Text(
                        text = "Inicia sesión aquí",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = buttonBlue,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.clickable {
                            onNavigateToLogin()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordRequirementItem(text: String, fulfilled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (fulfilled) Color(0xFF22C55E) else Color(0xFF94A3B8),
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = TextStyle(
                fontSize = 12.sp,
                color = if (fulfilled) Color(0xFF15803D) else Color(0xFF475569),
                fontWeight = if (fulfilled) FontWeight.Medium else FontWeight.Normal
            )
        )
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
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
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

@Composable
fun SplashScreen(onTimeOut: () -> Unit) {
    // Animación de entrada para el bus y el logo
    val busAlpha = remember { Animatable(0f) }
    val busScale = remember { Animatable(0.8f) }

    LaunchedEffect(Unit) {
        // Aparición animada
        launch {
            busAlpha.animateTo(1f, animationSpec = tween(1000))
        }
        launch {
            busScale.animateTo(
                1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
        }

        // El tiempo total del Splash
        delay(3000)
        onTimeOut()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Patrón geométrico superior izquierdo ajustado para no solaparse con la barra de estado
        Image(
            painter = painterResource(id = R.drawable.bg_pattern),
            contentDescription = null,
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.TopStart)
                .graphicsLayer {
                    translationX = -40.dp.toPx()
                    translationY = 0.dp.toPx()
                },
            contentScale = ContentScale.Fit
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Bus Icon (más grande)
            Image(
                painter = painterResource(id = R.drawable.ic_bus),
                contentDescription = "Bus Icon",
                modifier = Modifier
                    .size(260.dp)
                    .graphicsLayer {
                        alpha = busAlpha.value
                        scaleX = busScale.value
                        scaleY = busScale.value
                    }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Logo RutaPay (reemplaza las BouncingLetters)
            Image(
                painter = painterResource(id = R.drawable.logo_rutapay),
                contentDescription = "RutaPay Logo",
                modifier = Modifier
                    .width(280.dp)
                    .height(64.dp)
                    .graphicsLayer {
                        alpha = busAlpha.value
                        scaleX = busScale.value
                        scaleY = busScale.value
                    },
                contentScale = ContentScale.Fit
            )
        }
    }
}
