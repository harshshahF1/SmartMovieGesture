package com.harshshah.cinepulse

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
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
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
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

private const val DEFAULT_RELAY_URL = "https://YOUR-CLOUDFLARE-WORKER.workers.dev"

private suspend fun relayRequest(baseUrl: String, path: String, method: String, payload: JSONObject? = null): JSONObject? = withContext(Dispatchers.IO) {
    runCatching {
        val base = baseUrl.trim().removeSuffix("/")
        require(base.startsWith("https://")) { "Relay must use HTTPS" }
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5000
            readTimeout = 5000
            useCaches = false
            doInput = true
            setRequestProperty("Accept", "application/json")
            if (payload != null) { doOutput = true; setRequestProperty("Content-Type", "application/json") }
        }
        try {
            if (payload != null) connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            if (code !in 200..299) throw IllegalStateException(text)
            JSONObject(text)
        } finally { connection.disconnect() }
    }.getOrNull()
}

private suspend fun createSession(relayUrl: String): JSONObject? = relayRequest(relayUrl, "/v1/session", "POST")

private suspend fun sendRelayCommand(relayUrl: String, code: String, token: String, command: String): Boolean {
    val payload = JSONObject().put("code", code).put("token", token).put("command", command)
    return relayRequest(relayUrl, "/v1/command", "POST", payload)?.optBoolean("ok", false) == true
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
    var relayUrl by remember { mutableStateOf(DEFAULT_RELAY_URL) }
    var sessionCode by remember { mutableStateOf("") }
    var phoneToken by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Create a secure relay session") }
    var sessionTick by remember { mutableIntStateOf(0) }
    var sleepStarted by remember { mutableLongStateOf(0L) }
    var lastPresence by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastCommand by remember { mutableStateOf("") }
    var gestureStatus by remember { mutableStateOf("Hand gestures ready") }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
        status = if (it) "Camera monitoring enabled" else "Camera permission is required"
    }

    LaunchedEffect(sessionTick) {
        if (sessionTick > 0) {
            status = "Creating encrypted relay session…"
            val result = createSession(relayUrl)
            if (result != null) {
                sessionCode = result.optString("code")
                phoneToken = result.optString("token")
                connected = sessionCode.isNotBlank() && phoneToken.isNotBlank()
                status = if (connected) "Secure session created • give the code to Windows" else "Relay returned an invalid session"
            } else { connected = false; status = "Relay unavailable • check the HTTPS relay URL" }
        }
    }

    suspend fun command(value: String) {
        if (!connected || sessionCode.isBlank() || phoneToken.isBlank()) { status = "Create the secure relay session first"; return }
        if (sendRelayCommand(relayUrl, sessionCode, phoneToken, value)) {
            lastCommand = value
            status = when (value) { "rewind" -> "Rewound 5 seconds"; "forward" -> "Forwarded 5 seconds"; "pause" -> "Pause sent through secure relay"; else -> "Play sent through secure relay" }
        } else status = "Relay command failed"
    }
    LaunchedEffect(faces, monitoring, autoAttention, connected, sessionCode) {
        if (!monitoring || !autoAttention || !connected || sessionCode.isBlank()) return@LaunchedEffect
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
                    Icon(Icons.Rounded.Cloud, null, tint = Cyan); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Secure Cloud Relay", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(if (connected) "Session active • command-only relay" else "HTTPS command relay • no camera/audio/video", color = TextSecondary, fontSize = 11.sp)
                    }
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (connected) Cyan else Color(0xFF5A6472)))
                }
                OutlinedTextField(value = relayUrl, onValueChange = { relayUrl = it; connected = false }, label = { Text("Your HTTPS relay URL") }, placeholder = { Text("https://your-worker.workers.dev") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, focusedLabelColor = Accent))
                Button(onClick = { sessionTick++ }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)) {
                    Icon(Icons.Rounded.Lock, null); Spacer(Modifier.width(8.dp)); Text(if (connected) "Create new secure session" else "Create secure session", fontWeight = FontWeight.Bold)
                }
                if (sessionCode.isNotBlank()) {
                    Text("PAIRING CODE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(sessionCode, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text("Enter this code in the Windows CinePulse Controller. The relay carries commands only.", color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
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
                if (monitoring && cameraGranted) CameraAnalyzer(onFaces = { faces = it }, onGestureCommand = { value -> scope.launch { command(value) } }, onGestureStatus = { gestureStatus = it })
                Text("Cloud relay privacy", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Camera frames, face images, eye measurements, microphone audio, video and screen content stay on the phone. The relay receives only playback commands and temporary session routing data.", color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Text("Smart attention", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("No viewer for 2.5s → pause. Everyone's eyes closed for 10s → pause. When attention returns → play.", color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Text("On-device AI • no camera/audio/video upload • HTTPS command-only relay", color = Color(0xFF7F8A9A), fontSize = 11.sp)
            }
        }
        Text("Hand gestures: $gestureStatus", color = Color(0xFF6E7888), fontSize = 11.sp, lineHeight = 16.sp)
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
private fun CameraAnalyzer(
    onFaces: (List<Face>) -> Unit,
    onGestureCommand: (String) -> Unit,
    onGestureStatus: (String) -> Unit
) {
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
        val gestureRecognizer = runCatching {
            val baseOptions = BaseOptions.builder().setModelAssetPath("gesture_recognizer.task").build()
            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.55f)
                .setMinHandPresenceConfidence(0.55f)
                .setMinTrackingConfidence(0.55f)
                .setRunningMode(RunningMode.VIDEO)
                .build()
            GestureRecognizer.createFromOptions(context, options)
        }.getOrElse {
            onGestureStatus("Hand gesture model could not initialize")
            null
        }

        var lastGesture = ""
        var closeCount = 0
        var firstCloseAt = 0L
        var cooldownUntil = 0L

        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                val analysis = androidx.camera.core.ImageAnalysis.Builder()
                    .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val image = proxy.image
                    if (image == null) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    val timestamp = SystemClock.uptimeMillis()
                    val input = com.google.mlkit.vision.common.InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees)
                    detector.process(input).addOnSuccessListener { onFaces(it) }.addOnCompleteListener { if (gestureRecognizer == null) proxy.close() }

                    if (gestureRecognizer != null) {
                        runCatching {
                            val mpImage = MediaImageBuilder(image).build()
                            val rotation = ImageProcessingOptions.builder()
                                .setRotationDegrees(proxy.imageInfo.rotationDegrees)
                                .build()
                            val result = gestureRecognizer.recognizeForVideo(mpImage, rotation, timestamp)
                            var currentGesture = ""
                            var currentSide = ""
                            for (i in result.gestures().indices) {
                                val gesture = result.gestures()[i].firstOrNull()?.categoryName() ?: continue
                                val side = result.handedness().getOrNull(i)?.firstOrNull()?.categoryName() ?: continue
                                if (gesture == "Closed_Fist") {
                                    currentGesture = gesture
                                    currentSide = side
                                    break
                                }
                            }
                            val now = System.currentTimeMillis()
                            if (currentGesture == "Closed_Fist" && lastGesture != "Closed_Fist" && now >= cooldownUntil) {
                                if (firstCloseAt == 0L || now - firstCloseAt > 3000L) {
                                    firstCloseAt = now
                                    closeCount = 1
                                } else {
                                    closeCount++
                                }
                                if (closeCount >= 2) {
                                    val command = if (currentSide == "Left") "rewind" else "forward"
                                    onGestureCommand(command)
                                    onGestureStatus(if (command == "rewind") "Left fist double-close → rewind 5s" else "Right fist double-close → forward 5s")
                                    closeCount = 0
                                    firstCloseAt = 0L
                                    cooldownUntil = now + 1500L
                                } else {
                                    onGestureStatus("${currentSide} fist detected • open, then close again")
                                }
                            }
                            if (currentGesture.isEmpty() && lastGesture == "Closed_Fist") onGestureStatus("Hand open • gesture armed")
                            if (firstCloseAt != 0L && now - firstCloseAt > 3000L) {
                                closeCount = 0
                                firstCloseAt = 0L
                            }
                            lastGesture = currentGesture
                        }.onFailure {
                            onGestureStatus("Hand gesture processing unavailable")
                        }
                    }
                    if (gestureRecognizer != null) proxy.close()
                }
                provider.unbindAll()
                provider.bindToLifecycle(context as androidx.lifecycle.LifecycleOwner, androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            }
        }, executor)

        onDispose {
            detector.close()
            gestureRecognizer?.close()
            providerFuture.addListener({ runCatching { providerFuture.get().unbindAll() } }, executor)
        }
    }
}
