package pl.liczniki.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

class ReportProvider : ContentProvider() {
    private fun report(uri: Uri): File? {
        if (uri.lastPathSegment != "LICZNIKI_RAPORT.xlsx") return null
        return File(requireNotNull(context).cacheDir, "LICZNIKI_RAPORT.xlsx")
    }
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String? = if (report(uri) != null)
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else null
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val file = report(uri) ?: return null
        val columns = projection?.toList() ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns.toTypedArray())
        cursor.addRow(columns.map { when (it) {
            OpenableColumns.DISPLAY_NAME -> file.name
            OpenableColumns.SIZE -> file.length()
            else -> null
        } }.toTypedArray())
        return cursor
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw java.io.FileNotFoundException("Tylko odczyt")
        val file = report(uri) ?: throw java.io.FileNotFoundException("Nieznany raport")
        if (!file.exists()) throw java.io.FileNotFoundException("Brak raportu")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
