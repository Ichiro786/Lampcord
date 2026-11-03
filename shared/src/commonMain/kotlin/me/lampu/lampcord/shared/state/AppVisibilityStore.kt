package me.lampu.lampcord.shared.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppVisibilityStore {
    private val _isVisible = MutableStateFlow(true)

    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    val isVisibleNow: Boolean get() = _isVisible.value

    fun setVisible(visible: Boolean) {
        _isVisible.value = visible
    }

    fun start() = registerVisibilityListener(
        onVisible = { setVisible(true) },
        onHidden = { setVisible(false) }
    )
}

data class ChatInteraction(val channelId: String, val atBottom: Boolean)

internal expect fun registerVisibilityListener(onVisible: () -> Unit, onHidden: () -> Unit)
