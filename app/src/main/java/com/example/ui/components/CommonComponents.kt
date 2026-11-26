package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.DecryptedLocation
import com.example.data.MessageReceiptStatus
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.ReadReceiptBlueDark
import com.example.ui.theme.ReadReceiptBlueLight
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

@Composable
fun UserAvatar(
    name: String,
    photoUrl: String? = null,
    isGroup: Boolean = false,
    isOnline: Boolean = false,
    size: Dp = 48.dp
) {
    val bgPalette = listOf(
        Color(0xFF00897B),
        Color(0xFF1E88E5),
        Color(0xFF5E35B1),
        Color(0xFFD81B60),
        Color(0xFFF4511E),
        Color(0xFF00ACC1)
    )
    val bgColor = bgPalette[abs(name.hashCode()) % bgPalette.size]

    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (isGroup) EmeraldDeep else bgColor),
            contentAlignment = Alignment.Center
        ) {
            if (!photoUrl.isNullOrBlank() && !isGroup) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Foto profil $name",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isGroup) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = "Ikon grup $name",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.52f)
                )
            } else {
                val initials = name.trim()
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .take(2)
                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                    .joinToString("")
                if (initials.isNotEmpty()) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = name,
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.55f)
                    )
                }
            }
        }
        if (isOnline && !isGroup) {
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun LocationMiniMapCard(
    location: DecryptedLocation,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0E292E), Color(0xFF133A3F), Color(0xFF0B1F24))
                    )
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 28.dp.toPx()
                val gridColor = Color(0xFF235D63).copy(alpha = 0.45f)
                var x = 0f
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
                // Stylized road lines
                drawLine(
                    color = Color(0xFF3A8D93).copy(alpha = 0.6f),
                    start = Offset(0f, size.height * 0.68f),
                    end = Offset(size.width, size.height * 0.25f),
                    strokeWidth = 6f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f))
                )
                drawLine(
                    color = Color(0xFF2C6B73).copy(alpha = 0.55f),
                    start = Offset(size.width * 0.22f, 0f),
                    end = Offset(size.width * 0.78f, size.height),
                    strokeWidth = 5f
                )
                // Accuracy radius pulse circle
                drawCircle(
                    color = (if (isLive) EmeraldBright else EmeraldPrimary).copy(alpha = 0.22f),
                    radius = 36.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = (if (isLive) EmeraldBright else EmeraldPrimary).copy(alpha = 0.55f),
                    radius = 18.dp.toPx(),
                    center = center
                )
            }

            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Pin Lokasi",
                tint = if (isLive) EmeraldBright else Color(0xFFFF5252),
                modifier = Modifier
                    .size(34.dp)
                    .align(Alignment.Center)
            )

            Surface(
                color = (if (isLive) EmeraldPrimary else Color(0xFF1B2B32)).copy(alpha = 0.92f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isLive) Color(0xFF002119) else EmeraldBright,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isLive) "LIVE GPS" else "LOKASI TERKINI",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLive) Color(0xFF002119) else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = location.addressLabel.ifBlank { "Koordinat GPS Terenkripsi" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )
        Text(
            text = String.format(
                Locale.US,
                "%.5f, %.5f (±%.0fm)",
                location.latitude,
                location.longitude,
                location.accuracyMeters
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun formatTimeShort(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: return "..."
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
}

fun formatDurationMs(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

@Composable
fun MessageReceiptTicks(
    status: MessageReceiptStatus,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 15.dp
) {
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    val blueColor = if (isDark) ReadReceiptBlueDark else ReadReceiptBlueLight
    val (icon, tint, description) = when (status) {
        MessageReceiptStatus.SENDING -> Triple(Icons.Default.Schedule, mutedColor, "Mengirim pesan")
        MessageReceiptStatus.SENT -> Triple(Icons.Default.Check, mutedColor, "Terkirim")
        MessageReceiptStatus.DELIVERED -> Triple(Icons.Default.DoneAll, mutedColor, "Tersampaikan")
        MessageReceiptStatus.READ -> Triple(Icons.Default.DoneAll, blueColor, "Dibaca")
    }
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = modifier.size(size)
    )
}

