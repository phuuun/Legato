package com.phuuun.legato.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

/** "Removed X · Undo". A newer one replaces the one showing rather than queueing behind it. */
suspend fun SnackbarHostState.offerUndo(text: String, onUndo: () -> Unit) {
    currentSnackbarData?.dismiss()
    if (showSnackbar(text, "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) onUndo()
}
