package com.deepanjanxyz.notepad.ui.feature_drawing.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.deepanjanxyz.notepad.domain.model.CanvasGridType
import com.deepanjanxyz.notepad.domain.model.DrawingPoint
import com.deepanjanxyz.notepad.domain.model.DrawingStroke
import com.deepanjanxyz.notepad.domain.model.DrawingTool
import com.deepanjanxyz.notepad.ui.feature_drawing.util.drawCanvasGrid
import com.deepanjanxyz.notepad.ui.feature_drawing.util.drawDrawingStroke
import com.deepanjanxyz.notepad.ui.theme.AppColors
import kotlin.math.hypot

// The shortest move that counts as drawing rather than a stray touch, in pixels.
private const val MIN_DRAW_DISTANCE_PX = 1.8f
// Dash pattern of the marquee outline, in pixels.
private const val MARQUEE_DASH_LENGTH_PX = 10f
private const val MARQUEE_DASH_GAP_PX = 6f
// The two backgrounds treated as dark, so the grid is drawn light on them.
private const val DARK_BACKGROUND_MIDNIGHT = 0xFF121214L
private const val DARK_BACKGROUND_CHARCOAL = 0xFF1E1E22L

@Composable
fun DrawingCanvas(
    strokes: List<DrawingStroke>,
    currentStroke: DrawingStroke?,
    canvasBackgroundColor: Long,
    canvasGridType: CanvasGridType,
    activeTool: DrawingTool,
    selectedStrokeIndices: Set<Int>,
    selectionBounds: Rect?,
    selectionMarquee: Rect?,
    currentStrokeWidth: Float,
    selectedColor: Long,
    currentAlpha: Float,
    isCurrentHighlighter: Boolean,
    callbacks: DrawingCanvasCallbacks,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val stalkLengthPx = with(density) { 24.dp.toPx() }
    val drag = remember { SelectionDragState() }
    val inputs = rememberUpdatedState(
        DrawingCanvasInputs(
            strokes = strokes,
            selectedIndices = selectedStrokeIndices,
            bounds = selectionBounds,
            alpha = currentAlpha,
            isHighlighter = isCurrentHighlighter,
            callbacks = callbacks
        )
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .canvasGestures(
                activeTool = activeTool,
                currentStrokeWidth = currentStrokeWidth,
                selectedColor = selectedColor,
                drag = drag,
                stalkLengthPx = stalkLengthPx,
                marquee = { selectionMarquee },
                inputs = inputs
            )
            .testTag("drawing_canvas")
    ) {
        drawCanvasContent(
            strokes = strokes,
            currentStroke = currentStroke,
            canvasBackgroundColor = canvasBackgroundColor,
            canvasGridType = canvasGridType,
            selectionMarquee = selectionMarquee,
            selectionBounds = selectionBounds
        )
    }
}

/** The pointer handling: a touch-down note, then either selection or drawing. */
@Composable
private fun Modifier.canvasGestures(
    activeTool: DrawingTool,
    currentStrokeWidth: Float,
    selectedColor: Long,
    drag: SelectionDragState,
    stalkLengthPx: Float,
    marquee: () -> Rect?,
    inputs: State<DrawingCanvasInputs>
): Modifier = this
    .pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            inputs.value.callbacks.onCanvasTouchDown()
        }
    }
    .pointerInput(activeTool, currentStrokeWidth, selectedColor) {
        if (activeTool == DrawingTool.SELECT) {
            selectionGestures(
                drag = drag,
                strokes = { inputs.value.strokes },
                selectedIndices = { inputs.value.selectedIndices },
                bounds = { inputs.value.bounds },
                marquee = marquee,
                stalkLengthPx = stalkLengthPx,
                onTouchDown = inputs.value.callbacks.onCanvasTouchDown,
                onStrokesChange = inputs.value.callbacks.onStrokesChange,
                onSelectedIndicesChange = inputs.value.callbacks.onSelectedIndicesChange,
                onMarqueeChange = inputs.value.callbacks.onSelectionMarqueeChange,
                onPushHistory = inputs.value.callbacks.onPushHistory
            )
        } else {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                inputs.value.callbacks.onCanvasTouchDown()
                down.consume()
                val startOffset = down.position
                if (activeTool == DrawingTool.ERASER) {
                    eraserGesture(
                        startOffset = startOffset,
                        pointerId = down.id,
                        onPushHistory = inputs.value.callbacks.onPushHistory,
                        onEraseNear = inputs.value.callbacks.onEraseNear
                    )
                } else {
                    drawStrokeGesture(
                        startOffset = startOffset,
                        pointerId = down.id,
                        strokeWidth = currentStrokeWidth,
                        color = selectedColor,
                        alpha = inputs.value.alpha,
                        isHighlighter = inputs.value.isHighlighter,
                        strokes = { inputs.value.strokes },
                        onPushHistory = inputs.value.callbacks.onPushHistory,
                        onCurrentStrokeChange = inputs.value.callbacks.onCurrentStrokeChange,
                        onStrokesChange = inputs.value.callbacks.onStrokesChange
                    )
                }
            }
        }
    }

