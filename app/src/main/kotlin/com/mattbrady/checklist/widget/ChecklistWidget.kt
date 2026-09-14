package com.mattbrady.checklist.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Button
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import com.mattbrady.checklist.data.local.AppDatabase
import com.mattbrady.checklist.ui.CaptureActivity

/**
 * Home-screen widget. Shows your categories with an open-item count, and a
 * "+ Add" button that opens CaptureActivity — a small floating box overlaid
 * on the home screen, since Android widgets can't host a real text field
 * (see SETUP.md). Refreshed after every successful sync.
 */
class ChecklistWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)
        val categories = db.categoryDao().getCategoriesOnce()
        val subcategories = db.categoryDao().getSubcategoriesOnce()

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(Color(0xFFFFFFFF))
                        .padding(16.dp)
                ) {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Text(
                            text = "Checklist",
                            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        )
                        Spacer(modifier = GlanceModifier.height(1.dp).defaultWeight())
                        Button(
                            text = "+ Add",
                            onClick = actionStartActivity<CaptureActivity>(),
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(12.dp))
                    if (categories.isEmpty()) {
                        Text(
                            text = "Tap + Add to get started",
                            style = TextStyle(fontSize = 16.sp),
                        )
                    } else {
                        categories.take(6).forEach { category ->
                            val openCount = subcategories
                                .filter { it.categoryId == category.id }
                                .sumOf { it.openCount }
                            Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(
                                    text = category.name,
                                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                )
                                Spacer(modifier = GlanceModifier.height(1.dp).defaultWeight())
                                Text(
                                    text = "$openCount open",
                                    style = TextStyle(fontSize = 16.sp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class ChecklistWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChecklistWidget()
}
