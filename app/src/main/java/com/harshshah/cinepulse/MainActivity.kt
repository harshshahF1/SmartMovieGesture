package com.harshshah.cinepulse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Background = Color(0xFF07080B)
private val SurfaceDark = Color(0xFF11141A)
private val Accent = Color(0xFFA78BFA)
private val Cyan = Color(0xFF22D3EE)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFFAAB2C0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CinePulseApp() }
    }
}

private suspend fun laptopRequest(host: String, command: String? = null): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val path = if (command == null) "/" else "/command"
        val c = (URL("http://$host:8765$path").openConnection() as HttpURLConnection).apply {
            requestMethod = if (command == null) "GET" else "POST"
            connectTimeout = 1500
            readTimeout = 1500
            if (command != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (command != null) c.outputStream.use { it.write(JSONObject().put("command", command).toString().toByteArray()) }
        val ok = c.responseCode in 200..299
        c.disconnect()
        ok
    }.getOrDefault(false)
}

@Composable
private fun CinePulseApp() {
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1450); splash = false }
    Surface(Modifier.fillMaxSize(), color = Background) {
        AnimatedVisibility(splash, enter = fadeIn(), exit = fadeOut()) { SplashContent() }
        if (!splash) HomeScreen()
    }
}

@Composable
private fun SplashContent() {
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF20163A), Background), radius = 900f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(112.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Accent, Cyan))), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Background, modifier = Modifier.size(58.dp))
            }
            Spacer(Modifier.height(26.dp))
            Text("CinePulse", color = TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Smart viewing. Natural control.", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(48.dp))
            Text("Developed By Harsh Shah", color = Color(0xFF7E8797), fontSize = 12.sp)
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var monitoring by remember { mutableStateOf(false) }
    var autoAttention by remember { mutableStateOf(true) }
    var faces by remember { mutableStateOf<List<Face>>(emptyList()) }
    var laptopIp by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Connect your Windows laptop") }
    var connectTick by remember { mutableIntStateOf(0) }
    var sleepStarted by remember { mutableLongStateOf(0L) }
    var lastPresence by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastCommand by remember { mutableStateOf("") }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
        status = if (it) "Camera monitoring enabled" else "Camera permission is required"
    }

    LaunchedEffect(connectTick) {
        if (connectTick > 0 && laptopIp.isNotBlank()) {
            status = "Connecting…"
            connected = laptopRequest(laptopIp.trim())
            status = if (connected) "Connected • YouTube control ready" else "Could not connect • check IP, Wi-Fi and controller"
        }
    }

    suspend fun command(value: String) {
        if (!connected || laptopIp.isBlank()) {
            status = "Connect the Windows laptop first"
            return
        }
        if (laptopRequest(laptopIp.trim(), value)) {
            lastCommand = value
            status = when (value) {
                "rewind" -> "Rewound 5 seconds"
                "forward" -> "Forwarded 5 seconds"
                "pause" -> "Pause sent to Chrome"
                else -> "Play sent to Chrome"
            }
        } else {
            connected = false
            status = "Laptop connection lost"
        }
    }

    LaunchedEffect(faces, monitoring, autoAttention, connected, laptopIp) {
        if (!monitoring || !autoAttention || !connected || laptopIp.isBlank()) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (faces.isEmpty()) {
            if (now - lastPresence > 2500L && lastCommand != "pause") command("pause")
            sleepStarted = 0L
            return@LaunchedEffect
        }
        lastPresence = now
        val everyoneClosed = faces.all {
            (it.leftEyeOpenProbability ?: 1f) < 0.35f && (it.rightEyeOpenProbability ?: 1f) < 0.35f
        }
        if (everyoneClosed) {
            if (sleepStarted == 0L) sleepStarted = now
            if (now - sleepStarted >= 10_000L && lastCommand != "pause") command("pause")
        } else {
            sleepStarted = 0L
            if (lastCommand == "pause") command("play")
            else status = "Viewer detected • attention active"
        }
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A0B10), Background))).navigationBarsPadding().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CinePulse", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Your phone controls the screen", color = TextSecondary, fontSize = 12.sp)
            }
            IconButton(onClick = { autoAttention = !autoAttention; status = if (autoAttention) "Smart attention enabled" else "Smart attention paused" }) {
                Icon(Icons.Rounded.Settings, "Settings", tint = if (autoAttention) Accent else TextSecondary)
            }
        }

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Computer, null, tint = Cyan)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Windows + Chrome", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(if (connected) "Connected • YouTube control ready" else "Same Wi-Fi • enter laptop IP", color = TextSecondary, fontSize = 11.sp)
                    }
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (connected) Cyan else Color(0xFF5A6472)))
                }
                OutlinedTextField(value = laptopIp, onValueChange = { laptopIp = it; connected = false }, label = { Text("Laptop IPv4 address") }, placeholder = { Text("Example: 192.168.1.20") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, focusedLabelColor = Accent))
                Button(onClick = { connectTick++ }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)) {
                    Icon(if (connected) Icons.Rounded.Link else Icons.Rounded.LinkOff, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (connected) "Reconnect laptop" else "Connect laptop", fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ControlButton("Rewind 5s", Icons.Rounded.Replay5, Modifier.weight(1f)) { scope.launch { command("rewind") } }
            ControlButton("Forward 5s", Icons.Rounded.Forward5, Modifier.weight(1f)) { scope.launch { command("forward") } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ControlButton("Pause", Icons.Rounded.Pause, Modifier.weight(1f)) { scope.launch { command("pause") } }
            ControlButton("Play", Icons.Rounded.PlayArrow, Modifier.weight(1f)) { scope.launch { command("play") } }
        }

        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = .92f)), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (monitoring) Cyan else Color(0xFF5A6472)))
                    Spacer(Modifier.width(9.dp))
                    Text(status, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${faces.size} viewer" + if (faces.size == 1) "" else "s", color = TextSecondary, fontSize = 12.sp)
                }
                Button(onClick = {
                    if (cameraGranted) {
                        monitoring = !monitoring
                        lastPresence = System.currentTimeMillis()
                        status = if (monitoring) "Camera monitoring active" else "Monitoring paused"
                    } else permission.launch(Manifest.permission.CAMERA)
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = if (monitoring) Color(0xFF173A3C) else Color(0xFF1B1F27))) {
                    Icon(if (monitoring) Icons.Rounded.Visibility else Icons.Rounded.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (monitoring) "Monitoring active" else "Start camera monitoring")
                }
                if (monitoring && cameraGranted) CameraAnalyzer { faces = it }
                Text("Smart attention", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("No viewer for 2.5s → pause. Everyone's eyes closed for 10s → pause. When attention returns → play.", color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Text("Camera analysis stays on-device • no camera frames are sent to the laptop", color = Color(0xFF7F8A9A), fontSize = 11.sp)
            }
        }
        Text("Hand gestures will use the same Wi-Fi command channel for 5-second rewind/forward.", color = Color(0xFF6E7888), fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun ControlButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)) {
        Icon(icon, null)
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CameraAnalyzer(onFaces: (List<Face>) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val providerFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val detector = com.google.mlkit.vision.face.FaceDetection.getClient(
            com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
                .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(com.google.mlkit.vision.face.FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(com.google.mlkit.vision.face.FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking().setMinFaceSize(0.08f).build()
        )
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                val analysis = androidx.camera.core.ImageAnalysis.Builder().setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy ->
                    val image = proxy.image
                    if (image == null) { proxy.close(); return@setAnalyzer }
                    val input = com.google.mlkit.vision.common.InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees)
                    detector.process(input).addOnSuccessListener { onFaces(it) }.addOnCompleteListener { proxy.close() }
                }
                provider.unbindAll()
                provider.bindToLifecycle(context as androidx.lifecycle.LifecycleOwner, androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            }
        }, executor)
        onDispose {
            detector.close()
            providerFuture.addListener({ runCatching { providerFuture.get().unbindAll() } }, executor)
        }
    }
}
