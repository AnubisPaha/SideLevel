package com.example.sidelevel

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var rawAngle by mutableFloatStateOf(0f)
    private var calibrationOffset by mutableFloatStateOf(0f)

    private val alpha = 0.15f
    private var filteredGravity = FloatArray(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences("LevelPrefs", Context.MODE_PRIVATE)
        calibrationOffset = prefs.getFloat("calibration_offset", 0f)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setContent {
            LevelAppScreen(
                rawAngle = rawAngle,
                calibrationOffset = calibrationOffset,
                onCalibrate = {
                    calibrationOffset = rawAngle
                    prefs.edit().putFloat("calibration_offset", calibrationOffset).apply()
                },
                onResetCalibration = {
                    calibrationOffset = 0f
                    prefs.edit().putFloat("calibration_offset", 0f).apply()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val rawX = event.values.get(0)
            val rawY = event.values.get(1)
            val rawZ = event.values.get(2)

            val x = alpha * rawX + (1 - alpha) * filteredGravity.get(0)
            val y = alpha * rawY + (1 - alpha) * filteredGravity.get(1)
            val z = alpha * rawZ + (1 - alpha) * filteredGravity.get(2)

            filteredGravity.set(0, x)
            filteredGravity.set(1, y)
            filteredGravity.set(2, z)

            val horizontalProjection = sqrt((x * x + z * z).toDouble())
            val angleRad = atan2(y.toDouble(), horizontalProjection)
            rawAngle = Math.toDegrees(angleRad).toFloat()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun LevelAppScreen(
    rawAngle: Float,
    calibrationOffset: Float,
    onCalibrate: () -> Unit,
    onResetCalibration: () -> Unit
) {
    val currentAngle = rawAngle - calibrationOffset
    val isLevel = abs(currentAngle) < 0.2f

    var isSoundEnabled by remember { mutableStateOf(true) }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_ALARM, 85) }

    DisposableEffect(Unit) {
        onDispose { toneGenerator.release() }
    }

    LaunchedEffect(isLevel, isSoundEnabled) {
        while (!isLevel && isSoundEnabled) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            delay(450)
        }
    }

    val activeColor = if (isLevel) Color(0xFF00E676) else Color(0xFFFF5252)
    val backgroundColor = Color(0xFF121212)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // 1. ШКАЛА С ПУЗЫРЬКОМ ВДОЛЬ ОПОРНОЙ ЛЕВОЙ ГРАНИ
        Box(
            modifier = Modifier
                .width(75.dp)
                .fillMaxHeight()
                .background(Color(0xFF1E1E1E))
                .border(2.dp, activeColor),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                val centerX = size.width / 2f

                drawLine(
                    color = Color.White,
                    start = Offset(0f, centerY - 45.dp.toPx()),
                    end = Offset(size.width, centerY - 45.dp.toPx()),
                    strokeWidth = 3f
                )
                drawLine(
                    color = Color.White,
                    start = Offset(0f, centerY + 45.dp.toPx()),
                    end = Offset(size.width, centerY + 45.dp.toPx()),
                    strokeWidth = 3f
                )
                drawLine(
                    color = Color.Gray,
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                val maxOffset = (size.height / 2f) - 40.dp.toPx()
                val bubbleOffsetY = (currentAngle * 12.dp.toPx()).coerceIn(-maxOffset, maxOffset)

                drawCircle(
                    color = activeColor,
                    radius = 26.dp.toPx(),
                    center = Offset(centerX, centerY - bubbleOffsetY)
                )
            }
        }

        // 2. ПАНЕЛЬ УПРАВЛЕНИЯ, РАЗВЕРНУТАЯ НА 90° ПО ЧАСОВОЙ СТРЕЛКЕ
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .requiredWidth(maxHeight)
                    .requiredHeight(maxWidth)
                    .rotate(90f)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Левый блок (в рабочем положении): звук и подсказка
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    FilledTonalButton(
                        onClick = { isSoundEnabled = !isSoundEnabled },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isSoundEnabled) Color(0xFF2E3B4E) else Color(0xFF2A2A2A)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (isSoundEnabled) "🔊 ЗВУК: ВКЛ" else "🔇 ЗВУК: ВЫКЛ",
                            color = if (isSoundEnabled) Color(0xFF82B1FF) else Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "▼ ОПОРНАЯ ГРАНЬ ВНИЗУ",
                        color = activeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Центральный блок: крупные цифры угла
                Column(
                    modifier = Modifier.weight(1.2f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = String.format("%.1f°", abs(currentAngle)),
                        color = activeColor,
                        fontSize = 68.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = when {
                            isLevel -> "ГОРИЗОНТ (ТИШИНА)"
                            currentAngle > 0 -> "ПРАВЫЙ КРАЙ ВЫШЕ ▲"
                            else -> "ЛЕВЫЙ КРАЙ ВЫШЕ ▲"
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Правый блок: кнопки калибровки
                Column(
                    modifier = Modifier.weight(1.1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = String.format("Поправка: %+.2f°", calibrationOffset),
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Button(
