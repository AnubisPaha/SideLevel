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
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = alpha * event.values.get(0) + (1 - alpha) * filteredGravity.get(0)
            val y = alpha * event.values.get(1) + (1 - alpha) * filteredGravity.get(1)
            val z = alpha * event.values.get(2) + (1 - alpha) * filteredGravity.get(2)
            filteredGravity.set(0, x)
            filteredGravity.set(1, y)
            filteredGravity.set(2, z)
            val horizontalProjection = sqrt((x * x + z * z).toDouble())
            rawAngle = Math.toDegrees(atan2(y.toDouble(), horizontalProjection)).toFloat()
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

    DisposableEffect(Unit) { onDispose { toneGenerator.release() } }

    LaunchedEffect(isLevel, isSoundEnabled) {
        while (!isLevel && isSoundEnabled) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            delay(450)
        }
    }

    val activeColor = if (isLevel) Color(0xFF00E676) else Color(0xFFFF5252)

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        Box(
            modifier = Modifier.width(75.dp).fillMaxHeight().background(Color(0xFF1E1E1E)).border(2.dp, activeColor),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                val centerX = size.width / 2f
                drawLine(Color.White, Offset(0f, centerY - 45.dp.toPx()), Offset(size.width, centerY - 45.dp.toPx()), 3f)
                drawLine(Color.White, Offset(0f, centerY + 45.dp.toPx()), Offset(size.width, centerY + 45.dp.toPx()), 3f)
                drawLine(Color.Gray, Offset(0f, centerY), Offset(size.width, centerY), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                val maxOffset = (size.height / 2f) - 40.dp.toPx()
                val bubbleOffsetY = (currentAngle * 12.dp.toPx()).coerceIn(-maxOffset, maxOffset)
                drawCircle(activeColor, 26.dp.toPx(), Offset(centerX, centerY - bubbleOffsetY))
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.requiredWidth(maxHeight).requiredHeight(maxWidth).rotate(90f).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    FilledTonalButton(
                        onClick = { isSoundEnabled = !isSoundEnabled },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (isSoundEnabled) Color(0xFF2E3B4E) else Color(0xFF2A2A2A)),
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
                    Text("▼ ОПОРНАЯ ГРАНЬ ВНИЗУ", color = activeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(String.format("%.1f°", abs(currentAngle)), color = activeColor, fontSize = 68.sp, fontWeight = FontWeight.ExtraBold)
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

                Column(modifier = Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
                    Text(String.format("Поправка: %+.2f°", calibrationOffset), color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Button(
                        onClick = onCalibrate,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("КАЛИБРОВАТЬ 0°", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onResetCalibration,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Text("Сброс поправки", fontSize = 13.sp, color = Color.LightGray)
                    }
                }
            }
        }
    }
}
