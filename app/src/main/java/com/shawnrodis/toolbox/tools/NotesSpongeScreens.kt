package com.shawnrodis.toolbox.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawnrodis.toolbox.data.AppStore
import com.shawnrodis.toolbox.ui.ToolCardHeader
import com.shawnrodis.toolbox.ui.ToolScreen
import com.shawnrodis.toolbox.ui.copyToClipboard
import com.shawnrodis.toolbox.ui.toast
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// Note model mirrors the source JSON: {id, title, bullets: [..], updatedAt} (toolbox_notes)
data class Note(val id: String, val title: String, val bullets: List<String>, val updatedAt: Long)

/** Source noteTimeLabel(): toLocaleString with month short, day numeric, hour numeric, minute 2-digit. */
private fun noteTimeLabel(ts: Long): String =
    if (ts <= 0) "" else SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(ts))

/**
 * Notes — source behaviour:
 *  - list sorted by updatedAt descending; card shows title (or Untitled),
 *    first 2 non-empty bullets as preview, and non-empty bullet count
 *  - editor: title + one text field per bullet; "Add bullet" appends;
 *    editing touches updatedAt (monotonic in the source; System time here)
 *  - new note starts as {title:"", bullets:[""], updatedAt: now}
 *  - delete asks for confirmation ("Delete this note? This cannot be undone.")
 */
