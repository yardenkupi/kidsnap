package com.childfilter.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
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
        contentAlignment = Alignment.TopStart
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text(
                "\uD83D\uDCF8 KidSnap",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                if (isRunning) "\u25CF Active" else "\u25CB Stopped",
                style = TextStyle(
                    color = ColorProvider(if (isRunning) Color(0xFF81C784) else Color(0xFFBDBDBD)),
                    fontSize = 11.sp
                )
            )
            Spacer(GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Column {
                    Text(
                        scanned.toString(),
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 24.sp,
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
                Spacer(GlanceModifier.height(1.dp).padding(horizontal = 20.dp))
                Column {
                    Text(
                        matched.toString(),
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF81C784)),
                            fontSize = 24.sp,
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
