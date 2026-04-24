package com.nuzet.swipeclean

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.nuzet.swipeclean.ui.theme.SwipeCleanTheme
import com.nuzet.swipeclean.widget.SwipeCleanWidget
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SwipeCleanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Manifest.permission.READ_MEDIA_IMAGES
                else
                    Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    if (hasPermission) {
        SwipeScreen()
    } else {
        PermissionScreen {
            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE
            permissionLauncher.launch(perm)
        }
    }
}

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "SwipeClean",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Нужен доступ к галерее, чтобы показывать случайные фото для сортировки",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Разрешить доступ", fontSize = 16.sp)
        }
    }
}

@Composable
fun SwipeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var deletedCount by remember { mutableStateOf(0) }
    var keptCount by remember { mutableStateOf(0) }
    var pendingDeleteUri by remember { mutableStateOf<Uri?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            deletedCount++
        }
        pendingDeleteUri = null
        currentIndex++
    }

    LaunchedEffect(Unit) {
        photos = PhotoRepository.loadRandomPhotos(context, limit = 200)
        SwipeCleanWidget.refreshAll(context)
    }

    if (photos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (currentIndex >= photos.size) {
        EmptyScreen(deletedCount, keptCount) {
            scope.launch {
                photos = PhotoRepository.loadRandomPhotos(context, limit = 200)
                currentIndex = 0
                deletedCount = 0
                keptCount = 0
            }
        }
        return
    }

    val currentPhoto = photos[currentIndex]
    val nextPhoto = photos.getOrNull(currentIndex + 1)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TopBar(
                current = currentIndex + 1,
                total = photos.size,
                deleted = deletedCount,
                kept = keptCount
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Next card (underneath)
                nextPhoto?.let {
                    PhotoCard(
                        photo = it,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 0.95f
                                scaleY = 0.95f
                                alpha = 0.5f
                            }
                    )
                }

                // Current card (swipeable)
                SwipeableCard(
                    photo = currentPhoto,
                    onSwipeLeft = {
                        // delete
                        scope.launch {
                            val sender = PhotoRepository.buildDeleteRequest(context, currentPhoto.uri)
                            if (sender != null) {
                                pendingDeleteUri = currentPhoto.uri
                                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            } else {
                                currentIndex++
                            }
                        }
                    },
                    onSwipeRight = {
                        keptCount++
                        currentIndex++
                    }
                )
            }

            BottomActions(
                onDelete = {
                    scope.launch {
                        val sender = PhotoRepository.buildDeleteRequest(context, currentPhoto.uri)
                        if (sender != null) {
                            pendingDeleteUri = currentPhoto.uri
                            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        } else {
                            currentIndex++
                        }
                    }
                },
                onKeep = {
                    keptCount++
                    currentIndex++
                }
            )
        }
    }
}

@Composable
fun TopBar(current: Int, total: Int, deleted: Int, kept: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$current / $total",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("✓ $kept", fontSize = 14.sp, color = Color(0xFF4CAF50))
            Text("✕ $deleted", fontSize = 14.sp, color = Color(0xFFE57373))
        }
    }
}

@Composable
fun SwipeableCard(
    photo: Photo,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    var offsetX by remember(photo.uri) { mutableStateOf(0f) }
    var offsetY by remember(photo.uri) { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "offsetX"
    )
    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "offsetY"
    )
    val rotation = (animatedOffsetX / 40f).coerceIn(-20f, 20f)
    val threshold = 300f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset {
                IntOffset(animatedOffsetX.roundToInt(), animatedOffsetY.roundToInt())
            }
            .rotate(rotation)
            .pointerInput(photo.uri) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            offsetX < -threshold -> {
                                offsetX = -2000f
                                onSwipeLeft()
                            }
                            offsetX > threshold -> {
                                offsetX = 2000f
                                onSwipeRight()
                            }
                            else -> {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
    ) {
        PhotoCard(photo = photo, modifier = Modifier.fillMaxSize())

        // Delete overlay (swipe left)
        if (offsetX < 0) {
            val alpha = (abs(offsetX) / threshold).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFE57373).copy(alpha = alpha * 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer { this.alpha = alpha }
                )
            }
        }
        // Keep overlay (swipe right)
        if (offsetX > 0) {
            val alpha = (abs(offsetX) / threshold).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF4CAF50).copy(alpha = alpha * 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer { this.alpha = alpha }
                )
            }
        }
    }
}

@Composable
fun PhotoCard(photo: Photo, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun BottomActions(onDelete: () -> Unit, onKeep: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        FloatingActionButton(
            onClick = onDelete,
            containerColor = Color(0xFFE57373),
            contentColor = Color.White,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Удалить", modifier = Modifier.size(32.dp))
        }
        FloatingActionButton(
            onClick = onKeep,
            containerColor = Color(0xFF4CAF50),
            contentColor = Color.White,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(Icons.Rounded.Favorite, contentDescription = "Оставить", modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun EmptyScreen(deleted: Int, kept: Int, onReload: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Готово!", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Оставлено: $kept", fontSize = 18.sp, color = Color(0xFF4CAF50))
        Text("Удалено: $deleted", fontSize = 18.sp, color = Color(0xFFE57373))
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onReload,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Загрузить ещё", fontSize = 16.sp)
        }
    }
}
