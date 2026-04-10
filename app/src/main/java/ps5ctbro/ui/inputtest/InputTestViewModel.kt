package com.DueBoysenberry1226.ps5ctbro.ui.inputtest

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class InputTestViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val controller: InputTestController =
        InputTestControllerImpl(application.applicationContext)

    val uiState = controller.uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InputTestUiState()
    )

    fun onScreenVisible() {
        controller.onScreenVisible()
    }

    fun onScreenHidden() {
        controller.onScreenHidden()
    }

    fun refreshConnection() {
        controller.refreshConnection()
    }

    override fun onCleared() {
        controller.release()
        super.onCleared()
    }
}