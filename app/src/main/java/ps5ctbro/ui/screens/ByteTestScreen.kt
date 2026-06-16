package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import com.DueBoysenberry1226.ps5ctbro.ui.settings.ByteTestCommand
import com.DueBoysenberry1226.ps5ctbro.ui.settings.ByteTestSequence
import com.DueBoysenberry1226.ps5ctbro.ui.settings.ByteTestSequenceMode
import com.DueBoysenberry1226.ps5ctbro.ui.theme.TextMutedDark

private data class ByteGroup(
    val title: String,
    val entries: List<ByteEntry>
)

private data class ByteEntry(
    val id: String,
    val index: String,
    val value: String,
    val source: String,
    val currentMeaning: String,
    val sendIndex: Int? = null,
    val defaultSendValue: String = ""
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ByteTestScreen(
    notes: Map<String, String>,
    sendValues: Map<String, String>,
    sendLog: String,
    sequences: List<ByteTestSequence>,
    playingSequenceId: String?,
    onNoteChanged: (String, String) -> Unit,
    onSendValueChanged: (String, String) -> Unit,
    onSendClick: (String, Int, String) -> Unit,
    onAddSequence: (String, ByteTestSequenceMode, List<ByteTestCommand>) -> Unit,
    onDeleteSequence: (String) -> Unit,
    onPlaySequence: (String) -> Unit
) {
    var showSequenceDialog by remember { mutableStateOf(false) }
    var sequenceToDelete by remember { mutableStateOf<ByteTestSequence?>(null) }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.byte_test_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMutedDark
        )

        FillSendValuesCard(
            onFillClick = { value ->
                sendableEntries().forEach { entry ->
                    onSendValueChanged(entry.id, value)
                }
            }
        )

        ByteSequencesCard(
            sequences = sequences,
            playingSequenceId = playingSequenceId,
            onAddClick = { showSequenceDialog = true },
            onPlaySequence = onPlaySequence,
            onDeleteRequest = { sequenceToDelete = it }
        )

        if (sendLog.isNotBlank()) {
            SectionCard(title = stringResource(R.string.card_title_byte_send_log)) {
                Text(
                    text = sendLog,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        byteGroups().forEach { group ->
            SectionCard(title = group.title) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    group.entries.forEach { entry ->
                        ByteEntryRow(
                            entry = entry,
                            note = notes[entry.id].orEmpty(),
                            sendValue = sendValues[entry.id] ?: entry.defaultSendValue,
                            onNoteChanged = { onNoteChanged(entry.id, it) },
                            onSendValueChanged = { onSendValueChanged(entry.id, it) },
                            onSendClick = { index, value -> onSendClick(entry.id, index, value) }
                        )
                    }
                }
            }
        }
    }

    if (showSequenceDialog) {
        SequenceEditorDialog(
            onDismiss = { showSequenceDialog = false },
            onSave = { name, mode, commands ->
                onAddSequence(name, mode, commands)
                showSequenceDialog = false
            }
        )
    }

    sequenceToDelete?.let { sequence ->
        val sequenceText = sequence.toClipboardText()
        AlertDialog(
            onDismissRequest = { sequenceToDelete = null },
            title = { Text(sequence.name) },
            text = {
                Text(
                    text = sequenceText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(sequenceText))
                    }
                ) {
                    Text(stringResource(R.string.button_copy))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            onDeleteSequence(sequence.id)
                            sequenceToDelete = null
                        }
                    ) {
                        Text(stringResource(R.string.button_delete))
                    }

                    TextButton(onClick = { sequenceToDelete = null }) {
                        Text(stringResource(R.string.button_cancel))
                    }
                }
            }
        )
    }
}

