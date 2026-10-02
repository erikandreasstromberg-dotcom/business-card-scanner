package com.houseofsilver.cardscanner.ui.settings

import androidx.lifecycle.ViewModel
import com.houseofsilver.cardscanner.di.AppModule
import com.houseofsilver.cardscanner.domain.model.Contact
import com.houseofsilver.cardscanner.template.TemplateEngine
import kotlinx.coroutines.flow.*

data class TemplateEditorUiState(
    val subject: String = "",
    val body: String = "",
    val previewExpanded: Boolean = false,
    val previewWithEmpty: Boolean = false,
    val unknownVariables: List<String> = emptyList(),
    val showResetDialog: Boolean = false,
    val isDirty: Boolean = false
) {
    val previewSubject: String get() = TemplateEngine.render(subject, previewContact(previewWithEmpty))
    val previewBody: String get() = TemplateEngine.render(body, previewContact(previewWithEmpty))

    private fun previewContact(empty: Boolean) = Contact(
        name = "Jane Doe",
        email = "jane@acme.com",
        role = if (empty) null else "Head of Operations",
        company = if (empty) null else "Acme AB"
    )
}

class TemplateEditorViewModel(private val appModule: AppModule) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplateEditorUiState())
    val uiState: StateFlow<TemplateEditorUiState> = _uiState.asStateFlow()

    init {
        val prefs = appModule.templatePrefs
        _uiState.value = TemplateEditorUiState(
            subject = prefs.getSubject(),
            body = prefs.getBody()
        )
    }

    fun onSubjectChange(v: String) {
        _uiState.update {
            it.copy(
                subject = v,
                isDirty = true,
                unknownVariables = TemplateEngine.findUnknownVariables(v + "\n" + it.body)
            )
        }
    }

    fun onBodyChange(v: String) {
        _uiState.update {
            it.copy(
                body = v,
                isDirty = true,
                unknownVariables = TemplateEngine.findUnknownVariables(it.subject + "\n" + v)
            )
        }
    }

    fun togglePreview() { _uiState.update { it.copy(previewExpanded = !it.previewExpanded) } }
    fun toggleEmptyPreview() { _uiState.update { it.copy(previewWithEmpty = !it.previewWithEmpty) } }

    fun showResetDialog(show: Boolean) { _uiState.update { it.copy(showResetDialog = show) } }

    fun resetToDefault() {
        val prefs = appModule.templatePrefs
        prefs.resetToDefault()
        _uiState.update {
            it.copy(
                subject = prefs.getSubject(),
                body = prefs.getBody(),
                isDirty = false,
                showResetDialog = false,
                unknownVariables = emptyList()
            )
        }
    }

    fun save() {
        val state = _uiState.value
        appModule.templatePrefs.save(state.subject, state.body)
        _uiState.update { it.copy(isDirty = false) }
    }
}
