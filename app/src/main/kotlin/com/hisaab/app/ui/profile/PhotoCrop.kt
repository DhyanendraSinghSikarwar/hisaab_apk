package com.hisaab.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hisaab.app.i18n.t
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Where the user placed the photo under the crop circle, in viewport pixels: the source is drawn centred at
 * viewport centre + ([offsetX], [offsetY]), turned by [rotation] degrees and scaled by [scale]; the circle is
 * [diameter] wide, centred in the viewport.
 */
data class CropSpec(val rotation: Int, val scale: Float, val offsetX: Float, val offsetY: Float, val diameter: Float)

/**
 * Decodes [uri] for cropping: subsampled so the longest side stays near [maxSide] (memory safe for 50 MP photos)
 * and turned upright from its EXIF orientation. Call off the main thread.
 */
internal fun decodeForCrop(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val raw = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val orientation = runCatching {
        resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    val m = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        else -> return raw
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it !== raw) raw.recycle() }
}

/**
 * Renders exactly what sits inside the crop circle to an [out]×[out] square, with the same transform the
 * dialog draws. Renders at twice the size first and halves it, so a large downscale stays smooth.
 */
internal fun renderCrop(src: Bitmap, spec: CropSpec, out: Int = 512): Bitmap {
    val big = out * 2
    val canvasBitmap = Bitmap.createBitmap(big, big, Bitmap.Config.ARGB_8888)
    val k = big / spec.diameter
    android.graphics.Canvas(canvasBitmap).apply {
        drawColor(android.graphics.Color.WHITE)
        translate(big / 2f + spec.offsetX * k, big / 2f + spec.offsetY * k)
        rotate(spec.rotation.toFloat())
        scale(spec.scale * k, spec.scale * k)
        drawBitmap(src, -src.width / 2f, -src.height / 2f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }
    return Bitmap.createScaledBitmap(canvasBitmap, out, out, true).also { if (it !== canvasBitmap) canvasBitmap.recycle() }
}

/**
 * Full-screen crop: the photo under a circular window with the outside dimmed. Pinch (1×–5×) and drag to frame
 * it, double-tap to zoom, turn it a quarter at a time, or reset. [onConfirm] receives the placement to render.
 */
@Composable
fun PhotoCropDialog(source: Bitmap, saving: Boolean, onCancel: () -> Unit, onConfirm: (CropSpec) -> Unit) {
    val image = remember(source) { source.asImageBitmap() }
    var rotation by remember(source) { mutableIntStateOf(0) } // cumulative, so the turn always animates forwards
    var zoom by remember(source) { mutableFloatStateOf(1f) }
    var offset by remember(source) { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    val diameter = min(box.width, box.height) * 0.84f
    val sideways = (rotation / 90) % 2 != 0
    val rw = (if (sideways) source.height else source.width).toFloat()
    val rh = (if (sideways) source.width else source.height).toFloat()
    // Base scale makes the photo's short side exactly fill the circle at 1×.
    val base = if (diameter > 0f) diameter / min(rw, rh) else 1f
    val shownBase by animateFloatAsState(base, tween(280, easing = FastOutSlowInEasing), label = "base")
    val shownRotation by animateFloatAsState(rotation.toFloat(), tween(280, easing = FastOutSlowInEasing), label = "turn")

    // Keeps the circle covered by the photo at all times.
    fun clamp(o: Offset, z: Float): Offset {
        val s = base * z
        val mx = max(0f, (rw * s - diameter) / 2f)
        val my = max(0f, (rh * s - diameter) / 2f)
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    fun animateTo(targetZoom: Float, targetOffset: Offset) {
        val z0 = zoom; val o0 = offset
        scope.launch {
            animate(0f, 1f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)) { t, _ ->
                zoom = z0 + (targetZoom - z0) * t
                offset = clamp(o0 + (targetOffset - o0) * t, zoom)
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!saving) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Canvas(
                Modifier.fillMaxSize()
                    .onSizeChanged { box = it }
                    .pointerInput(source, rotation, box) {
                        detectTransformGestures { centroid, pan, zoomChange, _ ->
                            val newZoom = (zoom * zoomChange).coerceIn(1f, 5f)
                            val f = newZoom / zoom
                            val c = centroid - Offset(box.width / 2f, box.height / 2f)
                            offset = clamp((offset - c) * f + c + pan, newZoom)
                            zoom = newZoom
                        }
                    }
                    .pointerInput(source, rotation, box) {
                        detectTapGestures(onDoubleTap = { p ->
                            if (zoom > 1.5f) animateTo(1f, Offset.Zero)
                            else {
                                val c = p - Offset(box.width / 2f, box.height / 2f)
                                val tz = 2.5f
                                animateTo(tz, clamp((offset - c) * (tz / zoom) + c, tz))
                            }
                        })
                    },
            ) {
                val s = shownBase * zoom
                translate(size.width / 2f + offset.x, size.height / 2f + offset.y) {
                    rotate(shownRotation, pivot = Offset.Zero) {
                        scale(s, pivot = Offset.Zero) {
                            drawImage(
                                image, dstOffset = IntOffset(-image.width / 2, -image.height / 2),
                                filterQuality = FilterQuality.Medium,
                            )
                        }
                    }
                }
                val r = diameter / 2f
                val hole = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addOval(Rect(center, r))
                }
                drawPath(hole, Color.Black.copy(alpha = 0.62f))
                drawCircle(Color.White.copy(alpha = 0.9f), r, center, style = Stroke(1.5.dp.toPx()))
            }

            Column(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(t("Move and scale"), color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    t("Pinch to zoom, drag to position"), color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp),
                )
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ZoomOut, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    Slider(
                        value = zoom, onValueChange = { zoom = it; offset = clamp(offset, it) }, valueRange = 1f..5f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White, activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                        ),
                    )
                    Icon(Icons.Filled.ZoomIn, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel, enabled = !saving) { Text(t("Cancel"), color = Color.White) }
                    Spacer(Modifier.weight(1f))
                    val tonal = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.14f), contentColor = Color.White,
                    )
                    FilledTonalIconButton(onClick = { rotation += 90; animateTo(zoom, Offset.Zero) }, enabled = !saving, colors = tonal) {
                        Icon(Icons.Filled.Rotate90DegreesCw, t("Rotate"))
                    }
                    Spacer(Modifier.width(10.dp))
                    FilledTonalIconButton(
                        onClick = {
                            rotation = ((rotation + 359) / 360) * 360
                            animateTo(1f, Offset.Zero)
                        },
                        enabled = !saving, colors = tonal,
                    ) { Icon(Icons.Filled.RestartAlt, t("Reset")) }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { onConfirm(CropSpec(rotation % 360, base * zoom, offset.x, offset.y, diameter)) },
                        enabled = !saving && diameter > 0f,
                    ) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text(t("Use photo"))
                    }
                }
            }
        }
    }
}
