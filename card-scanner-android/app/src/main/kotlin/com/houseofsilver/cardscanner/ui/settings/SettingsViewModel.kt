package com.houseofsilver.cardscanner.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseofsilver.cardscanner.di.AppModule
import com.houseofsilver.cardscanner.email.MailtoSender
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val emailApps: List<MailtoSender.EmailApp> = emptyList(),
    val preferredEmailPackage: String = ""
)

class SettingsViewModel(private val appModule: AppModule) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun refresh(context: Context) {
        viewModelScope.launch {
            val prefs = appModule.templatePrefs
            _uiState.value = SettingsUiState(
                emailApps = MailtoSender.installedEmailApps(context),
                preferredEmailPackage = prefs.getPreferredEmailPackage()
            )
        }
    }

    fun onPreferredEmailAppChange(pkg: String) {
        _uiState.update { it.copy(preferredEmailPackage = pkg) }
        appModule.templatePrefs.savePreferredEmailPackage(pkg)
    }
}
