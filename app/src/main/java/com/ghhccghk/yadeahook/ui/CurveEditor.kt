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
    val density = LocalDensity.current
    
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
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            // 点击选择最近的点
                            val chartWidth = size.width.toFloat()
                            val chartHeight = size.height.toFloat()
                            
                            var minDist = Float.MAX_VALUE
                            var closestIndex = -1
                            
                            points.forEachIndexed { index, point ->
                                val x = point.speed / maxSpeed * chartWidth
                                val y = chartHeight - (point.strength / maxStrength * chartHeight)
                                val dist = (Offset(x, y) - offset).getDistance()
                                
                                if (dist < minDist && dist < 30f) {
                                    minDist = dist
                                    closestIndex = index
                                }
                            }
                            
                            selectedPointIndex = closestIndex
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            
                            if (selectedPointIndex >= 0 && selectedPointIndex < points.size) {
                                val chartWidth = size.width.toFloat()
                                val chartHeight = size.height.toFloat()
                                
                                dragOffset += dragAmount
                                
                                // 计算新位置
                                val currentPoint = points[selectedPointIndex]
                                val newX = (currentPoint.speed / maxSpeed * chartWidth + dragOffset.x)
                                    .coerceIn(0f, chartWidth)
                                val newY = (chartHeight - currentPoint.strength / maxStrength * chartHeight + dragOffset.y)
                                    .coerceIn(0f, chartHeight)
                                
                                // 转换回速度和强度
                                val newSpeed = (newX / chartWidth * maxSpeed).coerceIn(0f, maxSpeed)
                                val newStrength = ((chartHeight - newY) / chartHeight * maxStrength).coerceIn(0f, maxStrength)
                                
                                // 更新点
                                val newPoints = points.toMutableList()
                                
                                // 保持第一个和最后一个点的x坐标固定
                                if (selectedPointIndex == 0) {
                                    newPoints[selectedPointIndex] = currentPoint.copy(strength = newStrength)
                                } else if (selectedPointIndex == points.size - 1) {
                                    newPoints[selectedPointIndex] = currentPoint.copy(strength = newStrength)
                                } else {
                                    // 确保点不会越过相邻点
                                    val minSpeed = points[selectedPointIndex - 1].speed + 1f
                                    val maxSpeedLimit = points[selectedPointIndex + 1].speed - 1f
                                    newPoints[selectedPointIndex] = SpeedStrengthMapper.CurvePoint(
                                        speed = newSpeed.coerceIn(minSpeed, maxSpeedLimit),
                                        strength = newStrength
                                    )
                                }
                                
                                onCurvePointsChanged(newPoints)
                                dragOffset = Offset.Zero
                            }
                        }
                    }
            ) {
                val chartWidth = size.width
                val chartHeight = size.height
                
                // 绘制网格
                if (showGrid) {
                    drawGrid(chartWidth, chartHeight, maxSpeed, maxStrength, outlineColor)
                }
                
                // 绘制曲线
                drawCurve(points, chartWidth, chartHeight, maxSpeed, maxStrength, primaryColor)
                
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