package com.indianservers.circuitssimulator.ui

import com.indianservers.circuitssimulator.R

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val electronicsQuotes = listOf(
    "Every Circuit Starts with an Idea",
    "Discover the Power of Electronics",
    "Connect. Simulate. Discover.",
    "From Signals to Possibilities",
    "Bring Your Circuits to Life",
    "Explore the World of Electronics",
)

@Composable
internal fun ElectronicsSplashScreen(modifier: Modifier = Modifier) {
    val timeline = remember { Animatable(0f) }
    val quote = remember { electronicsQuotes.random() }

    LaunchedEffect(Unit) {
        launch { playSplashWhoosh() }
        timeline.animateTo(1f, tween(durationMillis = 1_500, easing = LinearEasing))
    }

    val progress = timeline.value
    val logoAlpha = phase(progress, .48f, .67f) * (1f - phase(progress, .96f, 1f) * .10f)
    val titleAlpha = phase(progress, .58f, .76f)
    val calculationAlpha = phase(progress, .72f, .82f)
    val legalAlpha = phase(progress, .20f, .40f)

    Box(
        modifier
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF071B3D), Color(0xFF020713), Color(0xFF050505)),
                    radius = 1_350f,
                ),
            )
            .safeDrawingPadding()
            .semantics { contentDescription = "Circuits Simulator electronics splash screen" },
    ) {
        CircuitUniverse(progress, Modifier.fillMaxSize())

        Image(
            painter = painterResource(R.drawable.splash_icon),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(112.dp)
                .graphicsLayer {
                    alpha = logoAlpha
                    val scale = .42f + phase(progress, .43f, .66f) * .58f + phase(progress, .66f, .72f) * .06f
                    scaleX = scale
                    scaleY = scale
                    rotationZ = (1f - phase(progress, .42f, .66f)) * -8f
                    shadowElevation = 32f * logoAlpha
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(top = 174.dp, start = 22.dp, end = 22.dp)
                .graphicsLayer { alpha = titleAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = quote,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
                maxLines = 2,
            )
            Text(
                text = "Visual Proofs  •  Interactive Learning",
                color = Color(0xFF78E7FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = .5.sp,
                maxLines = 2,
            )
        }

        Text(
            text = "Powering Up Your Workbench...",
            color = Color(0xFF5DF4FF),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 78.dp)
                .graphicsLayer { alpha = calculationAlpha },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .graphicsLayer { alpha = legalAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Powered by Indian Servers Pvt Ltd  •  www.IndianServers.com",
                color = Color(0xFFB8D7FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                "© 2026 Indian Servers Pvt Ltd. All Rights Reserved.",
                color = Color.White.copy(alpha = .68f),
                fontSize = 7.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun CircuitUniverse(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cyan = Color(0xFF49E7FF)
        val reveal = phase(progress, 0f, .45f)
        val fade = 1f - phase(progress, .68f, 1f) * .65f
        val center = Offset(size.width / 2f, size.height / 2f)
        val unit = size.minDimension
        val grid = unit / 12f
        repeat(13) { index ->
            val x = index * grid
            drawLine(cyan.copy(alpha = .055f), Offset(x, 0f), Offset(x, size.height), 1f)
        }
        repeat((size.height / grid).toInt() + 1) { index ->
            val y = index * grid
            drawLine(cyan.copy(alpha = .055f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        repeat(12) { index ->
            val angle = index * PI.toFloat() / 6f
            val start = Offset(center.x + cos(angle) * unit * .54f, center.y + sin(angle) * unit * .54f)
            val finish = Offset(center.x + cos(angle) * unit * .18f, center.y + sin(angle) * unit * .18f)
            val elbow = Offset(finish.x, start.y)
            val path = Path().apply {
                moveTo(start.x, start.y)
                lineTo(elbow.x, elbow.y)
                lineTo(finish.x, finish.y)
            }
            drawPath(path, cyan.copy(alpha = .09f * reveal * fade), style = Stroke(9f))
            drawPath(path, cyan.copy(alpha = .55f * reveal * fade), style = Stroke(1.8f))
            drawCircle(cyan.copy(alpha = .7f * reveal * fade), 4f, start, style = Stroke(1.6f))
            val travel = ((progress * 2.5f + index / 12f) % 1f)
            val pulse = if (travel < .5f) lerp(start, elbow, travel * 2f) else lerp(elbow, finish, (travel - .5f) * 2f)
            drawCircle(Color(0xFF7AFFAA).copy(alpha = reveal * fade), 3.5f, pulse)
        }
        // Oscilloscope trace above the central icon.
        val wave = Path()
        repeat(121) { index ->
            val x = unit * .14f + unit * .72f * index / 120f
            val y = center.y - unit * .33f + sin(index / 120f * PI.toFloat() * 6f - progress * 8f) * unit * .035f
            if (index == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
        }
        drawPath(wave, Color(0xFF77FFAD).copy(alpha = reveal * .65f), style = Stroke(2.4f))
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.rgb(91, 228, 255)
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = unit * .034f
                alpha = (reveal * fade * 150).roundToInt()
            }
            val equations = listOf("V = IR", "P = VI", "f = 1 / T", "Q = CV")
            equations.forEachIndexed { index, equation ->
                val x = size.width * if (index % 2 == 0) .20f else .80f
                val y = center.y + unit * if (index < 2) -.52f else .53f
                canvas.nativeCanvas.drawText(equation, x, y, paint)
            }
        }
    }
}

private fun phase(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun lerp(start: Offset, end: Offset, amount: Float): Offset =
    Offset(
        x = start.x + (end.x - start.x) * amount,
        y = start.y + (end.y - start.y) * amount,
    )

private suspend fun playSplashWhoosh() = withContext(Dispatchers.Default) {
    runCatching {
        val sampleRate = 24_000
        val durationSeconds = .45f
        val sampleCount = (sampleRate * durationSeconds).roundToInt()
        val samples = ShortArray(sampleCount)
        var filteredNoise = 0f
        val random = Random(System.nanoTime())
        for (index in samples.indices) {
            val t = index.toFloat() / sampleRate
            val normalized = t / durationSeconds
            val envelope = sin(PI.toFloat() * normalized).coerceAtLeast(0f)
            val frequency = 180f + 760f * normalized * normalized
            filteredNoise = filteredNoise * .82f + (random.nextFloat() * 2f - 1f) * .18f
            val tone = sin(2f * PI.toFloat() * frequency * t)
            samples[index] = ((tone * .34f + filteredNoise * .66f) * envelope * Short.MAX_VALUE * .10f).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        try {
            track.write(samples, 0, samples.size)
            track.setVolume(.16f)
            track.play()
            delay(500)
        } finally {
            track.release()
        }
    }
}
