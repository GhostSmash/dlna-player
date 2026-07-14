package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.LogManager
import com.example.data.database.CastHistoryItem
import com.example.data.dlna.DlnaDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

// Colors reflecting the original HTML Liquid Glass Dark Space aesthetic
val SpaceBg = Color(0xFF0B0F19)
val SpaceLight = Color(0xFF131826)
val GlassWhite = Color(0x0EFFFFFF)
val GlassBorder = Color(0x14FFFFFF)
val GlassBorderHover = Color(0x26FFFFFF)
val AccentBlue = Color(0xFF60A5FA)
val AccentCyan = Color(0xFF22D3EE)
val EmeraldSuccess = Color(0xFF34D399)
val ErrorRed = Color(0xFFF87171)

// Glass card styling helper
fun Modifier.glassCard(cornerRadius: Dp = 18.dp, borderWidth: Dp = 1.dp) = this
    .background(color = GlassWhite, shape = RoundedCornerShape(cornerRadius))
    .border(width = borderWidth, color = GlassBorder, shape = RoundedCornerShape(cornerRadius))

@Composable
fun MainScreen(viewModel: MediaViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val activeTab by viewModel.activeTab.collectAsState()
    val isTvConnected by viewModel.isTvConnected.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val settingsTvIp by viewModel.settingsTvIp.collectAsState()

    // Handle incoming Event Toast notifications
    LaunchedEffect(key1 = true) {
        viewModel.toastEvent.collect { toast ->
            snackbarHostState.showSnackbar(
                message = toast.text,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Permission launcher for Location access (SSDP on local network scan sometimes requires it)
    val hasLocationPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission.value = isGranted
        if (isGranted) {
            viewModel.showToast("Разрешение получено. Поиск запущен!")
            viewModel.startScanner()
        } else {
            viewModel.showToast("Без разрешений поиск в сети может быть ограничен", isError = true)
        }
    }

    // Floating dynamic blobs animation state
    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundBlobs")
    
    val drift1X by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "drift1X"
    )
    val drift1Y by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 150f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "drift1Y"
    )
    val drift2X by infiniteTransition.animateFloat(
        initialValue = 150f,
        targetValue = -50f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "drift2X"
    )
    val drift2Y by infiniteTransition.animateFloat(
        initialValue = 200f,
        targetValue = -100f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "drift2Y"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xEE0F1423),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                )
            }
        },
        containerColor = SpaceBg
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .drawBehind {
                    // Draw glowing cosmic background blobs matching the original website drift
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x3B2563EB), Color.Transparent),
                            radius = 450f
                        ),
                        radius = 450f,
                        center = Offset(size.width * 0.1f + drift1X, size.height * 0.1f + drift1Y)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x2E0891B2), Color.Transparent),
                            radius = 380f
                        ),
                        radius = 380f,
                        center = Offset(size.width * 0.8f + drift2X, size.height * 0.9f + drift2Y)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x281E40AF), Color.Transparent),
                            radius = 350f
                        ),
                        radius = 350f,
                        center = Offset(size.width * 0.5f, size.height * 0.45f)
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Composable
                HeaderSection(isTvConnected = isTvConnected, deviceName = selectedDevice?.friendlyName ?: settingsTvIp)

                // Tab Switcher Composable
                TabSwitcher(activeTab = activeTab, onTabSelect = { viewModel.setActiveTab(it) })

                // Content panels
                when (activeTab) {
                    "remote" -> {
                        RemotePanel(
                            viewModel = viewModel,
                            hasLocationPermission = hasLocationPermission.value,
                            onRequestPermission = {
                                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        )
                    }
                    "parser" -> {
                        ParserPanel(viewModel = viewModel)
                    }
                    "settings" -> {
                        SettingsPanel(
                            viewModel = viewModel,
                            hasLocationPermission = hasLocationPermission.value,
                            onRequestPermission = {
                                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun HeaderSection(isTvConnected: Boolean, deviceName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0x3360A5FA), Color(0x1A22D3EE))),
                        RoundedCornerShape(12.dp)
                    )
                    .border(1.dp, Color(0x3360A5FA), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Cast,
                    contentDescription = "Cast Icon",
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = "Медиа-Пульт DLNA",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "Liquid Glass · v1.0",
                    color = Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
            }
        }

        // Connection Pill
        Box(
            modifier = Modifier
                .background(Color(0x0AFFFFFF), RoundedCornerShape(20.dp))
                .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(if (isTvConnected) pulseAlpha else 1.0f)
                        .background(
                            color = if (isTvConnected) EmeraldSuccess else ErrorRed,
                            shape = CircleShape
                        )
                )
                Text(
                    text = if (isTvConnected) {
                        if (deviceName.length > 12) deviceName.take(10) + "..." else deviceName
                    } else "ТВ не подключён",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun TabSwitcher(activeTab: String, onTabSelect: (String) -> Unit) {
    val tabs = listOf("remote" to "Пульт", "parser" to "Парсер", "settings" to "Настройки")
    val selectedIndex = tabs.indexOfFirst { it.first == activeTab }.coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 16.dp, borderWidth = 1.dp)
            .padding(4.dp)
    ) {
        // Animated slider background
        val transition = updateTransition(selectedIndex, label = "TabIndicator")
        val indicatorOffsetFraction by transition.animateFloat(
            transitionSpec = { spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium) },
            label = "OffsetFraction"
        ) { pageIndex ->
            pageIndex.toFloat()
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val tabWidth = maxWidth / 3
            Box(
                modifier = Modifier
                    .width(tabWidth)
                    .fillMaxHeight()
                    .offset(x = tabWidth * indicatorOffsetFraction)
                    .padding(2.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0x2660A5FA), Color(0x1422D3EE))),
                        RoundedCornerShape(12.dp)
                    )
                    .border(1.dp, Color(0x3360A5FA), RoundedCornerShape(12.dp))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (tabKey, tabTitle) ->
                val icon = when (tabKey) {
                    "remote" -> Icons.Outlined.SettingsRemote
                    "parser" -> Icons.Outlined.ScreenSearchDesktop
                    else -> Icons.Outlined.Settings
                }
                val isActive = activeTab == tabKey
                val animatedTextColor by animateColorAsState(
                    targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.4f),
                    label = "TabColor"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelect(tabKey) }
                        )
                        .padding(vertical = 8.dp)
                        .testTag("tab_button_$tabKey"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tabTitle,
                        tint = animatedTextColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = tabTitle,
                        color = animatedTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun RemotePanel(
    viewModel: MediaViewModel,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val remoteUrl by viewModel.remoteUrl.collectAsState()
    val isLocalPlaying by viewModel.isLocalPlaying.collectAsState()
    val isLocalMuted by viewModel.isLocalMuted.collectAsState()
    val localVolume by viewModel.localVolume.collectAsState()
    val isTvConnected by viewModel.isTvConnected.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val activeCastUrl by viewModel.activeCastUrl.collectAsState()
    val playbackPosition by viewModel.remotePlaybackPosition.collectAsState()
    val remoteVolume by viewModel.remoteVolume.collectAsState()
    val history by viewModel.castHistory.collectAsState()

    var showEditDialogItem by remember { mutableStateOf<CastHistoryItem?>(null) }
    val isValid = remoteUrl.startsWith("http://", ignoreCase = true) || remoteUrl.startsWith("https://", ignoreCase = true)

    // Media Link Input Card
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard()
                .padding(16.dp)
        ) {
            Text(
                text = "Ссылка на медиа",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x0AFFFFFF), RoundedCornerShape(12.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = "Link Icon",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = remoteUrl,
                    onValueChange = { viewModel.setRemoteUrl(it) },
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)
                        .testTag("url_input_field"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { viewModel.castMedia() })
                )
                if (remoteUrl.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.setRemoteUrl("") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear",
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Url validation string
            if (remoteUrl.isNotEmpty()) {
                Text(
                    text = if (isValid) "Ссылка действительна" else "Неверный формат ссылки",
                    color = if (isValid) EmeraldSuccess else ErrorRed,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .testTag("url_validation_label")
                )
            }
        }

        // Preview Player Frame Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isValid && remoteUrl.isNotEmpty()) {
                    // Actual Native VideoView Player preview
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setMediaController(MediaController(ctx).apply {
                                    setAnchorView(this@apply)
                                })
                            }
                        },
                        update = { videoView ->
                            try {
                                if (videoView.tag != remoteUrl) {
                                    videoView.tag = remoteUrl
                                    videoView.setVideoPath(remoteUrl)
                                }
                                if (isLocalPlaying) {
                                    videoView.start()
                                } else {
                                    videoView.pause()
                                }
                            } catch (e: Exception) {
                                LogManager.e("PreviewPlayer", "VideoView loading exception", e)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Placeholder card layout matching HTML mockup
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.alpha(0.4f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircleFilled,
                            contentDescription = "Placeholder Icon",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Введите ссылку для превью",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Preview local playback and volume controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Play / pause preview button
                IconButton(
                    onClick = { viewModel.toggleLocalPlay() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0x0AFFFFFF), RoundedCornerShape(14.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        imageVector = if (isLocalPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Local Play",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Volume toggle
                IconButton(
                    onClick = { viewModel.toggleLocalMute() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0x0AFFFFFF), RoundedCornerShape(14.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        imageVector = if (isLocalMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = "Mute Volume",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Slider
                Slider(
                    value = localVolume.toFloat(),
                    onValueChange = { viewModel.setLocalVolume(it.toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentBlue,
                        activeTrackColor = AccentBlue,
                        inactiveTrackColor = Color(0x1AFFFFFF)
                    )
                )
            }

            // Remote DLNA casting action button
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { viewModel.castMedia() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("cast_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0x3360A5FA), Color(0x1F22D3EE))
                            )
                        )
                        .border(1.dp, Color(0x4D60A5FA), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SettingsInputAntenna,
                            contentDescription = "Cast Antenna",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Транслировать по DLNA",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // TV Remote Control Area (displays dynamically when casting active)
            AnimatedVisibility(visible = isTvConnected && activeCastUrl != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .background(Color(0x05FFFFFF), RoundedCornerShape(14.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "УПРАВЛЕНИЕ ТВ ПЛЕЕРОМ",
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Track Position Info
                    playbackPosition?.let { pos ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(pos.relTimeString, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            Text(pos.durationString, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }

                        val progress = if (pos.durationSeconds > 0) {
                            pos.relTimeSeconds.toFloat() / pos.durationSeconds
                        } else 0f

                        Slider(
                            value = progress,
                            onValueChange = { fraction ->
                                if (pos.durationSeconds > 0) {
                                    val targetSeconds = (fraction * pos.durationSeconds).toInt()
                                    viewModel.seekRemote(targetSeconds)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = AccentCyan,
                                activeTrackColor = AccentCyan,
                                inactiveTrackColor = Color(0x1AFFFFFF)
                            )
                        )
                    }

                    // Soap Action Keys
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.seekRemote((playbackPosition?.relTimeSeconds ?: 0) - 30) }) {
                            Icon(Icons.Filled.Replay30, "Seek Back", tint = Color.White)
                        }
                        IconButton(onClick = { viewModel.pauseRemote() }) {
                            Icon(Icons.Filled.Pause, "Remote Pause", tint = Color.White)
                        }
                        IconButton(
                            onClick = { viewModel.playRemote() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0x14FFFFFF), CircleShape)
                        ) {
                            Icon(Icons.Filled.PlayArrow, "Remote Play", tint = AccentCyan)
                        }
                        IconButton(onClick = { viewModel.stopRemote() }) {
                            Icon(Icons.Filled.Stop, "Remote Stop", tint = ErrorRed)
                        }
                        IconButton(onClick = { viewModel.seekRemote((playbackPosition?.relTimeSeconds ?: 0) + 30) }) {
                            Icon(Icons.Filled.Forward30, "Seek Forward", tint = Color.White)
                        }
                    }

                    // TV Volume controller
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.VolumeMute, "TV Mute", tint = Color.White.copy(alpha = 0.4f))
                        Slider(
                            value = remoteVolume.toFloat(),
                            onValueChange = { viewModel.setRemoteVolume(it.toInt()) },
                            valueRange = 0f..100f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = AccentCyan,
                                activeTrackColor = AccentCyan,
                                inactiveTrackColor = Color(0x1AFFFFFF)
                            )
                        )
                        Icon(Icons.Filled.VolumeUp, "TV VolUp", tint = AccentCyan)
                    }
                }
            }
        }

        // Cast History Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = "History Icon",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "История",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (history.isNotEmpty()) {
                    Text(
                        text = "Очистить",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { viewModel.clearAllHistory() }
                            .testTag("clear_history_btn")
                    )
                }
            }

            if (history.isEmpty()) {
                Text(
                    text = "История пуста",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    history.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x08FFFFFF), RoundedCornerShape(12.dp))
                                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setRemoteUrl(item.url)
                                    viewModel.showToast("Загружено в плеер")
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0x1A60A5FA), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = AccentBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.url,
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { showEditDialogItem = item },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Rename",
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.deleteHistoryItem(item.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = ErrorRed.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // History Renaming Dialog
    showEditDialogItem?.let { historyItem ->
        var tempName by remember { mutableStateOf(historyItem.name) }
        AlertDialog(
            onDismissRequest = { showEditDialogItem = null },
            title = { Text("Переименовать ссылку", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    placeholder = { Text("Название") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = Color(0xFF0F1423),
                        unfocusedContainerColor = Color(0xFF0F1423)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameHistoryItem(historyItem.id, tempName)
                        showEditDialogItem = null
                    }
                ) {
                    Text("Сохранить", color = AccentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialogItem = null }) {
                    Text("Отмена", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF131826),
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
fun ParserPanel(viewModel: MediaViewModel) {
    val parserUrl by viewModel.parserUrl.collectAsState()
    val isParsing by viewModel.isParsing.collectAsState()
    val currentStepIndex by viewModel.currentParseStepIndex.collectAsState()
    val parsedStreamUrl by viewModel.parsedStreamUrl.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    val steps = listOf(
        "Подключение к прокси-серверу...",
        "Обход CORS ограничений...",
        "Анализ DOM-дерева плеера...",
        "Поиск скрытых потоков .mp4/.m3u8...",
        "Поток успешно захвачен!"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Form link Input
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard()
                .padding(16.dp)
        ) {
            Text(
                text = "Ссылка на страницу онлайн-кинотеатра",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x0AFFFFFF), RoundedCornerShape(12.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = "Link Icon",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = parserUrl,
                    onValueChange = { viewModel.setParserUrl(it) },
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)
                        .testTag("parser_input_field"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.startParsing() })
                )
            }
        }

        // Parse Action Button
        Button(
            onClick = { viewModel.startParsing() },
            enabled = !isParsing,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("parse_action_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (isParsing) 0.5f else 1f)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0x3360A5FA), Color(0x1F22D3EE))
                        )
                    )
                    .border(1.dp, Color(0x4D60A5FA), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = "Download Cloud",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Вытянуть поток",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Active parser steps & concentric spinner circles matching HTML design
        AnimatedVisibility(visible = isParsing || currentStepIndex >= 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Concentric loader circles canvas
                if (isParsing) {
                    val infiniteTransition = rememberInfiniteTransition(label = "ParserLoader")
                    val angleSpeed1 by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ), label = "RingSpeed1"
                    )
                    val angleSpeed2 by infiniteTransition.animateFloat(
                        initialValue = 360f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(700, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ), label = "RingSpeed2"
                    )
                    val angleSpeed3 by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ), label = "RingSpeed3"
                    )

                    Canvas(modifier = Modifier.size(56.dp)) {
                        // Outer Ring (AccentBlue)
                        drawArc(
                            color = AccentBlue,
                            startAngle = angleSpeed1,
                            sweepAngle = 280f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx())
                        )
                        // Middle Ring (AccentCyan, reverse)
                        drawArc(
                            color = AccentCyan,
                            startAngle = angleSpeed2,
                            sweepAngle = 200f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx()),
                            size = size * 0.7f,
                            topLeft = Offset(size.width * 0.15f, size.height * 0.15f)
                        )
                        // Inner Ring (Purple)
                        drawArc(
                            color = Color(0xFFA78BFA),
                            startAngle = angleSpeed3,
                            sweepAngle = 140f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx()),
                            size = size * 0.45f,
                            topLeft = Offset(size.width * 0.275f, size.height * 0.275f)
                        )
                    }
                }

                // Steps indicators
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    steps.forEachIndexed { index, stepText ->
                        val active = currentStepIndex == index
                        val done = currentStepIndex > index
                        val revealed = currentStepIndex >= index

                        AnimatedVisibility(visible = revealed) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (done) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Done",
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else if (active) {
                                    CircularProgressIndicator(
                                        color = AccentBlue,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.size(16.dp))
                                }

                                Text(
                                    text = stepText,
                                    color = if (done) EmeraldSuccess else if (active) Color.White else Color.White.copy(alpha = 0.4f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Extraction results card
        parsedStreamUrl?.let { url ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard()
                    .border(1.dp, EmeraldSuccess.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Success",
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Поток захвачен",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Monospace URL text block
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EmeraldSuccess.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .border(1.dp, EmeraldSuccess.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = url,
                        color = EmeraldSuccess,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        overflow = TextOverflow.Clip
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ссылка автоматически добавлена в Пульт",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(url))
                            viewModel.showToast("Ссылка скопирована")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x1F34D399)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Копировать", color = EmeraldSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPanel(
    viewModel: MediaViewModel,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val settingsTvIp by viewModel.settingsTvIp.collectAsState()
    val isTvConnected by viewModel.isTvConnected.collectAsState()
    val dlnaDevices by viewModel.dlnaDevices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val logs by viewModel.logs.collectAsState()

    var logFilterTag by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Form manual IP Config Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard()
                .padding(16.dp)
        ) {
            Text(
                text = "IP-адрес телевизора",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x0AFFFFFF), RoundedCornerShape(12.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Tv,
                    contentDescription = "TV Icon",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = settingsTvIp,
                    onValueChange = { viewModel.setSettingsTvIp(it) },
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)
                        .testTag("ip_input_field"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.saveTvIpManual() })
                )
            }
        }

        // Action Save / Reset Settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { viewModel.saveTvIpManual() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("save_ip_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0x3360A5FA), Color(0x1F22D3EE))
                            )
                        )
                        .border(1.dp, Color(0x4D60A5FA), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Save, "Save", tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("Сохранить", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            IconButton(
                onClick = { viewModel.clearTvIpManual() },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0x0AFFFFFF), RoundedCornerShape(14.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .testTag("clear_ip_btn")
            ) {
                Icon(Icons.Filled.SettingsBackupRestore, "Reset", tint = Color.White.copy(alpha = 0.6f))
            }
        }

        // Wobbling Glossy Liquid Drop Status Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Liquid drop wobbling drawing
                WobblingLiquidDrop(isTvConnected = isTvConnected)

                Column {
                    Text(
                        text = "Статус подключения",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isTvConnected) "Подключено: ${selectedDevice?.friendlyName ?: settingsTvIp}" else "IP не задан",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .testTag("connection_status_text")
                    )
                }
            }
        }

        // Local Network Scanning Area (SSDP Devices List)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Устройства в сети (SSDP)",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (isScanning) {
                    CircularProgressIndicator(
                        color = AccentBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    IconButton(
                        onClick = {
                            if (!hasLocationPermission) {
                                onRequestPermission()
                            } else {
                                viewModel.startScanner()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Scan network",
                            tint = AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (!hasLocationPermission) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Для сканирования Wi-Fi требуется разрешение на геолокацию",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x1F60A5FA)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Разрешить", color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (dlnaDevices.isEmpty()) {
                Text(
                    text = "Устройств не обнаружено. Нажмите обновить.",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dlnaDevices.forEach { device ->
                        val isSelected = selectedDevice?.id == device.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (isSelected) Color(0x1F60A5FA) else Color(0x08FFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) AccentBlue.copy(alpha = 0.4f) else GlassBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.selectDevice(device) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0x1A22D3EE), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ConnectedTv,
                                        contentDescription = "Smart TV",
                                        tint = AccentCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.friendlyName,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${device.ip}:${device.port}",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Network Live Diagnostics Log Console Terminal (COOL addition for debugging!)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Terminal, "Console", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                    Text("Консоль отладки сети", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = { viewModel.clearLogs() }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.DeleteSweep, "Clear Logs", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                }
            }

            // Quick Tag Filter buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("" to "Все", "DlnaScanner" to "Поиск", "DlnaControl" to "Soap").forEach { (tag, label) ->
                    val selected = logFilterTag == tag
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (selected) AccentBlue else Color(0x14FFFFFF),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { logFilterTag = tag }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(label, color = if (selected) Color.Black else Color.White.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Scrollable terminal screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color(0xFF070B13), RoundedCornerShape(10.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                val filteredLogs = remember(logs, logFilterTag) {
                    if (logFilterTag.isEmpty()) logs else logs.filter { it.tag == logFilterTag }
                }

                if (filteredLogs.isEmpty()) {
                    Text(
                        "[Консоль готова. Логи появятся при поиске ТВ или трансляции]",
                        color = Color.White.copy(alpha = 0.2f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredLogs) { log ->
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = Color.Gray)) {
                                        append("[${log.timestamp}] ")
                                    }
                                    withStyle(SpanStyle(color = if (log.tag == "DlnaScanner") AccentBlue else AccentCyan)) {
                                        append("${log.tag}: ")
                                    }
                                    withStyle(SpanStyle(color = if (log.isError) ErrorRed else Color.LightGray)) {
                                        append(log.message)
                                    }
                                },
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // About card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Info",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "О приложении",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Медиа-Пульт DLNA позволяет транслировать медиаконтент на совместимые телевизоры по протоколу DLNA.\nВведите IP-адрес вашего ТВ в настройках, укажите ссылку на медиафайл и нажмите «Транслировать».",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

// 3D Wobbling Liquid Drop status indicator Composable drawing
@Composable
fun WobblingLiquidDrop(isTvConnected: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidDrop")
    val scope = rememberCoroutineScope()

    // Smooth color change
    val dropColor by animateColorAsState(
        targetValue = if (isTvConnected) EmeraldSuccess else ErrorRed,
        animationSpec = tween(600), label = "DropColor"
    )

    // Wobbling scales
    val wobbleX by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "wobbleX"
    )
    val wobbleY by infiniteTransition.animateFloat(
        initialValue = 1.05f,
        targetValue = 0.96f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "wobbleY"
    )

    // Glowing radius
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glowRadius"
    )

    Canvas(
        modifier = Modifier
            .size(56.dp)
            .blur(0.2.dp)
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.width / 2 * 0.8f

        // Draw outer fuzzy glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(dropColor.copy(alpha = 0.4f), Color.Transparent),
                radius = radius + glowRadius
            ),
            radius = radius + glowRadius,
            center = Offset(centerX, centerY)
        )

        // Draw 3D organic glossy liquid drop shape using Path with animated scales
        val path = Path().apply {
            val rx = radius * wobbleX
            val ry = radius * wobbleY
            moveTo(centerX, centerY - ry)
            cubicTo(centerX + rx * 0.9f, centerY - ry, centerX + rx, centerY - ry * 0.3f, centerX + rx, centerY)
            cubicTo(centerX + rx, centerY + ry * 0.8f, centerX + rx * 0.6f, centerY + ry, centerX, centerY + ry)
            cubicTo(centerX - rx * 0.6f, centerY + ry, centerX - rx, centerY + ry * 0.8f, centerX - rx, centerY)
            cubicTo(centerX - rx, centerY - ry * 0.3f, centerX - rx * 0.9f, centerY - ry, centerX, centerY - ry)
            close()
        }

        drawPath(
            path = path,
            brush = Brush.radialGradient(
                colors = listOf(dropColor, dropColor.copy(alpha = 0.6f), Color(0xFF000000)),
                center = Offset(centerX - radius * 0.3f, centerY - radius * 0.3f),
                radius = radius * 1.5f
            )
        )

        // Draw highlight gloss reflection
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.4f), Color.Transparent),
                radius = radius * 0.3f
            ),
            radius = radius * 0.3f,
            center = Offset(centerX - radius * 0.3f, centerY - radius * 0.4f)
        )
    }
}
