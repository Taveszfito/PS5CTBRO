# PS5CTBRO

An experimental Android app for testing and interacting with the PlayStation DualSense controller.

This project started as a simple experiment to see how much of the DualSense can actually be used on Android — even without official support.

---

## ✨ Features

- 🎮 Adaptive Trigger control  
- 🔊 Controller speaker support (live audio via custom pipeline)  
- 🎚 Dedicated app volume control (separate from system media)  
- 📳 Haptics / vibration testing (standalone + audio reactive)  
- 🌈 Light bar + player LEDs control (including audio-reactive "Disco Mode")  
- 🎯 Full input test (buttons, sticks, triggers)  
- 👆 Touchpad input with visual feedback  

---

## ⚙️ Audio

The app uses a custom audio routing approach:

- System audio is captured internally  
- Media volume is muted to avoid playback on the phone speaker  
- Audio is routed directly to the controller speaker  
- A dedicated in-app volume slider controls playback  

This enables controller-only audio playback, despite Android not supporting the controller as a native audio output device.

---

## ⚠️ Notes

Most of these features are not natively supported on Android, so the app relies on custom / workaround solutions.

Because of this:
- behavior may vary between devices  
- latency or glitches can occur  
- some features might not work on all Android versions  

---

## 🧪 Status

Early / experimental.

Things may break, behave inconsistently, or change frequently.

---

## 💬 Feedback

Any feedback is welcome.

If something doesn’t work as expected, or you notice weird behavior, feel free to open an issue or share your experience.

---

## 📌 Why this exists

There are very few tools that properly explore the full capabilities of the DualSense on Android.

This app is an attempt to experiment, learn, and push those limits further.
