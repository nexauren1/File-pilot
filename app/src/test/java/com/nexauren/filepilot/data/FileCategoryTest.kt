package com.nexauren.filepilot.data

import org.junit.Assert.assertEquals
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

    @Test fun keepsDirectoriesVisibleInTypedFilters() {
        assertTrue(FileCategory.ALL.matches("Pictures", true))
        assertTrue(FileCategory.IMAGES.matches("Pictures", true))
        assertEquals(FileCategory.OTHER, FileCategory.fromFileName("Pictures", true))
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
