# Nexus Archive

An Android file manager (Kotlin, Jetpack Compose, Material 3) that is growing into a full archive manager.

> **Verification status (read this first)**
>
> - **0.1.0 to 0.4.0** were built by the GitHub Action and installed by the project owner (screenshots of 0.3.x and
>   0.4.0 running were shared). That says nothing about every feature.
> - **0.5.0** (this version) is a large theme and interaction update. **None of it has been compiled, run or tested.**
>   It was written in an environment with no Android SDK, Gradle or network.
> - Treat everything marked "source only" as unproven until a green run and a device test say otherwise.
>   If a run is red, copy the **first** error line from the failing step.

## Getting the APK

1. Push this project to GitHub (see below). The **Build APK** workflow starts automatically.
2. Open the repo's **Actions** tab, open the latest run, and download the artifact **nexus-archive-debug-apk**.
3. Unzip it and install `app-debug.apk` on your phone (allow "install unknown apps" for your browser/file manager).
4. On first launch tap **Allow file access** and enable "Allow access to manage all files" for Nexus Archive.

If a run is red, open the failing step. The first error line is the only one that matters; later ones are usually fallout.

### Build locally (optional)

Requirements: JDK 17, Android SDK with platform 36, Gradle 8.13 (or Android Studio, which will offer to create the wrapper).

```bash
gradle wrapper --gradle-version 8.13   # once; generates ./gradlew
./gradlew :app:assembleDebug           # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest
```

## Feature status

### In the 0.1.0 base (reported working by the owner)

Material 3 theme with dynamic color, light/dark/system; Home / Browse / Operations / Settings navigation; file browser
(breadcrumbs, list/grid, sorting, filters, hidden files, search in folder); selection; copy, cut/paste, delete, rename,
new folder/file, share, open with, properties (size, MD5/SHA-256 on demand); conflict handling; temp-file-then-rename
writes; symlinks never followed; operations screen with real counters and cancel; DataStore settings; bookmarks and recents.

### New in 0.2.0 (source only, never compiled)

| Area | What it does | Where |
|---|---|---|
| Archive browser | Opens ZIP, 7z, TAR, TAR.GZ/BZ2/XZ, GZ, BZ2, XZ, AR, DEB, CPIO from the file browser and lists their contents as folders without extracting | `archive/`, `ui/archive/` |
| Extraction | Whole archive or selected entries; destination: folder named after the archive, or the archive's own folder; conflict policy keep both / replace / skip; real progress and cancel through the Operations screen | `ArchiveEngine.extract` |
| Compression | Selection menu, Compress. ZIP, 7z, TAR, TAR.GZ, TAR.BZ2, TAR.XZ, and single-file GZ/BZ2/XZ. Only levels the format really supports are offered | `CompressDialog`, `ArchiveEngine.create` |
| Passwords | ZIP: create with AES-256, extract AES or classic ZIP encryption, wrong password detected. 7z: extract with password (cannot create encrypted 7z). Passwords are kept only in memory and wiped after use. Nothing is saved to disk | `PasswordDialog` |
| Archive test | Reads every entry completely so CRC/format errors show up. Result appears in Operations. This is a read check, not proof that the files are meaningful | `ArchiveEngine.test` |
| Safety | Path-traversal and absolute-path entries are skipped and reported; every target is re-checked with canonical paths; links inside archives are never created; temp file then rename; nothing is executed | `ArchivePaths` |
| External storage (SAF) | Add storage from Home, Browse menu and Settings. Folder picker (`ACTION_OPEN_DOCUMENT_TREE`), persisted URI permission, saved label and kind (folder / SD card / USB / provider), access state (read-write, read-only, revoked, not connected), test access, reauthorize, rename, remove (never deletes files) | `storage/`, `ui/storage/` |
| Browsing SAF folders | List, open, new folder, delete, rename (where the provider allows), copy files in from anywhere, save a copy out, open in another app. Capability flags come from the provider | `SafBrowseScreen` |
| APK install | Tapping an `.apk` hands it to Android's package installer. The system prompt always appears; "install unknown apps" is requested through system settings | `FileActions.installApk` |

