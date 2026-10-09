package com.nexauren.filepilot.data

enum class FileCategory {
    ALL, IMAGES, VIDEOS, AUDIO, DOCUMENTS, ARCHIVES, APKS, DOWNLOADS, OTHER;

    // Folders remain visible while filtering so users can navigate into subdirectories.
    fun matches(fileName: String, isDirectory: Boolean, location: String? = null): Boolean {
        // Typed categories are collections of files, never navigation shortcuts to folders.
        // Only ALL exposes directories; folder navigation lives in the Storage view.
        if (this == ALL) return true
        if (isDirectory) return false
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
        /** True when FilePilot has a built-in viewer for this file type. */
        fun hasInAppPreview(fileName: String, mimeType: String? = null): Boolean {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return when {
                mimeType.equals("application/pdf", ignoreCase = true) || extension == "pdf" -> true
                mimeType?.startsWith("image/") == true || extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "tif", "tiff") -> true
                mimeType?.startsWith("video/") == true || extension in setOf("mp4", "mkv", "mov", "webm", "avi", "3gp", "m4v", "mpeg", "mpg") -> true
                mimeType?.startsWith("audio/") == true || extension in setOf("mp3", "m4a", "wav", "ogg", "flac", "aac", "opus", "mid", "midi") -> true
                mimeType?.startsWith("text/") == true || extension in setOf("txt", "md", "csv", "log", "json", "xml", "html", "htm", "yaml", "yml", "ini", "conf", "properties") -> true
                else -> false
            }
        }

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
