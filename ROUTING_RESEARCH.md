# Voice routing feasibility review

**Reviewed:** 2026-10-19  
**Project:** Girl Voice Changer  
**Status:** WhatsApp live-call microphone replacement is not implemented or verified.

## Current project baseline

The app currently records microphone PCM, plays recordings, exports WAV, and offers an experimental in-app pitch-up monitor. Its simple resampling effect changes pitch and duration/timbre; it is not a formant-preserving female voice-conversion engine. The app does not currently route its processed samples into WhatsApp.

## MicUp research

- Upstream: https://github.com/papergray/MicUp
- License: MIT, in https://github.com/papergray/MicUp/blob/main/LICENSE. Reused code must retain the copyright and permission notice. Each dependency, native plugin, and voice model needs a separate license review.
- Latest GitHub release currently listed: [v1.0.6](https://github.com/papergray/MicUp/releases/tag/v1.0.6), published 2026-08-11.
- Its source is a larger system, not a standalone virtual-microphone helper: it combines an Oboe/C++ audio engine, Shizuku API/provider, services, routing-tier selection, and multiple fallback paths. The project has native C++/CMake code and more dependencies than this app's single Kotlin Activity.
- The source exposes VoIP-compatible, software-loopback, Shizuku/ALSA, and root/Magisk paths. Its Shizuku path probes `/proc/asound/cards` for an actual ALSA loopback device because a local monitor can work even when other apps still receive unprocessed audio.
- The tier labels and routing code are tightly coupled. `SoftwareLoopback.kt` is part of the engine's processed-audio monitor pipeline; copying that file alone would not recreate the rest of the routing implementation.
- Source-level version policy deserves validation before any port: `VirtualMicService.kt` automatically selects its “Software Loopback” tier at API 34+, while its tier-availability check uses a different API threshold. We should not assume that tier selection means that WhatsApp actually receives the processed stream.

## Compatibility evidence

[MicUp issue #2](https://github.com/papergray/MicUp/issues/2) is still open as of this review. The report history includes:
- an Infinix Note 30 / MediaTek MT6789 report of Shizuku/root routing failures;
- a 2026-05-02 report that v1.0.6 still did not inject processed voice into calls on that device, despite local monitoring working; and
- a 2026-10-07 report from a non-rooted Android 16 device where trying MicUp's available modes still did not route the selected Bluetooth microphone into Discord.

These are reports from particular setups, not proof that MicUp never works. They are enough to show that its README claims are not a compatibility guarantee for every phone or WhatsApp.

## Engineering decision

Do **not** copy MicUp's routing code into this prototype yet. The current app has no Oboe/NDK/CMake or Shizuku integration, and a proper port would need its engine, service lifecycle, permissions, route selection, native build configuration, license notices, and device-specific fallbacks. A successful cloud build would still not prove that WhatsApp receives the transformed audio.

The safe next step is a real-phone feasibility test before deciding whether to port MicUp's architecture or investigate a device-specific alternative.

## Short phone test

1. Note the phone model and Android version. Install the current Girl Voice Changer APK from the [latest successful Actions run](https://github.com/malaramofficial/Girl-Voice-Changer/actions).
2. Separately install MicUp only from its [official v1.0.6 release page](https://github.com/papergray/MicUp/releases/tag/v1.0.6). Try its available software/VoIP route first. If testing its Shizuku route, start Shizuku using Wireless Debugging (Android 11+) and grant MicUp permission.
3. Turn **Monitor Output off** while testing routing; MicUp's maintainer has discussed audio-resource conflicts when monitoring and call capture compete.
4. Make a WhatsApp call to a second phone. Ask the other caller whether the received voice is processed or still raw. Hearing processed audio locally in a monitor is not proof. Stop if call audio becomes silent, distorted, or unstable.
5. Record the phone model, Android version, selected MicUp tier, whether Shizuku was used, and the other caller's result. If it fails, do not blindly port the routing code; that phone needs further device-specific investigation.

Until a real call test passes, WhatsApp live-call routing remains **not implemented and unverified** in Girl Voice Changer.
