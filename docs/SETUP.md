# Setup for Joshua (Windows 11)

Two places do work on this project:

1. **The cloud session** (where Claude runs). It has JDK 21 and the Android SDK, so it can build the engine, run tests that need no ROM, and build debug APKs. It has **no emulator** and **cannot see your PC or phone** on its own.
2. **Your PC and phone.** The ROMs stay on your PC. Golden tests on real ROMs, installing on the phone, memory measurements and boot tests all happen there. Claude can run commands on your PC only if you share a folder with the session through Remote Control (step 4).

All commands below are for **PowerShell** (Start menu, type "PowerShell").

## 1. Check the tools you have
```bash
git --version
```
If that says "not recognised", install Git for Windows from <https://git-scm.com/download/win> (default options), then open a new PowerShell window.

```bash
adb version
```
If that says "not recognised", Android Studio's `platform-tools` folder is not on PATH. Run this once, then open a new PowerShell window:
```bash
[Environment]::SetEnvironmentVariable("Path", $env:Path + ";$env:LOCALAPPDATA\Android\Sdk\platform-tools", "User")
```

In Android Studio, open **Tools > SDK Manager**. Under **SDK Platforms**, tick **Android 17 (API 37)**, then click **Apply**.

## 2. Clone the repository
Use a normal local folder, not OneDrive (it can lock files during builds):
```bash
cd E:\Local\Documents
```
```bash
git clone -b claude/wizardly-wozniak-5zrlnc https://github.com/JoshuaMootoo/universal-pokemon-randomizer-zx-APK.git
```

## 3. Copy the ROMs in (copy, never move)
```bash
New-Item -ItemType Directory -Force E:\Local\Documents\universal-pokemon-randomizer-zx-APK\testroms\nds
```
In File Explorer, **copy** your `.nds` files from `E:\Local\Documents\Roms` into that `testroms\nds` folder. Your originals stay where they are. `testroms/` is gitignored, so these files can't be committed. Check:
```bash
cd E:\Local\Documents\universal-pokemon-randomizer-zx-APK
```
```bash
git status
```
`git status` must not list any ROM.

## 4. Share the clone with the Claude session (Remote Control)
1. Open the **Claude desktop app** on your PC.
2. Go to **Settings > Claude Code > Remote Control** and add the folder `E:\Local\Documents\universal-pokemon-randomizer-zx-APK`. That page may change between app versions; if the option isn't there, tell Claude.
3. Keep the app running while Claude works. Claude will then ask to connect, and you approve it in the app.

Not every app version can share a Windows folder with a cloud session. If it doesn't work, start a Claude Code session on your PC in that folder instead, and it can pick up from `docs/PROGRESS.md`.

## 5. Phone (needed from Phase 3)
1. **Settings > About phone > Software information**, tap **Build number** seven times to unlock Developer options.
2. **Settings > Developer options**: turn on **USB debugging**.
3. Plug the phone into the PC, accept the "Allow USB debugging?" prompt, then run `adb devices`. Your phone should be listed as `device`.
4. To sideload the APK later, allow "Install unknown apps" for the app you open it with (for example My Files).
