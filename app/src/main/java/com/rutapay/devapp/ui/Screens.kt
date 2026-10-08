package com.rutapay.devapp.ui

import com.rutapay.devapp.R
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
                            delay(300) // Breve indicador de carga visual
                            isLoading = false
                            onLoginSuccess() // Salta directamente al Dashboard
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

// =============================================================
// DASHBOARD PRINCIPAL
// =============================================================
@Composable
fun DashboardScreen(configManager: ConfigManager) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val primaryColor = Color(0xFF78C0E0)   // #78C0E0 Primario
    val secondaryColor = Color(0xFF449DD1) // #449DD1 Secundario
    val tertiaryColor = Color(0xFF052B4A)  // #052B4A Terciario

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF1F5F9),
        bottomBar = {
            ModernAnimatedBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                tertiaryColor = tertiaryColor
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(250)) + slideInHorizontally { width -> if (targetState > initialState) width / 2 else -width / 2 } togetherWith
                            fadeOut(animationSpec = tween(250)) + slideOutHorizontally { width -> if (targetState > initialState) -width / 2 else width / 2 }
                },
                label = "DashboardTabTransition"
            ) { tab ->
                when (tab) {
                    0 -> MapViewSection(primaryColor, secondaryColor, tertiaryColor)
                    1 -> CardViewSection(primaryColor, secondaryColor, tertiaryColor)
                    2 -> QrViewSection(primaryColor, secondaryColor, tertiaryColor)
                    3 -> RewardsViewSection(primaryColor, secondaryColor, tertiaryColor)
                    4 -> ProfileViewSection(primaryColor, secondaryColor, tertiaryColor)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 1. MAP VIEW SECTION
// -------------------------------------------------------------
@Composable
private fun MapViewSection(primaryColor: Color, secondaryColor: Color, tertiaryColor: Color) {
    var searchQuery by remember { mutableStateOf("") }
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnimation.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by pulseAnimation.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Map Canvas Drawing
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Background Map Land & Water
            drawRect(color = Color(0xFFE2E8F0)) // Base terrain

            // Park Areas (Greenish slate)
            drawCircle(
                color = Color(0xFFD1FAE5),
                radius = w * 0.35f,
                center = Offset(w * 0.2f, h * 0.25f)
            )
            drawCircle(
                color = Color(0xFFD1FAE5),
                radius = w * 0.4f,
                center = Offset(w * 0.85f, h * 0.7f)
            )

            // Main River / Lake
            val riverPath = Path().apply {
                moveTo(0f, h * 0.45f)
                cubicTo(w * 0.3f, h * 0.4f, w * 0.6f, h * 0.55f, w, h * 0.5f)
            }
            drawPath(
                path = riverPath,
                color = Color(0xFFBAE6FD),
                style = Stroke(width = 48f, cap = StrokeCap.Round)
            )

            // Major Roads
            val mainRoad1 = Path().apply {
                moveTo(w * 0.1f, 0f)
                lineTo(w * 0.1f, h)
            }
            val mainRoad2 = Path().apply {
                moveTo(0f, h * 0.3f)
                lineTo(w, h * 0.3f)
            }
            val mainRoad3 = Path().apply {
                moveTo(0f, h * 0.7f)
                lineTo(w, h * 0.7f)
            }
            val routePath = Path().apply {
                moveTo(w * 0.15f, h * 0.1f)
                cubicTo(w * 0.7f, h * 0.15f, w * 0.3f, h * 0.6f, w * 0.85f, h * 0.85f)
            }

            drawPath(mainRoad1, color = Color.White, style = Stroke(width = 28f))
            drawPath(mainRoad2, color = Color.White, style = Stroke(width = 28f))
            drawPath(mainRoad3, color = Color.White, style = Stroke(width = 28f))

            // Bus Route Line
            drawPath(
                path = routePath,
                color = secondaryColor,
                style = Stroke(width = 16f, cap = StrokeCap.Round)
            )

            // Bus Stops (Dots)
            val stops = listOf(
                Offset(w * 0.18f, h * 0.12f),
                Offset(w * 0.42f, h * 0.28f),
                Offset(w * 0.45f, h * 0.55f),
                Offset(w * 0.75f, h * 0.78f)
            )
            stops.forEach { stop ->
                drawCircle(color = tertiaryColor, radius = 14f, center = stop)
                drawCircle(color = Color.White, radius = 7f, center = stop)
            }

            // Animated Bus Pulse Pin
            val busLocation = Offset(w * 0.45f, h * 0.55f)
            drawCircle(
                color = primaryColor.copy(alpha = pulseAlpha),
                radius = 45f * pulseScale,
                center = busLocation
            )
            drawCircle(color = tertiaryColor, radius = 22f, center = busLocation)
            drawCircle(color = primaryColor, radius = 14f, center = busLocation)
        }

        // Top Floating Search Bar (Header)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = tertiaryColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Buscar rutas, paradas o destinos...",
                            style = TextStyle(fontSize = 14.sp, color = Color(0xFF94A3B8))
                        )
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filtro",
                    tint = secondaryColor,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { }
                )
            }
        }

        // Floating Route Detail Card at Bottom
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = primaryColor.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsBus,
                                    contentDescription = null,
                                    tint = tertiaryColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Ruta 4B • Centro - Universidad",
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tertiaryColor)
                            )
                            Text(
                                text = "Llegando a parada: Terminal Central",
                                style = TextStyle(fontSize = 12.sp, color = Color(0xFF64748B))
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFDCFCE7)
                    ) {
                        Text(
                            text = "3 min",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D)),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tarifa: $12.00 MXN",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tertiaryColor)
                    )
                    Button(
                        onClick = { },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = secondaryColor)
                    ) {
                        Text("Ver Paradas", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. CARD VIEW SECTION (TARJETA)
// -------------------------------------------------------------
@Composable
private fun CardViewSection(primaryColor: Color, secondaryColor: Color, tertiaryColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Mi Tarjeta RutaPay",
            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = tertiaryColor)
        )
        Text(
            text = "Gestiona tu saldo y pases de transporte",
            style = TextStyle(fontSize = 14.sp, color = Color(0xFF64748B))
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Digital Credit/Transport Card Preview
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 10.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(tertiaryColor, secondaryColor, primaryColor)
                        )
                    )
                    .padding(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RutaPay Pass",
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Image(
                            painter = painterResource(id = R.drawable.logo_rutapay),
                            contentDescription = "Logo",
                            modifier = Modifier.width(90.dp).height(24.dp),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Column {
                        Text(
                            text = "Saldo Disponible",
                            style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        )
                        Text(
                            text = "$250.50 MXN",
                            style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "•••• •••• •••• 4829",
                            style = TextStyle(fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                        )
                        Text(
                            text = "EXP 08/28",
                            style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = secondaryColor)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Recargar", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, secondaryColor)
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = secondaryColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Historial", color = secondaryColor, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Últimos Movimientos",
            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tertiaryColor)
        )

        Spacer(modifier = Modifier.height(12.dp))

        val recentActivity = listOf(
            Triple("Ruta 4B - Centro", "- $12.00 MXN", "Hoy, 08:30 AM"),
            Triple("Recarga OXXO", "+ $100.00 MXN", "Ayer, 06:15 PM"),
            Triple("Ruta 12 - Universidad", "- $12.00 MXN", "Ayer, 01:20 PM")
        )

        recentActivity.forEach { (title, amount, date) ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold, color = tertiaryColor)
                        Text(date, fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                    Text(
                        amount,
                        fontWeight = FontWeight.Bold,
                        color = if (amount.startsWith("+")) Color(0xFF16A34A) else tertiaryColor
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. QR VIEW SECTION
// -------------------------------------------------------------
@Composable
private fun QrViewSection(primaryColor: Color, secondaryColor: Color, tertiaryColor: Color) {
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        qrBitmap = withContext(Dispatchers.Default) {
            QrUtils.generateQrCode("RUTAPAY_USER_PASS_9988")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Escáner y Pago QR",
            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = tertiaryColor)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Acerca este código a la terminal del autobús",
            style = TextStyle(fontSize = 14.sp, color = Color(0xFF64748B))
        )

        Spacer(modifier = Modifier.height(28.dp))

        Card(
            modifier = Modifier
                .size(280.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                qrBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Código QR",
                        modifier = Modifier.size(220.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = { },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = tertiaryColor)
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Escanear Código QR del Camión", fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// 4. REWARDS VIEW SECTION (RECOMPENSAS)
// -------------------------------------------------------------
@Composable
private fun RewardsViewSection(primaryColor: Color, secondaryColor: Color, tertiaryColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Recompensas RutaPay",
            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = tertiaryColor)
        )
        Text(
            text = "Acumula puntos en cada viaje y canjea beneficios",
            style = TextStyle(fontSize = 14.sp, color = Color(0xFF64748B))
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = tertiaryColor)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Tus Puntos", color = primaryColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("1,250 Pts", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("Nivel Plata • 250 Pts para Oro", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                Icon(
                    imageVector = Icons.Default.CardGiftcard,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Beneficios Disponibles",
            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tertiaryColor)
        )

        Spacer(modifier = Modifier.height(12.dp))

        val rewardsList = listOf(
            Pair("1 Viaje Gratis en Cualquier Ruta", "500 Pts"),
            Pair("50% Descuento en Pase Semanal", "800 Pts"),
            Pair("Pase Mensual Gratis", "2,500 Pts")
        )

        rewardsList.forEach { (title, cost) ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.SemiBold, color = tertiaryColor)
                        Text(cost, fontSize = 13.sp, color = secondaryColor, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = secondaryColor)
                    ) {
                        Text("Canjear", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 5. PROFILE VIEW SECTION (PERFIL)
// -------------------------------------------------------------
@Composable
private fun ProfileViewSection(primaryColor: Color, secondaryColor: Color, tertiaryColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            shape = CircleShape,
            color = secondaryColor.copy(alpha = 0.2f),
            modifier = Modifier.size(90.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Perfil",
                    tint = tertiaryColor,
                    modifier = Modifier.size(50.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text("Usuario RutaPay", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = tertiaryColor)
        Text("usuario@rutapay.com", fontSize = 14.sp, color = Color(0xFF64748B))

        Spacer(modifier = Modifier.height(28.dp))

        val options = listOf(
            Pair("Mis Tarjetas Guardadas", Icons.Default.CreditCard),
            Pair("Historial de Viajes", Icons.Default.DirectionsBus),
            Pair("Notificaciones", Icons.Default.Notifications),
            Pair("Seguridad y Contraseña", Icons.Default.Security),
            Pair("Ayuda y Soporte", Icons.Default.Help)
        )

        options.forEach { (option, icon) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable { },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = secondaryColor)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(option, fontWeight = FontWeight.Medium, color = tertiaryColor, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF94A3B8))
                }
            }
        }
    }
}

// -------------------------------------------------------------
// MODERN ANIMATED BOTTOM NAVIGATION BAR
// -------------------------------------------------------------
@Composable
private fun ModernAnimatedBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color
) {
    val items = listOf(
        NavTabItem("Mapa", Icons.Default.Map, Icons.Outlined.Map),
        NavTabItem("Tarjeta", Icons.Default.CreditCard, Icons.Outlined.CreditCard),
        NavTabItem("QR", Icons.Default.QrCodeScanner, Icons.Outlined.QrCodeScanner),
        NavTabItem("Regalo", Icons.Default.CardGiftcard, Icons.Outlined.CardGiftcard),
        NavTabItem("Perfil", Icons.Default.Person, Icons.Outlined.Person)
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = selectedTab == index

                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "scaleAnimation"
                )

                val activeBgColor by animateColorAsState(
                    targetValue = if (isSelected) tertiaryColor else Color.Transparent,
                    animationSpec = tween(200),
                    label = "bgColorAnimation"
                )

                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) primaryColor else Color(0xFF64748B),
                    animationSpec = tween(200),
                    label = "iconColorAnimation"
                )

                Surface(
                    onClick = { onTabSelected(index) },
                    shape = RoundedCornerShape(20.dp),
                    color = activeBgColor,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            tint = iconColor,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.label,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class NavTabItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)
