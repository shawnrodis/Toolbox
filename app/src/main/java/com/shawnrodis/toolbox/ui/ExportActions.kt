package com.shawnrodis.toolbox.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * v1.3.9 — the per-tool export trio (Share / JPG / PDF), published by a
 * tool's [ToolCardHeader] up to the mobile shell's bottom bar.
 *
 * The trio's content is computed by the tool screen itself (its
 * [ToolCardHeader] params), so the header publishes the whole bundle
 * and the bar just invokes the same export functions with it. A data
 * class, so the header's equality guard can tell a real change from a
 * recomposition with identical values (without it, publishing on every
 * composition would recompose the shell in a loop).
 */
data class ExportActions(
    val fileBase: String,
    val prettyName: String,
    val exportHeader: String,
    val lines: List<String>,
    val shareText: String
)

/** Mutable holder for the currently visible tool's trio, if any. */
class ExportActionsHolder {
    var current by mutableStateOf<ExportActions?>(null)
}

/**
 * Provided by the mobile shell around its NavHost. Desktop windows
 * never provide it, so their headers keep rendering the trio in place
 * (there is no bottom bar in Desktop Mode to receive it).
 */
val LocalExportActionsHolder = compositionLocalOf<ExportActionsHolder?> { null }
