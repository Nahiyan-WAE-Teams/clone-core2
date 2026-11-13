# CloneCore — Full Architecture & Engineering Plan

## 1. Project Overview & Goal
**CloneCore** is a non-root **Android App Cloner & Sandbox Virtualization Engine** targeting Android 7.0–16 (API 24–36). Its purpose is to let users clone a single installed application multiple times, run multiple sessions in parallel across isolated OS processes without system-level conflicts, monitor real-time kernel/process telemetry, rotate per-instance proxies automatically, and launch any cloned session directly via deep links or cross-platform bridge commands—without requiring root access.

---

## 2. Core Technical Requirements
1. **Multi-Instance Cloning (No Root)**: Clone any launchable app multiple times (single or batch deployment 1–5 instances) with isolated identities, storage, and preferences.
2. **Parallel Execution Without Conflicts**: Run cloned sessions simultaneously in isolated Android OS processes (`:sandbox_p0` .. `:sandbox_p3`) with separate heaps, PIDs, `ContentProvider` authority remapping, and signature digest preservation.
3. **Isolated Sandbox Virtual File System (VFS)**: Redirect app-private directories (`filesDir`, `cacheDir`, `databases`, `shared_prefs`, `code_cache`) per clone via a custom `ContextWrapper` (`VirtualContextWrapper`).
4. **Zero-Root Kernel & Driver Telemetry**: Read real Linux `/proc` & Android runtime interfaces (`/proc/stat`, `/proc/meminfo`, `/proc/loadavg`, `/proc/self/status`, `ActivityManager`, `PowerManager`, `TrafficStats`) to visualize live CPU/RAM usage, per-core load, process PSS memory, and resource governing.
5. **Automated Proxy Rotation**: Per-clone proxy rules (Direct, HTTP, HTTPS, SOCKS5) backed by an in-app local loopback TCP relay (`127.0.0.1:<port>`), real TCP/SOCKS5 RTT latency probing, and scheduled/on-launch rotation.
6. **Deep-Link & Cross-Platform Integration**: Unique `clonecore://session/<cloneId>` and `https://clonecore.app/s/<cloneId>` routes per session, pinned launcher shortcuts (`ShortcutManagerCompat`), and JSON/`adb` bridge payloads.
7. **Play-Policy & Android 13–16 Compliance**: Uses `<queries>` for launchable app discovery (no `QUERY_ALL_PACKAGES`), Android SAF (`OpenDocument`) for external `.apk` inspection (no broad storage permissions), `dataSync` foreground service declaration, and zero remote dynamic `.dex`/`.so` downloading.

---

## 3. System Architecture Diagram

```text
+-------------------------------------------------------------------------+
|                        JETPACK COMPOSE HOST UI                          |
|  [VaultScreen]   [AppsScreen]   [KernelScreen]   [NetworkAndLinksScreen]|
|        \              |               |                  /              |
|         +-------------+-------+-------+-----------------+               |
|                               |                                         |
|                        [MainViewModel]                                  |
|       (StateFlow, Coroutines, Lifecycle-Aware Telemetry Polling)        |
+-------------------------------+-----------------------------------------+
                                |
        +-----------------------+-----------------------+
        |                                               |
        v                                               v
+------------------------------+        +---------------------------------+
|   LOCAL PERSISTENCE (Room)   |        |      CORE ENGINE MODULES        |
|  - CloneInstanceEntity       |        |  - SandboxFileSystem / VFS      |
|  - ProxyNodeEntity           |        |  - BinderProxyEngine (Proxy)    |
|  - KernelAuditLogEntity      |        |  - KernelTelemetryReader (/proc)|
|  - CloneRepository           |        |  - ProxyRotationEngine (Relay)  |
+------------------------------+        |  - InstalledAppsScanner         |
                                        |  - DeepLinkAndBridgeManager     |
                                        +----------------+----------------+
                                                         |
                                              Messenger IPC / BindService
                                                         |
          +----------------------+-----------------------+----------------------+
          |                      |                       |                      |
          v                      v                       v                      v
+------------------+   +------------------+   +------------------+   +------------------+
| :sandbox_p0      |   | :sandbox_p1      |   | :sandbox_p2      |   | :sandbox_p3      |
| SandboxSlot0Svc  |   | SandboxSlot1Svc  |   | SandboxSlot2Svc  |   | SandboxSlot3Svc  |
| - Isolated PID   |   | - Isolated PID   |   | - Isolated PID   |   | - Isolated PID   |
| - Isolated Heap  |   | - Isolated Heap  |   | - Isolated Heap  |   | - Isolated Heap  |
| - VirtualContext |   | - VirtualContext |   | - VirtualContext |   | - VirtualContext |
+------------------+   +------------------+   +------------------+   +------------------+
```

