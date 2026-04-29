package com.DueBoysenberry1226.ps5ctbro.ui.connection

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class ControllerConnectionViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = ControllerConnectionRepository.get(application)

    val uiState = repository.uiState

    fun refresh() {
        repository.refresh()
    }
}