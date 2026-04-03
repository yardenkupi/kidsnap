package com.childfilter.app.unit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the isImageFile() predicate from FolderWatcherService.
 *
 * Extracted logic:
 *   fun isImageFile(name: String) = name.lowercase().let {
 *     it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp")
 *   }
 */
class IsImageFileTest {

    private fun isImageFile(name: String) = name.lowercase().let {
        it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp")
    }

    // ── Positive cases ──

    @Test
    fun `lowercase jpg extension is an image file`() {
        assertTrue(isImageFile("photo.jpg"))
    }

    @Test
    fun `lowercase jpeg extension is an image file`() {
        assertTrue(isImageFile("photo.jpeg"))
    }

    @Test
    fun `lowercase png extension is an image file`() {
        assertTrue(isImageFile("screenshot.png"))
    }

    @Test
    fun `lowercase webp extension is an image file`() {
        assertTrue(isImageFile("image.webp"))
    }

    @Test
    fun `uppercase JPG extension is an image file (case-insensitive)`() {
        assertTrue(isImageFile("PHOTO.JPG"))
    }

    @Test
    fun `uppercase JPEG extension is an image file`() {
        assertTrue(isImageFile("PHOTO.JPEG"))
    }

    @Test
    fun `uppercase PNG extension is an image file`() {
        assertTrue(isImageFile("SCREENSHOT.PNG"))
    }

    @Test
    fun `uppercase WEBP extension is an image file`() {
        assertTrue(isImageFile("IMAGE.WEBP"))
    }

    @Test
    fun `mixed case JpG extension is an image file`() {
        assertTrue(isImageFile("photo.JpG"))
    }

    @Test
    fun `typical WhatsApp filename format is an image file`() {
        assertTrue(isImageFile("IMG-20240101-WA0001.jpg"))
    }

    @Test
    fun `filename with dots in name and jpg extension is an image file`() {
        assertTrue(isImageFile("my.photo.summer.jpg"))
    }

    @Test
    fun `filename with spaces and jpg extension is an image file`() {
        assertTrue(isImageFile("my photo.jpg"))
    }

    // ── Negative cases ──

    @Test
    fun `mp4 video file is not an image file`() {
        assertFalse(isImageFile("video.mp4"))
    }

    @Test
    fun `gif file is not an image file`() {
        assertFalse(isImageFile("animation.gif"))
    }

    @Test
    fun `heic file is not an image file`() {
        assertFalse(isImageFile("photo.heic"))
    }

    @Test
    fun `txt file is not an image file`() {
        assertFalse(isImageFile("readme.txt"))
    }

    @Test
    fun `pdf file is not an image file`() {
        assertFalse(isImageFile("document.pdf"))
    }

    @Test
    fun `file without extension is not an image file`() {
        assertFalse(isImageFile("no_extension"))
    }

    @Test
    fun `empty string is not an image file`() {
        assertFalse(isImageFile(""))
    }

    @Test
    fun `just the extension without filename is an image file`() {
        assertTrue(isImageFile(".jpg"))
    }

    @Test
    fun `filename ending with jpg without dot is not an image file`() {
        assertFalse(isImageFile("photojpg"))
    }

    @Test
    fun `file with jpg in the middle but wrong extension is not an image file`() {
        assertFalse(isImageFile("photo.jpg.bak"))
    }

    @Test
    fun `dot-only filename is not an image file`() {
        assertFalse(isImageFile("."))
    }

    @Test
    fun `bmp file is not an image file (not in supported list)`() {
        assertFalse(isImageFile("image.bmp"))
    }

    @Test
    fun `tiff file is not an image file`() {
        assertFalse(isImageFile("scan.tiff"))
    }

    @Test
    fun `filename with path separator and valid extension is an image file`() {
        // FileObserver typically provides just the filename, not full path
        // but we still test that the function handles it correctly
        assertTrue(isImageFile("WhatsApp/Media/photo.jpg"))
    }
}