---

## 4. Tech Stack & Configuration
- **Language**: Kotlin (JVM 11 target, compiled with JDK 21)
- **UI**: Jetpack Compose + Material 3 + Navigation Compose (`Obsidian Telemetry` dark theme with bundled `Space Grotesk`, `Inter`, and `JetBrains Mono` fonts in `res/font/`)
- **Persistence**: Room (`2.7.0`) + KSP (`2.3.5`) + DataStore Preferences
- **Networking / Relay**: Java `ServerSocket` loopback relay + `Socket` TCP/SOCKS5 health probing + OkHttp
- **Build System**: Gradle `9.3.1` (Kotlin DSL), AGP `9.1.1`, `compileSdk 36`, `targetSdk 36`, `minSdk 24`
- **CI/CD**: `.github/workflows/build-apk.yml` (Ubuntu + JDK 21 + Android SDK license acceptance + Gradle `9.3.1` + artifact upload `CloneCore-debug-apk`)

---

## 5. Module & File Breakdown

### A. Application & Persistence Layer
- `app/src/main/java/com/example/CloneCoreApp.kt`: Process-aware `Application` entry point. Prevents `:sandbox_p0..p3` child processes from re-initializing the host database or duplicate loopback relay sockets.
- `app/src/main/java/com/example/data/db/Entities.kt`: Room tables for `clone_instances`, `proxy_nodes`, and `kernel_audit_logs`.
- `app/src/main/java/com/example/data/db/Daos.kt`: Reactive `Flow` queries and `suspend` CRUD operations.
- `app/src/main/java/com/example/data/db/CloneCoreDatabase.kt` & `CloneRepository.kt`: Singleton Room DB and repository abstraction.

### B. Virtualization & Sandbox Engine (`com.example.engine.*`)
1. **Virtual File System (`engine/vfs/SandboxFileSystem.kt`)**:
   - Provisions `filesDir/sandboxes/<cloneId>/data/{files,cache,code_cache,no_backup,shared_prefs,databases}` and `lib/arm64-v8a` plus `vfs_manifest.json`.
   - `VirtualContextWrapper` overrides storage, database, `SharedPreferences`, and `getPackageName()` calls into the isolated directory tree.
   - Exposes file listing, recursive storage stats, custom file writing, cache wiping, and container destruction.
2. **Binder & Dynamic Proxy Interceptor (`engine/binder/BinderProxyEngine.kt`)**:
   - Uses `java.lang.reflect.Proxy.newProxyInstance` over `VirtualPackageService`.
   - Remaps `ContentProvider` URIs (`content://<host>.vproc.<cloneId>.<orig_auth>/...`) to avoid authority conflicts.
   - Preserves original APK SHA-256 signing certificate digests (`GET_SIGNING_CERTIFICATES`) inside sandbox checks.
   - Isolates Google Play Services (`com.google.android.gms`) token namespaces and tracks live intercept counters (AMS, PMS, ContentProvider, GMS, `Settings.Secure.ANDROID_ID`).
3. **Multi-Process Slot Manager (`engine/process/SandboxSlotService.kt`, `SandboxProcessManager.kt`, `SandboxMonitorService.kt`)**:
   - Manifest declares 4 isolated process services (`:sandbox_p0`..`:sandbox_p3`).
   - `SandboxProcessManager` binds via `Messenger` IPC, initializes `VirtualContextWrapper` + `BinderProxyEngine` inside the remote process, applies `Process.setThreadPriority()`, and polls real remote PID, heap MB, native MB, threads, and uptime.
   - `SandboxMonitorService` provides an optional `foregroundServiceType="dataSync"` keep-alive notification.
4. **Zero-Root Kernel Telemetry (`engine/kernel/KernelTelemetryReader.kt`)**:
   - Reads `/proc/stat` (delta aggregate and per-core CPU %), `/proc/meminfo` (RAM + Swap), `/proc/loadavg`, `/proc/self/status` (`VmRSS`, `Threads`, `Seccomp`), `/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq` (with graceful `SELinux Guarded` fallback), `PowerManager.currentThermalStatus`, `TrafficStats` UID Rx/Tx KB, and `ActivityManager.getProcessMemoryInfo`.
   - Provides real heap trim (`System.gc()` + finalization) and thread priority boost controls.
