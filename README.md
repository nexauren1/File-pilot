# FilePilot

FilePilot is an Android file manager with its own visual identity, inspired by useful file-management patterns in Files by Google while keeping an independent design and implementation.

## FilePilot 0.5.0 beta

### Browse and home dashboard
- Collections for Downloads, Images, Videos, Audio, Documents, Archives, APK installer files, and Other files.
- Every file category on the home screen shows an item count and the total size found by the bounded scan; large storage locations may be only partially counted.
- **Apps** is a separate collection for installed Android applications, not APK installer files. The app list offers Launch (when available), App info, and uninstall request for eligible apps. APK files remain a separate file category.
- Image and video collections use a thumbnail grid where the device's Android codecs and document provider support previews.
- Recent files, starred Favorites, and the encrypted Safe Folder are visible from Home.
- File operations include browse folders, search, sort category matches by recent modification, create folders, open, share, rename, copy, move, star/unstar, and send files to FilePilot Trash.
- The navigation drawer is grouped into Navigation, Collections, and Tools & settings.

### Built-in viewers and players
- **PDF:** native Android PdfRenderer pages in a vertical reader for PDFs readable by the Android renderer.
- **Images:** locally decoded, downsampled previews for common image formats supported by Android.
- **Video:** Android VideoView and playback controls, using device codecs.
- **Audio:** Android MediaPlayer with play/pause and seek controls, using device codecs.
- **Text:** plain-text and common text formats, showing at most the first 1 MB in the viewer.
- **Other formats:** open-with chooser for compatible apps installed on Android. FilePilot does not yet include native editing/rendering for Word, Excel, PowerPoint, EPUB, archives, APK packages, or every image/video/audio codec.

### Clean and tools
- Cleaner reviews large files, APK installers, and exact duplicates based on SHA-256. The user decides what to move to Trash.
- App manager searches installed packages, launches eligible apps, opens Android's app info page, and requests uninstall for eligible user apps.
- Security review checks selected suspicious file patterns locally; it is a heuristic review, not an antivirus.
- Safe Folder encrypts file contents and its index locally with AES-GCM and is protected by the user's PIN.
- Trash stores deleted items in FilePilot's app-private storage and allows restore or permanent deletion.
- The Share screen uses Android's native sharing sheet. It is not a custom offline device-to-device transfer protocol.
- Portuguese and English interface strings, custom adaptive launcher icon, original vector illustrations, animated transitions, and a global processing indicator.

## How category sizes work

Category counts and sizes are calculated from accessible files under the selected storage root, using a bounded recursive scan (up to 8,000 files and 8 directory levels on the home dashboard). Items outside the current permissions, restricted Android directories, and files beyond the scan limit may be absent from the totals. Installed-app count is separate from APK-file count. File sizes are the file contents' reported sizes; they are not Android package-installation storage usage.

## Permissions, privacy, and limitations

The app declares media permissions for images, video, and audio on modern Android versions, installed-package visibility for its app manager, and may request Android's special “Manage all files” access. Alternatively, users can choose a folder through Android's Storage Access Framework; access then depends on the selected folder and document provider.

File browsing, local previews, category sizing, and SHA-256 duplicate checks run on the device. The project does not require a FilePilot account or upload scanned file contents to a server.

- FilePilot Trash is not Android's system-wide recycle bin. Emptying Trash, clearing app data, or uninstalling FilePilot may permanently remove Trash contents.
- Safe Folder contents may be unrecoverable if the PIN is forgotten. Clearing app data or uninstalling can delete the vault. As with all private vaults, test with copies of important files first.
- Installed applications cannot be stored inside Safe Folder; only supported files can be added.
- Security review is not a full antivirus and cannot certify a file as safe. Keep Android/Google Play Protect enabled.
- Category totals are estimates from a bounded scan, not a full disk-usage audit.
- Reader playback/rendering depends on Android's supported codecs, document provider permissions, and file readability. Some protected or damaged PDFs may not render.
- The custom QUERY_ALL_PACKAGES and broad storage access permissions require review against the policies of each app distribution store.
- Google Files is a UX reference only. FilePilot uses its own name, branding, colors, and implementation and does not promise feature parity with every Google Files capability.

## Build and beta installation

GitHub Actions runs unit tests and builds a debug APK on pushes and pull requests to main. Open the repository's Actions tab, choose the newest successful Android build run, and download the FilePilot-debug-apk artifact. Extract the ZIP before installing.

The beta release workflow builds a release-variant APK, verifies its signature and package metadata, and publishes the APK to GitHub Releases. Beta APKs use the Android debug signing key for sideload testing; they are not production-store signing builds.

Before a public store launch, test file operations and Safe Folder on physical Android devices, publish a privacy policy matching actual implementation, review Android permission and store policies, and configure a private production signing key.