private fun ByteTestSequence.toClipboardText(): String {
    return buildString {
        appendLine("name = $name")
        appendLine("mode = ${mode.name}")
        commands.forEach { command ->
            appendLine("[${command.index}] = ${command.value.uppercase()}")
        }
    }.trimEnd()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ByteSequencesCard(
    sequences: List<ByteTestSequence>,
    playingSequenceId: String?,
    onAddClick: () -> Unit,
    onPlaySequence: (String) -> Unit,
    onDeleteRequest: (ByteTestSequence) -> Unit
) {
    SectionCard(
        title = stringResource(R.string.card_title_byte_sequences),
        titleTrailing = {
            Button(onClick = onAddClick) {
                Text(stringResource(R.string.button_add))
            }
        }
    ) {
        if (sequences.isEmpty()) {
            Text(
                text = stringResource(R.string.msg_no_byte_sequences),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMutedDark
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sequences.forEach { sequence ->
                    val isPlaying = sequence.id == playingSequenceId
                    Surface(
                        modifier = Modifier.combinedClickable(
                            onClick = { if (!isPlaying) onPlaySequence(sequence.id) },
                            onLongClick = { onDeleteRequest(sequence) }
                        ),
                        shape = RoundedCornerShape(999.dp),
                        color = if (isPlaying) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    ) {
                        Text(
                            text = sequence.name,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Text(
                        text = if (sequence.mode == ByteTestSequenceMode.FULL_REPORT) "full" else "series",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedDark,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SequenceEditorDialog(
    onDismiss: () -> Unit,
    onSave: (String, ByteTestSequenceMode, List<ByteTestCommand>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ByteTestSequenceMode.SERIES) }
    var selectedFlag by remember { mutableStateOf("0") }
    var customFlag by remember { mutableStateOf("") }
    var hexValue by remember { mutableStateOf("") }
    var commands by remember { mutableStateOf<List<ByteTestCommand>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var pasteError by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val invalidPasteFormatMessage = stringResource(R.string.error_invalid_paste_format)

    val flagOptions = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "39", "42", "43", "44", "45", "46", "47", "Custom")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_new_sequence)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_sequence_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { mode = ByteTestSequenceMode.SERIES },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = if (mode == ByteTestSequenceMode.SERIES) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Text(stringResource(R.string.sequence_mode_series))
                    }

                    Button(
                        onClick = { mode = ByteTestSequenceMode.FULL_REPORT },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = if (mode == ByteTestSequenceMode.FULL_REPORT) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Text(stringResource(R.string.sequence_mode_full_report))
                    }
                }

                Text(
                    text = stringResource(
                        if (mode == ByteTestSequenceMode.FULL_REPORT) {
                            R.string.sequence_mode_full_report_desc
                        } else {
                            R.string.sequence_mode_series_desc
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedDark
                )

                Button(onClick = { expanded = true }) {
                    Text(stringResource(R.string.label_flag_selector, selectedFlag))
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    flagOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedFlag = option
                                expanded = false
                            }
                        )
                    }
                }

                if (selectedFlag == "Custom") {
                    OutlinedTextField(
                        value = customFlag,
                        onValueChange = { customFlag = it },
                        label = { Text(stringResource(R.string.label_custom_flag)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = hexValue,
                    onValueChange = { hexValue = it },
                    label = { Text(stringResource(R.string.label_byte_send_value)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val indexText = if (selectedFlag == "Custom") customFlag else selectedFlag
                        val index = indexText.toIntOrNull()
                        if (index != null && hexValue.isNotBlank()) {
                            commands = commands + ByteTestCommand(index = index, value = hexValue)
                            hexValue = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.button_add_command))
                }

                if (commands.isNotEmpty()) {
                    Text(
                        text = commands.joinToString("\n") { "[${it.index}] = ${it.value}" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }

                pasteError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, mode, commands) },
                enabled = name.isNotBlank() && commands.isNotEmpty()
            ) {
                Text(stringResource(R.string.button_add))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        val pastedText = clipboardManager.getText()?.text.orEmpty()
                        val pastedSequence = parsePastedSequence(pastedText)

                        if (pastedSequence == null) {
                            pasteError = invalidPasteFormatMessage
                        } else {
                            pastedSequence.name?.let { name = it }
                            pastedSequence.mode?.let { mode = it }
                            commands = pastedSequence.commands
                            pasteError = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.button_paste))
                }

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        }
    )
}

private data class PastedSequence(
    val name: String?,
    val mode: ByteTestSequenceMode?,
    val commands: List<ByteTestCommand>
)

private fun parsePastedSequence(text: String): PastedSequence? {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return null

    val metadataRegex = Regex("""(?im)^\s*(name|mode)\s*=\s*(.+?)\s*$""")
    var pastedName: String? = null
    var pastedMode: ByteTestSequenceMode? = null
    var invalidMetadata = false

    val commandText = metadataRegex.replace(trimmed) { match ->
        when (match.groupValues[1].lowercase()) {
            "name" -> pastedName = match.groupValues[2].trim().takeIf { it.isNotBlank() }
            "mode" -> {
                val parsedMode = parseSequenceMode(match.groupValues[2])
                if (parsedMode == null) {
                    invalidMetadata = true
                } else {
                    pastedMode = parsedMode
                }
            }
        }
        ""
    }
        .trim()

    if (invalidMetadata) return null

    val regex = Regex("""\[(\d+)]\s*=\s*([0-9a-fA-F]{2})""")
    val matches = regex.findAll(commandText).toList()
    if (matches.isEmpty()) return null

    var lastEnd = 0
    matches.forEach { match ->
        if (commandText.substring(lastEnd, match.range.first).isNotBlank()) return null
        lastEnd = match.range.last + 1
    }
    if (commandText.substring(lastEnd).isNotBlank()) return null

    val commands = matches.map { match ->
        val index = match.groupValues[1].toIntOrNull() ?: return null
        if (index !in 0..62) return null

        ByteTestCommand(
            index = index,
            value = match.groupValues[2].uppercase()
        )
    }

    return PastedSequence(
        name = pastedName,
        mode = pastedMode,
        commands = commands
    )
}

private fun parseSequenceMode(raw: String): ByteTestSequenceMode? {
    return when (raw.trim().uppercase().replace(" ", "_")) {
        "SERIES" -> ByteTestSequenceMode.SERIES
        "FULL", "FULL_REPORT" -> ByteTestSequenceMode.FULL_REPORT
        else -> null
    }
}

@Composable
private fun FillSendValuesCard(
    onFillClick: (String) -> Unit
) {
    var fillValue by remember { mutableStateOf("FF") }

    SectionCard(title = stringResource(R.string.card_title_byte_fill)) {
        Text(
            text = stringResource(R.string.byte_fill_description),
            style = MaterialTheme.typography.bodySmall,
            color = TextMutedDark
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = fillValue,
                onValueChange = { fillValue = it },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.label_byte_fill_value)) },
                singleLine = true
            )

            Button(
                onClick = { onFillClick(fillValue) },
                modifier = Modifier.width(104.dp)
            ) {
                Text(stringResource(R.string.button_fill))
            }
        }
    }
}

