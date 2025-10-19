package ai.automagen.smsrelay.ui.screens.settings

import ai.automagen.smsrelay.data.JsonDataParser
import ai.automagen.smsrelay.data.local.AppDatabase
import ai.automagen.smsrelay.data.local.PreferencesManager
import ai.automagen.smsrelay.data.local.RemoteConfig
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = PreferencesManager(application)
    private val _remotes = MutableLiveData<List<RemoteConfig>>(emptyList())
    val remotes: LiveData<List<RemoteConfig>> = _remotes

    private val _showDeleteConfirmationDialog = MutableLiveData(false)
    val showDeleteConfirmationDialog: LiveData<Boolean> = _showDeleteConfirmationDialog

    private val _snackbarMessage = MutableLiveData<String?>()
    val snackbarMessage: LiveData<String?> = _snackbarMessage

    private val _foregroundNotificationEnabled =
        MutableLiveData(preferencesManager.isForegroundNotificationEnabled())
    val foregroundNotificationEnabled: LiveData<Boolean> = _foregroundNotificationEnabled

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

    fun setForegroundNotificationEnabled(enabled: Boolean) {
        preferencesManager.setForegroundNotificationEnabled(enabled)
        _foregroundNotificationEnabled.value = enabled
    }

    fun importRemotesFromClipboard() {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData? = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val json = clip.getItemAt(0).text?.toString()
                if (!json.isNullOrBlank()) {
                    val remote = JsonDataParser.parseSingleRemoteFromUri(json)
                    if (remote != null) {
                        addRemote(remote)
                        _snackbarMessage.postValue("Remote imported successfully.")
                    } else {
                        _snackbarMessage.postValue("Clipboard data is not a valid remote config.")
                    }
                } else {
                    _snackbarMessage.postValue("Clipboard is empty.")
                }
            } else {
                _snackbarMessage.postValue("Clipboard is empty.")
            }
        }
    }
}