### New in 0.2.1 (source only, never compiled)

| Area | What it does | Where |
|---|---|---|
| Foreground service | Runs only while an operation is active, so Android is much less likely to kill long copies, compressions and extractions. It observes the existing operation manager; it does not run the work itself | `operations/OperationService.kt` |
| Progress notification | Percentage, bytes or items done, current file, determinate bar only when the real total is known, **Cancel** (or **Cancel all**) action | `OperationNotifications.progress` |
| Completion notification | Finished / finished with problems / failed, with the real summary. Cancelled operations are not announced | `OperationNotifications.postResult` |
| Permission | Android 13+ notification permission is requested once, the first time an operation starts | `NexusAppRoot` |
| Setting | Settings > Notifications. Off = minimal ongoing notice and no completion notices. Android requires some notification while a foreground service runs, so it can't be hidden entirely | `AppSettings.notifications` |

Limits: Android 15 caps how long a data-sync foreground service may run; the service stops cleanly when told to, which would
end its protection for very long jobs. Operations still do not survive the process being killed.

### New in 0.5.0 (source only, never compiled)

| Area | What changed | Where |
|---|---|---|
| Theme engine | Settings > Appearance: **Default** (the #777C6D / #B7B89F / #CBCBCB / #EEEEEE palette, with a deepened accent in light mode so text stays readable), **Monet** (Android 12+ system colours, follows light/dark), **Presets**. Every colour role is mapped, nothing is hard-coded in components | `ui/theme/Theme.kt` |
| Presets | Catppuccin, Dracula, Everforest, Gruvbox, Nord, OneDark, Poimandres, Rosé Pine, TokyoNight with name and swatches; applied instantly and saved. Dracula and Poimandres are dark only, so they stay dark even when the app is set to light. Palettes were written from memory; small colour differences from the originals are possible | `ThemePresets.kt`, `PresetPicker.kt` |
| Liquid glass | Global switch. Floating pill, search button, selection bar, paste bar and snackbars become translucent and follow the active palette. **This is translucency only, not a real backdrop blur** (that needs an extra library, not added yet). The "frosted glass on other elements" sub-toggle is not built | `floatingContainerColor` |
| Nav pill | No outline (a faint hairline only with glass on). Moving down a list shrinks the pill (label hides) and tucks the floating buttons away with a scale animation; moving back up restores them | `NexusBars.kt`, `NexusAppRoot` |
| Snackbars | Themed (not the white default), rounded, and placed above the pill | `NexusSnackbarHost` |
| Path bar | Rows now fade out softly under the path bar instead of being cut off; long-press the path for **Copy path** or **Edit path** (type a folder and press Go). The same works inside archives | `Fades.kt`, `PathEditorRow` |
| Sort sheet | List/Grid switch and an item-size slider moved here (the top-bar grid button is gone); the slider scales list icons and grid tiles and is saved | `SortFilterSheet` |
| Selection | Long-press selects every item between the last selected one and the one you pressed; tapping an icon still toggles a single item | `BrowserViewModel.rangeSelectTo` |
| Location dropdown | Now also lists Downloads, Documents, Music, Movies, Camera and Pictures (only those that exist) | `buildLocationOptions` |
| Recents | Settings > Privacy: new switch to keep dot-folders and `.nomedia` folders out of Recents (on by default); applied when recording and when showing | `RecentsFilter` |
| Fixes | Browse title no longer clipped by its rounded highlight; paste bar is one floating card with matching corner radii | `LocationPicker.kt`, `ClipboardBar` |

### Not built yet (planned in this order)

1. **Tabs** (at least two always open, add/close/reorder, grid or list tab view, swipe between tabs with a bounce at the ends, saved or fixed start tabs set in Settings, clipboard shared across tabs). Everything below depends on this.
2. Extract through the clipboard (Clear / Extract bar) and the merge / skip / keep-both prompts with Remember.
3. Landscape split view with two tabs, scrollbar, camera-cutout safe area.
4. Splash screen and the first-launch setup and intro.
5. Row details: time next to the date, item count on folders, special icons for Android, DCIM, Documents, Download, Music, Movies, Pictures, Ringtones, Alarms and Games.
6. Drag-to-select on the icon, pull-down storage card, compression ratio card.
7. Built-in text editor, image viewer, document reader, video player (Media3 ExoPlayer; mpvEx's libmpv is not being embedded because its native build can't be verified here) and music player, plus the "open with" chooser and a restructured Settings.

### New in 0.4.0 (source only, never compiled)

| Area | What changed | Where |
|---|---|---|
| Nav bar | The floating pill is now drawn **over** the content, so lists scroll behind it instead of stopping above a solid bar. Screens add `LocalFloatingBarInset` as bottom padding, and floating buttons, snackbars and the paste bar are lifted above it | `NexusAppRoot`, `LocalFloatingBarInset` |
| Archive layout | Same layout as the file browser: back, archive name with a location dropdown (storage, Quick access, external storage), search, sort, grid/list, menu; path bar underneath; Extract as the floating button | `ArchiveScreen` |
| Archive selection | Selects only when you tap the icon or long-press. Tapping a folder opens it; tapping a file asks **Open** or **Extract** | `ArchiveScreen.nodeClick` |
| Archive selection bar | Copy, share, select all, invert, and a menu with Extract, Open with, Rename (disabled, see below) and Properties | `ArchiveScreen` |
| Opening files from an archive | Open, Open with, Share and Copy first extract a temporary copy into the app cache (removed at the next app start). Copy puts the temporary files on the file browser's clipboard so you can paste them into a folder | `ArchiveViewModel.stage` |
| Archive search / sort / view | Search names anywhere in the archive; sort by name, size, date or type (folders first); list or grid | `ArchiveTree.sorted/search` |
| Archive menu in the browser | Tapping an archive opens a bottom sheet: View, Extract here, Extract to ./name/, Extract..., Test, Compress. Password-protected archives are handed to the archive screen, which has the password prompt | `ArchiveActionSheet`, `ArchiveQuickActions` |

Not possible yet: a **New** button inside an archive, and **Rename** of archive entries. Both need archive editing
(add, replace, rename entries), which is not built. Rename is shown disabled; the main button is Extract.

### New in 0.3.0 (source only, never compiled)

| Area | What changed | Where |
|---|---|---|
| Navigation | Floating pill bar (Home, Browse, Operations): icon-only tabs, the selected tab grows a labelled tonal pill, Operations shows a live badge. Search is its own round button beside it. Settings moved to the top-right of Home | `ui/components/NexusBars.kt`, `NexusAppRoot.kt` |
| Selection toolbar | One floating pill used in Browse, archives and external storage: close, live count, cut, copy, delete, select all, invert (on wide screens, otherwise in the menu), and a menu | `SelectionPill` |
| Menu | Share, Compress, Add to Quick access, Rename, Properties, Open with, Copy path, Copy name. Items that don't apply to the current selection are shown disabled instead of hidden | `BrowseScreen.SelectionTopBar` |
| Paste bar | Clipboard is now cleared after a copy-paste as well as a move, so Paste here / Clear disappear | `BrowserViewModel.startPaste` |
| Quick access | Select folders, menu, Add to Quick access | `BrowserViewModel.addToQuickAccess` |
| Location dropdown | The Browse title is a dropdown: storage volumes, Quick access folders, and added external storage (only when some exist) | `NormalTopBar` |
| Thumbnails | Image and video previews and APK icons in list and grid. Max 3 decodes at once, sub-sampled, memory-cached; anything that fails falls back to the type icon. Not available for external (SAF) storage | `ui/components/Thumbnails.kt` |
| Tap to select | Tapping a file or folder icon/thumbnail selects it (Browse, archives, external storage). The checkbox in archives is gone | `SelectableLeading` |
| Archive animations | Sliding transition between folders inside an archive and animated list items; both respect reduced motion | `ArchiveScreen` |
| Search | Full-screen search: type pills, a "Search in" dropdown (current folder, each storage, each Quick access folder), results with thumbnails, show-in-folder, stop button. A scan starts only on the search key or button, never while typing; symlinks are not followed; first 1000 matches shown. External (SAF) storage is not searched | `ui/search/` |
| Pause / resume | Pause and Resume on every operation and in its notification. Implemented by blocking the worker at its next progress report (every chunk or item), so it stops and continues from the same place. A paused operation keeps its worker thread and one of the two concurrency slots. Cancel always works while paused | `OperationManager.PauseGate` |
| Recents toggle | Settings > Privacy > Show recent folders and files. Off hides them on Home and stops recording new ones | `AppSettings.showRecents` |

Known gaps in 0.3.0: pause has no automated test; the nav bar is hidden on the search page; elapsed time and average
speed keep counting while an operation is paused.

### Archive format matrix (what is actually implemented)

| Format | Browse | Extract | Create | Password extract | Password create | Test |
|---|---|---|---|---|---|---|
| ZIP | yes | yes | yes | yes (AES, ZipCrypto) | yes (AES-256) | yes |
| 7z | yes | yes | yes | yes | **no** | yes |
| TAR | yes | yes | yes | n/a | n/a | yes |
| TAR.GZ / TGZ | yes | yes | yes | n/a | n/a | yes |
| TAR.BZ2 / TBZ | yes | yes | yes | n/a | n/a | yes |
| TAR.XZ | yes | yes | yes | n/a | n/a | yes |
| GZ, BZ2, XZ (single file) | yes | yes | yes | n/a | n/a | yes |
| AR, DEB (outer members only) | yes | yes | no | n/a | n/a | yes |
| CPIO | yes | yes | no | n/a | n/a | yes |

**Not supported** (the app says so instead of pretending): RAR/RAR5, ISO, CAB, ARJ, LZH/LHA, Z, RPM, ZIPX, XAR, WIM,
Zstandard, LZ4, LZMA, LZIP, DMG, IMG, CHM, CRAMFS, MTZ, ECM, EGG, ALZ.
Also not supported: multipart/split archives (any format), archive editing (add/remove/replace entries), saved passwords.

Progress note: for ZIP and 7z the counters are uncompressed bytes written. For TAR-family and single-stream formats
they are compressed bytes read from the archive, because the uncompressed size is unknown without decompressing twice.

### Storage provider limitations

- Android decides which folders an app may use. The picker usually refuses the top level of a volume and some system folders.
- SD cards, USB drives and provider folders all use the **same** Android folder picker. The four options in "Add storage"
  only change the saved label/icon and the tips shown; they do not change how access works.
- A USB drive or SD card that is unplugged shows "Not connected" until reconnected. Revoked access shows "Access revoked"
  with a Reauthorize button.
- Providers differ: some cannot rename, some cannot write, some report no size or date. Buttons that the provider
  doesn't support are disabled or the operation reports failure. Free space is **not** shown for SAF folders because the
  document API does not expose it reliably.
- **Archives stored on SAF folders cannot be opened in place yet** (the archive engine works on real file paths). Use
  "Save a copy" to put the archive in internal storage first.
- Compress/extract and the regular file browser (copy/paste, cut) do not yet work across SAF folders. Copy-in and
  save-a-copy work from the SAF screen.
- Android keeps a limited number of persisted folder grants per app (128 on older versions, 512 on newer).

## Not built (nothing in the UI pretends otherwise)

- RAR and the other unsupported formats listed above; multipart archives; archive editing; saved passwords / Keystore storage.
- Shizuku and root access (no controls are shown for them).
- Image/text previews, APK inspection, OBB management.
- Search history, search across several locations, duplicate finder, file comparison, favorites.
- Operation history that survives restarts.
- Instrumented and UI tests; TalkBack audit.
- True Material 3 Expressive 1.5.x components.
- New settings for archive defaults (default format, default extract location). The dialogs choose per use.

## Known limitations

- **Long operations can still be killed.** A foreground service now reduces the risk, but if the process dies the operation stops. Interrupted writes leave at most a hidden `.nx-*.nexus-part` or `.<name>.nexus-part` file; there is no automatic cleanup on next launch yet.
- **Memory.** XZ compression levels are capped (preset 4) because higher presets need hundreds of MB. Archives are streamed, not loaded into memory. Very large TAR.GZ/BZ2/XZ archives must be read through once to list them.
- **No zip-bomb limit.** Extraction does not cap the total expanded size; it checks free space only when the total is known.
- **Passwords in the dialog.** Compose text fields hold the typed password as a String while the dialog is open; it is converted to a char array and wiped after the operation.
- **ZIP names stay visible** even when contents are encrypted; this is how ZIP works.
- **Protected folders.** Android 11+ blocks `Android/data` and `Android/obb` for ordinary apps even with "All files access".
- **Delete is permanent.** There is no recycle bin.
- **Google Play.** `MANAGE_EXTERNAL_STORAGE` and `REQUEST_INSTALL_PACKAGES` are restricted on Google Play. This build is for sideloading.
- **Symbolic links are skipped** by copy, move, compress and extract (and reported); delete removes only the link.
- **Material 3 Expressive.** The stable `material3` release is 1.4.0; most Expressive components are only in 1.5.0 betas. This build implements an expressive look by hand.
- **Dependency versions are conservative** and, for the new libraries, were chosen from memory without being able to resolve them offline: if Gradle can't find a version, change it in `app/build.gradle.kts`.

## Toolchain (pinned)

| Tool | Version |
|---|---|
| Android Gradle Plugin | 8.13.2 (needs Gradle >= 8.13, JDK 17) |
| Kotlin | 2.2.20 |
| Compose BOM | 2025.09.00 (Material 3 1.4.0) |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |

## Project layout

```
app/src/main/java/com/nexusarchive/android/
  core/        Pure file logic (no Android UI): listing, sort/filter, copy/move/delete engine, hashing, volumes
  archive/     Format registry, detection, path safety, engine (list/extract/test/create), operation jobs
  storage/     SAF models, repository (DataStore), document-provider access
  data/        Settings model + DataStore repository
  operations/  Background operation manager and progress models
  ui/          Compose screens: home, browse, archive, storage, operations, settings, shared components, theme
app/src/test/  JVM unit tests (archive round trips, path safety, format detection, SAF labels, plus the base tests)
```

## Dependency and license inventory

| Library | Version | License | Used for |
|---|---|---|---|
| AndroidX (core-ktx, activity-compose, lifecycle, navigation-compose, datastore-preferences) | see `app/build.gradle.kts` | Apache 2.0 | App framework |
| Jetpack Compose (ui, foundation, material3, material-icons-extended) | BOM 2025.09.00 | Apache 2.0 | UI |
| Material Components (com.google.android.material) | 1.12.0 | Apache 2.0 | XML window theme `Theme.Material3.DayNight.NoActionBar` |
| kotlinx.coroutines | 1.10.1 | Apache 2.0 | Concurrency |
| Apache Commons Compress | 1.27.1 | Apache 2.0 | 7z, TAR, GZIP, BZIP2, XZ, AR, CPIO |
| XZ for Java (org.tukaani:xz) | 1.10 | 0BSD / public domain | XZ and 7z LZMA codecs |
| zip4j (net.lingala.zip4j) | 2.11.5 | Apache 2.0 | ZIP with AES encryption |
| JUnit 4 (tests only) | 4.13.2 | EPL 1.0 | Tests |

No analytics, ads, or network libraries. The manifest declares no `INTERNET` permission. Dependency vulnerability
scanning has **not** been run.

## Privacy

No telemetry, no accounts, no network permission. File names and paths are never sent anywhere.

## Next steps (proposed order)

1. Push 0.2.0, read the first Action run, fix whatever the compiler reports, then run the unit tests and look at the report.
2. Test on a device: archive browse/extract/create, password ZIP, SD card/USB folder add/revoke/reconnect, APK install.
3. Archive support for SAF-hosted archives; cross-storage copy; opening archives from other apps.
4. Multipart archives, archive editing, more formats (RAR needs a licensed or native library that has not been evaluated).
5. Previews, Material 3 Expressive 1.5.x components, TalkBack audit, instrumented tests.
