# Ray-Ban Meta Extended

Android companion app for Ray-Ban Meta / Meta AI glasses. The first feature is remote photo capture: place the glasses somewhere, use the phone as the trigger, and save the glasses photo back to the phone.

This helps with hands-free selfies and shots where pressing the glasses capture button would move the frame.

## What It Does

1. Connects to paired Ray-Ban Meta glasses through Meta's Wearables Device Access Toolkit.
2. Registers the app with Meta AI in Developer Mode.
3. Starts a glasses camera stream session.
4. Captures a photo from the phone.
5. Saves the JPEG to Android Photos through MediaStore.

Saved photos go here:

```text
Pictures/Ray-Ban Meta Extended
```

## Requirements

- Android phone. Tested on Pixel 7a.
- Ray-Ban Meta glasses paired and connected in the Meta AI app.
- Meta AI Developer Mode enabled.
- Android debugging enabled for installing from source with `adb`.
- GitHub personal access token with `read:packages` so Gradle can download Meta DAT artifacts from GitHub Packages.

## Setup

Create your local Gradle secrets file:

```bash
cp local.properties.example local.properties
```

Edit `local.properties` and set your GitHub package token:

```properties
github_token=YOUR_TOKEN_HERE
```

`local.properties` is ignored by Git and must not be committed.

## Build And Install

From the project root:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If Android prompts for USB debugging or install permission, allow it.

## Run Workflow

1. Open **Ray-Ban Meta Extended** on the phone.
2. Grant Android Bluetooth and camera permissions.
3. Tap **Register**.
4. Complete the Meta AI registration screen.
5. Return to the app.
6. Tap **Allow Camera** to grant glasses camera permission.
7. Tap **Start Preview**.
8. Wait for `Stream: STREAMING`.
9. Tap **Capture Photo**.
10. Open Photos or a file manager and check `Pictures/Ray-Ban Meta Extended`.

## Tools And Stack

- Android app written in Kotlin.
- Jetpack Compose and Material 3 for the UI.
- Gradle Kotlin DSL for builds.
- Meta Wearables Device Access Toolkit:
  - `mwdat-core`
  - `mwdat-camera`
- Android MediaStore for saving photos.
- AndroidX ExifInterface for orientation correction.
- `adb` for local debug installation.

## Project Layout

```text
app/src/main/java/com/apurv/metaremotecapture/
  MainActivity.kt        App lifecycle, DAT registration, session, capture, and saving
  camera/               Preview frame conversion helpers
  model/                UI state
  ui/                   Compose screen and controls
```

## Current State

- Remote capture works after the DAT stream is running.
- The capture path is verified on Pixel 7a with Ray-Ban Meta glasses.
- The preview surface is present, but capture is the primary verified workflow.
- The app uses `APPLICATION_ID=0`, which is for Meta AI Developer Mode testing.

## Privacy And Repository Safety

- No real GitHub token is committed.
- `local.properties` is ignored and stays on your machine.
- Build output, APKs, Gradle caches, and IDE files are ignored.
- The committed `local.properties.example` contains only placeholders.

## Troubleshooting

- If registration opens Meta AI and fails, confirm Meta AI Developer Mode is enabled.
- If no device appears, confirm the glasses are connected in Meta AI first.
- If capture says preview must be started, tap **Start Preview** and wait for `Stream: STREAMING`.
- If Gradle cannot download DAT packages, confirm `github_token` exists in `local.properties` and has `read:packages`.

## Distribution

This project is configured for local Developer Mode testing. For non-developer distribution, create an app in Meta Wearables Developer Center and replace the developer-mode application ID/client token values in the Android config.
