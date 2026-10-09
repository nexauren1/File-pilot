# FilePilot

FilePilot is an Android file manager with a familiar, category-first layout and its own violet visual identity. It is designed to keep file operations local to the device.

## Current MVP

- Home screen with storage usage and quick access to common file categories.
- Categories for images, videos, audio, documents, archives, APKs, Downloads, and other files.
- Recently opened files list, stored locally.
- Favorites list, stored locally.
- Browse shared storage when Android grants broad access, with Storage Access Framework folder selection as a fallback.
- Search and filter the current folder.
- Create folders; open, share, rename, and delete items.
- Copy or move individual files to a folder selected by the user.
- Safe Folder protected by a PIN, with file contents and its index encrypted locally using AES-GCM.
- Restore an encrypted item to a destination chosen by the user or permanently delete it from the vault.
- English and Portuguese strings.
- GitHub Actions workflow that runs unit tests, builds a debug APK, and uploads it as an artifact.

## Build without a computer

Pushes to `main` trigger the GitHub Actions workflow. Open the repository's **Actions** tab, choose a successful **Android build** run, and download the `FilePilot-debug-apk` artifact.

The artifact is a debug APK for device testing, not a signed production release. Extract the downloaded ZIP on Android before installing the APK.

## Safe Folder and privacy

The Safe Folder derives an AES-256 key from the PIN using PBKDF2-HMAC-SHA256 and encrypts vault files and the item index with AES-GCM. Vault contents stay in app-private storage unless the user restores them.

**Important:** if the user forgets their Safe Folder PIN, the encrypted items cannot be recovered. Uninstalling or clearing app data may permanently delete the vault. The MVP does not yet include a recovery mechanism, automatic background locking, or secure backup.

FilePilot stores favorite locations and recent file locations locally. Core file operations do not require a server. The current build does not yet include an advertising SDK.

Broad storage access through `MANAGE_EXTERNAL_STORAGE` is optional and remains subject to Google Play eligibility and review. Before publication, the app requires device testing, privacy documentation, policy review, any needed ad consent, and production release signing.

## Current limitations

- Copy/move operations currently act on individual files, not whole directory trees.
- Archive creation/extraction, multi-selection actions, storage-cleaning recommendations, and advertising are not yet implemented.
- Safe Folder behavior and storage access still require testing on physical Android devices and different document providers.

This is an early development build, not a production release.
