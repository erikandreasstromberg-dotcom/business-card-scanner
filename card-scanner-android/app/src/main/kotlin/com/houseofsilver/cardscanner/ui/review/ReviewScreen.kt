package com.houseofsilver.cardscanner.ui.review

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.houseofsilver.cardscanner.R
import com.houseofsilver.cardscanner.di.AppModule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    scanId: String,
    appModule: AppModule,
    onNavigateToConfirmation: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    val vm: ReviewViewModel = viewModel(
        key = scanId,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReviewViewModel(scanId, appModule) as T
        }
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        vm.cancel(context) { onNavigateBack() }
                    }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Card image thumbnail
            state.imageUri?.let { uriStr ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    AsyncImage(
                        model = Uri.parse(uriStr),
                        contentDescription = "Business card image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // 2. Low-confidence warning
            if (state.hasLowConfidence) {
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
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            stringResource(R.string.low_confidence_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 4. Editable fields
            ReviewTextField(
                label = "Name",
                value = state.name,
                onValueChange = vm::onNameChange,
                isWarning = state.name.isBlank()
            )
            ReviewTextField(
                label = "Email",
                value = state.email,
                onValueChange = vm::onEmailChange,
                // Not a fault when a LinkedIn QR simply carried no address
                isWarning = !isValidEmail(state.email) && state.linkedin.isBlank(),
                keyboardType = KeyboardType.Email
            )
            ReviewTextField(label = "Company", value = state.company, onValueChange = vm::onCompanyChange)
            ReviewTextField(label = "Role / Title", value = state.role, onValueChange = vm::onRoleChange)
            ReviewTextField(
                label = "Phone",
                value = state.phone,
                onValueChange = vm::onPhoneChange,
                keyboardType = KeyboardType.Phone
            )
            ReviewTextField(
                label = "Website",
                value = state.website,
                onValueChange = vm::onWebsiteChange,
                keyboardType = KeyboardType.Uri
            )
            ReviewTextField(label = "LinkedIn", value = state.linkedin, onValueChange = vm::onLinkedinChange)

            OutlinedTextField(
                value = state.address,
                onValueChange = vm::onAddressChange,
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // 5. Context note — the only thing that makes an email-less contact findable later,
            // so it gets a nudge when there's nobody to write to.
            val canEmail = isValidEmail(state.email)
            if (!canEmail) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        stringResource(R.string.no_email_note_hint),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            OutlinedTextField(
                value = state.contextNote,
                onValueChange = vm::onContextChange,
                label = { Text("Context note") },
                placeholder = { Text(stringResource(R.string.context_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = if (canEmail) 2 else 3,
                maxLines = 5
            )

            // 6. Email preview (collapsible)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Email preview", style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = vm::togglePreview) {
                            Icon(
                                if (state.previewExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = if (state.previewExpanded) "Collapse" else "Expand"
                            )
                        }
                    }
                    if (state.previewExpanded) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Subject: ${state.renderedSubject}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.renderedBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Error message
            state.sendError?.let { err ->
                Text(
                    "Error: $err",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // 7. Action buttons
            Spacer(Modifier.height(8.dp))

            // A LinkedIn QR gives a profile but no address, so there is no draft to open.
            // Recording the contact is then the useful action, with the profile a tap away.
            if (state.linkedin.isNotBlank()) {
                OutlinedButton(
                    onClick = { vm.openLinkedin(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.open_linkedin))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { vm.cancel(context) { onNavigateBack() } },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSending
                ) {
                    Text(stringResource(R.string.cancel_button))
                }
                Button(
                    onClick = {
                        if (canEmail) {
                            vm.send(
                                context = context,
                                onSuccess = { onNavigateToConfirmation(scanId) },
                                onError = { /* shown via state */ }
                            )
                        } else {
                            vm.saveOnly { onNavigateToConfirmation(scanId) }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = state.name.isNotBlank() && !state.isSending
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            stringResource(
                                if (canEmail) R.string.send_button else R.string.save_contact_button
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReviewTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isWarning: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = if (isWarning) {
            { Icon(Icons.Default.Warning, contentDescription = "Low confidence", tint = MaterialTheme.colorScheme.error) }
        } else null
    )
}

private fun isValidEmail(email: String): Boolean {
    return email.matches(Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"))
}
