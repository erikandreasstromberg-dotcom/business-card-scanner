package com.houseofsilver.cardscanner.ui.history

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseofsilver.cardscanner.data.local.ScanEntity
import com.houseofsilver.cardscanner.di.AppModule
import com.houseofsilver.cardscanner.domain.model.Contact
import com.houseofsilver.cardscanner.domain.model.ScanStatus
import com.houseofsilver.cardscanner.email.MailtoSender
import com.houseofsilver.cardscanner.export.ScanCsv
import com.houseofsilver.cardscanner.template.TemplateEngine
import com.houseofsilver.cardscanner.ui.openUrl
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class HistoryUiState(
    val scans: List<ScanEntity> = emptyList()
)

class HistoryViewModel(private val appModule: AppModule) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = appModule.scanDao
        .getAllScans()
        .map { scans -> HistoryUiState(scans = scans) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState()
        )

    private val _expandedId = MutableStateFlow<String?>(null)
    val expandedId: StateFlow<String?> = _expandedId.asStateFlow()

    private val _pendingDeleteId = MutableStateFlow<String?>(null)
    val pendingDeleteId: StateFlow<String?> = _pendingDeleteId.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _exportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = _exportError.asStateFlow()

    fun toggleExpand(scanId: String) {
        _expandedId.update { current -> if (current == scanId) null else scanId }
    }

    // ------------------------------------------------------------------ selection / export

    fun startSelection() {
        _selectionMode.value = true
        _expandedId.value = null
    }

    fun cancelSelection() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
        _exportError.value = null
    }

    fun toggleSelected(scanId: String) {
        _selectedIds.update { current ->
            if (scanId in current) current - scanId else current + scanId
        }
    }

    fun exportSelected(context: Context) {
        viewModelScope.launch {
            _exportError.value = null
            val ids = _selectedIds.value
            if (ids.isEmpty()) return@launch

            // Keep the on-screen ordering (newest first) in the exported file
            val scans = appModule.scanDao.getAllScans().first().filter { it.id in ids }
            if (scans.isEmpty()) return@launch

            val uri = writeCsvToShareableFile(context, scans)
            if (uri == null) {
                _exportError.value = "Could not prepare the export file"
                return@launch
            }

            val result = MailtoSender.sendAttachment(
                context = context,
                attachment = uri,
                mimeType = "text/csv",
                subject = "Business card scans (${scans.size})",
                body = "Attached: ${scans.size} scanned contact(s) as CSV, ready to import.",
                preferredPackage = appModule.templatePrefs.getPreferredEmailPackage()
            )
            if (result.launched) {
                cancelSelection()
            } else {
                _exportError.value = result.error ?: "Could not open email app"
            }
        }
    }

    private fun writeCsvToShareableFile(context: Context, scans: List<ScanEntity>): Uri? {
        return try {
            val dir = File(context.filesDir, "exports").apply { mkdirs() }
            // One file per export, replaced each time, so exports don't accumulate on disk
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, ScanCsv.fileName(scans.size, System.currentTimeMillis()))
            file.writeText(ScanCsv.build(scans))
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ per-scan actions

    fun resend(context: Context, scanId: String) {
        viewModelScope.launch {
            val scan = appModule.scanDao.getScanByIdOnce(scanId) ?: return@launch
            val prefs = appModule.templatePrefs
            val subject = scan.emailSubjectSent ?: TemplateEngine.render(prefs.getSubject(), scan.toContact())
            val body = scan.emailBodySent ?: TemplateEngine.render(prefs.getBody(), scan.toContact())

            val result = MailtoSender.send(
                context = context,
                to = scan.contactEmail,
                subject = subject,
                body = body,
                preferredPackage = prefs.getPreferredEmailPackage()
            )
            if (result.launched) {
                appModule.scanDao.updateScan(
                    scan.copy(
                        emailStatus = ScanStatus.SENT.name,
                        emailSentAt = System.currentTimeMillis(),
                        emailSubjectSent = subject,
                        emailBodySent = body,
                        templateVersion = prefs.getVersion()
                    )
                )
            }
        }
    }

    fun openLinkedin(context: Context, scanId: String) {
        viewModelScope.launch {
            val url = appModule.scanDao.getScanByIdOnce(scanId)
                ?.contactLinkedin?.takeIf { it.isNotBlank() } ?: return@launch
            openUrl(context, url)
        }
    }

    fun requestDelete(scanId: String) {
        _pendingDeleteId.value = scanId
    }

    fun cancelDelete() {
        _pendingDeleteId.value = null
    }

    fun confirmDelete() {
        val id = _pendingDeleteId.value ?: return
        _pendingDeleteId.value = null
        viewModelScope.launch {
            val scan = appModule.scanDao.getScanByIdOnce(id) ?: return@launch
            scan.imageUri?.let { uriStr ->
                try {
                    val uri = Uri.parse(uriStr)
                    if (uri.scheme == "file") {
                        uri.path?.let { path -> File(path).delete() }
                    }
                } catch (_: Exception) { /* ignore */ }
            }
            appModule.scanDao.deleteScanById(id)
            if (_expandedId.value == id) _expandedId.value = null
            _selectedIds.update { it - id }
        }
    }

    private fun ScanEntity.toContact() = Contact(
        name = contactName,
        email = contactEmail,
        role = contactRole,
        company = contactCompany,
        phone = contactPhone,
        website = contactWebsite,
        linkedin = contactLinkedin,
        address = contactAddress
    )
}
