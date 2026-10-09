# FilePilot

A lightweight Android file manager built with Kotlin and Jetpack Compose.

## Current MVP scope

- Minimal, responsive home screen
- Browse files in shared storage when access is granted
- Alternative folder access using Android's Storage Access Framework
- Search and filter the current folder
- Open, share, rename, and delete files
- Basic category filtering
- GitHub Actions workflow to compile a debug APK and run unit tests

## Build

The project uses Android Gradle Plugin 8.13.2, Kotlin 2.2.10, Gradle 8.13, and Android SDK 36.

Open the repository's Actions tab, choose the latest workflow run, and download the FilePilot-debug-apk artifact if the run succeeds.

## Permissions and privacy

FilePilot is designed to work locally. This MVP does not upload file names, contents, or folder paths and does not yet include an advertising SDK.

The app requests broad file access only for its core file-manager functionality. If access is not granted, a user can choose a folder through Android's Storage Access Framework. Google Play eligibility for MANAGE_EXTERNAL_STORAGE must be reviewed before publication.

## Project status

This is an initial development build, not a production release. Device testing, accessibility checks, privacy documentation, store policy review, advertising integration, and release signing must be completed before publication.
