package com.childfilter.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.defaultWeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.childfilter.app.MainActivity
import com.childfilter.app.data.AppPreferences
import kotlinx.coroutines.flow.first

class KidSnapWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = AppPreferences.getInstance(context)
        val stats = prefs.getStats().first()
        val isRunning = prefs.isServiceEnabled().first()

        provideContent {
            WidgetContent(
                scanned = stats.first,
                matched = stats.second,
                isRunning = isRunning
            )
        }
    }
}

@Composable
fun WidgetContent(scanned: Int, matched: Int, isRunning: Boolean) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF0D47A1)))
            .clickable(actionStartActivity<MainActivity>())
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header row: app name + status dot
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "\uD83D\uDCF8 KidSnap",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    if (isRunning) "\u25CF Active" else "\u25CB Off",
                    style = TextStyle(
                        color = ColorProvider(if (isRunning) Color(0xFF81C784) else Color(0xFFBDBDBD)),
                        fontSize = 12.sp
                    )
                )
            }
            Spacer(GlanceModifier.height(12.dp))
            // Stats row
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        scanned.toString(),
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "Scanned",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFBBDEFB)),
                            fontSize = 11.sp
                        )
                    )
                }
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        matched.toString(),
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF81C784)),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "Matched",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFBBDEFB)),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}
