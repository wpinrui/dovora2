package com.wpinrui.dovora.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.roundToInt

@Composable
fun AccountSheet(
    isVisible: Boolean,
    isSignedIn: Boolean,
    userName: String?,
    userEmail: String?,
    userPhotoUrl: String?,
    aiPrefillEnabled: Boolean,
    defaultDownloadType: DefaultDownloadType,
    maxVideoQuality: MaxVideoQuality,
    onAiPrefillChange: (Boolean) -> Unit,
    onDefaultDownloadChange: (DefaultDownloadType) -> Unit,
    onMaxQualityChange: (MaxVideoQuality) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeight.toPx() }
    
    val offsetAnimatable = remember { Animatable(screenHeightPx) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val dismissThreshold = screenHeightPx * 0.2f
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            offsetAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else {
            offsetAnimatable.animateTo(
                targetValue = screenHeightPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }
    
    if (isVisible || offsetAnimatable.value < screenHeightPx) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * (1f - offsetAnimatable.value / screenHeightPx)))
                .clickable(onClick = onDismiss)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, (offsetAnimatable.value + dragOffset).roundToInt().coerceAtLeast(0)) }
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF1A1A1A))
                    .clickable(enabled = false) {} // Prevent click-through
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = {
                                isDragging = false
                                if (dragOffset > dismissThreshold) {
                                    onDismiss()
                                }
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                isDragging = false
                                dragOffset = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount > 0 || dragOffset > 0) {
                                    dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                                }
                            }
                        )
                    }
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    // Handle bar
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .align(Alignment.CenterHorizontally)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                    
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Account",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // User section
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(16.dp)
                        ) {
                            if (isSignedIn) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Profile picture
                                    if (userPhotoUrl != null) {
                                        AsyncImage(
                                            model = userPhotoUrl,
                                            contentDescription = "Profile picture",
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "Profile",
                                                tint = Color.White.copy(alpha = 0.5f),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(16.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (userName != null) {
                                            Text(
                                                text = userName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White
                                            )
                                        }
                                        if (userEmail != null) {
                                            Text(
                                                text = userEmail,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                    
                                    OutlinedButton(
                                        onClick = onSignOut,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFCF6679)
                                        )
                                    ) {
                                        Text("Sign Out")
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Not signed in",
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Sign in to sync your library",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = onSignIn,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Text(
                                            text = "G",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4285F4),
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text("Continue with Google")
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Settings section
                        Text(
                            text = "SETTINGS",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                        ) {
                            Column {
                                // AI Prefill Setting
                                SettingRow(
                                    title = "AI Prefill",
                                    subtitle = "Automatically fill song title and artist",
                                    trailing = {
                                        Switch(
                                            checked = aiPrefillEnabled,
                                            onCheckedChange = onAiPrefillChange,
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = Color(0xFF4CAF50),
                                                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                                                uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                                            )
                                        )
                                    }
                                )
                                
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                
                                // Default Download Type
                                var showDownloadTypeMenu by remember { mutableStateOf(false) }
                                SettingRow(
                                    title = "Default Download",
                                    subtitle = "Start with this option selected",
                                    trailing = {
                                        Box {
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { showDownloadTypeMenu = true }
                                                    .background(Color.White.copy(alpha = 0.1f))
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (defaultDownloadType == DefaultDownloadType.MUSIC) 
                                                        Icons.Default.MusicNote else Icons.Default.Videocam,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (defaultDownloadType == DefaultDownloadType.MUSIC) "Music" else "Video",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showDownloadTypeMenu,
                                                onDismissRequest = { showDownloadTypeMenu = false },
                                                modifier = Modifier.background(Color(0xFF2A2A2A))
                                            ) {
                                                DropdownMenuItem(
                                                    text = { 
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                                            Spacer(Modifier.width(8.dp))
                                                            Text("Music", color = Color.White)
                                                        }
                                                    },
                                                    onClick = {
                                                        onDefaultDownloadChange(DefaultDownloadType.MUSIC)
                                                        showDownloadTypeMenu = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { 
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.Videocam, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                                            Spacer(Modifier.width(8.dp))
                                                            Text("Video", color = Color.White)
                                                        }
                                                    },
                                                    onClick = {
                                                        onDefaultDownloadChange(DefaultDownloadType.VIDEO)
                                                        showDownloadTypeMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                )
                                
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                
                                // Max Video Quality
                                var showQualityMenu by remember { mutableStateOf(false) }
                                SettingRow(
                                    title = "Max Video Quality",
                                    subtitle = "Downloads will not exceed this quality",
                                    trailing = {
                                        Box {
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { showQualityMenu = true }
                                                    .background(Color.White.copy(alpha = 0.1f))
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = maxVideoQuality.label,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showQualityMenu,
                                                onDismissRequest = { showQualityMenu = false },
                                                modifier = Modifier.background(Color(0xFF2A2A2A))
                                            ) {
                                                MaxVideoQuality.entries.forEach { quality ->
                                                    DropdownMenuItem(
                                                        text = { 
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(quality.label, color = Color.White)
                                                                if (quality == maxVideoQuality) {
                                                                    Icon(
                                                                        Icons.Default.Check,
                                                                        null,
                                                                        tint = Color(0xFF4CAF50),
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        onClick = {
                                                            onMaxQualityChange(quality)
                                                            showQualityMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        trailing()
    }
}

