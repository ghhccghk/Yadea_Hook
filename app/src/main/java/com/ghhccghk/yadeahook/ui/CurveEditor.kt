package com.ghhccghk.yadeahook.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ghhccghk.yadeahook.bluetooth.SpeedStrengthMapper

/**
 * 车速-强度曲线编辑器
 */
@Composable
fun CurveEditor(
    curvePoints: List<SpeedStrengthMapper.CurvePoint>,
    onCurvePointsChanged: (List<SpeedStrengthMapper.CurvePoint>) -> Unit,
    modifier: Modifier = Modifier,
    maxSpeed: Float = 60f,
    maxStrength: Float = 100f,
    showGrid: Boolean = true
) {
    // 确保至少有两个点
    val points = remember(curvePoints) {
        if (curvePoints.size < 2) {
            listOf(
                SpeedStrengthMapper.CurvePoint(0f, 0f),
                SpeedStrengthMapper.CurvePoint(maxSpeed, maxStrength)
            )
        } else {
            curvePoints.sortedBy { it.speed }
        }
    }
    
    var selectedPointIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    
    Column(modifier = modifier) {
        // 图表标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "车速-强度曲线",
                style = MaterialTheme.typography.titleSmall
            )
            TextButton(onClick = {
                // 添加新点
                val newPoints = points.toMutableList()
                val midIndex = points.size / 2
                val midSpeed = (points[midIndex - 1].speed + points[midIndex].speed) / 2
                val midStrength = (points[midIndex - 1].strength + points[midIndex].strength) / 2
                newPoints.add(midIndex, SpeedStrengthMapper.CurvePoint(midSpeed, midStrength))
                onCurvePointsChanged(newPoints)
            }) {
                Text("添加点")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 图表
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(surfaceVariant, MaterialTheme.shapes.medium)
                .padding(16.dp)
        ) {
            val currentPoints by rememberUpdatedState(points)
            val currentOnCurvePointsChanged by rememberUpdatedState(onCurvePointsChanged)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val chartWidth = size.width.toFloat()
                                val chartHeight = size.height.toFloat()

                                var minDist = Float.MAX_VALUE
                                var closestIndex = -1

                                currentPoints.forEachIndexed { index, point ->
                                    val x = point.speed / maxSpeed * chartWidth
                                    val y = chartHeight -
                                            (point.strength / maxStrength * chartHeight)

                                    val distance = (Offset(x, y) - offset).getDistance()

                                    // 30dp 左右的命中范围
                                    if (distance < minDist && distance < 40f) {
                                        minDist = distance
                                        closestIndex = index
                                    }
                                }

                                selectedPointIndex = closestIndex
                                dragOffset = Offset.Zero
                            },

                            onDrag = { change, dragAmount ->
                                change.consume()

                                val index = selectedPointIndex

                                if (index < 0 || index >= currentPoints.size) {
                                    return@detectDragGestures
                                }

                                val chartWidth = size.width.toFloat()
                                val chartHeight = size.height.toFloat()

                                val oldPoints = currentPoints
                                val currentPoint = oldPoints[index]

                                // 当前点的画布坐标
                                val currentX =
                                    currentPoint.speed / maxSpeed * chartWidth

                                val currentY =
                                    chartHeight -
                                            currentPoint.strength / maxStrength * chartHeight

                                // 根据本次拖动计算新位置
                                val newX = (currentX + dragAmount.x)
                                    .coerceIn(0f, chartWidth)

                                val newY = (currentY + dragAmount.y)
                                    .coerceIn(0f, chartHeight)

                                // 转换回实际数据
                                val newSpeed =
                                    (newX / chartWidth * maxSpeed)
                                        .coerceIn(0f, maxSpeed)

                                val newStrength =
                                    ((chartHeight - newY) / chartHeight * maxStrength)
                                        .coerceIn(0f, maxStrength)

                                val newPoints = oldPoints.toMutableList()

                                when (index) {

                                    // 首点只能上下移动
                                    0 -> {
                                        newPoints[index] =
                                            currentPoint.copy(
                                                strength = newStrength
                                            )
                                    }

                                    // 尾点只能上下移动
                                    oldPoints.lastIndex -> {
                                        newPoints[index] =
                                            currentPoint.copy(
                                                strength = newStrength
                                            )
                                    }

                                    // 中间点可以自由移动
                                    else -> {
                                        val minSpeed =
                                            oldPoints[index - 1].speed + 0.1f

                                        val maxSpeedLimit =
                                            oldPoints[index + 1].speed - 0.1f

                                        newPoints[index] =
                                            SpeedStrengthMapper.CurvePoint(
                                                speed = newSpeed.coerceIn(
                                                    minSpeed,
                                                    maxSpeedLimit
                                                ),
                                                strength = newStrength
                                            )
                                    }
                                }

                                currentOnCurvePointsChanged(newPoints)
                            },

                            onDragEnd = {
                                selectedPointIndex = -1
                                dragOffset = Offset.Zero
                            },

                            onDragCancel = {
                                selectedPointIndex = -1
                                dragOffset = Offset.Zero
                            }
                        )
                    }
            ) {
                val chartWidth = size.width
                val chartHeight = size.height

                // 绘制网格
                if (showGrid) {
                    drawGrid(
                        chartWidth,
                        chartHeight,
                        maxSpeed,
                        maxStrength,
                        outlineColor
                    )
                }

                // 绘制曲线
                drawCurve(
                    points,
                    chartWidth,
                    chartHeight,
                    maxSpeed,
                    maxStrength,
                    primaryColor
                )

                // 绘制控制点
                drawControlPoints(
                    points,
                    chartWidth,
                    chartHeight,
                    maxSpeed,
                    maxStrength,
                    primaryColor,
                    selectedPointIndex
                )
            }
            
            // Y轴标签
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 0.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("100%", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text("50%", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text("0%", style = MaterialTheme.typography.labelSmall)
            }
        }
        
        // X轴标签
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0", style = MaterialTheme.typography.labelSmall)
            Text("${maxSpeed.toInt() / 2}", style = MaterialTheme.typography.labelSmall)
            Text("${maxSpeed.toInt()} km/h", style = MaterialTheme.typography.labelSmall)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 点列表
        Text(
            text = "控制点",
            style = MaterialTheme.typography.titleSmall
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        points.forEachIndexed { index, point ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${point.speed.toInt()} km/h",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${point.strength.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // 删除按钮（不能删除首尾点）
                if (index != 0 && index != points.size - 1) {
                    Button(
                        onClick = {
                            val newPoints = points.toMutableList()
                            newPoints.removeAt(index)
                            onCurvePointsChanged(newPoints)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("删除")
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawGrid(
    width: Float,
    height: Float,
    maxSpeed: Float,
    maxStrength: Float,
    color: Color
) {
    val gridColor = color.copy(alpha = 0.2f)
    
    // 水平线
    for (i in 0..4) {
        val y = height * i / 4
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1f
        )
    }
    
    // 垂直线
    for (i in 0..4) {
        val x = width * i / 4
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 1f
        )
    }
}

private fun DrawScope.drawCurve(
    points: List<SpeedStrengthMapper.CurvePoint>,
    width: Float,
    height: Float,
    maxSpeed: Float,
    maxStrength: Float,
    color: Color
) {
    if (points.size < 2) return
    
    val path = Path()
    
    points.forEachIndexed { index, point ->
        val x = point.speed / maxSpeed * width
        val y = height - (point.strength / maxStrength * height)
        
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 3f)
    )
}

private fun DrawScope.drawControlPoints(
    points: List<SpeedStrengthMapper.CurvePoint>,
    width: Float,
    height: Float,
    maxSpeed: Float,
    maxStrength: Float,
    color: Color,
    selectedIndex: Int
) {
    points.forEachIndexed { index, point ->
        val x = point.speed / maxSpeed * width
        val y = height - (point.strength / maxStrength * height)
        
        val pointColor = if (index == selectedIndex) {
            color.copy(alpha = 0.8f)
        } else {
            color
        }
        
        // 外圈
        drawCircle(
            color = pointColor,
            radius = 8f,
            center = Offset(x, y)
        )
        
        // 内圈
        drawCircle(
            color = Color.White,
            radius = 4f,
            center = Offset(x, y)
        )
    }
}