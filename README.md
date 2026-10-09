# FilePilot

FilePilot is an Android file manager with its own visual identity, built around fast file collections, practical clean-up recommendations, and everyday file sharing.

## FilePilot 0.4.0 beta

This revision reorganizes the app around a **Clean · Browse · Share** bottom navigation, with a separate menu for less-frequent tools and settings.

### Browse
- Quick category collections for Downloads, Images, Videos, Audio, Documents, APK installers, Archives, and other files.
- Category views recursively search accessible folders and list matching files only; directories are kept in the separate Storage / All files screen.
- Images and Videos use a two-column thumbnail grid with locally generated image previews and video frames where supported; other typed collections use compact file rows.
- Recent files, Favorites, and Safe Folder collections.
- Search, create folders, open files, share, rename, copy, move, and send items to Trash.
- Installed-app manager shortcut.

### Clean
- A redesigned recommendations page for large files, exact duplicates, and APK installers.
- SHA-256 checks to group identical files. During clean-up, FilePilot offers the extra copies for review and keeps one copy from each group.
- Items are never removed automatically. The user chooses which candidates to send to FilePilot Trash.
- Scan limits protect very large storage collections from unbounded traversal.

### Share
- Choose any supported file using Android's file picker and share through Android's Sharesheet.
- Quick access to recently opened files for sharing. Available targets depend on the device and installed apps; this is not a custom offline-transfer protocol.

### Other tools
- **Trash:** restore deleted items to a folder, delete permanently, or empty Trash.
- **App manager:** search installed apps, open Android app information, and request uninstall for eligible user apps.
- **Security review:** locally review suspicious file types and misleading double extensions.
- **Safe Folder:** a PIN-protected vault encrypting file contents and the index locally with AES-GCM.
- Custom adaptive launcher icon, original vector art for tutorial/empty/success states, animated transitions, and a global processing indicator for core operations.
- Portuguese and English interface strings.

## Permissions and privacy

On Android 13 and later, FilePilot declares media permissions for images, videos, and audio. Access to the wider shared-storage area can require Android's **Manage all files** special access. A user can instead choose a folder through Android's Storage Access Framework; access is then limited by the selected folder and document provider.

The app manager declares installed-package visibility for its core app-management screen. Permission and package-visibility requirements vary by Android version and distribution policy; review them before submitting to an app store.

File browsing and SHA-256 duplicate checks run locally. The current project does not require a FilePilot account or upload scanned file contents to a server.

## Important limitations

- FilePilot's Trash is an app-private recovery area, not a system-wide recycle bin. Emptying Trash, clearing FilePilot's app data, or uninstalling it may permanently remove Trash contents.
- The Safe Folder is encrypted on the device; forgetting the PIN may make its contents unrecoverable. Clearing app data or uninstalling may delete the vault.
- **Security review is not a full antivirus.** It does not use a malware-signature database and cannot certify a file as safe. Keep Android/Google Play Protect enabled.
- Cleaning suggestions do not clear private caches belonging to other apps. Android controls those areas.
- Category scans are bounded and may not cover every folder on a very large device.
- Provider-specific file operations depend on the permissions and capabilities of the selected document provider.
- Google Files-inspired UX patterns are used as a reference; FilePilot uses its own name, colors, and implementation rather than Google's branding or source code.

## Build and release on Android

GitHub Actions runs unit tests and builds a debug APK on pushes to `main`. Open the repository's **Actions** tab, choose the newest successful **Android build** run, and download the `FilePilot-debug-apk` artifact. Extract the ZIP on your phone before installing.

The beta release workflow builds a release-variant APK, verifies its signature and package metadata, and publishes it to GitHub Releases. Beta APKs use the Android debug signing key for sideload testing; they are **not production-store signing builds**.

Before public release, test file operations and the Safe Folder on physical Android devices, publish an accurate privacy policy, review permission and store policies, and configure a private production signing key.
