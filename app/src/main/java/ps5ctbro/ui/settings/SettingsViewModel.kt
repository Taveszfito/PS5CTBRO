package com.DueBoysenberry1226.ps5ctbro.ui.settings

import android.Manifest
import android.app.Application
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Process
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.DueBoysenberry1226.ps5ctbro.audio.AudioController
import com.DueBoysenberry1226.ps5ctbro.audio.AudioControllerImpl
import com.DueBoysenberry1226.ps5ctbro.ui.connection.ControllerConnectionRepository
import com.DueBoysenberry1226.ps5ctbro.ui.connection.ControllerConnectionType
import com.DueBoysenberry1226.ps5ctbro.ui.hid.DualSenseUsbHidManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

private val SUPPORTED_LANGUAGE_TAGS = setOf("en", "hu", "es", "de", "fr", "pt-BR", "ja")

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val audioController: AudioController = AudioControllerImpl.getInstance(application)
    private val connectionRepository = ControllerConnectionRepository.get(application)
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val appContext = application.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val hidManager = DualSenseUsbHidManager.get(appContext)
    private val usageStatsManager =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val appOpsManager = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    private var byteTestReceiverRegistered = false
    private var pendingByteSend: PendingByteSend? = null
    private var byteTestHandle: HidOutHandle? = null
    private var byteTestToast: Toast? = null

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            currentLanguage = getCurrentLanguageCode(),
            appVersion = getVersionName(),
            audioGain = audioController.uiState.value.audioGain,
            usageAccessGranted = hasUsageAccessPermission(),
            recordAudioGranted = hasRecordAudioPermission(),
            notificationsGranted = hasNotificationPermission(),
            bluetoothConnectGranted = hasBluetoothConnectPermission(),
            byteTestUnlocked = preferences.getBoolean(KEY_BYTE_TEST_UNLOCKED, false),
            byteTestNotes = loadByteTestNotes(),
            byteTestSendValues = loadByteTestSendValues(),
            byteTestSequences = loadByteTestSequences()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private data class PendingByteSend(
        val index: Int,
        val value: Int
    )

    private data class HidOutHandle(
        val connection: UsbDeviceConnection,
        val usbInterface: UsbInterface,
        val outEndpoint: UsbEndpoint
    ) {
        fun close() {
            try {
                connection.releaseInterface(usbInterface)
            } catch (_: Throwable) {
            }

            try {
                connection.close()
            } catch (_: Throwable) {
            }
        }
    }

    private val byteTestPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_BYTE_TEST_USB_PERMISSION) return

            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            val pending = pendingByteSend
            pendingByteSend = null

            if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) || device == null || pending == null) {
                setByteTestLog("USB permission denied or byte send expired.")
                return
            }

            sendByteToDevice(device, pending)
        }
    }

    init {
        registerByteTestReceiver()
        combine(
            audioController.uiState,
            connectionRepository.uiState
        ) { audioState, connectionState ->
            val battery = if (connectionState.type == ControllerConnectionType.BLUETOOTH && connectionState.batteryLevel >= 0) {
                connectionState.batteryLevel
            } else {
                audioState.batteryLevel
            }

            val address = if (connectionState.type == ControllerConnectionType.BLUETOOTH && !connectionState.btAddress.isNullOrBlank()) {
                connectionState.btAddress
            } else {
                audioState.btAddress
            }

            _uiState.update { it.copy(
                audioGain = audioState.audioGain,
                controllerInfo = ControllerInfo(
                    isConnected = connectionState.isConnected,
                    connectionType = connectionState.type,
                    deviceName = connectionState.deviceName,
                    isWired = connectionState.type == ControllerConnectionType.USB,
                    batteryLevel = battery,
                    serialNumber = audioState.serialNumber,
                    btAddress = address,
                    firmwareVersion = audioState.firmwareVersion,
                    firmwareType = audioState.firmwareType,
                    softwareSeries = audioState.softwareSeries,
                    hardwareInfo = audioState.hardwareInfo,
                    updateVersion = audioState.updateVersion,
                    buildDate = audioState.buildDate,
                    buildTime = audioState.buildTime,
                    deviceInfo = audioState.deviceInfo,
                    controllerColor = audioState.controllerColor
                )
            ) }
        }.launchIn(viewModelScope)
    }

    private fun getCurrentLanguageCode(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        val tag = if (locales.isEmpty) {
            Locale.getDefault().toLanguageTag()
        } else {
            locales.get(0)?.toLanguageTag()
        }
        return when {
            tag.isNullOrBlank() -> "en"
            SUPPORTED_LANGUAGE_TAGS.contains(tag) -> tag
            tag.startsWith("hu", ignoreCase = true) -> "hu"
            tag.startsWith("es", ignoreCase = true) -> "es"
            tag.startsWith("de", ignoreCase = true) -> "de"
            tag.startsWith("fr", ignoreCase = true) -> "fr"
            tag.startsWith("pt", ignoreCase = true) -> "pt-BR"
            tag.startsWith("ja", ignoreCase = true) -> "ja"
            else -> "en"
        }
    }

    private fun getVersionName(): String {
        return try {
            val context = getApplication<Application>()
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    fun setLanguage(languageCode: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
        
        // Frissítjük a lokális állapotot, hogy a gombokon látszódjon a kijelölés
        _uiState.update { it.copy(currentLanguage = languageCode) }
    }

    fun setAudioGain(gain: Float) {
        audioController.setAudioGain(gain)
    }

    fun refreshControllerInfo() {
        connectionRepository.refresh()
    }

    fun setShowLogWindows(show: Boolean) {
        _uiState.update { it.copy(showLogWindows = show) }
    }

    fun refreshPermissionStatuses() {
        _uiState.update {
            it.copy(
                usageAccessGranted = hasUsageAccessPermission(),
                recordAudioGranted = hasRecordAudioPermission(),
                notificationsGranted = hasNotificationPermission(),
                bluetoothConnectGranted = hasBluetoothConnectPermission()
            )
        }
    }

    fun onVersionClicked() {
        val current = _uiState.value
        val nextCount = (current.byteTestUnlockTapCount + 1).coerceAtMost(BYTE_TEST_UNLOCK_TAPS)
        val remaining = BYTE_TEST_UNLOCK_TAPS - nextCount

        if (remaining > 0) {
            val action = if (current.byteTestUnlocked) {
                "elrejtéséig"
            } else {
                "feloldásáig"
            }
            showByteTestToast("Még $remaining érintés van hátra a Byte Test képernyő $action.")
            _uiState.update {
                it.copy(byteTestUnlockTapCount = nextCount)
            }
            return
        }

        val unlocked = !current.byteTestUnlocked
        preferences.edit()
            .putBoolean(KEY_BYTE_TEST_UNLOCKED, unlocked)
            .apply()

        _uiState.update {
            it.copy(
                byteTestUnlockTapCount = 0,
                byteTestUnlocked = unlocked
            )
        }

        if (unlocked) {
            showByteTestToast("Byte Test képernyő sikeresen feloldva. $BYTE_TEST_UNLOCK_TAPS érintés kell még az elrejtésig.")
        } else {
            showByteTestToast("Byte Test képernyő elrejtve. $BYTE_TEST_UNLOCK_TAPS érintés kell még a feloldásig.")
        }
    }

    fun updateByteTestNote(id: String, note: String) {
        preferences.edit()
            .putString(byteNoteKey(id), note)
            .apply()

        _uiState.update { current ->
            current.copy(
                byteTestNotes = current.byteTestNotes.toMutableMap().apply {
                    put(id, note)
                }
            )
        }
    }

    fun updateByteTestSendValue(id: String, value: String) {
        preferences.edit()
            .putString(byteSendValueKey(id), value)
            .apply()

        _uiState.update { current ->
            current.copy(
                byteTestSendValues = current.byteTestSendValues.toMutableMap().apply {
                    put(id, value)
                }
            )
        }
    }

    fun sendByteTestValue(id: String, index: Int, rawValue: String) {
        if (!_uiState.value.byteTestUnlocked) {
            setByteTestLog("Byte Test is locked.")
            return
        }

        if (index !in 0 until BYTE_TEST_REPORT_SIZE) {
            setByteTestLog("Invalid byte index: $index")
            return
        }

        val value = parseByteValue(rawValue)

        if (value == null) {
            setByteTestLog("Invalid byte value for [$index]: $rawValue")
            return
        }

        updateByteTestSendValue(id, rawValue)

        val pending = PendingByteSend(index = index, value = value)
        val device = findDualSenseDevice()

        if (device == null) {
            closeByteTestHandle()
            setByteTestLog("No USB DualSense connected.")
            return
        }

        if (!usbManager.hasPermission(device)) {
            pendingByteSend = pending
            requestByteTestPermission(device)
            setByteTestLog("USB permission requested for Byte Test.")
            return
        }

        sendByteToDevice(device, pending)
    }

    fun addByteTestSequence(
        name: String,
        mode: ByteTestSequenceMode,
        commands: List<ByteTestCommand>
    ) {
        val fixedName = name.trim()
        if (fixedName.isBlank()) {
            setByteTestLog("Sequence name is empty.")
            return
        }

        if (commands.isEmpty()) {
            setByteTestLog("Sequence has no commands.")
            return
        }

        val normalizedCommands = commands.mapNotNull { command ->
            val value = parseByteValue(command.value) ?: return@mapNotNull null
            if (command.index !in 0 until BYTE_TEST_REPORT_SIZE) return@mapNotNull null

            ByteTestCommand(
                index = command.index,
                value = value.toString(16).uppercase().padStart(2, '0')
            )
        }

        if (normalizedCommands.size != commands.size) {
            setByteTestLog("Sequence has invalid commands.")
            return
        }

        val sequence = ByteTestSequence(
            id = UUID.randomUUID().toString(),
            name = fixedName,
            mode = mode,
            commands = normalizedCommands
        )

        _uiState.update { current ->
            current.copy(byteTestSequences = current.byteTestSequences + sequence)
        }

        saveByteTestSequences(_uiState.value.byteTestSequences)
        setByteTestLog("Sequence saved: $fixedName")
    }

    fun deleteByteTestSequence(id: String) {
        val sequence = _uiState.value.byteTestSequences.firstOrNull { it.id == id } ?: return

        _uiState.update { current ->
            current.copy(byteTestSequences = current.byteTestSequences.filterNot { it.id == id })
        }

        saveByteTestSequences(_uiState.value.byteTestSequences)
        setByteTestLog("Sequence deleted: ${sequence.name}")
    }

    fun playByteTestSequence(id: String) {
        val sequence = _uiState.value.byteTestSequences.firstOrNull { it.id == id } ?: return
        if (_uiState.value.byteTestPlayingSequenceId != null) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(byteTestPlayingSequenceId = id) }

            try {
                val device = findDualSenseDevice()

                if (device == null) {
                    closeByteTestHandle()
                    setByteTestLog("No USB DualSense connected.")
                    return@launch
                }

                if (!usbManager.hasPermission(device)) {
                    requestByteTestPermission(device)
                    setByteTestLog("USB permission requested for sequence.")
                    return@launch
                }

                when (sequence.mode) {
                    ByteTestSequenceMode.SERIES -> {
                        sequence.commands.forEachIndexed { index, command ->
                            val value = parseByteValue(command.value)
                            if (value == null || command.index !in 0 until BYTE_TEST_REPORT_SIZE) {
                                setByteTestLog("Sequence stopped at ${index + 1}: invalid command.")
                                return@launch
                            }

                            val sent = sendByteToDevice(
                                device = device,
                                pending = PendingByteSend(index = command.index, value = value),
                                updateLog = false
                            )

                            if (!sent) {
                                setByteTestLog("Sequence failed at ${index + 1}: [${command.index}] = ${command.value}")
                                return@launch
                            }

                            delay(BYTE_TEST_SEQUENCE_DELAY_MS)
                        }
                    }

                    ByteTestSequenceMode.FULL_REPORT -> {
                        val report = ByteArray(BYTE_TEST_REPORT_SIZE)
                        report[0] = BYTE_TEST_OUTPUT_REPORT_ID.toByte()

                        sequence.commands.forEachIndexed { index, command ->
                            val value = parseByteValue(command.value)
                            if (value == null || command.index !in 0 until BYTE_TEST_REPORT_SIZE) {
                                setByteTestLog("Full report stopped at ${index + 1}: invalid command.")
                                return@launch
                            }
                            report[command.index] = value.toByte()
                        }

                        val sent = sendReportToDevice(
                            device = device,
                            report = report,
                            updateLog = false
                        )

                        if (!sent) {
                            setByteTestLog("Full report failed: ${sequence.name}")
                            return@launch
                        }
                    }
                }

                val modeLabel = if (sequence.mode == ByteTestSequenceMode.FULL_REPORT) {
                    "full report"
                } else {
                    "series"
                }
                setByteTestLog("Sequence played ($modeLabel): ${sequence.name}")
            } finally {
                _uiState.update { it.copy(byteTestPlayingSequenceId = null) }
            }
        }
    }

    private fun loadByteTestNotes(): Map<String, String> {
        return preferences.all
            .filterKeys { it.startsWith(KEY_BYTE_NOTE_PREFIX) }
            .mapNotNull { (key, value) ->
                val note = value as? String ?: return@mapNotNull null
                key.removePrefix(KEY_BYTE_NOTE_PREFIX) to note
            }
            .toMap()
    }

    private fun loadByteTestSendValues(): Map<String, String> {
        return preferences.all
            .filterKeys { it.startsWith(KEY_BYTE_SEND_VALUE_PREFIX) }
            .mapNotNull { (key, value) ->
                val sendValue = value as? String ?: return@mapNotNull null
                key.removePrefix(KEY_BYTE_SEND_VALUE_PREFIX) to sendValue
            }
            .toMap()
    }

    private fun loadByteTestSequences(): List<ByteTestSequence> {
        val raw = preferences.getString(KEY_BYTE_SEQUENCES, null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val commandsArray = item.getJSONArray("commands")
                    val commands = buildList {
                        for (c in 0 until commandsArray.length()) {
                            val command = commandsArray.getJSONObject(c)
                            add(
                                ByteTestCommand(
                                    index = command.getInt("index"),
                                    value = command.getString("value")
                                )
                            )
                        }
                    }

                    add(
                        ByteTestSequence(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            mode = runCatching {
                                ByteTestSequenceMode.valueOf(item.getString("mode"))
                            }.getOrDefault(ByteTestSequenceMode.SERIES),
                            commands = commands
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun saveByteTestSequences(sequences: List<ByteTestSequence>) {
        val array = JSONArray()

        sequences.forEach { sequence ->
            val commandsArray = JSONArray()
            sequence.commands.forEach { command ->
                commandsArray.put(
                    JSONObject()
                        .put("index", command.index)
                        .put("value", command.value)
                )
            }

            array.put(
                JSONObject()
                    .put("id", sequence.id)
                    .put("name", sequence.name)
                    .put("mode", sequence.mode.name)
                    .put("commands", commandsArray)
            )
        }

        preferences.edit()
            .putString(KEY_BYTE_SEQUENCES, array.toString())
            .apply()
    }

    private fun sendByteToDevice(
        device: UsbDevice,
        pending: PendingByteSend,
        updateLog: Boolean = true
    ): Boolean {
        val report = ByteArray(BYTE_TEST_REPORT_SIZE)
        report[0] = BYTE_TEST_OUTPUT_REPORT_ID.toByte()
        report[pending.index] = pending.value.toByte()
        return sendReportToDevice(device, report, pending.index, pending.value, updateLog)
    }

    private fun sendReportToDevice(
        device: UsbDevice,
        report: ByteArray,
        changedIndex: Int? = null,
        changedValue: Int? = null,
        updateLog: Boolean = true
    ): Boolean {
        if (!hidManager.refreshConnection()) {
            if (updateLog) {
                setByteTestLog(hidManager.state.value.logText)
            }
            return false
        }

        val ok = hidManager.send(report, 1000)

        if (ok) {
            if (updateLog) {
                if (changedIndex != null && changedValue != null) {
                    val hexValue = "0x" + changedValue.toString(16).uppercase().padStart(2, '0')
                    setByteTestLog("Sent [$changedIndex] = $hexValue (${report.size} bytes)")
                } else {
                    setByteTestLog("Sent full report (${report.size} bytes)")
                }
            }
            return true
        } else {
            if (updateLog) {
                setByteTestLog("Send failed. ${hidManager.state.value.logText}")
            }
            return false
        }
    }

    private fun reopenByteTestHandle(device: UsbDevice): HidOutHandle? {
        closeByteTestHandle()
        byteTestHandle = openByteTestHandle(device)
        return byteTestHandle
    }

    private fun openByteTestHandle(device: UsbDevice): HidOutHandle? {
        if (!usbManager.hasPermission(device)) return null

        val connection = try {
            usbManager.openDevice(device)
        } catch (_: SecurityException) {
            return null
        } ?: return null

        try {
            val targetInterface = findHidInterfaceWithOutEndpoint(device) ?: run {
                connection.close()
                return null
            }

            if (!connection.claimInterface(targetInterface, true)) {
                connection.close()
                return null
            }

            val outEndpoint = findOutEndpoint(targetInterface) ?: run {
                connection.releaseInterface(targetInterface)
                connection.close()
                return null
            }

            return HidOutHandle(
                connection = connection,
                usbInterface = targetInterface,
                outEndpoint = outEndpoint
            )
        } catch (_: Throwable) {
            try {
                connection.close()
            } catch (_: Throwable) {
            }
            return null
        }
    }

    private fun findDualSenseDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull {
            it.vendorId == SONY_VENDOR_ID && it.productId == DUALSENSE_PRODUCT_ID
        }
    }

    private fun findHidInterfaceWithOutEndpoint(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (
                usbInterface.interfaceClass == UsbConstants.USB_CLASS_HID &&
                findOutEndpoint(usbInterface) != null
            ) {
                return usbInterface
            }
        }
        return null
    }

    private fun findOutEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                return endpoint
            }
        }
        return null
    }

    private fun requestByteTestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }

        val permissionIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(ACTION_BYTE_TEST_USB_PERMISSION),
            flags
        )

        usbManager.requestPermission(device, permissionIntent)
    }

    private fun registerByteTestReceiver() {
        if (byteTestReceiverRegistered) return

        ContextCompat.registerReceiver(
            appContext,
            byteTestPermissionReceiver,
            IntentFilter(ACTION_BYTE_TEST_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        byteTestReceiverRegistered = true
    }

    private fun unregisterByteTestReceiver() {
        if (!byteTestReceiverRegistered) return

        try {
            appContext.unregisterReceiver(byteTestPermissionReceiver)
        } catch (_: Throwable) {
        }

        byteTestReceiverRegistered = false
    }

    private fun closeByteTestHandle() {
        byteTestHandle?.close()
        byteTestHandle = null
    }

    private fun setByteTestLog(text: String) {
        _uiState.update { it.copy(byteTestSendLog = text) }
    }

    private fun showByteTestToast(message: String) {
        byteTestToast?.cancel()
        byteTestToast = Toast.makeText(appContext, message, Toast.LENGTH_SHORT).also {
            it.show()
        }
    }

    private fun parseByteValue(rawValue: String): Int? {
        val normalized = rawValue.trim()
            .removePrefix("0x")
            .removePrefix("0X")

        if (normalized.isBlank()) return null

        return normalized.toIntOrNull(16)?.takeIf { it in 0..0xFF }
    }

    private fun byteNoteKey(id: String): String = KEY_BYTE_NOTE_PREFIX + id
    private fun byteSendValueKey(id: String): String = KEY_BYTE_SEND_VALUE_PREFIX + id

    private fun hasUsageAccessPermission(): Boolean {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        }

        if (mode != AppOpsManager.MODE_ALLOWED) {
            return false
        }

        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 60_000L,
            now
        )
        return !stats.isNullOrEmpty()
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    override fun onCleared() {
        closeByteTestHandle()
        unregisterByteTestReceiver()
        super.onCleared()
    }

    companion object {
        private const val ACTION_BYTE_TEST_USB_PERMISSION =
            "com.DueBoysenberry1226.ps5ctbro.BYTE_TEST_USB_PERMISSION"
        private const val PREFERENCES_NAME = "settings"
        private const val KEY_BYTE_TEST_UNLOCKED = "byte_test_unlocked"
        private const val KEY_BYTE_NOTE_PREFIX = "byte_note_"
        private const val KEY_BYTE_SEND_VALUE_PREFIX = "byte_send_value_"
        private const val KEY_BYTE_SEQUENCES = "byte_sequences"
        private const val BYTE_TEST_UNLOCK_TAPS = 7
        private const val SONY_VENDOR_ID = 0x054c
        private const val DUALSENSE_PRODUCT_ID = 0x0ce6
        private const val BYTE_TEST_REPORT_SIZE = 63
        private const val BYTE_TEST_OUTPUT_REPORT_ID = 0x02
        private const val BYTE_TEST_SEQUENCE_DELAY_MS = 40L
    }
}
