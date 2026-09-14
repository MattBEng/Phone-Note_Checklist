package com.mattbrady.checklist.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.mattbrady.checklist.ChecklistApp
import kotlinx.coroutines.launch

/**
 * The screen the widget's "+ Add" button opens. It's a real Activity, but
 * themed fully transparent/floating (see Theme.Checklist.Transparent) so it
 * looks like a quick-capture box sitting on top of the home screen — the
 * same trick Google Keep / Tasks widgets use, since Android widgets
 * themselves can't contain a live text field.
 */
class CaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CaptureScreen(
                    onSave = { text -> saveAndFinish(text) },
                    onCancel = { finish() },
                )
            }
        }
    }

    private fun saveAndFinish(text: String) {
        val repo = ChecklistApp.repository(applicationContext)
        lifecycleScope.launch {
            val preview = repo.addNote(text)
            if (preview != null) {
                Toast.makeText(
                    this@CaptureActivity,
                    "Added to ${preview.category} › ${preview.subcategory}",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(
                    this@CaptureActivity,
                    "Need at least: Category Subcategory your note",
                    Toast.LENGTH_LONG,
                ).show()
            }
            finish()
        }
    }
}

@Composable
private fun CaptureScreen(onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { /* absorb clicks so they don't fall through to the scrim */ },
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Quick note", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Fishing Organisation buy new hooks") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = false,
                )
                Text(
                    text = "First word = category, second = subcategory, rest = the note.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (text.isNotBlank()) onSave(text) }) { Text("Save") }
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
