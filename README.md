# Girl Voice Changer (Android prototype)

A Kotlin Android app for recording microphone audio, previewing the original or a simple higher-pitch demo, and exporting a WAV recording using Android's document picker.

## Implemented in this prototype

- Microphone recording with runtime permission.
- Original recording playback.
- A simple higher-pitch playback demo.
- Stop playback control.
- Export recording as mono 16-bit PCM WAV to a user-selected location.
- GitHub Actions debug APK build and artifact upload.

## Important limitations

- The pitch demo is **not** natural-sounding female voice conversion. It raises playback sample rate, so it also makes audio shorter/faster.
- A natural voice-conversion engine (licensed DSP or neural model), its model assets, performance tuning, and device testing are still required.
- It does **not** inject audio into WhatsApp or another app's live-call microphone.
- On ordinary, unrooted Android, third-party apps do not have a public API to replace another app's microphone input. A speaker/acoustic workaround would be unreliable and is not equivalent to true microphone injection.
- Recording is held in memory until the app closes; export it as WAV to keep a copy. Very long recordings can use substantial memory.

## Build APK with GitHub Actions

1. Open **Actions** and select **Build Android APK**.
2. Open the latest successful run.
3. Download the `girl-voice-changer-debug-apk` artifact.

The workflow uses Java 17 and Gradle 8.9, runs `assembleDebug`, and uploads the debug APK artifact.

## Next engineering steps

1. Install and test the APK on a real Android phone; verify recording, playback, WAV export, and playback cancellation.
2. Select and integrate a properly licensed pitch/formant-preserving DSP or neural voice-conversion engine; include model files only after checking license, size, latency, and device compatibility.
3. Benchmark audio quality, heat, battery, memory use, and latency on the target phone.
4. Keep WhatsApp live-call routing marked unsupported unless a safe, unrooted, device-compatible method is actually demonstrated. If true live-call injection is mandatory, it may require root/system-level audio changes or a different calling architecture; ordinary Play Store apps cannot promise this feature.

## License notes

Review the license of every future DSP library and model before shipping it. This starter does not include third-party model weights.


Latest prototype addition: the higher-pitch demo can also be exported as a separate WAV file. This effect raises pitch by playing the samples faster, so the result is shorter and is not natural AI voice conversion.
