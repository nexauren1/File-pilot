# FilePilot

FilePilot is an Android file manager with a clean category-first interface and a violet visual identity. File operations are designed to stay on the device.

## Features in the 0.2.0 beta

- **Browse and search:** navigate folders and search the current folder.
- **Media and file categories:** images, videos, audio, documents, archives, APK installers, Downloads, and other files.
- **Common file actions:** open, share, rename, copy, move, create folders, and move selected items to Trash.
- **Favorites and recents:** locally stored quick access lists.
- **Safe Folder:** a PIN-protected vault that encrypts file contents and its index locally with AES-GCM.
- **Trash:** deleted items are copied into FilePilot's private app storage, then removed from their original location. Users can restore an item to a folder they choose or delete it permanently.
- **Storage cleaner:** reviews large files (100 MB or more), APK installer files, and exact duplicate files detected using SHA-256 hashes. Nothing is cleaned automatically; the user chooses what to move to Trash.
- **App manager:** searches installed apps, shows app information in Android settings, and lets the user request uninstall for eligible user-installed apps.
- **Security review:** locally reviews accessible file names/types for potentially misleading extensions and executable or installer files. It does **not** claim to detect all malware and is not a full antivirus.
- **English and Portuguese** interface strings.

The features are organized across separate screens so the home page is not overloaded.

## Android permissions and privacy

On Android 13 and later, FilePilot requests the media permissions for images, videos, and audio. Managing all shared files can additionally require the Android **Manage all files** special access. Users can instead choose a folder through Android's Storage Access Framework; that access is limited to the folder they select and the operations its provider permits.

The app manager declares installed-package visibility for its app-management screen. Device and store policies can restrict broad storage access and package visibility, so publication eligibility and permission flows must be reviewed for the intended distribution channel.

FilePilot's file scanning and SHA-256 duplicate checks run locally. The current project does not require a FilePilot account or upload scanned file contents to a server. The operating system's own security tools should remain enabled.

## Trash and Safe Folder limitations

- **FilePilot Trash is an app-private recovery area**, not a system-wide recycle bin. Its contents can be permanently removed when the user empties Trash, clears FilePilot's app data, or uninstalls the app. Keep a backup of important data.
- The Safe Folder derives an AES-256 key from the PIN using PBKDF2-HMAC-SHA256 and encrypts files and the item index using AES-GCM.
- If the Safe Folder PIN is forgotten, its encrypted contents cannot be recovered. Clearing app data or uninstalling may permanently delete the vault.
- The current implementation should be tested on physical devices and different Android document providers before general release.

## Build and download on Android

GitHub Actions runs unit tests and builds a debug APK whenever `main` changes. Open the repository's **Actions** tab, select the newest successful **Android build** run, and download the `FilePilot-debug-apk` artifact. Extract the ZIP on your phone before installing the APK.

The beta release workflow also builds and verifies a release-variant APK, then attaches the APK to a GitHub pre-release. The APK is signed with the Android debug signing key for beta sideload testing; it is **not** a production-store signing build.

## Known limitations before a public launch

- File operations with Storage Access Framework providers depend on the permissions and capabilities offered by each provider.
- Large scans are intentionally bounded and may not review every folder on a very large device.
- Security review is heuristic and does not use a malware signature database or provide antivirus protection.
- Storage cleaning requires the user to inspect candidates; the app does not clear other apps' private caches.
- Whole-folder copy/move support is not universal across all provider types yet.
- Device testing, permission-policy review, privacy-policy publication, and a private production signing key are required before a production store launch.
