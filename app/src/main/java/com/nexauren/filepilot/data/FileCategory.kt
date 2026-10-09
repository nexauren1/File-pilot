package com.nexauren.filepilot.data

enum class FileCategory {
    ALL, IMAGES, VIDEOS, AUDIO, DOCUMENTS, ARCHIVES, APKS, APPS, DOWNLOADS, OTHER;

    // Folders remain visible while filtering so users can navigate into subdirectories.
    fun matches(fileName: String, isDirectory: Boolean, location: String? = null): Boolean {
        // Typed categories are collections of files, never navigation shortcuts to folders.
        // Only ALL exposes directories; folder navigation lives in the Storage view.
        if (this == ALL) return true
        if (isDirectory) return false
        // Installed applications are managed by PackageManager, not indexed as ordinary files.
        if (this == APPS) return false
        if (this == DOWNLOADS) {
            val path = location.orEmpty().replace('\\', '/').lowercase()
            return path.contains("/download/") ||
                path.endsWith("/download") ||
                path.contains("/downloads/") ||
                path.endsWith("/downloads") ||
                (path.startsWith("content://") && path.contains("download"))
        }
        return fromFileName(fileName) == this
    }

    companion object {
        fun fromFileName(fileName: String, isDirectory: Boolean = false): FileCategory {
            if (isDirectory) return OTHER
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return when (extension) {
                "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "tif", "tiff" -> IMAGES
                "mp4", "mkv", "mov", "webm", "avi", "3gp", "m4v", "mpeg", "mpg" -> VIDEOS
                "mp3", "m4a", "wav", "ogg", "flac", "aac", "opus", "mid", "midi" -> AUDIO
                "pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "rtf", "odt", "ods", "odp" -> DOCUMENTS
                "zip", "rar", "7z", "tar", "gz", "bz", "bz2", "xz" -> ARCHIVES
                "apk", "apks", "xapk" -> APKS
                else -> OTHER
            }
        }
    }
}
