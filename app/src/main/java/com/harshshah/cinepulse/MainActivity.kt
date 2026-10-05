package com.harshshah.cinepulse

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.mlkit.vision.face.Face
import kotlin.math.roundToInt

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

@Composable
private fun CinePulseApp() {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1450)
        showSplash = false
    }
    Surface(Modifier.fillMaxSize(), color = Background) {
        AnimatedVisibility(showSplash, enter = fadeIn(), exit = fadeOut()) { SplashContent() }
        if (!showSplash) HomeScreen()
    }
}

@Composable
private fun SplashContent() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF20163A), Background), radius = 900f)
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(112.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Accent, Cyan))),
                contentAlignment = Alignment.Center
            ) {
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
    var selectedVideo by remember { mutableStateOf<Uri?>(null) }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var monitoring by remember { mutableStateOf(false) }
    var autoAttention by remember { mutableStateOf(true) }
    var faces by remember { mutableStateOf<List<Face>>(emptyList()) }
    var lastPresence by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var status by remember { mutableStateOf("Ready to watch") }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        status = if (granted) "Camera monitoring enabled" else "Camera permission is required for smart controls"
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedVideo = uri
        status = if (uri != null) "Video loaded — ready for smart playback" else "No video selected"
    }

    val player = remember(selectedVideo) {
        ExoPlayer.Builder(context).build().also { exo ->
            selectedVideo?.let { exo.setMediaItem(MediaItem.fromUri(it)); exo.prepare() }
        }
    }

    DisposableEffect(player) { onDispose { player.release() } }

    LaunchedEffect(faces, monitoring, autoAttention) {
        if (monitoring && autoAttention) {
            if (faces.isNotEmpty()) {
                lastPresence = System.currentTimeMillis()
                if (!player.isPlaying && player.currentMediaItem != null) player.play()
                status = "Viewer detected • attention active"
            } else if (System.currentTimeMillis() - lastPresence > 2500L) {
                if (player.isPlaying) player.pause()
                status = "Paused • no viewer detected"
            }
        }
    }

    val progress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player, selectedVideo) {
        while (true) {
            if (player.duration > 0) progress.floatValue =
                (player.currentPosition.toFloat() / player.duration.toFloat()).coerceIn(0f, 1f)
            kotlinx.coroutines.delay(250)
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0B10), Background)))
            .navigationBarsPadding().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CinePulse", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Gesture-powered movie control", color = TextSecondary, fontSize = 12.sp)
            }
            IconButton(onClick = {
                autoAttention = !autoAttention
                status = if (autoAttention) "Smart attention enabled" else "Smart attention paused"
            }) {
                Icon(Icons.Rounded.Settings, "Settings", tint = if (autoAttention) Accent else TextSecondary)
            }
        }

        if (selectedVideo == null) {
            EmptyPlayerCard {
                picker.launch("video/*")
            }
        } else {
            PlayerCard(player, progress.floatValue, { player.seekBack() }, { player.seekForward() })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    picker.launch("video/*")
                },
                Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(7.dp))
                Text(if (selectedVideo == null) "Choose movie" else "Change video", fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = {
                    if (cameraGranted) {
                        monitoring = !monitoring
                        status = if (monitoring) "Camera monitoring active" else "Monitoring paused"
                    } else cameraPermission.launch(Manifest.permission.CAMERA)
                },
                Modifier.size(54.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (monitoring) Color(0xFF173A3C) else SurfaceDark)
            ) {
                Icon(if (monitoring) Icons.Rounded.Visibility else Icons.Rounded.CameraAlt, "Camera",
                    tint = if (monitoring) Cyan else TextPrimary)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.92f)),
            shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (monitoring) Cyan else Color(0xFF5A6472)))
                    Spacer(Modifier.width(9.dp))
                    Text(status, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${faces.size} viewer" + if (faces.size == 1) "" else "s", color = TextSecondary, fontSize = 12.sp)
                }

                if (monitoring && cameraGranted) {
                    CameraAnalyzer(true) { detected -> faces = detected }
                }

                Text("Smart attention", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Pauses when everyone is absent, then resumes when a viewer returns.",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp
                )
                Text("Vision is processed on-device • camera frames are not saved",
                    color = Color(0xFF7F8A9A), fontSize = 11.sp)
            }
        }

        Text(
            "Next gesture layer: raise a hand and close it twice to seek. " +
                "The player and privacy architecture are already isolated for this module.",
            color = Color(0xFF6E7888), fontSize = 11.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 3.dp)
        )
    }
}

@Composable
private fun EmptyPlayerCard(onPick: () -> Unit) {
    Card(Modifier.fillMaxWidth().aspectRatio(16f / 9f), shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1016))) {
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF211936), Color(0xFF0D1016)), radius = 650f)
        ), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Accent, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("Your private cinema", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("Pick a video to begin", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onPick) { Text("Select video", color = Cyan, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PlayerCard(player: ExoPlayer, progress: Float, onBack: () -> Unit, onForward: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)) {
        Column {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                    }
                },
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
            LinearProgressIndicator(
                progress = { progress }, modifier = Modifier.fillMaxWidth(),
                color = Accent, trackColor = Color(0xFF272B33)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.Replay10, "Rewind", tint = TextPrimary) }
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Icon(if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        "Play/Pause", tint = TextPrimary, modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = onForward) { Icon(Icons.Rounded.Forward10, "Forward", tint = TextPrimary) }
            }
        }
    }
}

@Composable
private fun CameraAnalyzer(enabled: Boolean, onFaces: (List<Face>) -> Unit) {
    val context = LocalContext.current

    DisposableEffect(enabled) {
        if (!enabled) {
            onDispose { }
        } else {
            val providerFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
            val executor = ContextCompat.getMainExecutor(context)
            val detector = com.google.mlkit.vision.face.FaceDetection.getClient(
                com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
                    .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(com.google.mlkit.vision.face.FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setClassificationMode(com.google.mlkit.vision.face.FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .enableTracking()
                    .setMinFaceSize(0.08f)
                    .build()
            )

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
                        val input = com.google.mlkit.vision.common.InputImage.fromMediaImage(
                            image, proxy.imageInfo.rotationDegrees
                        )
                        detector.process(input)
                            .addOnSuccessListener { result -> onFaces(result) }
                            .addOnCompleteListener { proxy.close() }
                    }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        context as androidx.lifecycle.LifecycleOwner,
                        androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA,
                        analysis
                    )
                }
            }, executor)

            onDispose {
                detector.close()
                providerFuture.addListener({
                    runCatching { providerFuture.get().unbindAll() }
                }, executor)
            }
        }
    }
}