/** Rubs out along the drag until the pointer lifts. */
private suspend fun AwaitPointerEventScope.eraserGesture(
    startOffset: Offset,
    pointerId: PointerId,
    onPushHistory: () -> Unit,
    onEraseNear: (Offset, Offset?) -> Unit
) {
    onPushHistory()
    var lastEraserPos: Offset? = startOffset
    onEraseNear(startOffset, null)
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId }
        if (change == null || !change.pressed) {
            change?.consume()
            break
        }
        change.consume()
        val curPos = change.position
        onEraseNear(curPos, lastEraserPos)
        lastEraserPos = curPos
    }
}

/** Lays down a stroke along the drag, and hands it over when the pointer lifts. */
private suspend fun AwaitPointerEventScope.drawStrokeGesture(
    startOffset: Offset,
    pointerId: PointerId,
    strokeWidth: Float,
    color: Long,
    alpha: Float,
    isHighlighter: Boolean,
    strokes: () -> List<DrawingStroke>,
    onPushHistory: () -> Unit,
    onCurrentStrokeChange: (DrawingStroke?) -> Unit,
    onStrokesChange: (List<DrawingStroke>) -> Unit
) {
    onPushHistory()
    var activeStroke = DrawingStroke(
        points = listOf(DrawingPoint(startOffset.x, startOffset.y)),
        color = color,
        strokeWidth = strokeWidth,
        alpha = alpha,
        isHighlighter = isHighlighter
    )
    onCurrentStrokeChange(activeStroke)

    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId }
        if (change == null || !change.pressed) {
            change?.consume()
            break
        }
        change.consume()
        val pos = change.position
        val lastPt = activeStroke.points.lastOrNull()
        if (lastPt == null ||
            hypot(lastPt.x - pos.x, lastPt.y - pos.y) >= MIN_DRAW_DISTANCE_PX
        ) {
            activeStroke = activeStroke.copy(
                points = activeStroke.points + DrawingPoint(pos.x, pos.y)
            )
            onCurrentStrokeChange(activeStroke)
        }
    }

    if (activeStroke.points.isNotEmpty()) {
        onStrokesChange(strokes() + activeStroke)
    }
    onCurrentStrokeChange(null)
}

/** Draws the grid, the strokes and the selection marks. */
private fun DrawScope.drawCanvasContent(
    strokes: List<DrawingStroke>,
    currentStroke: DrawingStroke?,
    canvasBackgroundColor: Long,
    canvasGridType: CanvasGridType,
    selectionMarquee: Rect?,
    selectionBounds: Rect?
) {
    val isDark = canvasBackgroundColor == DARK_BACKGROUND_CHARCOAL ||
        canvasBackgroundColor == DARK_BACKGROUND_MIDNIGHT
    drawCanvasGrid(canvasGridType, isDark)

    for (stroke in strokes) {
        drawDrawingStroke(stroke)
    }
    currentStroke?.let { stroke -> drawDrawingStroke(stroke) }

    selectionMarquee?.let { marquee ->
        drawRect(
            color = AppColors.AccentWash,
            topLeft = marquee.topLeft,
            size = marquee.size
        )
        drawRect(
            color = AppColors.Accent,
            topLeft = marquee.topLeft,
            size = marquee.size,
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(MARQUEE_DASH_LENGTH_PX, MARQUEE_DASH_GAP_PX)
                )
            )
        )
    }

    selectionBounds?.let { b -> drawSelectionBoundingBox(b) }
}