@Composable
fun NotesScreen(store: AppStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notesJson by store.stringState("toolbox_notes", "[]").collectAsState(initial = "[]")
    val notes: List<Note> = remember(notesJson) {
        try {
            val a = JSONArray(notesJson.ifEmpty { "[]" })
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                val b = o.optJSONArray("bullets")
                Note(
                    o.optString("id"), o.optString("title"),
                    if (b != null) List(b.length()) { j -> b.optString(j) } else listOf(""),
                    o.optLong("updatedAt", 0)
                )
            }
        } catch (e: Exception) { emptyList() }
    }
    var openId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save(list: List<Note>) {
        scope.launch {
            val a = JSONArray()
            list.forEach { n ->
                val b = JSONArray()
                n.bullets.forEach { b.put(it) }
                a.put(JSONObject().put("id", n.id).put("title", n.title).put("bullets", b).put("updatedAt", n.updatedAt))
            }
            store.setStringState("toolbox_notes", a.toString())
        }
    }
    fun touch(n: Note): Note = n.copy(updatedAt = System.currentTimeMillis())

    val openNote = notes.find { it.id == openId }

    if (openNote == null) {
        ToolScreen(title = "Notes", onBack = onBack) {
            val sorted = notes.sortedByDescending { it.updatedAt }
            // v1.0.1 (defect #4): card header with the trio (the share/
            // export content is the notes branch of __shareTextFor() —
            // open or most-recent note).
            val shareNote = sorted.firstOrNull()
            val noteLines = shareNote?.bullets?.filter { it.isNotBlank() } ?: emptyList()
            ToolCardHeader(
                faIcon = "fa-note-sticky",
                title = "Notes",
                description = "Bullet-point notes, newest first",
                fileBase = "notes-tool",
                prettyName = "Notes",
                exportHeader = "Notes",
                lines = if (shareNote == null) listOf("No notes yet.")
                else listOf(shareNote.title.ifEmpty { "Note" }) + noteLines.map { "• $it" },
                shareText = if (shareNote == null) "" else
                    (shareNote.title.ifEmpty { "Note" }) +
                        (if (noteLines.isNotEmpty())
                            "\n" + noteLines.joinToString("\n") { "• $it" } else "")
            )
            if (sorted.isEmpty()) {
                Text("No notes yet. Tap New note to start one.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            sorted.forEach { n ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { openId = n.id }) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(n.title.ifEmpty { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                            Text(noteTimeLabel(n.updatedAt), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val nonEmpty = n.bullets.filter { it.isNotBlank() }
                        nonEmpty.take(2).forEach { b ->
                            Text("• $b", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${nonEmpty.size} bullet${if (nonEmpty.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Button(onClick = {
                // Source addNote(): id 'n'+Date.now(36)+'_'+seq, bullets [''], then open it.
                val n = Note(
                    "n" + System.currentTimeMillis().toString(36) + "_" + (notes.size),
                    "", listOf(""), System.currentTimeMillis()
                )
                save(notes + n)
                openId = n.id
            }, modifier = Modifier.padding(top = 10.dp)) { Text("New note") }
        }
    } else {
        ToolScreen(title = "Note", onBack = { openId = null }) {
            OutlinedTextField(
                value = openNote.title,
                onValueChange = { v ->
                    save(notes.map { if (it.id == openNote.id) touch(it.copy(title = v)) else it })
                },
                label = { Text("Title") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Modified ${noteTimeLabel(openNote.updatedAt)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
            openNote.bullets.forEachIndexed { idx, bullet ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("•", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        value = bullet,
                        onValueChange = { v ->
                            save(notes.map { n ->
                                if (n.id == openNote.id) touch(n.copy(bullets = n.bullets.mapIndexed { i, b -> if (i == idx) v else b }))
                                else n
                            })
                        },
                        placeholder = { Text("Bullet point…") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (openNote.bullets.size > 1) {
                        TextButton(onClick = {
                            save(notes.map { n ->
                                if (n.id == openNote.id) touch(n.copy(bullets = n.bullets.filterIndexed { i, _ -> i != idx }))
                                else n
                            })
                        }) { Text("✕") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                Button(onClick = {
                    save(notes.map { n ->
                        if (n.id == openNote.id) touch(n.copy(bullets = n.bullets + "")) else n
                    })
                }) { Text("Add bullet") }
                OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete note") }
            }
        }
    }

    if (confirmDelete && openNote != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    save(notes.filter { it.id != openNote.id })
                    confirmDelete = false
                    openId = null
                    toast(context, "Note deleted.")
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

/**
 * Source sbmSpongeTransform():
 *  - empty input -> "gEnErAtEd tExT HeRe"
 *  - Alternating mode: first letter's case is random, then strictly alternates
 *    per letter (non-letters copied unchanged, don't toggle)
 *  - Randomized mode: every letter's case is independently random.
 */
fun sbmSpongeTransform(str: String, randomMode: Boolean): String {
    if (str.isEmpty()) return "gEnErAtEd tExT HeRe"
    val sb = StringBuilder()
    var upper = Random.nextBoolean()
    for (ch in str) {
        if (ch.isLetter()) {
            if (randomMode) upper = Random.nextBoolean()
            sb.append(if (upper) ch.uppercaseChar() else ch.lowercaseChar())
            if (!randomMode) upper = !upper
        } else sb.append(ch)
    }
    return sb.toString()
}

/**
 * SpongeBob Meme Generator — text persists in toolbox_sbm_state (source);
 * "Press Enter to copy" in web becomes an explicit Copy button natively;
 * mode toggle label Alternating / Randomized (source sbmToggleMode).
 */
@Composable
fun SpongeScreen(store: AppStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedText by store.stringState("toolbox_sbm_state").collectAsState(initial = "")
    var input by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var randomMode by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("gEnErAtEd tExT HeRe") }

    androidx.compose.runtime.LaunchedEffect(savedText) {
        if (!loaded && savedText.isNotEmpty()) {
            input = savedText
            output = sbmSpongeTransform(savedText, randomMode)
            loaded = true
        }
    }

    ToolScreen(title = "sPoNgEbOb mEmE GeNeRaToR", onBack = onBack) {
        // Source tab-memegen is a bespoke card (no standard header block):
        // badge row ("✨ Meme Tool" pill + "Press Enter ↵ to copy") and a
        // big centred Fredoka h1. v1.0.1: rendered in-content again now
        // that the Material TopAppBar is gone.
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                color = androidx.compose.ui.graphics.Color(0xFF2563EB)
            ) {
                Text(
                    "✨ Meme Tool",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Text(
                "Press Enter ↵ to copy",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "sPoNgEbOb mEmE GeNeRaToR",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = com.shawnrodis.toolbox.ui.theme.FredokaFamily,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        )
        OutlinedTextField(
            value = input,
            onValueChange = { v ->
                input = v
                scope.launch { store.setStringState("toolbox_sbm_state", v) }
            },
            label = { Text("insert text here") },
            placeholder = { Text("Type or paste your text here...") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            minLines = 3
        )
        Text("${input.length} chars", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { output = sbmSpongeTransform(input, randomMode) }) { Text("gEnErAtE SpOnGeBoB TeXt!") }
            OutlinedButton(onClick = {
                input = ""
                output = sbmSpongeTransform("", randomMode)
                scope.launch { store.setStringState("toolbox_sbm_state", "") }
            }) { Text("Clear Text") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Text("Mode:", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = {
                randomMode = !randomMode
                output = sbmSpongeTransform(input, randomMode)
            }) { Text(if (randomMode) "Randomized" else "Alternating") }
        }
        Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            // Parity pass: the source renders the meme text/headings in
            // Fredoka (loaded alongside Inter); bundled in res/font now.
            Text(
                output,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = com.shawnrodis.toolbox.ui.theme.FredokaFamily
                ),
                modifier = Modifier.padding(14.dp)
            )
        }
        Button(
            onClick = { copyToClipboard(context, output, "Copied to clipboard!") },
            modifier = Modifier.padding(top = 10.dp)
        ) { Text("Copy to Clipboard") }
    }
}
