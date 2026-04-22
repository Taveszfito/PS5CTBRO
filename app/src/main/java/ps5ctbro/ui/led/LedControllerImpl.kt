package com.DueBoysenberry1226.ps5ctbro.ui.led

import com.DueBoysenberry1226.ps5ctbro.audio.AudioControllerImpl
import android.app.PendingIntent
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
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.DueBoysenberry1226.ps5ctbro.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class LedControllerImpl private constructor(
    context: Context
) : LedController {

    companion object {
        private const val ACTION_USB_PERMISSION =
            "com.DueBoysenberry1226.ps5ctbro.LED_USB_PERMISSION"

        private const val SONY_VENDOR_ID = 0x054c
        private const val MUTE_BUTTON_LED_INDEX = 9
        private const val VALID_FLAG1_MIC_MUTE_LED_CONTROL_ENABLE = 0x01
        private const val DUALSENSE_PRODUCT_ID = 0x0ce6
        private const val DUALSENSE_EDGE_PRODUCT_ID = 0x0df2

        private const val OUTPUT_REPORT_ID_USB = 0x02
        private const val OUTPUT_REPORT_SIZE_USB = 63

        private const val INPUT_BUFFER_SIZE = 64
        private const val INPUT_OFFSET_BUTTONS_2 = 10

        private const val VALID_FLAG1_INDEX = 2
        private const val VALID_FLAG2_INDEX = 39
        private const val LIGHTBAR_SETUP_INDEX = 42
        private const val LED_BRIGHTNESS_INDEX = 43
        private const val PLAYER_LEDS_INDEX = 44
        private const val LIGHTBAR_RED_INDEX = 45
        private const val LIGHTBAR_GREEN_INDEX = 46
        private const val LIGHTBAR_BLUE_INDEX = 47

        private const val VALID_FLAG1_LIGHTBAR_CONTROL_ENABLE = 0x04
        private const val VALID_FLAG1_PLAYER_INDICATOR_CONTROL_ENABLE = 0x10

        private const val VALID_FLAG2_LED_BRIGHTNESS_CONTROL_ENABLE = 0x01
        private const val VALID_FLAG2_LIGHTBAR_SETUP_CONTROL_ENABLE = 0x02

        private const val LIGHTBAR_SETUP_LIGHT_OUT = 0x01
        private const val LIGHTBAR_SETUP_LIGHT_ON = 0x02

        private const val PLAYER_LED_INSTANT_BIT = 0x20

        @Volatile
        private var INSTANCE: LedControllerImpl? = null

        fun getInstance(context: Context): LedControllerImpl {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LedControllerImpl(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    private val audioController = AudioControllerImpl.getInstance(appContext)

    @Volatile
    private var smoothedAudioLevel = 0f
    private var lastRawLevel = 0f
    private var musicHue = 0f
    private var musicSaturation = 1f
    private var flashIntensity = 0f
    private var lastBeatMs = 0L
    private var rollingMaxLevel = 0.1f

    private var midEnergy = 0f
    private var midPunch = 0f
    private var highEnergy = 0f
    private var colorShiftAccumulator = 0f

    private var currentPaletteIndex = 0
    private var lastPaletteSwitchMs = 0L

    private val colorPalettes = arrayOf(
        floatArrayOf(235f, 90f),  // Neon
        floatArrayOf(330f, 60f),  // Cyber
        floatArrayOf(180f, 70f),  // Ocean
        floatArrayOf(45f, 50f),   // Gold
        floatArrayOf(280f, 40f)   // Deep
    )

    private val _uiState = MutableStateFlow(
        LedUiState(
            logText = appContext.getString(R.string.led_log_ready)
        )
    )
    override val uiState: StateFlow<LedUiState> = _uiState

    private var receiverRegistered = false
    private var hidHandle: HidHandle? = null

    @Volatile
    private var effectLoopRunning = false
    private var effectThread: Thread? = null

    @Volatile
    private var inputReaderRunning = false
    private var inputReaderThread: Thread? = null

    @Volatile
    private var screenVisible = false

    @Volatile
    private var lastMicMuteButtonPressed = false

    private var lastSentSnapshot: LedSendSnapshot? = null

    private data class HidHandle(
        val connection: UsbDeviceConnection,
        val usbInterface: UsbInterface,
        val outEndpoint: UsbEndpoint,
        val inEndpoint: UsbEndpoint
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

    private data class LedSendSnapshot(
        val effect: LedEffect,
        val red: Int,
        val green: Int,
        val blue: Int,
        val lightbarBrightnessPercent: Int,
        val animationSpeedPercent: Int,
        val playerLedBrightnessRaw: Int,
        val playerLedMaskRaw: Int,
        val micLedEnabled: Boolean,
        val lightbarEnabled: Boolean
    )

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return

            val device: UsbDevice? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

            if (!granted || device == null) {
                setConnectionState(false, appContext.getString(R.string.log_usb_permission_denied))
                return
            }

            reopenHandle(device)

            if (hidHandle != null) {
                setConnectionState(true, appContext.getString(R.string.log_trigger_control_ready))
            } else {
                setConnectionState(false, appContext.getString(R.string.log_usb_open_failed))
            }
        }
    }

    init {
        registerReceiver()
    }

    override fun onScreenVisible() {
        screenVisible = true
        refreshConnection()
    }

    override fun onScreenHidden() {
        screenVisible = false
        stopEffectLoop()
        closeHandle()
        _uiState.update {
            it.copy(
                controllerConnected = false,
                logText = appContext.getString(R.string.log_led_screen_left)
            )
        }
    }

    override fun updateConfig(config: LedConfig) {
        val normalized = normalizeConfig(config)
        val previous = _uiState.value.config

        if (previous == normalized) return

        _uiState.update { current ->
            current.copy(config = normalized)
        }

        if (screenVisible) {
            applyConfig(normalized, triggeredByUserChange = true)
        }
    }

    override fun applyCurrentState() {
        applyConfig(_uiState.value.config, triggeredByUserChange = false)
    }

    override fun refreshConnection() {
        val device = findDualSenseDevice()
        if (device == null) {
            stopEffectLoop()
            closeHandle()
            lastSentSnapshot = null
            setConnectionState(false, appContext.getString(R.string.log_no_usb_dualsense))
            return
        }

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            setConnectionState(false, appContext.getString(R.string.log_dualsense_found_usb_permission))
            return
        }

        reopenHandle(device)

        if (hidHandle != null) {
            setConnectionState(true, appContext.getString(R.string.log_dualsense_connection_active))
        } else {
            setConnectionState(false, appContext.getString(R.string.log_hid_interface_not_found))
        }
    }

    override fun resetToDefault() {
        stopEffectLoop()

        val defaultConfig = LedConfig(
            effect = LedEffect.STATIC,
            color = LedColor(0, 114, 255),
            lightbarBrightnessPercent = 100,
            animationSpeedPercent = 50,
            playerLedBrightness = PlayerLedBrightness.HIGH,
            playerLedMask = 0b00100,
            micLedEnabled = false
        )

        lastSentSnapshot = null
        _uiState.update {
            it.copy(
                config = defaultConfig,
                logText = appContext.getString(R.string.log_led_settings_reset)
            )
        }

        if (screenVisible) {
            applyConfig(defaultConfig, triggeredByUserChange = true)
        }
    }

    override fun sendWakeKick() {
        val device = findDualSenseDevice()
        if (device == null) {
            setConnectionState(false, appContext.getString(R.string.log_no_usb_dualsense))
            return
        }

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            setConnectionState(false, appContext.getString(R.string.log_dualsense_found_usb_permission))
            return
        }

        if (hidHandle == null) {
            reopenHandle(device)
        }

        val handle = hidHandle
        if (handle == null) {
            setConnectionState(false, appContext.getString(R.string.log_hid_interface_not_found))
            return
        }

        stopEffectLoop()

        val wakeConfig = LedConfig(
            effect = LedEffect.STATIC,
            color = LedColor(0, 114, 255),
            lightbarBrightnessPercent = 100,
            animationSpeedPercent = 50,
            playerLedBrightness = PlayerLedBrightness.HIGH,
            playerLedMask = 0b00100,
            micLedEnabled = false
        )

        val report = buildLedReport(
            config = wakeConfig,
            lightbarColor = wakeConfig.color,
            lightbarEnabled = true
        )

        if (sendReport(handle, report)) {
            setConnectionState(true, appContext.getString(R.string.log_static_led_applied))
        } else {
            closeHandle()
            setConnectionState(false, appContext.getString(R.string.log_static_led_failed))
        }
    }

    override fun release() {
        stopEffectLoop()
        closeHandle()
        unregisterReceiver()
    }

    private fun applyConfig(
        config: LedConfig,
        triggeredByUserChange: Boolean
    ) {
        val normalized = normalizeConfig(config)

        val device = findDualSenseDevice()
        if (device == null) {
            stopEffectLoop()
            closeHandle()
            lastSentSnapshot = null
            setConnectionState(false, appContext.getString(R.string.log_no_usb_dualsense))
            return
        }

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            setConnectionState(
                false,
                appContext.getString(R.string.log_usb_permission_retry)
            )
            return
        }

        if (hidHandle == null) {
            reopenHandle(device)
        }

        val handle = hidHandle
        if (handle == null) {
            setConnectionState(false, appContext.getString(R.string.log_hid_interface_not_found))
            return
        }

        stopEffectLoop()

        when (normalized.effect) {
            LedEffect.OFF -> {
                val offColor = LedColor(0, 0, 0)
                val snapshot = snapshotOf(
                    config = normalized,
                    color = offColor,
                    lightbarEnabled = false
                )

                if (triggeredByUserChange && snapshot == lastSentSnapshot) {
                    return
                }

                val report = buildLedReport(
                    config = normalized,
                    lightbarColor = offColor,
                    lightbarEnabled = false
                )

                if (sendReport(handle, report)) {
                    lastSentSnapshot = snapshot
                    _uiState.update {
                        it.copy(
                            controllerConnected = true,
                            logText = appContext.getString(R.string.log_led_off)
                        )
                    }
                } else {
                    closeHandle()
                    setConnectionState(false, appContext.getString(R.string.log_led_off_failed))
                }
            }

            LedEffect.STATIC -> {
                val snapshot = snapshotOf(
                    config = normalized,
                    color = normalized.color,
                    lightbarEnabled = normalized.lightbarBrightnessPercent > 0
                )

                if (triggeredByUserChange && snapshot == lastSentSnapshot) {
                    return
                }

                val report = buildLedReport(
                    config = normalized,
                    lightbarColor = normalized.color,
                    lightbarEnabled = normalized.lightbarBrightnessPercent > 0
                )

                if (sendReport(handle, report)) {
                    lastSentSnapshot = snapshot
                    _uiState.update {
                        it.copy(
                            controllerConnected = true,
                            logText = appContext.getString(R.string.log_static_led_applied)
                        )
                    }
                } else {
                    closeHandle()
                    setConnectionState(false, appContext.getString(R.string.log_static_led_failed))
                }
            }

            LedEffect.BREATH,
            LedEffect.COLOR_CYCLE,
            LedEffect.MUSIC_REACTIVE -> {
                resetMusicReactiveState()
                startEffectLoop(normalized)
                _uiState.update {
                    it.copy(
                        controllerConnected = true,
                        logText = appContext.getString(
                            R.string.log_led_effect_running,
                            appContext.getString(normalized.effect.titleRes)
                        )
                    )
                }
            }
        }
    }

    private fun resetMusicReactiveState() {
        smoothedAudioLevel = 0f
        lastRawLevel = 0f
        musicHue = 0f
        musicSaturation = 1f
        flashIntensity = 0f
        lastBeatMs = 0L
        rollingMaxLevel = 0.1f
        midEnergy = 0f
        midPunch = 0f
        highEnergy = 0f
        colorShiftAccumulator = 0f
        currentPaletteIndex = 0
        lastPaletteSwitchMs = 0L
    }

    private fun startEffectLoop(config: LedConfig) {
        val handle = hidHandle ?: return
        if (effectLoopRunning) return

        effectLoopRunning = true

        effectThread = Thread {
            val startTime = SystemClock.elapsedRealtime()

            while (effectLoopRunning) {
                val elapsedMs: Long = SystemClock.elapsedRealtime() - startTime

                val color: LedColor = when (config.effect) {
                    LedEffect.BREATH -> calculateBreathColor(config, elapsedMs)
                    LedEffect.COLOR_CYCLE -> calculateCycleColor(config, elapsedMs)

                    LedEffect.MUSIC_REACTIVE -> {
                        val audioValue: Float = audioController.audioLevelFlow.value.coerceIn(0f, 1f)
                        val now: Long = SystemClock.elapsedRealtime()

                        rollingMaxLevel = maxOf(
                            rollingMaxLevel * 0.997f,
                            audioValue,
                            0.05f
                        )

                        val normalized: Float = (audioValue / rollingMaxLevel).coerceIn(0f, 1f)

                        val songIntensity: Float =
                            (rollingMaxLevel.coerceIn(0.05f, 0.4f) - 0.05f) / 0.35f
                        val isQuiet: Boolean = songIntensity < 0.35f

                        val delta: Float = (audioValue - lastRawLevel).coerceAtLeast(0f)
                        lastRawLevel = audioValue

                        val energyDecay: Float =
                            if (isQuiet) {
                                0.85f
                            } else {
                                0.70f
                            }

                        midEnergy = (midEnergy * energyDecay) + (normalized * (1f - energyDecay))
                        midPunch = (midPunch * 0.55f) + (delta * 6.0f)
                        highEnergy = (highEnergy * 0.40f) + (delta * 12.0f)

                        val decayRate: Float =
                            if (isQuiet) {
                                0.96f
                            } else {
                                0.90f
                            }

                        smoothedAudioLevel =
                            if (normalized > smoothedAudioLevel) {
                                (smoothedAudioLevel * 0.45f) + (normalized * 0.55f)
                            } else {
                                (smoothedAudioLevel * decayRate) + (normalized * (1f - decayRate))
                            }

                        val beatCooldownMs: Long =
                            if (isQuiet) {
                                170L
                            } else {
                                130L
                            }

                        val beatDeltaThreshold: Float =
                            if (isQuiet) {
                                0.020f
                            } else {
                                0.035f
                            }

                        val beatLevelThreshold: Float =
                            if (isQuiet) {
                                0.12f
                            } else {
                                0.20f
                            }

                        val isBeat: Boolean =
                            (delta > beatDeltaThreshold && normalized > beatLevelThreshold) &&
                                    (now - lastBeatMs > beatCooldownMs)

                        val timeSinceLastSwitch: Long = now - lastPaletteSwitchMs
                        val timeSinceLastBeat: Long = now - lastBeatMs
                        val isBreak: Boolean = timeSinceLastBeat > 450L && timeSinceLastBeat < 2000L

                        if ((isBreak && timeSinceLastSwitch > 3000L) || timeSinceLastSwitch > 15000L) {
                            currentPaletteIndex = (currentPaletteIndex + 1) % colorPalettes.size
                            lastPaletteSwitchMs = now
                        }

                        if (isBeat) {
                            lastBeatMs = now

                            val flashBoost: Float =
                                if (normalized > 0.72f || highEnergy > 1.05f) {
                                    1.15f
                                } else {
                                    0.90f
                                }

                            flashIntensity = flashBoost

                            val jump: Float =
                                if (normalized > 0.7f) {
                                    80f
                                } else if (normalized > 0.45f) {
                                    50f
                                } else {
                                    30f
                                }

                            colorShiftAccumulator += jump
                        } else {
                            val flashDecay: Float =
                                if (isQuiet) {
                                    0.94f
                                } else {
                                    0.82f
                                }

                            flashIntensity *= flashDecay

                            val hueDriftBase: Float = 0.5f + (midEnergy * 2.0f)
                            val hueDriftScale: Float =
                                if (isQuiet) {
                                    0.35f
                                } else {
                                    1.0f
                                }

                            colorShiftAccumulator += hueDriftBase * hueDriftScale
                        }

                        val palette: FloatArray = colorPalettes[currentPaletteIndex]
                        val baseHue: Float = palette[0]
                        val range: Float = palette[1]

                        val swingBase: Float =
                            sin(colorShiftAccumulator * 0.015f)

                        var currentHue: Float = baseHue + (swingBase * range)
                        if (currentHue < 0f) currentHue += 360f
                        if (currentHue >= 360f) currentHue %= 360f

                        if (flashIntensity > 0.95f && ((now / 140L) % 2L == 0L)) {
                            currentHue = (currentHue + 60f) % 360f
                        }

                        musicHue = currentHue

                        musicSaturation =
                            if (isQuiet) {
                                (0.86f + (smoothedAudioLevel * 0.14f)).coerceIn(0.82f, 1f)
                            } else {
                                (0.58f + (smoothedAudioLevel * 0.42f)).coerceIn(0.50f, 1f)
                            }

                        val baseIntensity: Float =
                            (
                                    (smoothedAudioLevel * 0.42f) +
                                            ((smoothedAudioLevel * smoothedAudioLevel) * 1.35f)
                                    ).coerceIn(0.10f, 2.35f)

                        val punchBoost: Float =
                            (midPunch * 0.10f).coerceIn(0f, 0.45f)

                        val transientBoost: Float =
                            (highEnergy * 0.05f).coerceIn(0f, 0.35f)

                        val finalIntensity: Float =
                            (baseIntensity + flashIntensity + punchBoost + transientBoost)
                                .coerceIn(0f, 3.2f)

                        val hsvColor: LedColor = hsvToRgb(musicHue, musicSaturation, 1f)

                        LedColor(
                            red = (hsvColor.red.toFloat() * finalIntensity).toInt().coerceIn(0, 255),
                            green = (hsvColor.green.toFloat() * finalIntensity).toInt().coerceIn(0, 255),
                            blue = (hsvColor.blue.toFloat() * finalIntensity).toInt().coerceIn(0, 255)
                        )
                    }

                    LedEffect.STATIC -> config.color
                    LedEffect.OFF -> LedColor(0, 0, 0)
                }

                val lightbarEnabled: Boolean =
                    config.lightbarBrightnessPercent > 0 && config.effect != LedEffect.OFF
                val now: Long = SystemClock.elapsedRealtime()

                val playerMaskForMusic: Int =
                    if (config.effect == LedEffect.MUSIC_REACTIVE) {
                        var mask = 0

                        val songIntensity: Float =
                            (rollingMaxLevel.coerceIn(0.05f, 0.4f) - 0.05f) / 0.35f
                        val isQuiet: Boolean = songIntensity < 0.35f
                        val sens: Float = if (isQuiet) 0.50f else 1.0f

                        val centerThreshold: Float =
                            if (isQuiet) {
                                0.42f
                            } else {
                                0.88f
                            }

                        val beatHoldMs: Long =
                            if (isQuiet) {
                                220L
                            } else {
                                130L
                            }

                        if ((now - lastBeatMs) < beatHoldMs || smoothedAudioLevel > centerThreshold) {
                            mask = mask or 0b00100
                        }

                        if (midPunch > (0.72f * sens) || (isQuiet && midEnergy > 0.32f)) {
                            mask = mask or 0b01010
                        }

                        if (highEnergy > (0.82f * sens)) {
                            mask = mask or 0b10001
                        }

                        if (!isQuiet && (midPunch > 0.95f || highEnergy > 1.35f || flashIntensity > 0.95f)) {
                            mask = 0b11111
                        }

                        mask
                    } else {
                        config.playerLedMask
                    }

                val micLedForMusic: Boolean =
                    if (config.effect == LedEffect.MUSIC_REACTIVE) {
                        (highEnergy > 0.62f) || (((now - lastBeatMs) < 45L) && highEnergy > 0.38f)
                    } else {
                        config.micLedEnabled
                    }

                val playerBrightnessForMusic: Int =
                    if (config.effect == LedEffect.MUSIC_REACTIVE) {
                        when {
                            smoothedAudioLevel > 0.75f || flashIntensity > 0.95f -> 0
                            smoothedAudioLevel > 0.40f -> 1
                            else -> 2
                        }
                    } else {
                        config.playerLedBrightness.rawValue
                    }

                val snapshot = snapshotOf(
                    config = config,
                    color = color,
                    lightbarEnabled = lightbarEnabled,
                    playerLedMaskOverride = playerMaskForMusic,
                    playerLedBrightnessOverride = playerBrightnessForMusic,
                    micLedEnabledOverride = micLedForMusic
                )

                if (snapshot != lastSentSnapshot) {
                    val report = buildLedReport(
                        config = config,
                        lightbarColor = color,
                        lightbarEnabled = lightbarEnabled,
                        playerLedMaskOverride = playerMaskForMusic,
                        playerLedBrightnessOverride = playerBrightnessForMusic,
                        micLedEnabledOverride = micLedForMusic
                    )

                    val success: Boolean = sendReport(handle, report)
                    if (!success) {
                        _uiState.update {
                            it.copy(
                                controllerConnected = false,
                                logText = appContext.getString(R.string.log_led_effect_interrupted)
                            )
                        }
                        effectLoopRunning = false
                        closeHandle()
                        break
                    }

                    lastSentSnapshot = snapshot
                }

                try {
                    Thread.sleep(16L)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply {
            name = "DualSenseLedEffectLoop"
            isDaemon = true
            start()
        }
    }

    private fun stopEffectLoop() {
        effectLoopRunning = false
        effectThread?.interrupt()
        effectThread = null
    }

    private fun calculateBreathColor(config: LedConfig, elapsedMs: Long): LedColor {
        val speedFactor = 0.30f + (config.animationSpeedPercent / 100f) * 2.70f
        val phase = ((elapsedMs / 1000f) * speedFactor * 2f * PI.toFloat())
        val wave = ((sin(phase) + 1f) / 2f).coerceIn(0f, 1f)

        val minLevel = 0.08f
        val multiplier = minLevel + (1f - minLevel) * wave

        return LedColor(
            red = (config.color.red * multiplier).toInt().coerceIn(0, 255),
            green = (config.color.green * multiplier).toInt().coerceIn(0, 255),
            blue = (config.color.blue * multiplier).toInt().coerceIn(0, 255)
        )
    }

    private fun calculateCycleColor(config: LedConfig, elapsedMs: Long): LedColor {
        val speedFactor = 0.10f + (config.animationSpeedPercent / 100f) * 1.90f
        val hue = ((elapsedMs / 1000f) * 360f * speedFactor) % 360f
        return hsvToRgb(hue, 1f, 1f)
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float): LedColor {
        val c = v * s
        val x = v * s * (1f - abs(((h / 60f) % 2f) - 1f))
        val m = v - c

        val (rPrime, gPrime, bPrime) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return LedColor(
            red = ((rPrime + m) * 255f).toInt().coerceIn(0, 255),
            green = ((gPrime + m) * 255f).toInt().coerceIn(0, 255),
            blue = ((bPrime + m) * 255f).toInt().coerceIn(0, 255)
        )
    }

    private fun snapshotOf(
        config: LedConfig,
        color: LedColor,
        lightbarEnabled: Boolean,
        playerLedMaskOverride: Int? = null,
        playerLedBrightnessOverride: Int? = null,
        micLedEnabledOverride: Boolean? = null
    ): LedSendSnapshot {
        return LedSendSnapshot(
            effect = config.effect,
            red = color.red,
            green = color.green,
            blue = color.blue,
            lightbarBrightnessPercent = config.lightbarBrightnessPercent,
            animationSpeedPercent = config.animationSpeedPercent,
            playerLedBrightnessRaw = playerLedBrightnessOverride ?: config.playerLedBrightness.rawValue,
            playerLedMaskRaw = (playerLedMaskOverride ?: config.playerLedMask) and 0x1F,
            micLedEnabled = micLedEnabledOverride ?: config.micLedEnabled,
            lightbarEnabled = lightbarEnabled
        )
    }

    private fun normalizeConfig(config: LedConfig): LedConfig {
        return config.copy(
            color = LedColor(
                red = config.color.red.coerceIn(0, 255),
                green = config.color.green.coerceIn(0, 255),
                blue = config.color.blue.coerceIn(0, 255)
            ),
            lightbarBrightnessPercent = config.lightbarBrightnessPercent.coerceIn(0, 100),
            animationSpeedPercent = config.animationSpeedPercent.coerceIn(0, 100),
            playerLedMask = config.playerLedMask and 0x1F
        )
    }

    private fun buildLedReport(
        config: LedConfig,
        lightbarColor: LedColor,
        lightbarEnabled: Boolean,
        playerLedMaskOverride: Int? = null,
        playerLedBrightnessOverride: Int? = null,
        micLedEnabledOverride: Boolean? = null
    ): ByteArray {
        val report = ByteArray(OUTPUT_REPORT_SIZE_USB)

        report[0] = OUTPUT_REPORT_ID_USB.toByte()

        // Fix byte-ok a hangszóró és rezgés ébrentartásához (AudioController kompatibilitás)
        report[1] = 0xF3.toByte()

        report[VALID_FLAG1_INDEX] =
            (0x86 or
                    VALID_FLAG1_LIGHTBAR_CONTROL_ENABLE or
                    VALID_FLAG1_PLAYER_INDICATOR_CONTROL_ENABLE or
                    VALID_FLAG1_MIC_MUTE_LED_CONTROL_ENABLE).toByte()

        // Hangerő és egyebek fenntartása (0xFF)
        report[5] = 0xFF.toByte()
        report[6] = 0xFF.toByte()
        report[7] = 0xFF.toByte()
        report[8] = 0xFF.toByte()

        report[VALID_FLAG2_INDEX] =
            (0x03 or
                    VALID_FLAG2_LED_BRIGHTNESS_CONTROL_ENABLE or
                    VALID_FLAG2_LIGHTBAR_SETUP_CONTROL_ENABLE).toByte()

        report[LIGHTBAR_SETUP_INDEX] =
            if (lightbarEnabled) {
                LIGHTBAR_SETUP_LIGHT_ON.toByte()
            } else {
                LIGHTBAR_SETUP_LIGHT_OUT.toByte()
            }

        val micLedEnabled = micLedEnabledOverride ?: config.micLedEnabled
        val playerLedMask = (playerLedMaskOverride ?: config.playerLedMask) and 0x1F
        val playerBrightnessRaw = playerLedBrightnessOverride ?: config.playerLedBrightness.rawValue

        report[MUTE_BUTTON_LED_INDEX] =
            if (micLedEnabled) 1.toByte() else 0.toByte()

        report[LED_BRIGHTNESS_INDEX] = playerBrightnessRaw.toByte()
        report[PLAYER_LEDS_INDEX] =
            (playerLedMask or PLAYER_LED_INSTANT_BIT).toByte()

        // Fenntartjuk az AudioController által is használt flaget
        report[42] = (report[42].toInt() or 0x02).toByte()
        report[44] = (report[44].toInt() or 0x24).toByte()

        val scaledColor = scaleColor(lightbarColor, config.lightbarBrightnessPercent)

        report[LIGHTBAR_RED_INDEX] = scaledColor.red.toByte()
        report[LIGHTBAR_GREEN_INDEX] = scaledColor.green.toByte()
        report[LIGHTBAR_BLUE_INDEX] = scaledColor.blue.toByte()

        return report
    }

    private fun scaleColor(color: LedColor, brightnessPercent: Int): LedColor {
        val scale = brightnessPercent.coerceIn(0, 100) / 100f
        return LedColor(
            red = (color.red * scale).toInt().coerceIn(0, 255),
            green = (color.green * scale).toInt().coerceIn(0, 255),
            blue = (color.blue * scale).toInt().coerceIn(0, 255)
        )
    }

    private fun sendReport(
        handle: HidHandle,
        report: ByteArray
    ): Boolean {
        val sent = try {
            handle.connection.bulkTransfer(
                handle.outEndpoint,
                report,
                report.size,
                1000
            )
        } catch (_: SecurityException) {
            -1
        } catch (_: Throwable) {
            -1
        }

        return sent == report.size
    }

    private fun setConnectionState(
        connected: Boolean,
        log: String
    ) {
        _uiState.update {
            it.copy(
                controllerConnected = connected,
                logText = log
            )
        }
    }

    private fun findDualSenseDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { device ->
            device.vendorId == SONY_VENDOR_ID &&
                    (device.productId == DUALSENSE_PRODUCT_ID ||
                            device.productId == DUALSENSE_EDGE_PRODUCT_ID)
        }
    }

    private fun reopenHandle(device: UsbDevice) {
        closeHandle()
        hidHandle = openHandle(device)
        lastSentSnapshot = null
        lastMicMuteButtonPressed = false
        startInputReaderIfPossible()
    }

    private fun openHandle(device: UsbDevice): HidHandle? {
        if (!usbManager.hasPermission(device)) return null

        val connection = try {
            usbManager.openDevice(device)
        } catch (_: SecurityException) {
            return null
        } ?: return null

        try {
            val targetInterface = findHidInterfaceWithInAndOutEndpoint(device) ?: run {
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

            val inEndpoint = findInEndpoint(targetInterface) ?: run {
                connection.releaseInterface(targetInterface)
                connection.close()
                return null
            }

            return HidHandle(
                connection = connection,
                usbInterface = targetInterface,
                outEndpoint = outEndpoint,
                inEndpoint = inEndpoint
            )
        } catch (_: Throwable) {
            try {
                connection.close()
            } catch (_: Throwable) {
            }
            return null
        }
    }

    private fun findHidInterfaceWithInAndOutEndpoint(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_HID &&
                findOutEndpoint(usbInterface) != null &&
                findInEndpoint(usbInterface) != null
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

    private fun findInEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                return endpoint
            }
        }
        return null
    }

    private fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }

        val permissionIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(ACTION_USB_PERMISSION),
            flags
        )

        usbManager.requestPermission(device, permissionIntent)
    }

    private fun startInputReaderIfPossible() {
        val handle = hidHandle ?: return
        if (inputReaderRunning) return

        inputReaderRunning = true
        inputReaderThread = Thread {
            val buffer = ByteArray(INPUT_BUFFER_SIZE)

            while (inputReaderRunning) {
                val read = try {
                    handle.connection.bulkTransfer(
                        handle.inEndpoint,
                        buffer,
                        buffer.size,
                        250
                    )
                } catch (_: Throwable) {
                    -1
                }

                if (!inputReaderRunning) break

                if (read > INPUT_OFFSET_BUTTONS_2) {
                    parseInputReport(buffer, read)
                }
            }
        }.apply {
            name = "DualSenseLedInputReader"
            isDaemon = true
            start()
        }
    }

    private fun stopInputReader() {
        inputReaderRunning = false
        inputReaderThread?.interrupt()
        inputReaderThread = null
        lastMicMuteButtonPressed = false
    }

    private fun parseInputReport(buffer: ByteArray, size: Int) {
        if (size <= INPUT_OFFSET_BUTTONS_2) return

        val buttons2 = buffer[INPUT_OFFSET_BUTTONS_2].toUByte().toInt()
        val micMutePressed = (buttons2 and 0x04) != 0

        if (micMutePressed && !lastMicMuteButtonPressed) {
            val toggledConfig = _uiState.value.config.copy(
                micLedEnabled = !_uiState.value.config.micLedEnabled
            )

            _uiState.update { current ->
                current.copy(
                    config = toggledConfig,
                    logText = if (toggledConfig.micLedEnabled) {
                        appContext.getString(R.string.log_mic_led_on_from_controller)
                    } else {
                        appContext.getString(R.string.log_mic_led_off_from_controller)
                    }
                )
            }

            applyConfig(toggledConfig, triggeredByUserChange = false)
        }

        lastMicMuteButtonPressed = micMutePressed
    }

    private fun registerReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            appContext,
            permissionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return

        try {
            appContext.unregisterReceiver(permissionReceiver)
        } catch (_: Throwable) {
        }

        receiverRegistered = false
    }

    private fun closeHandle() {
        stopInputReader()
        hidHandle?.close()
        hidHandle = null
    }
}