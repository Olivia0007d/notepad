package com.deepanjanxyz.notepad.ui.feature_drawing.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepanjanxyz.notepad.domain.model.EraserMode
import com.deepanjanxyz.notepad.ui.theme.AppColors
import kotlin.math.abs

// The three eraser sizes offered as presets, in pixels.
private const val ERASER_SMALL_RADIUS_PX = 18f
private const val ERASER_MEDIUM_RADIUS_PX = 32f
private const val ERASER_LARGE_RADIUS_PX = 48f

@Composable
fun DrawingEraserDrawer(
    currentMode: EraserMode,
    onModeSelected: (EraserMode) -> Unit,
    currentRadiusPx: Float,
    onRadiusChanged: (Float) -> Unit,
    onClearCanvas: () -> Unit
) = EraserOptionsDrawer(currentMode, onModeSelected, currentRadiusPx, onRadiusChanged, onClearCanvas)

@Composable
fun EraserOptionsDrawer(
    currentMode: EraserMode,
    onModeSelected: (EraserMode) -> Unit,
    currentRadiusPx: Float,
    onRadiusChanged: (Float) -> Unit,
    onClearCanvas: () -> Unit
) {
    Surface(
        color = AppColors.Panel,
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Eraser Mode selection: Precision (touch only) vs Whole Stroke
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Precision (Touch only)
                val precisionSelected = currentMode == EraserMode.SEGMENT
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (precisionSelected) AppColors.Accent else AppColors.ControlIdle,
                    border = BorderStroke(
                        1.dp,
                        if (precisionSelected) AppColors.AccentEdge else AppColors.ControlEdge
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onModeSelected(EraserMode.SEGMENT) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Precision (Touch only)",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = if (precisionSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (precisionSelected) Color.White else AppColors.TextIdle
                            )
                        )
                    }
                }

                // Whole Stroke
                val strokeSelected = currentMode == EraserMode.STROKE
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (strokeSelected) AppColors.Accent else AppColors.ControlIdle,
                    border = BorderStroke(
                        1.dp,
                        if (strokeSelected) AppColors.AccentEdge else AppColors.ControlEdge
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onModeSelected(EraserMode.STROKE) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Stroke (Whole line)",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = if (strokeSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (strokeSelected) Color.White else AppColors.TextIdle
                            )
                        )
                    }
                }
            }

            // Eraser size presets & Clear Canvas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Size:",
                        style = TextStyle(fontSize = 12.sp, color = AppColors.Label)
                    )
                    listOf(
                        ERASER_SMALL_RADIUS_PX to "S",
                        ERASER_MEDIUM_RADIUS_PX to "M",
                        ERASER_LARGE_RADIUS_PX to "L"
                    ).forEach { (rad, label) ->
                        val isSelected = abs(currentRadiusPx - rad) < 5f
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) AppColors.Accent else AppColors.DotIdle)
                                .clickable { onRadiusChanged(rad) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            )
                        }
                    }
                }

                // Clear Canvas button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.DangerSurface,
                    border = BorderStroke(1.dp, AppColors.Danger.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable { onClearCanvas() }
                ) {
                    Text(
                        text = "Clear All",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.Danger),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}
