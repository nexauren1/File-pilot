package com.nexauren.filepilot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileCategoryTest {
    @Test fun recognizesCommonTypesIgnoringCase() {
        assertEquals(FileCategory.IMAGES, FileCategory.fromFileName("holiday.JPG"))
        assertEquals(FileCategory.VIDEOS, FileCategory.fromFileName("clip.mp4"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromFileName("voice.m4a"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromFileName("report.PDF"))
        assertEquals(FileCategory.ARCHIVES, FileCategory.fromFileName("backup.zip"))
        assertEquals(FileCategory.APKS, FileCategory.fromFileName("package.apk"))
    }

    @Test fun categoryCollectionsExcludeDirectories() {
        assertTrue(FileCategory.ALL.matches("Pictures", true))
        assertFalse(FileCategory.IMAGES.matches("Pictures", true))
        assertFalse(FileCategory.VIDEOS.matches("Movies", true))
        assertFalse(FileCategory.DOWNLOADS.matches("Download", true, "/storage/emulated/0/Download"))
        assertEquals(FileCategory.OTHER, FileCategory.fromFileName("Pictures", true))
    }

    @Test fun categoryFiltersIncludeOnlyMatchingFiles() {
        assertTrue(FileCategory.IMAGES.matches("holiday.jpg", false))
        assertTrue(FileCategory.VIDEOS.matches("clip.mp4", false))
        assertFalse(FileCategory.VIDEOS.matches("clip.jpg", false))
        assertFalse(FileCategory.IMAGES.matches("document.pdf", false))
    }

    @Test fun downloadsCategoryUsesLocationPath() {
        assertTrue(FileCategory.DOWNLOADS.matches("report.pdf", false, "/storage/emulated/0/Download/report.pdf"))
        assertTrue(FileCategory.DOWNLOADS.matches("photo.jpg", false, "content://provider/tree/primary%3ADownload/document/primary%3ADownload%2Fphoto.jpg"))
        org.junit.Assert.assertFalse(FileCategory.DOWNLOADS.matches("report.pdf", false, "/storage/emulated/0/Documents/report.pdf"))
    }

    @Test fun unknownExtensionsAreOther() {
        assertEquals(FileCategory.OTHER, FileCategory.fromFileName("data.custom"))
        assertEquals(FileCategory.OTHER, FileCategory.fromFileName("README"))
    }
}
