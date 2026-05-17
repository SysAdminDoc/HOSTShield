package com.hostshield.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shape.rounded
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.hostshield.ui.theme.Blue
import com.hostshield.ui.theme.Flamingo
import com.hostshield.ui.theme.Green
import com.hostshield.ui.theme.LocalHighContrastAmoled
import com.hostshield.ui.theme.Mauve
import com.hostshield.ui.theme.Peach
import com.hostshield.ui.theme.Red
import com.hostshield.ui.theme.Sky
import com.hostshield.ui.theme.Surface3
import com.hostshield.ui.theme.Teal
import com.hostshield.ui.theme.TextPrimary
import com.hostshield.ui.theme.TextSecondary
import com.hostshield.ui.theme.Yellow

private val queryTypeColors: List<Color>
    get() = listOf(Teal, Mauve, Blue, Peach, Flamingo, Green, Yellow, Sky, Red)

// -- 1. HourlyBlockedChart --

@Composable
fun HourlyBlockedChart(
    data: List<Pair<Int, Int>>,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data) {
        modelProducer.runTransaction {
            lineSeries {
                series(
                    x = data.map { it.first.toDouble() },
                    y = data.map { it.second.toDouble() },
                )
            }
        }
    }

    val hourFormatter = CartesianValueFormatter { _, value, _ ->
        val h = value.toInt().coerceIn(0, 23)
        when (h) {
            0 -> "12a"
            6 -> "6a"
            12 -> "12p"
            18 -> "6p"
            else -> ""
        }
    }

    val lineColor = Teal
    val lineProvider = LineCartesianLayer.LineProvider.series(
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
        )
    )

    key(LocalHighContrastAmoled.current) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(lineProvider = lineProvider),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = hourFormatter,
                ),
            ),
            modelProducer = modelProducer,
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
        )
    }
}

// -- 2. DailyTrendChart --

@Composable
fun DailyTrendChart(
    blocked: List<Pair<String, Int>>,
    allowed: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val dayLabels = remember(blocked) { blocked.map { it.first } }

    LaunchedEffect(blocked, allowed) {
        modelProducer.runTransaction {
            columnSeries {
                series(blocked.map { it.second })
                series(allowed.map { it.second })
            }
        }
    }

    val dayFormatter = CartesianValueFormatter { _, value, _ ->
        dayLabels.getOrElse(value.toInt()) { "" }
    }

    val columnProvider = ColumnCartesianLayer.ColumnProvider.series(
        LineComponent(
            fill = fill(Teal),
            thicknessDp = 16f,
            shape = CorneredShape.rounded(topLeftPercent = 20, topRightPercent = 20),
        ),
        LineComponent(
            fill = fill(Mauve),
            thicknessDp = 16f,
            shape = CorneredShape.rounded(topLeftPercent = 20, topRightPercent = 20),
        ),
    )

    key(LocalHighContrastAmoled.current) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = columnProvider,
                    mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = dayFormatter,
                ),
            ),
            modelProducer = modelProducer,
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
        )
    }
}

// -- 3. QueryTypeDistribution --

@Composable
fun QueryTypeDistribution(
    distribution: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    val total = distribution.values.sum().coerceAtLeast(1)
    val entries = distribution.entries.toList()

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(16.dp),
        ) {
            val strokeWidth = size.minDimension * 0.18f
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)

            var startAngle = -90f
            entries.forEachIndexed { index, (_, count) ->
                val sweep = (count.toFloat() / total) * 360f
                val color = queryTypeColors[index % queryTypeColors.size]
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }

        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            entries.forEachIndexed { index, (label, count) ->
                val color = queryTypeColors[index % queryTypeColors.size]
                val pct = (count * 100f / total).let { "%.0f".format(it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(color, CircleShape)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "$label $pct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// -- 4. LatencyHistogram --

@Composable
fun LatencyHistogram(
    buckets: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val bucketLabels = remember(buckets) { buckets.map { it.first } }

    LaunchedEffect(buckets) {
        val n = buckets.size
        val greenSeries = MutableList(n) { 0 }
        val yellowSeries = MutableList(n) { 0 }
        val redSeries = MutableList(n) { 0 }

        buckets.forEachIndexed { i, (_, count) ->
            val ratio = i.toFloat() / (n - 1).coerceAtLeast(1)
            when {
                ratio <= 0.33f -> greenSeries[i] = count
                ratio <= 0.66f -> yellowSeries[i] = count
                else -> redSeries[i] = count
            }
        }

        modelProducer.runTransaction {
            columnSeries {
                series(greenSeries)
                series(yellowSeries)
                series(redSeries)
            }
        }
    }

    val bucketFormatter = CartesianValueFormatter { _, value, _ ->
        bucketLabels.getOrElse(value.toInt()) { "" }
    }

    val columnProvider = ColumnCartesianLayer.ColumnProvider.series(
        LineComponent(
            fill = fill(Green),
            thicknessDp = 20f,
            shape = CorneredShape.rounded(topLeftPercent = 20, topRightPercent = 20),
        ),
        LineComponent(
            fill = fill(Yellow),
            thicknessDp = 20f,
            shape = CorneredShape.rounded(topLeftPercent = 20, topRightPercent = 20),
        ),
        LineComponent(
            fill = fill(Red),
            thicknessDp = 20f,
            shape = CorneredShape.rounded(topLeftPercent = 20, topRightPercent = 20),
        ),
    )

    key(LocalHighContrastAmoled.current) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = columnProvider,
                    mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = bucketFormatter,
                ),
            ),
            modelProducer = modelProducer,
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
        )
    }
}

// -- 5. TopDomainsChart --

@Composable
fun TopDomainsChart(
    domains: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
) {
    val maxCount = domains.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val items = domains.take(10)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { (domain, count) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(120.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    val fraction = count.toFloat() / maxCount
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp),
                    ) {
                        drawRoundRect(
                            color = Surface3,
                            size = Size(size.width, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                        )
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Teal.copy(alpha = 0.7f), Teal),
                            ),
                            size = Size(size.width * fraction, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
    }
}
