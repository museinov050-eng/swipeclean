package com.nuzet.swipeclean

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri

data class Photo(
    val id: Long,
    val uri: Uri,
    val dateAdded: Long
)

object PhotoRepository {

    fun loadRandomPhotos(context: Context, limit: Int = 200): List<Photo> {
        val photos = mutableListOf<Photo>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val date = cursor.getLong(dateCol)
                val uri = Uri.withAppendedPath(collection, id.toString())
                photos.add(Photo(id, uri, date))
            }
        }

        return photos.shuffled().take(limit)
    }

    fun getRandomPhotoUri(context: Context): Uri? {
        return loadRandomPhotos(context, limit = 1).firstOrNull()?.uri
    }

    fun buildDeleteRequest(context: Context, uri: Uri): IntentSender? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(
                context.contentResolver,
                listOf(uri)
            ).intentSender
        } else {
            // Для старых версий — просто удаление без системного диалога
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
    }
}
