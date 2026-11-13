package com.example.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.engine.process.SlotProcessTelemetry
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldMatrix
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun AppIconBadge(
    icon: Drawable?,
    cloneIndex: Int? = null,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(icon) {
        icon?.let {
            runCatching { it.toBitmap(width = 128, height = 128).asImageBitmap() }.getOrNull()
        }
    }

    Box(modifier = modifier.size(size)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(14.dp),
            color = ObsidianSurfaceHigh,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Application icon",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = "Application icon",
                        tint = CyanPulse,
                        modifier = Modifier.size(size * 0.55f)
                    )
                }
            }
        }

        if (cloneIndex != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(CyanPulse)
                    .border(1.5.dp, ObsidianCanvas, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$cloneIndex",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = ObsidianCanvas
                    )
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = accentColor.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun SlotStatusStrip(
    slots: Map<Int, SlotProcessTelemetry>,
    onSlotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (0..3).forEach { idx ->
            val slot = slots[idx]
            val isActive = slot?.isBound == true
            val accent = if (isActive) EmeraldMatrix else TextMuted
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSlotClick(idx) }
                    .testTag("slot_pill_$idx"),
                shape = RoundedCornerShape(12.dp),
                color = if (isActive) EmeraldMatrix.copy(alpha = 0.1f) else ObsidianSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isActive) EmeraldMatrix.copy(alpha = 0.55f) else ObsidianOutline
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Text(
                            text = "P$idx",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) EmeraldMatrix else TextSecondary
                            )
                        )
                    }
                    Text(
                        text = if (isActive) "PID ${slot?.pid}" else "IDLE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isActive) MaterialTheme.colorScheme.onSurface else TextMuted
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isActive) "${"%.1f".format(slot?.heapUsedMb ?: 0f)}M" else "Ready",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun TelemetrySparklineChart(
    samples: List<Float>,
    lineColor: Color,
    label: String,
    currentValueText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                )
                Text(
                    text = currentValueText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = lineColor
                    )
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
            ) {
                val w = size.width
                val h = size.height
                // Subtle horizontal reference lines
                listOf(0.25f, 0.5f, 0.75f).forEach { ratio ->
                    drawLine(
                        color = ObsidianOutline.copy(alpha = 0.5f),
                        start = Offset(0f, h * ratio),
                        end = Offset(w, h * ratio),
                        strokeWidth = 1f
                    )
                }

                if (samples.size >= 2) {
                    val stepX = w / (samples.size - 1).coerceAtLeast(1)
                    val path = Path()
                    val fillPath = Path()
                    samples.forEachIndexed { index, value ->
                        val x = index * stepX
                        val norm = (value / 100f).coerceIn(0f, 1f)
                        val y = h - (norm * (h - 6f)) - 3f
                        if (index == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }
                    fillPath.lineTo((samples.size - 1) * stepX, h)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.35f),
                                lineColor.copy(alpha = 0.02f)
                            )
                        )
                    )
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

@Composable
fun PinVerifyDialog(
    cloneLabel: String,
    onDismiss: () -> Unit,
    onVerify: (String) -> Boolean
) {
    var pinInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "PIN lock",
                tint = AmberWarn
            )
        },
        title = { Text("Unlock $cloneLabel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter the container PIN to spin up this isolated sandbox session.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        isError = false
                    },
                    label = { Text("Container PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = isError,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_verify_input")
                )
                if (isError) {
                    Text(
                        text = "Incorrect PIN. Access denied.",
                        style = MaterialTheme.typography.bodySmall.copy(color = CoralAlert)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!onVerify(pinInput)) {
                        isError = true
                    }
                },
                modifier = Modifier.testTag("pin_verify_confirm")
            ) {
                Text("Unlock & Launch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
