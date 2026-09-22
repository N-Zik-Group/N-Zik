package app.n_zik.android.components.ui.screens.rewind.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import android.graphics.BitmapFactory
import androidx.compose.ui.text.TextStyle

// Month names for display
val monthNames = mapOf(
    1 to "January", 2 to "February", 3 to "March", 4 to "April",
    5 to "May", 6 to "June", 7 to "July", 8 to "August",
    9 to "September", 10 to "October", 11 to "November", 12 to "December"
)

// Day names for display
val dayNames = mapOf(
    "MON" to "Monday", "TUE" to "Tuesday", "WED" to "Wednesday", 
    "THU" to "Thursday", "FRI" to "Friday", "SAT" to "Saturday", "SUN" to "Sunday"
)

// Hour labels with descriptions
val hourDescriptions = mapOf(
    "00:00" to "Midnight", "01:00" to "Early Night", "02:00" to "Late Night",
    "03:00" to "Late Night", "04:00" to "Early Morning", "05:00" to "Dawn",
    "06:00" to "Morning", "07:00" to "Morning", "08:00" to "Morning Commute",
    "09:00" to "Morning", "10:00" to "Mid-morning", "11:00" to "Late Morning",
    "12:00" to "Noon", "13:00" to "Early Afternoon", "14:00" to "Afternoon",
    "15:00" to "Mid-afternoon", "16:00" to "Late Afternoon", "17:00" to "Evening Start",
    "18:00" to "Evening", "19:00" to "Evening", "20:00" to "Night",
    "21:00" to "Late Evening", "22:00" to "Late Evening", "23:00" to "Late Night"
)

fun getMonthName(monthNumber: String): String {
    return try {
        val monthInt = monthNumber.toInt()
        monthNames[monthInt] ?: "Month $monthNumber"
    } catch (e: Exception) {
        "Month $monthNumber"
    }
}

@Composable
fun StatItem(value: String, label: String, icon: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = icon,
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun StatRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = icon, fontSize = 20.sp)
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp
            )
        }
        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NetworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
) {
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(url) {
        if (url != null) {
            isLoading = true
            imageBitmap = withContext(Dispatchers.IO) {
                try {
                    val connection = URL(url).openConnection()
                    connection.connectTimeout = 2000
                    connection.readTimeout = 2000
                    val inputStream = connection.getInputStream()
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    bitmap?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
            isLoading = false
        } else {
            isLoading = false
        }
    }
    
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF3D2E54),
                        Color(0xFF2A1F3A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            placeholder()
        } else if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            Text(
                text = if (contentDescription?.contains("artist") == true) "🎤" else "🎵",
                fontSize = 20.sp
            )
        }
    }
}