@Composable
private fun ByteEntryRow(
    entry: ByteEntry,
    note: String,
    sendValue: String,
    onNoteChanged: (String) -> Unit,
    onSendValueChanged: (String) -> Unit,
    onSendClick: (Int, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.index,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(2f)) {
                Text(
                    text = entry.source,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.currentMeaning,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedDark
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = note,
            onValueChange = onNoteChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.label_byte_note)) },
            minLines = 2,
            maxLines = 5
        )

        val sendIndex = entry.sendIndex
        if (sendIndex != null) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = sendValue,
                    onValueChange = onSendValueChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.label_byte_send_value)) },
                    singleLine = true
                )

                Button(
                    onClick = { onSendClick(sendIndex, sendValue) },
                    modifier = Modifier.width(104.dp)
                ) {
                    Text(stringResource(R.string.button_send))
                }
            }
        }
    }
}

@Composable
fun ByteTestDocsDialog(
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val exampleText = """
        name = speaker_loud_clean
        mode = FULL_REPORT
        [0] = 02
        [1] = F3
        [5] = FF
        [6] = FF
        [8] = FF
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.byte_test_docs_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.byte_test_docs_intro)
                )
                Text(
                    text = stringResource(R.string.byte_test_docs_spacing)
                )
                Text(
                    text = stringResource(R.string.byte_test_docs_name_mode)
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = exampleText,
                            modifier = Modifier.padding(
                                start = 12.dp,
                                top = 12.dp,
                                end = 48.dp,
                                bottom = 12.dp
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(exampleText))
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy example"
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.byte_test_docs_modes)
                )
                Text(
                    text = stringResource(R.string.byte_test_docs_error_handling),
                    color = TextMutedDark
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_ok))
            }
        }
    )
}

private fun byteGroups(): List<ByteGroup> = listOf(
    ByteGroup(
        title = "USB output report 0x02",
        entries = listOf(
            ByteEntry("out_00", "[0]", "0x02", "All USB output builders", "Report ID for DualSense USB output report.", sendIndex = 0, defaultSendValue = "02"),
            ByteEntry("out_01", "[1]", "0xFF / 0xF3 / 0xE0 / 0x0C", "Vibration, LED wake, audio route, adaptive trigger", "Primary enable/route flags. Several features currently depend on this byte.", sendIndex = 1, defaultSendValue = "00"),
            ByteEntry("out_02", "[2]", "0xF7 / 0x86 / 0x00 / LED flags", "Vibration, speaker wake, adaptive trigger, LED", "Second flag byte. LED uses this for valid flag 1 controls.", sendIndex = 2, defaultSendValue = "00"),
            ByteEntry("out_03", "[3]", "0x00..0xFF", "VibrationController", "Right vibration motor strength in the current vibration report.", sendIndex = 3, defaultSendValue = "00"),
            ByteEntry("out_04", "[4]", "0x00..0xFF", "VibrationController", "Left vibration motor strength in the current vibration report.", sendIndex = 4, defaultSendValue = "00"),
            ByteEntry("out_05", "[5]", "0xFF / 0x7F", "Speaker wake, audio routing", "Audio/haptics path setup. Jack mode uses 0x7F as headphone volume.", sendIndex = 5, defaultSendValue = "00"),
            ByteEntry("out_06", "[6]", "0x00..0xFF", "Speaker wake, audio routing", "Controller speaker volume or internal speaker mute/max depending on route.", sendIndex = 6, defaultSendValue = "00"),
            ByteEntry("out_07", "[7]", "0xFF / 0x40", "Speaker wake, audio routing", "Audio route setup. Current routing report treats 0x40 as mic volume placeholder.", sendIndex = 7, defaultSendValue = "00"),
            ByteEntry("out_08", "[8]", "0xFF / 0x30 / 0x00", "Speaker wake, audio routing", "Observed speaker/headset route selector.", sendIndex = 8, defaultSendValue = "00"),
            ByteEntry("out_09", "[9]", "0x00 / 0x01", "LedControllerImpl", "Mic mute LED state.", sendIndex = 9, defaultSendValue = "00"),
            ByteEntry("out_11_21", "[11..21]", "trigger block", "DualSenseTriggerReportBuilder", "Right adaptive trigger effect block."),
            ByteEntry("out_22_32", "[22..32]", "trigger block", "DualSenseTriggerReportBuilder", "Left adaptive trigger effect block."),
            ByteEntry("out_39", "[39]", "0x03 / LED valid flag 2", "LED, speaker wake", "LED brightness/setup enable flags; also present in speaker wake reports.", sendIndex = 39, defaultSendValue = "00"),
            ByteEntry("out_42", "[42]", "0x01 / 0x02", "LED, speaker wake", "Lightbar setup: currently used as light out/on and also in wake reports.", sendIndex = 42, defaultSendValue = "00"),
            ByteEntry("out_43", "[43]", "0x00 / 0x01 / 0x02", "LedControllerImpl", "Player LED brightness raw value.", sendIndex = 43, defaultSendValue = "00"),
            ByteEntry("out_44", "[44]", "0x20..0x3F", "LedControllerImpl, speaker wake", "Player LED mask plus instant bit 0x20. Wake reports currently use 0x24.", sendIndex = 44, defaultSendValue = "00"),
            ByteEntry("out_45", "[45]", "0x00..0xFF", "LedControllerImpl", "Lightbar red channel.", sendIndex = 45, defaultSendValue = "00"),
            ByteEntry("out_46", "[46]", "0x00..0xFF", "LedControllerImpl", "Lightbar green channel.", sendIndex = 46, defaultSendValue = "00"),
            ByteEntry("out_47", "[47]", "0x00..0xFF", "LedControllerImpl", "Lightbar blue channel.", sendIndex = 47, defaultSendValue = "00")
        )
    ),
    ByteGroup(
        title = "USB input report",
        entries = listOf(
            ByteEntry("in_01", "[1]", "0x00..0xFF", "InputTestControllerImpl", "Left stick X raw byte."),
            ByteEntry("in_02", "[2]", "0x00..0xFF", "InputTestControllerImpl", "Left stick Y raw byte."),
            ByteEntry("in_03", "[3]", "0x00..0xFF", "InputTestControllerImpl", "Right stick X raw byte."),
            ByteEntry("in_04", "[4]", "0x00..0xFF", "InputTestControllerImpl", "Right stick Y raw byte."),
            ByteEntry("in_05", "[5]", "0x00..0xFF", "InputTestControllerImpl, AdaptiveTriggerControllerImpl", "L2 analog raw byte."),
            ByteEntry("in_06", "[6]", "0x00..0xFF", "InputTestControllerImpl, AdaptiveTriggerControllerImpl", "R2 analog raw byte."),
            ByteEntry("in_08", "[8]", "bitfield", "InputTestControllerImpl", "D-pad and face buttons."),
            ByteEntry("in_09", "[9]", "bitfield", "InputTestControllerImpl", "Shoulders, trigger clicks, Create/Options, stick clicks."),
            ByteEntry("in_10", "[10]", "bitfield", "InputTestControllerImpl, LedControllerImpl", "PS, touchpad click, mic mute."),
            ByteEntry("in_16_21", "[16..21]", "s16 LE", "InputTestControllerImpl", "Gyroscope X/Y/Z raw values."),
            ByteEntry("in_22_27", "[22..27]", "s16 LE", "InputTestControllerImpl", "Accelerometer X/Y/Z raw values."),
            ByteEntry("in_33_36", "[33..36]", "packed touch", "InputTestControllerImpl, AudioControllerImpl", "Touchpad point 1 active bit and packed X/Y."),
            ByteEntry("in_37_40", "[37..40]", "packed touch", "InputTestControllerImpl, AudioControllerImpl", "Touchpad point 2 active bit and packed X/Y."),
            ByteEntry("in_53", "[53]", "battery nibble", "AudioControllerImpl", "Battery capacity nibble converted to percent.")
        )
    ),
    ByteGroup(
        title = "Feature reports",
        entries = listOf(
            ByteEntry("feature_09", "0x09", "20 bytes", "AudioControllerImpl", "Pairing report used for Bluetooth address parsing."),
            ByteEntry("feature_20", "0x20", "64 bytes", "AudioControllerImpl", "Firmware/build/device info report.")
        )
    )
)

private fun sendableEntries(): List<ByteEntry> {
    return byteGroups()
        .flatMap { it.entries }
        .filter { it.sendIndex != null }
}
