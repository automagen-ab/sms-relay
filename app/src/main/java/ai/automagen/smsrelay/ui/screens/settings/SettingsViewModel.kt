package ai.automagen.smsrelay.ui.screens.settings

import ai.automagen.smsrelay.data.local.RemoteConfig
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import android.app.Application
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ai.automagen.smsrelay.data.local.AppDatabase
import ai.automagen.smsrelay.data.local.PreferencesManager

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = PreferencesManager(application)
    private val _remotes = MutableLiveData<List<RemoteConfig>>(emptyList())
    val remotes: LiveData<List<RemoteConfig>> = _remotes

    private val _showDeleteConfirmationDialog = MutableLiveData(false)
    val showDeleteConfirmationDialog: LiveData<Boolean> = _showDeleteConfirmationDialog

    private val _snackbarMessage = MutableLiveData<String?>()
    val snackbarMessage: LiveData<String?> = _snackbarMessage

    init {
        loadRemotes()
    }

    fun addRemote(newRemote: RemoteConfig) {
        preferencesManager.addRemote(newRemote)
        loadRemotes()
    }

    private fun loadRemotes() {
        _remotes.postValue(preferencesManager.getRemoteConfigs())
    }

    fun updateRemote(updatedRemote: RemoteConfig) {
        preferencesManager.updateRemote(updatedRemote)
        loadRemotes()
    }

    fun deleteRemote(remoteId: String) {
        preferencesManager.deleteRemote(remoteId)
        loadRemotes()
    }

    fun onDeleteHistoryClicked() {
        _showDeleteConfirmationDialog.value = true
    }

    fun confirmDeleteHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val database = AppDatabase.getDatabase(getApplication())
                database.smsLogDao().deleteAllSmsLogs()
                withContext(Dispatchers.Main) {
                    _snackbarMessage.value = "SMS Log history deleted successfully."
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error deleting SMS history", e)
                withContext(Dispatchers.Main) {
                    _snackbarMessage.value = "Error deleting history."
                }
            }
            _showDeleteConfirmationDialog.postValue(false)
        }
    }

    fun dismissDeleteConfirmationDialog() {
        _showDeleteConfirmationDialog.value = false
    }

    fun consumeSnackbarMessage() {
        _snackbarMessage.value = null
    }
}