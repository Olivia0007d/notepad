package com.deepanjanxyz.notepad.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import java.io.IOException

/**
 * The folder the user has handed to the app to keep its backup in.
 *
 * A backup file written by an earlier installation belongs to that installation.
 * After a reinstall this one cannot read it, write over it or delete it, and the
 * media store quietly saves every new backup beside it as "name (1)", "name (2)"
 * and so on - one more file on every run, for ever. The only way in is for the
 * user to grant the folder itself, once. This holds that grant and the file
 * operations it makes possible.
 */
object BackupFolder {

    /** Name of the folder the backup is kept in, under Downloads. */
    const val FOLDER_NAME = "EliteMemoPro"

    private const val PREFERENCES = "backup_folder"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_ADOPT_PENDING = "adopt_pending"
    private const val AUTHORITY = "com.android.externalstorage.documents"
    private const val MIME_TYPE = "application/json"

    /** One file in the granted folder. */
    data class Child(val uri: Uri, val name: String, val lastModified: Long)

    /** The granted folder, or null when the user has not granted one. */
    fun treeUri(context: Context): Uri? =
        preferences(context).getString(KEY_TREE_URI, null)?.let(Uri::parse)

    fun hasAccess(context: Context): Boolean = treeUri(context) != null

    /**
     * The folder the picker should open at, so the user only has to confirm it.
     * Null below Android 8, where the picker has no way to be pointed at it.
     */
    fun initialUri(folder: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) folderDocumentUri(folder) else null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun folderDocumentUri(folder: String): Uri =
        DocumentsContract.buildDocumentUri(
            AUTHORITY,
            "primary:${Environment.DIRECTORY_DOWNLOADS}/$folder"
        )

    /**
     * Keeps the folder the user picked, so it survives restarts and reboots. The
     * folder is also marked for adoption: whatever backup is already in it was
     * not written by this installation, and its notes are brought in on the first
     * write rather than overwritten unseen.
     */
    fun take(context: Context, uri: Uri): Boolean {
        val taken = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.isSuccess
        if (taken) {
            preferences(context).edit()
                .putString(KEY_TREE_URI, uri.toString())
                .putBoolean(KEY_ADOPT_PENDING, true)
                .apply()
        }
        return taken
    }

    /** True once, for the first write after a folder is granted. */
    fun consumeAdoption(context: Context): Boolean {
        val pending = preferences(context).getBoolean(KEY_ADOPT_PENDING, false)
        if (pending) {
            preferences(context).edit().putBoolean(KEY_ADOPT_PENDING, false).apply()
        }
        return pending
    }

    /** Every file in the granted folder. */
    fun children(context: Context): List<Child> {
        val tree = treeUri(context) ?: return emptyList()
        val children = mutableListOf<Child>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree)
        )
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                children += Child(
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                    name = name,
                    lastModified = cursor.getLong(2)
                )
            }
        }
        return children
    }

    /** The file in the folder with this exact name, or null. */
    fun find(context: Context, name: String): Child? =
        children(context).firstOrNull { it.name == name }

    fun read(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()

    /** Replaces the contents of an existing file and reads them back. */
    fun write(context: Context, uri: Uri, json: String) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Could not open the backup file for writing")
        stream.use { it.write(json.toByteArray()) }
        if (read(context, uri) != json) {
            throw IOException("The backup could not be verified after writing")
        }
    }

    /**
     * Creates the file under exactly this name, or returns null when it cannot be
     * created - which is what keeps a second file from ever being written beside
     * the first.
     */
    fun create(context: Context, name: String): Uri? {
        val tree = treeUri(context) ?: return null
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree)
        )
        return runCatching {
            DocumentsContract.createDocument(context.contentResolver, parent, MIME_TYPE, name)
        }.getOrNull()
    }

    fun delete(context: Context, uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            .getOrDefault(false)

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
