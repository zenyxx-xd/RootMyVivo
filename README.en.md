# RootMyVivo

Root for vivo and iQOO phones with a locked bootloader. No unlocking, no flashing — install the APK, press the button, wait.

Built on GhostLock (CVE-2026-43499, a use-after-free in futex PI) plus KernelSU installed on top of the temporary root.

[Русский](README.md) · [中文](README.zh.md)

## How it goes

1. Install the [APK from releases](https://github.com/zenyxx-xd/RootMyVivo/releases)
2. Start Shizuku (once, for shell access) or bring up ADB over TCP
3. Press "Get ROOT" — the app picks the payload for your kernel, downloads it and runs the exploit
4. Once rooted, KernelSU gets installed (four managers to choose from); root lives until reboot
5. Rebooted? Press the button again, or enable auto-restore in settings and the exploit re-runs by itself

Yes, kernel panics happen. That's normal: the phone reboots and you try again. A timing lottery, not a brick.

## Features

- Device, kernel version and KMI detection without root
- Payload matching against the full kernel string (one model can ship different kernel builds — they differ)
- Live exploit log inside the app, run history
- Root restore after phone reboot (background exploit re-run)
- "Restart exploit" while root is alive — for when root works but KernelSU didn't install
- In-app updates: check after every run, a card with a button, download with progress, system installer
- "Supported devices" screen — the whole payload catalog, what's available for your model

## Supported devices

The full, always-fresh list lives in [RootMyVivo-Payloads](https://github.com/zenyxx-xd/RootMyVivo-Payloads) — the app pulls it on every launch.

Verified on real hardware:

| Device | Kernel | Status |
|---|---|---|
| iQOO Neo 11 (PD2520) | 6.6.89 / 6.6.127 | verified by the author |
| iQOO Z10 Turbo Pro (PD2453) | 6.6.89 | working for users |

Built ports (need on-device testing): iQOO 13 (India), iQOO Neo10 Pro, vivo X200, vivo X200 Pro.

Your kernel must be without the CVE-2026-43499 fix: for 6.6 that's everything below 6.6.140, for 6.1 below 6.1.145.

## Transport

First run: Shizuku in wireless ADB mode (pairing, no PC needed). After the first root the app pins ADB over TCP on port 5555 and Shizuku is no longer required.

## Building

```sh
./gradlew :app:assembleRelease
```

JDK 17+, SDK 35. Sign with your own key — no keystores in this repo.

## Related repos

- [RootMyVivo-Payloads](https://github.com/zenyxx-xd/RootMyVivo-Payloads) — payload catalog and binaries
- [RootMyVivo-Exploit](https://github.com/zenyxx-xd/RootMyVivo-Exploit) — exploit sources

## Disclaimer

Your devices only. The author is not responsible for bricks, data loss or warranty drama. Use at your own risk.
