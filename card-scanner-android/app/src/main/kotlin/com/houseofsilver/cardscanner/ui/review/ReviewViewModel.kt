package com.houseofsilver.cardscanner.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseofsilver.cardscanner.data.local.ScanEntity
import com.houseofsilver.cardscanner.di.AppModule
import com.houseofsilver.cardscanner.domain.model.Contact
import com.houseofsilver.cardscanner.domain.model.ScanStatus
import com.houseofsilver.cardscanner.email.MailtoSender
import com.houseofsilver.cardscanner.template.TemplateEngine
import com.houseofsilver.cardscanner.ui.openUrl
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ReviewUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val email: String = "",
    val company: String = "",
    val role: String = "",
    val phone: String = "",
    val website: String = "",
    val linkedin: String = "",
    val address: String = "",
    val contextNote: String = "",
    val imageUri: String? = null,
    val rawText: String = "",
    val ocrConfidence: Float = 0f,
    val hasLowConfidence: Boolean = false,
    val previewExpanded: Boolean = false,
    val renderedSubject: String = "",
    val renderedBody: String = "",
    val isSending: Boolean = false,
    val sendError: String? = null,
    /** True when the fields came from a decoded code, so OCR confidence is irrelevant. */
    val fromCode: Boolean = false
)

class ReviewViewModel(
    private val scanId: String,
    private val appModule: AppModule
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private var originalEntity: ScanEntity? = null

    init {
        loadScan()
    }

    private fun loadScan() {
        viewModelScope.launch {
            appModule.scanDao.getScanById(scanId)
                .filterNotNull()
                .take(1)
                .collect { entity ->
                    originalEntity = entity
                    val fromCode = entity.source == "qr"
                    val lowConf = !fromCode && entity.ocrConfidence < 0.7f

                    val state = ReviewUiState(
                        fromCode = fromCode,
                        isLoading = false,
                        name = entity.contactName,
                        email = entity.contactEmail,
                        company = entity.contactCompany ?: "",
                        role = entity.contactRole ?: "",
                        phone = entity.contactPhone ?: "",
                        website = entity.contactWebsite ?: "",
                        linkedin = entity.contactLinkedin ?: "",
                        address = entity.contactAddress ?: "",
                        imageUri = entity.imageUri,
                        rawText = entity.rawText,
                        ocrConfidence = entity.ocrConfidence,
                        hasLowConfidence = lowConf
                    )
                    _uiState.value = state
                    updatePreview(state)
                }
        }
    }

    fun onNameChange(v: String) { _uiState.update { it.copy(name = v) }; updatePreview() }
    fun onEmailChange(v: String) { _uiState.update { it.copy(email = v) }; updatePreview() }
    fun onCompanyChange(v: String) { _uiState.update { it.copy(company = v) }; updatePreview() }
    fun onRoleChange(v: String) { _uiState.update { it.copy(role = v) }; updatePreview() }
    fun onPhoneChange(v: String) { _uiState.update { it.copy(phone = v) } }
    fun onWebsiteChange(v: String) { _uiState.update { it.copy(website = v) } }
    fun onLinkedinChange(v: String) { _uiState.update { it.copy(linkedin = v) } }
    fun onAddressChange(v: String) { _uiState.update { it.copy(address = v) } }
    fun onContextChange(v: String) { _uiState.update { it.copy(contextNote = v) } }
    fun togglePreview() { _uiState.update { it.copy(previewExpanded = !it.previewExpanded) } }

    private fun updatePreview(state: ReviewUiState = _uiState.value) {
        val contact = state.toContact()
        val subject = TemplateEngine.render(appModule.templatePrefs.getSubject(), contact)
        val body = TemplateEngine.render(appModule.templatePrefs.getBody(), contact)
        _uiState.update { it.copy(renderedSubject = subject, renderedBody = body) }
    }

    fun send(
        context: android.content.Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, sendError = null) }
            try {
                val state = _uiState.value
                val contact = state.toContact()
                val prefs = appModule.templatePrefs
                val subject = TemplateEngine.render(prefs.getSubject(), contact)
                val body = TemplateEngine.render(prefs.getBody(), contact)

                val result = MailtoSender.send(
                    context = context,
                    to = state.email,
                    subject = subject,
                    body = body,
                    preferredPackage = prefs.getPreferredEmailPackage()
                )

                val now = System.currentTimeMillis()
                val newStatus = if (result.launched) ScanStatus.SENT.name else ScanStatus.FAILED.name

                val updatedEntity = originalEntity!!.copy(
                    contactName = state.name,
                    contactEmail = state.email,
                    contactCompany = state.company.takeIf { it.isNotBlank() },
                    contactRole = state.role.takeIf { it.isNotBlank() },
                    contactPhone = state.phone.takeIf { it.isNotBlank() },
                    contactWebsite = state.website.takeIf { it.isNotBlank() },
                    contactLinkedin = state.linkedin.takeIf { it.isNotBlank() },
                    contactAddress = state.address.takeIf { it.isNotBlank() },
                    context = state.contextNote.takeIf { it.isNotBlank() },
                    emailStatus = newStatus,
                    emailSentAt = if (result.launched) now else null,
                    emailSubjectSent = subject,
                    emailBodySent = body,
                    templateVersion = prefs.getVersion()
                )
                appModule.scanDao.updateScan(updatedEntity)

                if (result.launched) {
                    onSuccess()
                } else {
                    val msg = result.error ?: "Could not open email app"
                    _uiState.update { it.copy(sendError = msg) }
                    onError(msg)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(sendError = e.message) }
                onError(e.message ?: "Unknown error")
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    /**
     * Records the contact without an email draft — the only option when a LinkedIn QR gave us
     * a profile but no address. The context note is what makes it useful later.
     */
    fun saveOnly(onDone: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            originalEntity?.let { entity ->
                appModule.scanDao.updateScan(
                    entity.copy(
                        contactName = state.name,
                        contactEmail = state.email,
                        contactCompany = state.company.takeIf { it.isNotBlank() },
                        contactRole = state.role.takeIf { it.isNotBlank() },
                        contactPhone = state.phone.takeIf { it.isNotBlank() },
                        contactWebsite = state.website.takeIf { it.isNotBlank() },
                        contactLinkedin = state.linkedin.takeIf { it.isNotBlank() },
                        contactAddress = state.address.takeIf { it.isNotBlank() },
                        context = state.contextNote.takeIf { it.isNotBlank() },
                        emailStatus = ScanStatus.SAVED.name
                    )
                )
            }
            onDone()
        }
    }

    fun openLinkedin(context: android.content.Context) {
        val url = _uiState.value.linkedin.takeIf { it.isNotBlank() } ?: return
        openUrl(context, url)
    }

    fun cancel(context: android.content.Context, onDone: () -> Unit) {
        viewModelScope.launch {
            originalEntity?.let { entity ->
                appModule.scanDao.updateScan(entity.copy(emailStatus = ScanStatus.CANCELLED.name))
            }
            onDone()
        }
    }

    private fun ReviewUiState.toContact() = Contact(
        name = name,
        email = email,
        role = role.takeIf { it.isNotBlank() },
        company = company.takeIf { it.isNotBlank() },
        phone = phone.takeIf { it.isNotBlank() },
        website = website.takeIf { it.isNotBlank() },
        linkedin = linkedin.takeIf { it.isNotBlank() },
        address = address.takeIf { it.isNotBlank() }
    )
}
