package com.deepanjanxyz.notepad.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deepanjanxyz.notepad.MainActivity
import com.deepanjanxyz.notepad.R
import com.deepanjanxyz.notepad.data.NoteRepositoryProvider
import com.deepanjanxyz.notepad.domain.model.DrawingSerializer

class NoteReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "note_reminders_channel"
        const val CHANNEL_NAME = "Note Reminders"
        const val KEY_NOTE_ID = "key_note_id"
        const val KEY_NOTE_TITLE = "key_note_title"
        const val KEY_NOTE_CONTENT = "key_note_content"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Reminders for notes and scheduled tasks"
                    enableLights(true)
                    enableVibration(true)
                }
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.createNotificationChannel(channel)
            }
        }
    }

    override suspend fun doWork(): Result {
        val noteId = inputData.getLong(KEY_NOTE_ID, -1L)
        if (noteId == -1L) {
            return Result.failure()
        }

        val repository = NoteRepositoryProvider.create(applicationContext)
        val note = repository.getNoteById(noteId)

        // If note was deleted or moved to trash, don't show notification
        if (note == null || note.inTrash) {
            return Result.success()
        }

        // Create channel on Oreo+
        createNotificationChannel(applicationContext)

        val displayTitle = when {
            note.title.isNotBlank() -> note.title
            inputData.getString(KEY_NOTE_TITLE)?.isNotBlank() == true -> inputData.getString(KEY_NOTE_TITLE)!!
            else -> applicationContext.getString(R.string.app_name)
        }

        val rawContent = if (note.content.isNotBlank()) {
            note.content
        } else {
            inputData.getString(KEY_NOTE_CONTENT) ?: ""
        }

        val isDrawing = DrawingSerializer.isDrawing(rawContent)
        val cleanPreview = when {
            isDrawing -> "🎨 Drawing note reminder"
            rawContent.isNotBlank() -> {
                rawContent.lines()
                    .map { line ->
                        line.replace(Regex("^\\[[ xX]\\]\\s*"), "• ")
                    }
                    .filter { it.isNotBlank() }
                    .take(4)
                    .joinToString("\n")
            }
            else -> "Reminder for your saved note"
        }

        // Tap intent to open MainActivity with specific note
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_note_id", noteId)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            noteId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(cleanPreview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cleanPreview))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = NotificationManagerCompat.from(applicationContext)
        if (!canDeliver(manager)) {
            // Nothing would be shown, so the reminder is not cleared: it is still
            // owed to the user, and retrying means it fires once notifications are
            // allowed again.
            return Result.retry()
        }

        try {
            manager.notify(noteId.toInt(), notification)
        } catch (e: SecurityException) {
            // The permission was revoked between the check and the post, so again
            // nothing was shown and the reminder is kept.
            return Result.retry()
        }

        // The notification was posted, so the reminder has now fired.
        repository.updateReminderTime(noteId, null)

        return Result.success()
    }

    /**
     * True when a notification posted now would actually be seen: the app's
     * notifications are allowed and its channel has not been switched off.
     *
     * On Android 13 and above a revoked permission makes notify() a silent no-op
     * rather than throwing, so this has to be asked before posting, not after.
     */
    private fun canDeliver(manager: NotificationManagerCompat): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        val channelBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
        return !channelBlocked
    }
}
