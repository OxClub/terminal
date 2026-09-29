# NEXUS TERMINAL

NEXUS TERMINAL is an Android-native terminal application with a real PTY backend, local app-private filesystem, multiple terminal sessions, ANSI rendering, an extra-key keyboard, a text editor, command history storage, and an extensible package repository architecture.

## Architecture

- `engine/NativePty.kt` + `src/main/cpp/native_pty.cpp`: JNI PTY process layer. Each session owns its own pseudo-terminal, shell process, stdin writer, output reader, and terminal resize state.
- `engine/TerminalBuffer.kt`: bounded terminal screen/scrollback model with common ANSI CSI cursor, erase, and SGR color handling.
- `engine/TerminalView.kt`: efficient custom Canvas renderer plus Android IME/key event bridge.
- `engine/SessionManager.kt`: lifecycle and multi-session management.
- `editor/`: local text editor.
- `packagex/`: repository metadata and real ZIP package installation with optional SHA-256 verification. No package database is fabricated.
- `data/`: local preferences/history/models.
- `ui/`: responsive Android UI and navigation.

## Terminal environment

The shell runs in an app-private home directory under `files/home`. `TERM=xterm-256color`, `HOME`, `SHELL`, `NEXUS_HOME`, and a controlled `PATH` are provided. The default shell is detected from available Android paths; users can configure another executable path.

The app does **not** claim unrestricted Linux/Termux compatibility. Android's sandbox remains in force. Commands/runtimes that are not installed in the environment simply will not exist.

## Build

Requirements: JDK 17, Android SDK 35, CMake 3.22.1, NDK 27.0.12077973, and Gradle 8.9.

```bash
gradle --no-daemon testDebugUnitTest assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions

`.github/workflows/android-build.yml` installs the required Android platform, build tools, CMake and NDK, runs unit tests, builds the debug APK, and uploads `nexus-terminal-debug-apk` as a workflow artifact.

## Permissions

The app requests only `INTERNET` because repository/package downloads may need network access. Core PTY, private files, history, and editing work without network access. Shared storage is intentionally not treated as unrestricted; a future/connected SAF directory can be granted by the user.

## Security

No credentials, private keys, or tokens are collected or logged. The app does not execute hidden commands. Shell commands originate from user input or an explicit user action. Package archives may optionally be verified with SHA-256 before extraction and are protected against ZIP path traversal.

## Package repositories

A repository is represented by a JSON index containing `name`, `version`, `description`, `archiveUrl`, and optional `sha256`. Archives are ZIP files installed into the app's `files/bin` directory. Because Android cannot provide a universal Linux package ecosystem inside an APK, NEXUS TERMINAL does not pretend that `apt`, `pkg`, or arbitrary Linux packages exist. A compatible repository/bootstrap can be configured and installed explicitly.

## Known Android limitations

- Android sandboxing prevents unrestricted access to `/`, kernel devices, and privileged processes.
- Shell availability varies by Android device. `bash`, `zsh`, Python, Git, SSH, etc. are available only if a compatible runtime is actually present/installed.
- Android may kill ordinary app processes in the background. The current architecture keeps sessions in the app process while it is alive; a future foreground-service host can provide stronger persistence subject to Android foreground-service policy.
- Full POSIX terminal behavior is broader than the implemented common ANSI/CSI subset; uncommon DEC/private sequences may render differently.
- Shared storage should be accessed through Storage Access Framework rather than assuming a global filesystem.

## Testing

Unit tests cover ANSI color stripping/render state and cursor movement. The PTY layer should additionally be exercised on a physical Android device because pseudo-terminal behavior depends on the Android runtime/NDK.

## Release

Configure a local or CI keystore outside the repository. Never commit signing passwords or private keys. The supplied workflow intentionally builds an unsigned debug APK only.
