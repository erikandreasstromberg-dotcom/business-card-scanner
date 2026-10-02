package com.houseofsilver.cardscanner.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.houseofsilver.cardscanner.di.AppModule
import com.houseofsilver.cardscanner.template.TemplateEngine

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TemplateEditorScreen(
    appModule: AppModule,
    onNavigateBack: () -> Unit
) {
    val vm: TemplateEditorViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TemplateEditorViewModel(appModule) as T
        }
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Track cursor position in body field for variable insertion
    var bodyValue by remember {
        mutableStateOf(TextFieldValue(text = state.body))
    }
    LaunchedEffect(state.body) {
        if (bodyValue.text != state.body) {
            bodyValue = bodyValue.copy(text = state.body)
        }
    }

    if (state.showResetDialog) {
        AlertDialog(
            onDismissRequest = { vm.showResetDialog(false) },
            title = { Text("Reset to default?") },
            text = { Text("This will discard your current template and restore the default.") },
            confirmButton = {
                TextButton(onClick = { vm.resetToDefault() }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { vm.showResetDialog(false) }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Email Template") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Subject field
            OutlinedTextField(
                value = state.subject,
                onValueChange = vm::onSubjectChange,
                label = { Text("Subject") },
                placeholder = { Text("Available: {first_name}, {name}, {company}") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 2. Body field
            OutlinedTextField(
                value = bodyValue,
                onValueChange = { newVal ->
                    bodyValue = newVal
                    vm.onBodyChange(newVal.text)
                },
                label = { Text("Body") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                minLines = 8
            )

            // 3. Variable chips
            Text("Insert variable:", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TemplateEngine.KNOWN_VARIABLES.forEach { variable ->
                    AssistChip(
                        onClick = {
                            // Insert at current cursor position
                            val sel = bodyValue.selection
                            val newText = bodyValue.text.substring(0, sel.start) +
                                    variable +
                                    bodyValue.text.substring(sel.end)
                            val newCursor = sel.start + variable.length
                            bodyValue = TextFieldValue(
                                text = newText,
                                selection = TextRange(newCursor)
                            )
                            vm.onBodyChange(newText)
                        },
                        label = { Text(variable) }
                    )
                }
            }

            // 4. Unknown variable warning
            if (state.unknownVariables.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            "Unknown variable(s): ${state.unknownVariables.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 5. Live preview (collapsible)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Preview", style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = vm::togglePreview) {
                            Icon(
                                if (state.previewExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null
                            )
                        }
                    }
                    if (state.previewExpanded) {
                        Spacer(Modifier.height(8.dp))

                        // 6. Empty fields toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(
                                checked = state.previewWithEmpty,
                                onCheckedChange = { vm.toggleEmptyPreview() }
                            )
                            Text("Preview with empty fields", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Subject: ${state.previewSubject}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.previewBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 7. Reset button
            OutlinedButton(
                onClick = { vm.showResetDialog(true) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset to default")
            }

            // 8. Save button
            Button(
                onClick = {
                    vm.save()
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isDirty
            ) {
                Text("Save")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
