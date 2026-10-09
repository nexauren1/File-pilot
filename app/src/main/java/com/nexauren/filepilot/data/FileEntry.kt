package com.nexauren.filepilot.data

data class FileEntry(
    val location: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val mimeType: String?,
) {
    val category: FileCategory
        get() = FileCategory.fromFileName(name, isDirectory)
}
