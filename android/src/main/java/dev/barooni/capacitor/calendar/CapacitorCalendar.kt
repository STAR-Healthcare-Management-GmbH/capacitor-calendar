package dev.barooni.capacitor.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import dev.barooni.capacitor.calendar.model.RecurrenceRule
import java.util.Calendar
import java.util.TimeZone

class CapacitorCalendar {
    var eventIdsArray: List<Long> = emptyList()

    @Throws(Exception::class)
    fun fetchCalendarEventIDs(context: Context): List<Long> {
        val projection = arrayOf(CalendarContract.Events._ID)
        val uri = CalendarContract.Events.CONTENT_URI
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        val eventIds = mutableListOf<Long>()

        cursor?.use {
            while (it.moveToNext()) {
                val eventId = it.getLong(0)
                eventIds.add(eventId)
            }
        }
        return eventIds
    }

    @Throws(Exception::class)
    fun getNewEventIds(newIds: List<Long>): List<Long> = newIds.filterNot { it in eventIdsArray }

    @Throws(Exception::class)
    fun listCalendars(context: Context): JSArray {
        val projection =
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR,
            )

        val calendars = JSArray()

        context.contentResolver
            .query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                val idColumnIndex = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val nameColumnIndex = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val calendarColorColumnIndex = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumnIndex)
                    val title = cursor.getString(nameColumnIndex)
                    val calendarColor = cursor.getInt(calendarColorColumnIndex)
                    val calendar =
                        JSObject().apply {
                            put("id", "$id")
                            put("title", title)
                            put("color", String.format("#%06X", 0xFFFFFF and calendarColor))
                        }

                    calendars.put(calendar)
                }
            } ?: throw Exception("Cursor is null")

        return calendars
    }

    @Throws(Exception::class)
    fun getDefaultCalendar(context: Context): JSObject? {
        val projection =
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            )

        val selection = "${CalendarContract.Calendars.IS_PRIMARY} = ?"
        val selectionArgs = arrayOf("1")

        context.contentResolver
            .query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumnIndex = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                    val nameColumnIndex = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                    val id = cursor.getLong(idColumnIndex)
                    val title = cursor.getString(nameColumnIndex)

                    val calendarObject =
                        JSObject().apply {
                            put("id", id.toString())
                            put("title", title)
                        }
                    return calendarObject
                } else {
                    return null
                }
            }
        throw Exception("No primary calendar found")
    }

    @Throws(Exception::class)
    fun createEvent(
        context: Context,
        title: String,
        calendarId: String?,
        location: String?,
        startDate: Long?,
        endDate: Long?,
        isAllDay: Boolean?,
        alertOffsetInMinutesSingle: Float?,
        alertOffsetInMinutesMultiple: JSArray?,
        url: String?,
        notes: String?,
        recurrenceRule: RecurrenceRule?,
    ): Uri {
        val startMillis = startDate ?: Calendar.getInstance().timeInMillis
        val endMillis = endDate ?: (startMillis + 3600 * 1000)

        val eventValues =
            ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, title)
                location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
                put(CalendarContract.Events.CALENDAR_ID, calendarId ?: getDefaultCalendar(context)?.getString("id"))
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                isAllDay?.let { put(CalendarContract.Events.ALL_DAY, if (it) 1 else 0) }
                put(CalendarContract.Events.DESCRIPTION, listOfNotNull(notes, url?.let { "URL: $it" }).joinToString("\n"))
                recurrenceRule?.let { put(CalendarContract.Events.RRULE, it.toString()) }
            }

        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, eventValues)
        val eventId = eventUri?.lastPathSegment?.toLong() ?: throw IllegalArgumentException("Failed to convert event id to long")

        when {
            alertOffsetInMinutesSingle != null && alertOffsetInMinutesSingle > -1 -> {
                val alertValues = createAlertValues(eventId, alertOffsetInMinutesSingle)
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, alertValues)
            }
            alertOffsetInMinutesMultiple != null -> {
                alertOffsetInMinutesMultiple
                    .toList<Any>()
                    .mapNotNull { alert ->
                        try {
                            val alertFloat = alert.toString().toFloat()
                            if (alertFloat > -1) alertFloat else null
                        } catch (e: NumberFormatException) {
                            Log.e("Error", "Failed to convert alert to float: $alert", e)
                            null
                        }
                    }.forEach { alertFloat ->
                        val alertValues = createAlertValues(eventId, alertFloat)
                        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, alertValues)
                    }
            }
        }

        return eventUri
    }

    @Throws(Exception::class)
    fun createCalendar(context: Context, name: String, color: String?): Uri {
        val calendarValues =
            ContentValues().apply {
                put(CalendarContract.SyncState.ACCOUNT_NAME, name)
                put(CalendarContract.SyncState.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, name)
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name)
                color?.let { put(CalendarContract.Calendars.CALENDAR_COLOR, color) }
                put(CalendarContract.Calendars.VISIBLE, 1)
            }

        val calendarsUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.SyncState.ACCOUNT_NAME, name)
            .appendQueryParameter(
                CalendarContract.SyncState.ACCOUNT_TYPE,
                CalendarContract.ACCOUNT_TYPE_LOCAL
            )
            .build()

        return context.contentResolver.insert(calendarsUri, calendarValues)?: throw Exception("Calendar was not created")
    }

    fun deleteCalendar(context: Context, id: Long): Boolean {
        val deleteUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id)
        val rows = context.contentResolver.delete(deleteUri, null, null)
        return rows > 0
    }

    @Throws(Exception::class)
    fun modifyEvent(
        context: Context,
        id: Long,
        update: JSObject,
    ): Boolean {
        val title = update.getString("title")
        val calendarId = update.getString("calendarId")
        val location = update.getString("location")
        val startDate = update.getLong("startDate")
        val endDate = update.getLong("endDate")
        val isAllDay = update.getBoolean("isAllDay")
        val url = update.getString("url")
        val notes = update.getString("notes")

        val eventUri: Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)

        val values =
            ContentValues().apply {
                if (title != null) put(CalendarContract.Events.TITLE, title)
                if (calendarId != null) put(CalendarContract.Events.CALENDAR_ID, calendarId)
                if (location != null) put(CalendarContract.Events.EVENT_LOCATION, location)
                if (startDate != null) put(CalendarContract.Events.DTSTART, startDate)
                if (endDate != null) put(CalendarContract.Events.DTEND, endDate)
                if (isAllDay != null) put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
                if (notes !=
                    null
                ) {
                    put(CalendarContract.Events.DESCRIPTION, listOfNotNull(notes, url?.let { "URL: $it" }).joinToString("\n"))
                }
            }

        val rows: Int = context.contentResolver.update(eventUri, values, null, null)
        return rows > 0
    }

    private fun createAlertValues(
        eventId: Long,
        alertOffset: Float,
    ): ContentValues =
        ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, alertOffset)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }

    @Throws(Exception::class)
    fun openCalendar(timestamp: Long): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("content://com.android.calendar/time/$timestamp")
        }

    @Throws(Exception::class)
    fun listEventsInRange(
        context: Context,
        startDate: Long,
        endDate: Long,
    ): JSArray {
        val projection =
            arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.CALENDAR_COLOR,
                CalendarContract.Events.ORGANIZER,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_TIMEZONE,
                CalendarContract.Events.EVENT_END_TIMEZONE,
                CalendarContract.Events.DURATION,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.CALENDAR_ID,
            )
        val selection = "(${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTEND} <= ?)"
        val selectionArgs = arrayOf(startDate.toString(), endDate.toString())

        val events = JSArray()

        context.contentResolver
            .query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                val idColumnIndex = cursor.getColumnIndex(CalendarContract.Events._ID)
                val nameColumnIndex = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val locationColumnIndex = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val calendarColorColumnIndex = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_COLOR)
                val organizerColumnIndex = cursor.getColumnIndex(CalendarContract.Events.ORGANIZER)
                val descriptionColumnIndex = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val dtStartColumnIndex = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val dtEndColumnIndex = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                val eventTimezoneColumnIndex = cursor.getColumnIndex(CalendarContract.Events.EVENT_TIMEZONE)
                val eventEndTimezoneColumnIndex = cursor.getColumnIndex(CalendarContract.Events.EVENT_END_TIMEZONE)
                val durationColumnIndex = cursor.getColumnIndex(CalendarContract.Events.DURATION)
                val isAllDayColumnIndex = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
                val calendarIdColumnIndex = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumnIndex)
                    val title = cursor.getString(nameColumnIndex)
                    val location = cursor.getString(locationColumnIndex)
                    val calendarColor = cursor.getInt(calendarColorColumnIndex)
                    val organizer = cursor.getString(organizerColumnIndex)
                    val desc = cursor.getString(descriptionColumnIndex)
                    val dtStart = cursor.getLong(dtStartColumnIndex)
                    val dtEnd = cursor.getLong(dtEndColumnIndex)
                    val eventTimezone = cursor.getString(eventTimezoneColumnIndex)
                    val eventEndTimezone = cursor.getString(eventEndTimezoneColumnIndex)
                    val duration = cursor.getString(durationColumnIndex)
                    val allDay = cursor.getInt(isAllDayColumnIndex) == 1
                    val calendarId = cursor.getLong(calendarIdColumnIndex)

                    val event =
                        JSObject().apply {
                            put("id", id.toString())
                            title?.takeIf { it.isNotEmpty() }?.let { put("title", it) }
                            location?.takeIf { it.isNotEmpty() }?.let { put("location", it) }
                            calendarColor.takeIf { it != 0 }?.let { put("eventColor", String.format("#%06X", 0xFFFFFF and it)) }
                            organizer?.takeIf { it.isNotEmpty() }?.let { put("organizer", it) }
                            desc?.takeIf { it.isNotEmpty() }?.let { put("description", it) }
                            dtStart.takeIf { it != 0.toLong() }?.let { put("startDate", it) }
                            dtEnd.takeIf { it != 0.toLong() }?.let { put("endDate", it) }
                            eventTimezone?.takeIf { it.isNotEmpty() }?.let { timezone ->
                                val abbreviation = getTimeZoneAbbreviation(timezone)
                                val obj = JSObject()
                                obj.put("region", timezone)
                                obj.put("abbreviation", abbreviation)
                                put("eventTimezone", obj)
                            }
                            eventEndTimezone?.takeIf { it.isNotEmpty() }?.let {
                                val abbreviation = getTimeZoneAbbreviation(it)
                                val obj = JSObject()
                                obj.put("region", it)
                                obj.put("abbreviation", abbreviation)
                                put("eventEndTimezone", obj)
                            }
                            duration?.takeIf { it.isNotEmpty() }?.let { put("duration", it) }
                            put("isAllDay", allDay)
                            calendarId.takeIf { it != 0.toLong() }?.let { put("calendarId", it.toString()) }
                        }
                    events.put(event)
                }
            } ?: throw Exception("Cursor is null")
        return events
    }

    @Throws(Exception::class)
    fun deleteEventsById(
        context: Context,
        ids: JSArray,
    ): JSObject {
        val deletedEvents = JSArray()
        val failedToDeleteEvents = JSArray()
        val contentResolver = context.contentResolver

        ids.toList<String>().forEach { id ->
            try {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id.toLong())
                val rowsDeleted = contentResolver.delete(uri, null, null)
                if (rowsDeleted > 0) {
                    deletedEvents.put(id)
                } else {
                    failedToDeleteEvents.put(id)
                }
            } catch (error: Exception) {
                failedToDeleteEvents.put(id)
            }
        }

        val ret = JSObject()
        ret.put("deleted", deletedEvents)
        ret.put("failed", failedToDeleteEvents)
        return ret
    }

    fun getTimeZoneAbbreviation(timeZoneId: String): String {
        val timeZone = TimeZone.getTimeZone(timeZoneId)
        val now = Calendar.getInstance(timeZone)
        return timeZone.getDisplayName(timeZone.inDaylightTime(now.time), TimeZone.SHORT)
    }
}
