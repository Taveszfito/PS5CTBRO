# PS5CTBRO

An experimental Android app for testing and interacting with the PlayStation DualSense controller.

This project started as a simple experiment to see how much of the DualSense can actually be used on Android — even without official support.

Over time, it evolved into a full testing tool covering most controller features including audio, triggers, haptics, motion sensors, and more.

---

## ✨ Features

### 🎮 Input & Interaction
- Full input test (buttons, sticks, triggers)
- Touchpad input with visual feedback
- Bluetooth support for basic testing (input + vibration)

### 🎯 Adaptive Triggers
- Precise resistance and vibration control
- Improved accuracy and consistency (v2.0)

### 🔊 Audio (Controller Speaker)
- Live system audio routed to the controller
- Custom audio pipeline (not natively supported on Android)
- Controller-only playback (phone speaker muted)

### 🎚 Volume Control
- Dedicated in-app volume slider
- Integrated into Android volume panel
- Independent from system media volume

### 📳 Haptics / Vibration
- Standalone vibration testing
- Audio-reactive vibration support

### 🌈 LED Control
- Light bar + player LEDs control
- Audio-reactive “Disco Mode”

### 🌀 Motion Sensors
- Real-time gyroscope test
- Live motion visualization

### ℹ️ Controller Info
- Dedicated controller information screen
- Displays available controller-related data

---

## ⚙️ Audio System

The app uses a custom audio routing approach:

- System audio is captured internally
- Media volume is muted to avoid playback on the phone speaker
- Audio is streamed directly to the controller speaker
- A dedicated in-app volume slider controls playback

This enables controller-only audio playback, even though Android does not support the DualSense as a native audio output device.

---

## 🔌 Connection Modes

### USB (Recommended)
- Full feature support
- Required for:
  - Audio streaming
  - Adaptive triggers
  - LED control

### Bluetooth
- Limited support
- Available features:
  - Input test
  - Vibration test

---

## ⚠️ Notes

Most features are not officially supported on Android.

The app relies on custom communication and workaround solutions.

Because of this:
- behavior may vary between devices
- latency or glitches can occur
- some features may not work on all Android versions
- controller firmware differences can affect behavior

---

## 🧪 Status

Experimental.

This project pushes hardware beyond typical Android support limits, so instability and inconsistencies are expected.

---

## 💬 Feedback

Any feedback is welcome.

If something doesn’t work as expected or behaves strangely, feel free to open an issue or share your experience.

---

## 📌 Why this exists

There are very few tools that explore the full capabilities of the DualSense on Android.

This app exists to:
- experiment with low-level controller features
- understand hardware behavior
- push beyond official limitations
- provide a usable testing environment for developers and enthusiasts
