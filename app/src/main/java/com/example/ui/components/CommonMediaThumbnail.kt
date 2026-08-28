package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaItem

private val sampleGradients = listOf(
    Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF60A5FA))),
    Brush.linearGradient(listOf(Color(0xFFF97316), Color(0xFFFBBF24))),
    Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFC084FC))),
    Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF34D399))),
    Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFFF472B6))),
    Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF38BDF8))),
    Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFA78BFA))),
    Brush.linearGradient(listOf(Color(0xFFEA580C), Color(0xFFFB923C))),
    Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF6EE7B7))),
    Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF818CF8)))
)

@Composable
fun CommonMediaThumbnail(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (mediaItem.uriString.isNotBlank()) {
            AsyncImage(
                model = mediaItem.uriString,
                contentDescription = mediaItem.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            // High aesthetic photo placeholder based on index
            val gradient = sampleGradients[mediaItem.sampleImageIndex % sampleGradients.size]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                if (mediaItem.isLarge) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Video",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Photo",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(12.dp)
                    )
                }

                // Small badge
                if (mediaItem.isBlurry) {
                    Text(
                        text = "BLUR",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                } else if (mediaItem.isScreenshot) {
                    Text(
                        text = "SCREENSHOT",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(Color(0xFF0F47D0).copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
