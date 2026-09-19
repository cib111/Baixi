package com.baixi.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 系统分享入口的文本中转站：MainActivity 收到 ACTION_SEND 文本后推入，
 * MainScreen 收集并转入解析页（StateFlow 重放当前值，冷启动分享也不丢）。
 */
object ShareIntentHub {
    private val _pendingText = MutableStateFlow<String?>(null)
    val pendingText: StateFlow<String?> = _pendingText.asStateFlow()

    /** 推送分享文本（空文本忽略） */
    fun push(text: String?) {
        if (!text.isNullOrBlank()) _pendingText.value = text
    }

    /** 消费当前文本 */
    fun consume() {
        _pendingText.value = null
    }
}
