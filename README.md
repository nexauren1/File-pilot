# FilePilot

FilePilot is a minimalist Android file manager with a familiar, category-first browsing experience and its own violet visual identity.

## Current MVP

- Home screen with storage usage and quick-access categories.
- Browse shared storage when Android grants broad access, with Storage Access Framework folder selection as a fallback.
- Categories for images, videos, audio, documents, archives, APKs, Downloads, and other files.
- Search and filter within the current folder.
- Open, share, rename, and delete individual files.
- Move individual files into the Safe Folder.
- Safe Folder protected by a user PIN, with file contents and its index encrypted locally using AES-GCM.
- Restore an encrypted item to a location selected by the user, or permanently delete it from the vault.
- English and Portuguese strings.
- GitHub Actions workflow that runs unit tests, builds a debug APK, and uploads it as an artifact.

## Build without a computer

Pushes to `main` trigger the GitHub Actions workflow. Open the repository's **Actions** tab, choose the latest completed **Android build** run, and download the `FilePilot-debug-apk` artifact when the build succeeds.

The APK is for testing. A signed release AAB/APK and device testing are still required before distribution.

## Safe Folder and privacy

The Safe Folder derives an AES-256 key from the PIN using PBKDF2-HMAC-SHA256 and encrypts vault files and the item index with AES-GCM. File contents remain in app-private storage unless the user explicitly restores or shares a file.

**Important:** if the user forgets their Safe Folder PIN, the encrypted items cannot be recovered. Uninstalling or clearing app data may permanently delete the vault. The MVP does not yet include a recovery mechanism, automatic background locking, or a secure backup.

FilePilot does not currently include an advertising SDK and does not require a server for core file operations. Before publication, privacy documentation, broad-storage permission eligibility, Google Play declarations, ad consent, and release signing must be reviewed.

## Development status

This is an early development build. The GitHub Actions result must be successful and the app must be manually tested on Android devices before calling the MVP ready.
