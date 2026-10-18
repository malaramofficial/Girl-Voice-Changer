# Girl Voice Changer (Android prototype)

A minimal Kotlin Android app that records microphone audio and lets you play it back, including a deliberately simple higher-pitch demonstration effect.

## Important limitations

- This prototype is **not** a natural-sounding female voice conversion model. Its demo effect plays audio at a higher sample rate, which raises pitch **and makes playback shorter/faster**.
- It does **not** inject audio into WhatsApp or any other app's live-call microphone.
- On ordinary, unrooted Android, a third-party app generally cannot replace WhatsApp's microphone input with processed audio. Do not treat live-call support as implemented.
- Recordings are held in memory only and are lost when the app process is closed.

## Build APK with GitHub Actions

1. Open **Actions** and enable workflows if GitHub asks.
2. Run **Build Android APK** using **Run workflow**, or push a commit.
3. Open the successful workflow run and download the `girl-voice-changer-debug-apk` artifact.

The workflow installs Gradle 8.9 and Java 17, builds `assembleDebug`, and uploads the APK artifact.

## Next engineering steps

1. Confirm this prototype builds and runs on the OPPO CPH2869.
2. Replace the naive playback-rate demo with a properly licensed pitch/formant-preserving DSP or neural voice-conversion engine.
3. Test conversion quality and latency separately from WhatsApp integration.
4. Keep WhatsApp live-call routing marked unsupported unless a safe, unrooted, device-compatible method is demonstrated.

## License notes

The initial code in this starter project is newly written for this project. No third-party source code or model weights are copied into it. Review any future library/model license before adding it.
