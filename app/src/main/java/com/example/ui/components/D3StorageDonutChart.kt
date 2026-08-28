package com.example.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.StorageStats
import com.example.data.model.formatFileSize
import com.example.ui.theme.*

@Composable
fun D3StorageDonutChart(
    stats: StorageStats,
    modifier: Modifier = Modifier
) {
    val photoBytes = stats.photoBytes
    val videoBytes = stats.videoBytes
    val appBytes = stats.appBytes
    val docBytes = stats.docBytes
    val systemBytes = stats.systemBytes + stats.junkBytes
    val freeBytes = stats.freeBytes

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val webBgColor = if (isDark) "#2B2930" else "#FFFFFF"
    val webTextColor = if (isDark) "#E6E1E5" else "#1C1B1F"
    val webSubColor = if (isDark) "#CAC4D0" else "#5F6368"
    val legendItemBg = if (isDark) "#36343B" else "#F6F8FC"

    val htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, Roboto, sans-serif; }
                body { background-color: ${webBgColor}; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 12px; }
                #chart-container { position: relative; width: 100%; max-width: 320px; height: 260px; display: flex; justify-content: center; align-items: center; }
                #center-info { position: absolute; text-align: center; pointer-events: none; }
                #center-title { font-size: 12px; font-weight: 600; color: ${webSubColor}; text-transform: uppercase; letter-spacing: 0.5px; }
                #center-value { font-size: 20px; font-weight: 800; color: ${webTextColor}; margin-top: 2px; }
                #center-sub { font-size: 11px; color: #0F52BA; font-weight: 700; margin-top: 2px; }
                .slice { transition: transform 0.2s ease, opacity 0.2s ease; cursor: pointer; }
                .slice:hover, .slice.active { opacity: 0.9; transform: scale(1.04); }
                .legend-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; width: 100%; max-width: 320px; margin-top: 10px; }
                .legend-item { display: flex; align-items: center; font-size: 11px; color: ${webTextColor}; font-weight: 500; background: ${legendItemBg}; padding: 6px 10px; border-radius: 8px; }
                .legend-dot { width: 10px; height: 10px; border-radius: 50%; margin-right: 8px; flex-shrink: 0; }
                .legend-size { margin-left: auto; font-weight: 700; color: ${webSubColor}; }
            </style>

            <script src="https://cdnjs.cloudflare.com/ajax/libs/d3/7.8.5/d3.min.js"></script>
        </head>
        <body>

        <div id="chart-container">
            <div id="center-info">
                <div id="center-title">Total Storage</div>
                <div id="center-value">${stats.formattedTotal}</div>
                <div id="center-sub">${((stats.usedBytes.toDouble() / stats.totalBytes.coerceAtLeast(1).toDouble()) * 100).toInt()}% Used</div>
            </div>
            <svg id="donut-svg"></svg>
        </div>

        <div class="legend-grid" id="legend"></div>

        <script>
            const rawData = [
                { label: "Photos", bytes: ${photoBytes}, color: "#0F52BA", formatted: "${formatFileSize(photoBytes)}" },
                { label: "Videos", bytes: ${videoBytes}, color: "#8B5CF6", formatted: "${formatFileSize(videoBytes)}" },
                { label: "Apps & Data", bytes: ${appBytes}, color: "#F59E0B", formatted: "${formatFileSize(appBytes)}" },
                { label: "System", bytes: ${systemBytes}, color: "#64748B", formatted: "${formatFileSize(systemBytes)}" },
                { label: "Documents", bytes: ${docBytes}, color: "#10B981", formatted: "${formatFileSize(docBytes)}" },
                { label: "Free Space", bytes: ${freeBytes}, color: "#34D399", formatted: "${formatFileSize(freeBytes)}" }
            ];

            const total = rawData.reduce((acc, d) => acc + d.bytes, 0) || 1;
            const data = rawData.map(d => ({ ...d, percentage: ((d.bytes / total) * 100).toFixed(1) }));

            // Dimensions
            const width = 240, height = 240, margin = 10;
            const radius = Math.min(width, height) / 2 - margin;
            const innerRadius = radius * 0.65;

            const svg = d3.select("#donut-svg")
                .attr("width", width)
                .attr("height", height)
                .append("g")
                .attr("transform", "translate(" + width / 2 + "," + height / 2 + ")");

            const pie = d3.pie()
                .sort(null)
                .value(d => d.bytes);

            const arc = d3.arc()
                .innerRadius(innerRadius)
                .outerRadius(radius)
                .cornerRadius(4);

            const arcHover = d3.arc()
                .innerRadius(innerRadius - 4)
                .outerRadius(radius + 6)
                .cornerRadius(6);

            const arcs = svg.selectAll(".arc")
                .data(pie(data))
                .enter()
                .append("g")
                .attr("class", "arc");

            arcs.append("path")
                .attr("class", "slice")
                .attr("fill", d => d.data.color)
                .attr("d", arc)
                .on("click", function(event, d) {
                    d3.selectAll(".slice").attr("d", arc).classed("active", false);
                    d3.select(this).attr("d", arcHover).classed("active", true);

                    d3.select("#center-title").text(d.data.label);
                    d3.select("#center-value").text(d.data.formatted);
                    d3.select("#center-sub").text(d.data.percentage + "% of total");
                });

            // Populate Legend
            const legendContainer = d3.select("#legend");
            data.forEach(d => {
                const item = legendContainer.append("div").attr("class", "legend-item");
                item.append("div").attr("class", "legend-dot").style("background-color", d.color);
                item.append("span").text(d.label);
                item.append("span").attr("class", "legend-size").text(d.formatted);
            });
        </script>
        </body>
        </html>
    """.trimIndent()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.03f))
            .testTag("d3_storage_donut_chart_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Interactive Category Breakdown (D3)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            setBackgroundColor(0)
                            loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
