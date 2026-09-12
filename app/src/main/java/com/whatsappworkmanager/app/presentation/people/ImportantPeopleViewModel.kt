package com.whatsappworkmanager.app.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.ImportantContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ImportantPeopleUiState(
    val isLoading: Boolean = true,
    val contacts: List<ImportantContact> = emptyList()
)

class ImportantPeopleViewModel(private val app: WwmApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportantPeopleUiState())
    val uiState: StateFlow<ImportantPeopleUiState> = _uiState

    init {
        app.importantContactRepository.observeContacts()
            .onEach { contacts -> _uiState.value = ImportantPeopleUiState(isLoading = false, contacts = contacts) }
            .launchIn(viewModelScope)
    }

    fun addContact(name: String, phoneNumber: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            app.importantContactRepository.upsert(
                ImportantContact(name = name.trim(), enabled = true, phoneNumber = phoneNumber?.takeIf { it.isNotBlank() })
            )
        }
    }

    fun updateContact(contact: ImportantContact, name: String, phoneNumber: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            app.importantContactRepository.upsert(
                contact.copy(name = name.trim(), phoneNumber = phoneNumber?.takeIf { it.isNotBlank() })
            )
        }
    }

    fun setEnabled(contact: ImportantContact, enabled: Boolean) {
        viewModelScope.launch { app.importantContactRepository.upsert(contact.copy(enabled = enabled)) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { app.importantContactRepository.delete(id) }
    }

    class Factory(private val app: WwmApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ImportantPeopleViewModel(app) as T
    }
}
