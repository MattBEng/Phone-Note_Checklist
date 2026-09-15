package com.mattbrady.checklist.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Button
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
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

private const val NONE_EXPANDED = -1L
private val expandedCategoryKey = longPreferencesKey("expanded_category_id")
private val categoryIdParam = ActionParameters.Key<Long>("category_id")

/**
 * Home-screen widget. Shows your categories with an open-item count; tap a
 * category to expand/collapse its subcategories, and "+ Add" opens
 * CaptureActivity - a small floating box overlaid on the home screen, since
 * Android widgets can't host a real text field (see SETUP.md). Refreshed
 * after every successful sync.
 */
class ChecklistWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)
        val categories = db.categoryDao().getCategoriesOnce()
        val subcategories = db.categoryDao().getSubcategoriesOnce()
        val openNotes = db.noteDao().getOpenNotesOnce()

        provideContent {
            val prefs = currentState<Preferences>()
            val expandedId = prefs[expandedCategoryKey] ?: NONE_EXPANDED

            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(Color(0xFFFFFFFF))
                        .padding(16.dp)
                ) {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Text(
                            text = "Checklist ✓",
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
                            val categorySubs = subcategories.filter { it.categoryId == category.id }
                            val openCount = categorySubs.sumOf { it.openCount }
                            val isExpanded = expandedId == category.id

                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable(
                                        actionRunCallback<ToggleCategoryAction>(
                                            actionParametersOf(categoryIdParam to category.id)
                                        )
                                    )
                            ) {
                                Text(
                                    text = (if (isExpanded) "▾ " else "▸ ") + category.name,
                                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                )
                                Spacer(modifier = GlanceModifier.height(1.dp).defaultWeight())
                                Text(
                                    text = "$openCount open",
                                    style = TextStyle(fontSize = 16.sp),
                                )
                            }

                            if (isExpanded) {
                                categorySubs.forEach { sub ->
                                    Row(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                                    ) {
                                        Text(
                                            text = sub.name,
                                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                        )
                                        Spacer(modifier = GlanceModifier.height(1.dp).defaultWeight())
                                        Text(
                                            text = "${sub.openCount} open",
                                            style = TextStyle(fontSize = 14.sp),
                                        )
                                    }

                                    // Second tier: the actual open items in this
                                    // subcategory, not just its count. Notes are
                                    // matched by category/subcategory name since
                                    // that's how they're stored locally.
                                    val subNotes = openNotes.filter {
                                        it.category == category.name && it.subcategory == sub.name
                                    }
                                    subNotes.take(5).forEach { note ->
                                        Text(
                                            text = "• " + note.body,
                                            style = TextStyle(fontSize = 12.sp),
                                            modifier = GlanceModifier
                                                .fillMaxWidth()
                                                .padding(start = 28.dp, top = 1.dp, bottom = 1.dp),
                                        )
                                    }
                                    if (subNotes.size > 5) {
                                        Text(
                                            text = "+ ${subNotes.size - 5} more",
                                            style = TextStyle(fontSize = 12.sp),
                                            modifier = GlanceModifier.padding(start = 28.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Toggles a category's expanded/collapsed state when its row is tapped. */
class ToggleCategoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val tappedId = parameters[categoryIdParam] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            val current = prefs[expandedCategoryKey] ?: NONE_EXPANDED
            prefs[expandedCategoryKey] = if (current == tappedId) NONE_EXPANDED else tappedId
        }
        ChecklistWidget().updateAll(context)
    }
}

class ChecklistWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChecklistWidget()
}
