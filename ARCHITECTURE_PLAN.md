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

## 6. Verified Implementation Status & Next-Phase Roadmap

The following status was checked against the current Kotlin source on 2026-11-13. Do not describe a capability as implemented unless it is listed under the verified items below.

### 6.1 What the current build actually does

| Area | Verified behavior | Important boundary |
|---|---|---|
| Process slots | Four bound Android services can run in four named app processes and exchange Messenger messages. Slot ownership is reserved to prevent two clone records from claiming one slot. | These processes share the host application's UID and permissions. They are separate app processes, not isolated security principals. |
| Sandbox storage | Clone-specific directories are provisioned, listed, measured, and cache-cleaned. Clone IDs are validated before path construction. | VirtualContextWrapper only redirects calls made through that wrapper. No guest APK currently uses it, and native file I/O or calls through the host application context are not transparently redirected. |
| Binder compatibility helpers | A local Java proxy wraps CloneCore's own VirtualPackageService interface and can demonstrate package-name mapping, URI formatting, and namespace string generation. | It does not hook Android AMS, PMS, ContentProvider Binder interfaces, Settings.Secure, or Google Play Services. Diagnostic counters are not framework interception metrics. |
| Proxy relay | A loopback TCP listener can forward bytes to one selected upstream socket endpoint. TCP reachability and a SOCKS5 greeting can be probed. | It is not connected to guest-app traffic, has one global upstream rather than per-clone routing, and does not implement proxy protocol translation or upstream authentication. A null upstream rejects relay clients; it is not direct Internet passthrough. |
| Telemetry | The host and slot service can report process PID, Java heap, native heap allocation, thread estimate, and service uptime. | These are measurements of CloneCore processes, not of a guest application process. |
| App discovery | Launchable installed apps can be discovered and external APK metadata can be inspected. | Inspection is not installation or cloning. |
| Session links | CloneCore can route its own deep links and shortcuts to a clone record. | The link does not launch a guest APK because the guest runtime is not implemented. |

### 6.2 Core capability that is not implemented yet

**Arbitrary third-party APK execution inside a clone session is not implemented.** The current launch path creates a CloneCore service session; it does not load the selected APK's code/resources or run its Activities. The separate "Open Target" action starts the original installed application through Android's normal launch intent.

Consequently, the current build must not be advertised as providing:
- independent runnable copies of arbitrary installed apps;
- separate Android package identities or signatures;
- complete SharedPreferences, database, provider, or native-file isolation for guest code;
- real Android Binder interception or Google Play Services account isolation;
- per-clone network capture, proxy enforcement, or proxy rotation;
- guest-app CPU/RAM telemetry.

### 6.3 Engineering roadmap

1. **Guest APK intake and validation**
   - Persist imported APK bytes inside the clone directory instead of temporary paths.
   - Support base APK plus split APK sets explicitly; reject incomplete split sets.
   - Record package name, version, SDK range, ABI, signing-certificate digest, and source provenance.
   - Keep original APK bytes immutable and verify their digest after copying.

2. **Guest runtime proof of concept**
   - Build a dedicated guest runtime module with an explicit DexClassLoader/PathClassLoader, resource loading, guest Application lifecycle, and predeclared host stub Activities.
   - Start with a small locally built fixture APK and a documented supported API range.
   - Do not claim general compatibility until Activity launch, configuration changes, process recreation, services, providers, and permissions have been tested.
   - Treat hidden Android APIs and framework interception as version-sensitive compatibility work, not as guaranteed hooks.

3. **Context and storage isolation**
   - Route guest-created contexts through the clone-specific context bridge.
   - Cover files, cache, databases, preferences, no-backup storage, content providers, and package metadata.
   - Add tests proving clone A cannot read or modify clone B's data.
   - Document that native openat/syscall access is not covered by a Kotlin ContextWrapper; add an NDK layer only after a tested guest runtime requires it.

4. **Network isolation and proxy enforcement**
   - Design traffic capture before presenting proxy settings as enforced.
   - A loopback TCP relay alone does not capture application traffic. Evaluate a VpnService or a guest-runtime socket layer, and explicitly resolve how traffic is distinguished between sessions sharing the host UID.
   - Implement protocol negotiation, authentication, DNS behavior, IPv4/IPv6, UDP handling, connection failure reporting, and per-clone upstream state.
   - Add local proxy fixtures for HTTP CONNECT, SOCKS5 no-auth, and SOCKS5 username/password tests.

5. **Process lifecycle and resource management**
   - Bind a process slot to one active clone at a time; reject launches when all four slots are occupied.
   - Persist actual slot assignment and reconcile it after process death.
   - Test rapid launch/stop, bind failure, service death, app restart, and concurrent launch requests.

6. **Verification before release**
   - Add unit tests for clone ID/path validation, slot ownership, proxy handshakes, and APK metadata.
   - Add instrumentation tests using fixture APKs for guest launch and cross-clone storage separation.
   - Run CI build and tests only after the implementation milestone is ready; an APK assembly alone is not proof of guest-app compatibility.

