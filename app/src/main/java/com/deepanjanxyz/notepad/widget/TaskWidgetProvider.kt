package com.deepanjanxyz.notepad.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.widget.RemoteViewsCompat
import com.deepanjanxyz.notepad.MainActivity
import com.deepanjanxyz.notepad.R
import com.deepanjanxyz.notepad.data.NoteRepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Home-screen widget that surfaces the checklist items of every pinned note, so
 * pending tasks can be seen and ticked without opening the app.
 *
 * The pin is the control: pinning a checklist note puts it on the widget and
 * unpinning removes it, which reuses the gesture users already know instead of
 * adding a second place to manage the same thing.
 *
 * The rows are built from the pinned notes and handed to the list as a remote
 * collection, so however many tasks are pinned the list scrolls instead of being
 * cut off at the widget's edge. [RemoteViewsCompat] is what carries that
 * collection: the platform method for it only exists from Android 12, and this
 * widget is offered from Android 7.
 */
class TaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_ITEM -> {
                val noteId = intent.getLongExtra(EXTRA_NOTE_ID, INVALID_ID)
                val itemIndex = intent.getIntExtra(EXTRA_ITEM_INDEX, INVALID_INDEX)
                if (noteId != INVALID_ID && itemIndex >= 0) {
                    toggleItem(context, noteId, itemIndex)
                }
            }
            ACTION_REFRESH -> refresh(context)
            else -> super.onReceive(context, intent)
        }
    }

    private fun toggleItem(context: Context, noteId: Long, lineIndex: Int) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            // The repository flips the line inside a database transaction, so a
            // burst of taps cannot lose an update.
            NoteRepositoryProvider.create(appContext).toggleChecklistLine(noteId, lineIndex)
            renderAll(appContext)
        }
    }

    companion object {
        private const val ACTION_TOGGLE_ITEM = "com.deepanjanxyz.notepad.widget.ACTION_TOGGLE_ITEM"
        private const val ACTION_REFRESH = "com.deepanjanxyz.notepad.widget.ACTION_REFRESH"
        internal const val EXTRA_NOTE_ID = "extra_note_id"
        internal const val EXTRA_ITEM_INDEX = "extra_item_index"
        private const val INVALID_ID = -1L
        private const val INVALID_INDEX = -1
        private const val REQUEST_CODE_TOGGLE_TEMPLATE = 1000
        private const val REQUEST_CODE_REFRESH = 1
        private const val REQUEST_CODE_OPEN_APP = 0

        /**
         * Every row shares one layout, so the whole list is a single view type.
         * The collection has to be told that, or the host rebuilds the adapter
         * whenever a row it has not seen before arrives.
         */
        private const val ROW_VIEW_TYPE_COUNT = 1

        private val pendingIntentFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        /**
         * The collection template has to be mutable.
         *
         * A row does not carry its own PendingIntent: it fills in which note and
         * which line were tapped through [RemoteViews.setOnClickFillInIntent], and
         * the fill-in is merged into this template. From Android 12 an immutable
         * PendingIntent silently drops that fill-in, so the tap arrived with no
         * note id and nothing happened. Older releases have no mutability
         * requirement and stay mutable by default.
         */
        private val templatePendingIntentFlags: Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

        /** Rebuilds every placed widget from the current notes. */
        fun refresh(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch { renderAll(appContext) }
        }

        private fun renderAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TaskWidgetProvider::class.java)
            // The notes are read once for every placed widget: the rows are the
            // same for all of them, only the widget id differs.
            val rows = flattenRows(
                runBlocking { loadTaskGroups(NoteRepositoryProvider.create(context)) }
            )
            for (id in manager.getAppWidgetIds(component)) {
                manager.updateAppWidget(id, buildViews(context, id, rows))
            }
        }

        private fun buildViews(
            context: Context,
            appWidgetId: Int,
            rows: List<WidgetRow>
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tasks)

            val items = RemoteViewsCompat.RemoteCollectionItems.Builder()
                .setHasStableIds(false)
                .setViewTypeCount(ROW_VIEW_TYPE_COUNT)
            rows.forEachIndexed { index, row ->
                items.addItem(index.toLong(), buildRowViews(context, row))
            }
            RemoteViewsCompat.setRemoteAdapter(
                context = context,
                remoteViews = views,
                appWidgetId = appWidgetId,
                viewId = R.id.widget_list,
                items = items.build()
            )

            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
            // A row inside a collection cannot carry its own PendingIntent, so
            // the list holds the template and each row fills in which item it is.
            views.setPendingIntentTemplate(R.id.widget_list, toggleTemplateIntent(context))

            views.setOnClickPendingIntent(R.id.widget_title, openAppIntent(context))
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context))
            return views
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(context, REQUEST_CODE_OPEN_APP, intent, pendingIntentFlags)
        }

        /** The extras a row fills in so the template knows which item was tapped. */
        internal fun toggleFillInIntent(noteId: Long, itemIndex: Int): Intent = Intent().apply {
            putExtra(EXTRA_NOTE_ID, noteId)
            putExtra(EXTRA_ITEM_INDEX, itemIndex)
        }

        private fun toggleTemplateIntent(context: Context): PendingIntent {
            val intent = Intent(context, TaskWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_ITEM
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_TOGGLE_TEMPLATE,
                intent,
                templatePendingIntentFlags
            )
        }

        private fun refreshIntent(context: Context): PendingIntent {
            val intent = Intent(context, TaskWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            return PendingIntent.getBroadcast(context, REQUEST_CODE_REFRESH, intent, pendingIntentFlags)
        }
    }
}
