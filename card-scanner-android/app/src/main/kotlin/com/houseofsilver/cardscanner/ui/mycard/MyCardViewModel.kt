package com.houseofsilver.cardscanner.ui.mycard

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.houseofsilver.cardscanner.di.AppModule
import com.houseofsilver.cardscanner.domain.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class MyCardUiState(
    val name: String = "",
    val email: String = "",
    val company: String = "",
    val role: String = "",
    val phone: String = "",
    val website: String = "",
    val linkedin: String = "",
    val imageUri: String? = null
) {
    fun toContact() = Contact(
        name = name.trim(),
        email = email.trim(),
        company = company.trim().takeIf { it.isNotBlank() },
        role = role.trim().takeIf { it.isNotBlank() },
        phone = phone.trim().takeIf { it.isNotBlank() },
        website = website.trim().takeIf { it.isNotBlank() },
        linkedin = linkedin.trim().takeIf { it.isNotBlank() },
        address = null
    )
}

class MyCardViewModel(private val appModule: AppModule) : ViewModel() {

    private val _uiState = MutableStateFlow(MyCardUiState())
    val uiState: StateFlow<MyCardUiState> = _uiState.asStateFlow()

    init {
        val contact = appModule.ownCardPrefs.getContact()
        _uiState.value = MyCardUiState(
            name = contact.name,
            email = contact.email,
            company = contact.company ?: "",
            role = contact.role ?: "",
            phone = contact.phone ?: "",
            website = contact.website ?: "",
            linkedin = contact.linkedin ?: "",
            imageUri = appModule.ownCardPrefs.getImageUri()
        )
    }

    fun onNameChange(v: String) = update { it.copy(name = v) }
    fun onEmailChange(v: String) = update { it.copy(email = v) }
    fun onCompanyChange(v: String) = update { it.copy(company = v) }
    fun onRoleChange(v: String) = update { it.copy(role = v) }
    fun onPhoneChange(v: String) = update { it.copy(phone = v) }
    fun onWebsiteChange(v: String) = update { it.copy(website = v) }
    fun onLinkedinChange(v: String) = update { it.copy(linkedin = v) }

    private fun update(transform: (MyCardUiState) -> MyCardUiState) {
        _uiState.update(transform)
        appModule.ownCardPrefs.save(_uiState.value.toContact())
    }

    /** Creates the destination for a camera capture; the file exists only once the camera writes it. */
    fun newCaptureFile(context: Context): File {
        val dir = File(context.filesDir, "mycard").apply { mkdirs() }
        return File(dir, "card-${System.currentTimeMillis()}.jpg")
    }

    fun onImageCaptured(file: File) {
        if (!file.exists() || file.length() == 0L) return
        pruneOldImages(file)
        setImageUri(Uri.fromFile(file).toString())
    }

    /**
     * Copies a picked image into app storage. The gallery URI is only readable while the
     * permission grant lasts, so keeping the reference alone would break on next launch.
     */
    fun onImagePicked(context: Context, source: Uri) {
        try {
            val target = newCaptureFile(context)
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return
            pruneOldImages(target)
            setImageUri(Uri.fromFile(target).toString())
        } catch (_: Exception) {
            // leave the existing image in place
        }
    }

    fun clearImage() {
        _uiState.value.imageUri?.let { uriStr ->
            try {
                Uri.parse(uriStr).path?.let { File(it).delete() }
            } catch (_: Exception) { /* ignore */ }
        }
        setImageUri(null)
    }

    private fun setImageUri(uri: String?) {
        appModule.ownCardPrefs.saveImageUri(uri)
        _uiState.update { it.copy(imageUri = uri) }
    }

    /** Filenames are timestamped so Coil can't serve a stale cache entry; drop the superseded ones. */
    private fun pruneOldImages(keep: File) {
        keep.parentFile?.listFiles()?.forEach { if (it.name != keep.name) it.delete() }
    }
}
