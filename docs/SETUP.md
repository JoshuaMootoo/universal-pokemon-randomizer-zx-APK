# Setup for Joshua

Two places do work on this project:

1. **The cloud session** (where Claude runs). It has JDK 21 and can download the Android SDK, so it can build the engine, run JVM tests and (from Phase 3) build debug APKs. It has **no emulator** (no hardware virtualisation) and **no access to your phone or your ROMs** unless you upload them to a session.
2. **Your computer and phone.** Needed for: running the golden tests on your ROMs (or uploading ROMs to a cloud session), installing APKs on your phone, measuring memory, and booting outputs in an emulator.

## On your computer

### 1. Android Studio (includes a JDK and the SDK)
Think of it as Visual Studio for Android: an IDE plus the SDK manager, emulator and `adb`.

1. Download the current stable Android Studio from <https://developer.android.com/studio> and install it with default options.
2. On first launch choose **Standard** setup. Accept the SDK licences when asked — that is your decision to make, so Claude has not done it for you.
3. **Tools > SDK Manager > SDK Platforms**: tick **Android 17 (API 37)**. **SDK Tools**: make sure **Android SDK Build-Tools**, **Android SDK Platform-Tools** and **Android Emulator** are ticked. Apply.
4. Check it worked — open a terminal (PowerShell on Windows) and run:
   ```bash
   adb version
   ```
   If "not recognised", add the SDK's `platform-tools` folder to PATH (Windows default: `%LOCALAPPDATA%\Android\Sdk\platform-tools`).

### 2. Git
You already have the repo on GitHub. To get your phone and ROMs involved you will want a local clone:
```bash
git clone https://github.com/JoshuaMootoo/universal-pokemon-randomizer-zx-APK.git
```

### 3. ROM folder
Create `testroms/` in the clone root and put your own decrypted ROM files in it. It is gitignored. Before every commit, `git status` must not show any ROM.

## On your phone
1. **Settings > About phone**, tap **Build number** seven times to enable Developer options.
2. **Settings > System > Developer options**: turn on **USB debugging**.
3. Plug into the computer, accept the "Allow USB debugging" prompt, then run `adb devices` — your phone should be listed as `device`.
4. Sideloading the APK later also needs "Install unknown apps" allowed for whichever app you open the APK with (Files, browser).

Nothing here is needed until Phase 1 (ROMs) and Phase 3 (phone).