5. **Automated Proxy Rotation (`engine/network/ProxyRotationEngine.kt`)**:
   - Spawns a daemon `ServerSocket` on `127.0.0.1:0` and forwards sockets to the active upstream `ProxyNodeEntity` with atomic upstream switching.
   - Measures real TCP + SOCKS5 handshake RTT latency (`probeProxyLatency`).
   - Supports rotation triggers: `ON_LAUNCH`, `INTERVAL_5M`, `INTERVAL_15M`, `MANUAL`.
6. **App Discovery & APK Inspector (`engine/apps/InstalledAppsScanner.kt`)**:
   - Queries `ACTION_MAIN` + `CATEGORY_LAUNCHER` via `PackageManager` (`<queries>` manifest block).
   - Extracts version, `targetSdk`, `minSdk`, APK size, and native ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`) from `nativeLibraryDir` or APK zip entries.
   - Inspects external `.apk` files via Android SAF `OpenDocument`.
7. **Deep-Link & Cross-Platform Bridge (`engine/deeplink/DeepLinkAndBridgeManager.kt`)**:
   - Handles `clonecore://session/<cloneId>` and `https://clonecore.app/s/<cloneId>` intents in `MainActivity` (`launchMode="singleTask"`).
   - Creates badged home-screen pinned shortcuts (`ShortcutManagerCompat.requestPinShortcut`).
   - Exports JSON bridge manifests including `adb shell am start` trigger commands and relay endpoint metadata.

### C. UI Screens (`com.example.ui.*`)
- `VaultScreen.kt`: Hero banner, live `:sandbox_p0..p3` slot pills, clone instance cards (launch/terminate, proxy rotate, copy deep link, pin shortcut, PIN unlock dialog).
- `AppsScreen.kt`: Searchable launchable app list, filter chips (`User Apps`, `All`, `Cloned`), external APK SAF picker, and batch multi-clone config modal bottom sheet.
- `KernelScreen.kt`: Live CPU & RAM `Canvas` sparkline charts, per-core CPU bars, `/proc` memory & Seccomp stats, OS process PSS table, Binder hook diagnostics, monitor service switch, and live audit log stream.
- `NetworkAndLinksScreen.kt`: Segmented view for **Proxy Rotation** (loopback relay status, add/delete/probe proxy nodes) and **Deep Links & Bridge** (interactive URI tester, shortcut pinning, JSON/ADB share).
- `CloneDetailScreen.kt`: Dedicated container inspector with live slot heap/native metrics, Binder hook self-test, VFS file browser (write test file / wipe cache), virtual identity editor (`ANDROID_ID` randomizer, isolation switches, thread priority), and per-clone audit logs.

---

## 6. Current Limitations & Next-Phase Roadmap (For Future Engineering)
Right now, CloneCore's virtualization engine provides **process-slot isolation, VFS redirection, dynamic proxy hooks, proxy rotation, kernel telemetry, and deep-link/shortcut routing** cleanly in user space. If the next phase requires running arbitrary third-party APK binaries **inside** the host's `:sandbox_pN` processes (full guest APK containerization rather than host launch intent + sandbox state), the next engineering steps are:
1. **Guest APK Dex & Resource Loader**: Load the target APK's `base.apk` using an isolated `DexClassLoader` / `PathClassLoader` inside `:sandbox_pN` and attach its `AssetManager` / `Resources` via `PackageManager.getResourcesForApplication`.
2. **AMS / `Instrumentation` Activity Stubbing**: Register pre-declared stub Activities (`StubActivity0..3`) in `AndroidManifest.xml` and hook `ActivityThread.mH` / `Instrumentation.execStartActivity` to map guest Activities onto manifest-declared stubs inside `:sandbox_pN`.
3. **Native `libc` IO & Binder Hook (NDK / JNI)**: Add a C++ NDK module (`libclonecore_vfs.so`) using PLT/GOT or inline syscall hooks (e.g., `openat`, `stat`, `access`) to redirect native `.so` file paths transparently to `filesDir/sandboxes/<cloneId>/`.
4. **Local `VpnService` Per-Process Traffic Routing**: Implement an Android `VpnService` packet forwarder (`tun2socks`) to force all TCP/UDP packets from sandboxed sessions through `127.0.0.1:<relayPort>` and the rotating upstream SOCKS5/HTTP proxy.
