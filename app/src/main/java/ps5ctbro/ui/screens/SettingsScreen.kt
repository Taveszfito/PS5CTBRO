package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSliderRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSwitchRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import com.DueBoysenberry1226.ps5ctbro.ui.components.StatusRow
import com.DueBoysenberry1226.ps5ctbro.ui.settings.SettingsUiState
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onLanguageSelected: (String) -> Unit,
    onGainChanged: (Float) -> Unit,
    onShowLogWindowsChanged: (Boolean) -> Unit,
    onRefreshControllerInfo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        GeneralSettingsCard(
            showLogs = uiState.showLogWindows,
            onShowLogsChanged = onShowLogWindowsChanged
        )

        LanguageCard(
            currentLanguage = uiState.currentLanguage,
            onLanguageSelected = onLanguageSelected
        )

        ControllerInfoCard(
            info = uiState.controllerInfo,
            onRefresh = onRefreshControllerInfo
        )

        AboutCard(
            version = uiState.appVersion
        )
    }
}

@Composable
private fun ControllerInfoCard(
    info: com.DueBoysenberry1226.ps5ctbro.ui.settings.ControllerInfo?,
    onRefresh: () -> Unit
) {
    SectionCard(
        title = stringResource(R.string.card_title_controller_info),
        titleTrailing = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        if (info == null || !info.isConnected) {
            StatusRow(
                label = stringResource(R.string.label_connection_status),
                value = stringResource(R.string.status_disconnected)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoItem(
                    label = stringResource(R.string.label_connection_status),
                    value = stringResource(R.string.status_connected)
                )
                if (!info.deviceName.isNullOrBlank()) {
                    InfoItem(
                        label = stringResource(R.string.label_device_name),
                        value = info.deviceName
                    )
                }
                InfoItem(
                    label = stringResource(R.string.label_connection_type),
                    value = stringResource(if (info.isWired) R.string.value_wired else R.string.value_wireless)
                )

                // Bluetooth vagy USB adatok megjelenítése szűrve
                val notQueried = stringResource(R.string.label_not_queried)
                val notAvailable = stringResource(R.string.label_not_available)
                val unknown = stringResource(R.string.label_unknown)

                fun isValueValid(value: String?): Boolean {
                    if (value.isNullOrBlank()) return false
                    if (value == notQueried) return false
                    val lower = value.lowercase()
                    if (lower.contains(notAvailable.lowercase())) return false
                    if (lower.contains(unknown.lowercase())) return false
                    if (value == "00:00:00:00:00:00") return false
                    return true
                }

                if (isValueValid(info.btAddress)) {
                    InfoItem(label = stringResource(R.string.label_bt_address), value = info.btAddress)
                }
                if (isValueValid(info.firmwareVersion)) {
                    InfoItem(label = stringResource(R.string.label_firmware_version), value = info.firmwareVersion)
                }
                if (isValueValid(info.firmwareType)) {
                    InfoItem(label = stringResource(R.string.label_firmware_type), value = info.firmwareType)
                }
                if (isValueValid(info.softwareSeries)) {
                    InfoItem(label = stringResource(R.string.label_software_series), value = info.softwareSeries)
                }
                if (isValueValid(info.hardwareInfo)) {
                    InfoItem(label = stringResource(R.string.label_hardware_info), value = info.hardwareInfo)
                }
                if (isValueValid(info.buildDate)) {
                    InfoItem(label = stringResource(R.string.label_build_date), value = info.buildDate)
                }
                if (isValueValid(info.buildTime)) {
                    InfoItem(label = stringResource(R.string.label_build_time), value = info.buildTime)
                }
                if (isValueValid(info.updateVersion)) {
                    InfoItem(label = stringResource(R.string.label_update_version), value = info.updateVersion)
                }
                if (isValueValid(info.serialNumber)) {
                    InfoItem(label = stringResource(R.string.label_serial_number), value = info.serialNumber)
                }

                // Akkumulátor: Megjelenítjük, ha van értelmes adatunk (0-nál nagyobb)
                if (info.batteryLevel > 0) {
                    InfoItem(
                        label = stringResource(R.string.label_battery_level),
                        value = "${info.batteryLevel}%"
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
        )
    }
}


@Composable
private fun GeneralSettingsCard(
    showLogs: Boolean,
    onShowLogsChanged: (Boolean) -> Unit
) {
    SectionCard(title = stringResource(R.string.card_title_settings)) {
        AppSwitchRow(
            label = stringResource(R.string.label_show_log_windows),
            subLabel = stringResource(R.string.desc_show_log_windows),
            checked = showLogs,
            onCheckedChange = onShowLogsChanged
        )
    }
}

@Composable
private fun LanguageCard(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    SectionCard(title = stringResource(R.string.card_title_language)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentLanguage == "en",
                onClick = { onLanguageSelected("en") },
                label = { Text(stringResource(R.string.label_language_english)) },
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = currentLanguage == "hu",
                onClick = { onLanguageSelected("hu") },
                label = { Text(stringResource(R.string.label_language_hungarian)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AboutCard(version: String) {
    SectionCard(title = stringResource(R.string.card_title_about)) {
        StatusRow(
            label = stringResource(R.string.label_version),
            value = version
        )
    }
}