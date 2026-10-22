package com.shawnrodis.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * v1.0.1 (defect #4) — the source's per-tool MAIN-CARD HEADER BLOCK, shared
 * by every tool screen in both shells (mobile tab + Desktop window; the
 * window titlebar is a separate frame and the source shows this same
 * in-tab header inside its windows).
 *
 * Source pattern (verified per tab, e.g. tab-curr):
 *   <div class="flex items-center justify-between pb-3 border-b ...">
 *     left:  accent-icon-box (rounded-xl) with the tool's FA icon, then the
 *            in-card <h2> title (which can differ from the registry name —
 *            Currency's is "Global Currency Converter") and an 11px
 *            description line under it.
 *     right: small rounded-xl bordered icon buttons — first the tool's own
 *            header buttons (Currency: Clear fa-xmark, Refresh fa-rotate),
 *            then the export trio in source order/colours:
 *            Share fa-share-nodes emerald-500, JPG fa-file-image sky-500,
 *            PDF fa-file-pdf rose-500 (handlers shareAppToJPG /
 *            exportToolToJPG / exportToolToPDF — or the dedicated
 *            Split/Tip/Tally export bases, passed in by those screens).
 *
 * The trio is present only when [fileBase] != null (tools whose source tab
 * carries the export trio). Tools without it (PDF Tools, Square Pic,
 * Settings) pass null and get the plain header block.
 *
 * v1.0.2 (defect A) — LAYOUT: v1.0.1 put [icon + weighted text column] and
 * the (unweighted) button Row in one Row. Compose measures unweighted
 * children first, so the button group claimed its full intrinsic width
 * (~257dp for Currency's five buttons) and the weighted text column was
 * crushed to the ~20-60dp leftover on a portrait phone, wrapping mid-word
 * into a vertical ribbon ("Glo bal / Conve rt any world count ry's
 * curren cy"). The header now measures through [ToolHeaderRow]: the
 * button group is measured first, and the single-row arrangement is used
 * ONLY when the text column still keeps [HEADER_MIN_TEXT_W]; otherwise
 * the buttons wrap onto their own right-aligned row under the title
 * block (the desktop-window/wide case stays a single row, as before).
 */

// v1.0.2 (defect A) — header arrangement maths (pure, unit-tested).
/** Min width (dp) the title/description column must keep in one row. */
internal const val HEADER_MIN_TEXT_W = 120f
/** Icon box (40dp) + the gap between it and the text column (10dp). */
internal const val HEADER_ICON_BLOCK_W = 50f
/** Min horizontal gap between the text column and the button group. */
internal const val HEADER_MIN_GAP_W = 8f
/** Vertical gap when the button group wraps under the title block. */
internal const val HEADER_WRAP_GAP_DP = 8f

/**
 * True when a single header row can hold the button group AND still
 * leave the text column at least [HEADER_MIN_TEXT_W] wide.
 */
internal fun headerSingleRow(availableW: Float, buttonsW: Float): Boolean =
    buttonsW + HEADER_MIN_GAP_W + HEADER_ICON_BLOCK_W + HEADER_MIN_TEXT_W <= availableW

/**
 * Two-block header layout: [left] = icon + title/description, [buttons] =
 * the header action group. Single row (buttons flush right, both blocks
 * vertically centred) when [headerSingleRow] holds; otherwise [left]
 * takes the full width and [buttons] wraps onto its own row beneath it,
 * right-aligned like the source's justify-between.
 */
@Composable
private fun ToolHeaderRow(
    left: @Composable () -> Unit,
    buttons: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        content = { left(); buttons() },
        modifier = modifier
    ) { measurables, constraints ->
        val maxW = constraints.maxWidth
        val bounded = maxW != Constraints.Infinity
        val hGapPx = HEADER_MIN_GAP_W.dp.roundToPx()
        val vGapPx = HEADER_WRAP_GAP_DP.dp.roundToPx()
        val buttonsPlaceable = measurables[1].measure(Constraints(maxWidth = maxW))
        val single = bounded && headerSingleRow(
            maxW / density, buttonsPlaceable.width / density
        )
        val leftMaxW = when {
            !bounded -> Constraints.Infinity
            single -> (maxW - buttonsPlaceable.width - hGapPx).coerceAtLeast(0)
            else -> maxW
        }
        val leftPlaceable = measurables[0].measure(Constraints(maxWidth = leftMaxW))
        val width = if (bounded) maxW
            else leftPlaceable.width + buttonsPlaceable.width
        val height = if (single || !bounded) {
            maxOf(leftPlaceable.height, buttonsPlaceable.height)
        } else {
            leftPlaceable.height + vGapPx + buttonsPlaceable.height
        }
        layout(width, height) {
            if (single || !bounded) {
                leftPlaceable.placeRelative(0, (height - leftPlaceable.height) / 2)
                buttonsPlaceable.placeRelative(
                    width - buttonsPlaceable.width,
                    (height - buttonsPlaceable.height) / 2
                )
            } else {
                leftPlaceable.placeRelative(0, 0)
                buttonsPlaceable.placeRelative(
                    width - buttonsPlaceable.width,
                    leftPlaceable.height + vGapPx
                )
            }
        }
    }
}

// Source export-button colours (Tailwind emerald-500 / sky-500 / rose-500).
private val SHARE_GREEN = Color(0xFF10B981)
private val JPG_SKY = Color(0xFF0EA5E9)
private val PDF_ROSE = Color(0xFFF43F5E)

/**
 * One small rounded-xl bordered header button (source classes:
 * p-2, bg-slate-100/zinc-900, border slate-200/zinc-800, rounded-xl,
 * text-xs, active:scale-95). [text] null = icon-only (the tool-specific
 * buttons); the export trio carries its label, as in the source.
 */
@Composable
fun ToolHeaderButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    text: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(13.dp))
        if (text != null) {
            // v1.0.2: a header button label never wraps — the button group
            // is measured at its natural single-line size by ToolHeaderRow.
            Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@Composable
fun ToolCardHeader(
    faIcon: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    fileBase: String? = null,
    prettyName: String = "",
    exportHeader: String = "",
    lines: List<String> = emptyList(),
    shareText: String = "",
    leadingActions: @Composable RowScope.() -> Unit = {},
    trailingActions: @Composable RowScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxWidth()) {
        ToolHeaderRow(
            left = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToolIconBox(faIcon, boxDp = 40.dp, glyphDp = 20.dp, cornerDp = 12.dp)
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2
                        )
                        if (description.isNotEmpty()) {
                            Text(
                                description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            buttons = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    leadingActions()
                    if (fileBase != null) {
                        ToolHeaderButton(
                            icon = toolIconVector("fa-share-nodes")!!,
                            contentDescription = "Share",
                            tint = SHARE_GREEN,
                            text = "Share",
                            onClick = {
                                scope.launch {
                                    toast(context, shareAsJpg(context, fileBase, prettyName, exportHeader, lines, shareText))
                                }
                            }
                        )
                        ToolHeaderButton(
                            icon = toolIconVector("fa-file-image")!!,
                            contentDescription = "Export to JPG",
                            tint = JPG_SKY,
                            text = "JPG",
                            onClick = {
                                scope.launch {
                                    toast(context, exportToJpg(context, fileBase, prettyName, exportHeader, lines))
                                }
                            }
                        )
                        ToolHeaderButton(
                            icon = toolIconVector("fa-file-pdf")!!,
                            contentDescription = "Export to PDF",
                            tint = PDF_ROSE,
                            text = "PDF",
                            onClick = {
                                scope.launch {
                                    toast(context, exportToPdf(context, fileBase, prettyName, exportHeader, lines))
                                }
                            }
                        )
                    }
                    // A few tools put an extra action AFTER the trio in the
                    // source header (Time: "Current", fa-clock) — trailing slot.
                    trailingActions()
                }
            }
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )
    }
}
