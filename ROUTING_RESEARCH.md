# Voice routing feasibility review

**Reviewed:** 2026-10-19  
**Status:** WhatsApp live-call microphone replacement is not implemented or verified.

## Current project baseline

Girl Voice Changer records microphone PCM, plays recordings, exports WAV, and offers an experimental in-app pitch-up monitor. The resampling effect changes pitch and duration/timbre; it is not a formant-preserving female voice-conversion engine. The app does not route processed samples into WhatsApp.

## MicUp research

- Source: https://github.com/papergray/MicUp
- License: MIT, at https://github.com/papergray/MicUp/blob/main/LICENSE. Reused code must retain the copyright and permission notice. Dependencies, native plugins, and voice models need separate license review.
- Latest GitHub release currently listed: [v1.0.6](https://github.com/papergray/MicUp/releases/tag/v1.0.6), published 2026-08-11.
- MicUp is a larger system, not a drop-in virtual-mic helper: Oboe/C++ audio engine, Shizuku API/provider, background services, routing-tier selection, and fallback paths. It has native C++/CMake code and more dependencies than this app's single Kotlin Activity.
- It exposes VoIP-compatible, software-loopback, Shizuku/ALSA, and root/Magisk paths. The Shizuku path probes `/proc/asound/cards` for an actual ALSA loopback device because local monitoring can work even when another app still receives unprocessed audio.
- The routing logic is coupled to MicUp's engine and services. Copying only `SoftwareLoopback.kt` would not recreate the rest of the routing implementation.
- Source-level tier policy needs validation before a port: `VirtualMicService.kt` automatically selects its “Software Loopback” tier at API 34+, while its availability check uses a different API threshold. Tier selection is not proof that WhatsApp receives the processed stream.

## Compatibility evidence

[MicUp issue #2](https://github.com/papergray/MicUp/issues/2) remains open as of this review. Reports include an Infinix Note 30 / MediaTek MT6789 where Shizuku/root routing failed; a 2026-05-02 report that v1.0.6 still did not inject processed voice into calls despite local monitoring; and a 2026-10-07 report from non-root Android 16 where MicUp's available modes still did not route the selected Bluetooth input into Discord. These are device-specific reports, not proof that MicUp never works, but they rule out assuming universal compatibility.

## Engineering decision

Do **not** copy MicUp's routing code into this prototype yet. The current app has no Oboe/NDK/CMake or Shizuku integration. A proper port would need its engine, service lifecycle, permissions, route selection, native build configuration, license notices, and device-specific fallbacks. A successful cloud build cannot prove WhatsApp microphone routing.

## Short phone test

1. Note the phone model and Android version. Install the current Girl Voice Changer APK from the [Actions page](https://github.com/malaramofficial/Girl-Voice-Changer/actions).
2. Separately install MicUp only from its [official v1.0.6 release](https://github.com/papergray/MicUp/releases/tag/v1.0.6). Try its software/VoIP route first. If testing Shizuku, start it using Wireless Debugging (Android 11+) and grant MicUp permission.
3. Turn **Monitor Output off** while testing routing; MicUp's maintainer has discussed audio-resource conflicts when monitoring and call capture compete.
4. Make a WhatsApp call to a second phone. Ask the other caller whether the received voice is processed or still raw. Hearing processed audio locally in a monitor is not proof. Stop if call audio becomes silent, distorted, or unstable.
5. Record the phone model, Android version, selected MicUp tier, whether Shizuku was used, and what the other caller heard. If it fails, do not port the routing code blindly; investigate that device specifically.

Until a real call test passes, WhatsApp live-call routing remains **not implemented and unverified** in Girl Voice Changer.
