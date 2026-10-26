package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

class FileRepository(private val context: Context) {

    fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result
    }

    fun getFileSize(uri: Uri): Long {
        var size = -1L
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (index >= 0) size = cursor.getLong(index)
                    }
                }
            } catch (e: Exception) {}
        }
        if (size <= 0) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                    size = fd.length
                }
            } catch (e: Exception) {}
        }
        return size
    }
}
