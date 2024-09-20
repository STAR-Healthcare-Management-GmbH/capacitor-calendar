package dev.barooni.capacitor.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.result.ActivityResult
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import dev.barooni.capacitor.calendar.model.RecurrenceFrequency
import dev.barooni.capacitor.calendar.model.RecurrenceRule

@CapacitorPlugin(
    name = "CapacitorCalendar",
    permissions = [
        Permission(
            alias = "readCalendar",
            strings = [
                Manifest.permission.READ_CALENDAR,
            ],
        ),
        Permission(
            alias = "writeCalendar",
            strings = [
                Manifest.permission.WRITE_CALENDAR,
            ],
        ),
        Permission(
            alias = "readWriteCalendar",
            strings = [
                Manifest.permission.WRITE_CALENDAR,
                Manifest.permission.READ_CALENDAR,
            ],
        ),
    ],
)
class CapacitorCalendarPlugin : Plugin() {
    private var implementation = CapacitorCalendar()
    private var eventIdOptional = false

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun createEventWithPrompt(call: PluginCall) {
        try {
            val title = call.getString("title", "")
            val calendarId = call.getString("calendarId")
            val location = call.getString("location")
            val startDate = call.getLong("startDate")
            val endDate = call.getLong("endDate")
            val isAllDay = call.getBoolean("isAllDay", false)
            val url = call.getString("url")
            val notes = call.getString("notes")
            eventIdOptional = call.getBoolean("eventIdOptional", false) ?: false

            if (!eventIdOptional) implementation.eventIdsArray = implementation.fetchCalendarEventIDs(context)

            val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)

            intent.putExtra(CalendarContract.Events.TITLE, title)
            calendarId?.let { intent.putExtra(CalendarContract.Events.CALENDAR_ID, it) }
            location?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            startDate?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
            endDate?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            isAllDay?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, it) }
            intent.putExtra(CalendarContract.Events.DESCRIPTION, listOfNotNull(notes, url?.let { "URL: $it" }).joinToString("\n"))

            return startActivityForResult(
                call,
                intent,
                "openCalendarIntentActivityCallback",
            )
        } catch (error: Exception) {
            call.reject("", "[CapacitorCalendar.${::openCalendarIntentActivityCallback.name}] Could not create the event")
            return
        }
    }

    @ActivityCallback
    private fun openCalendarIntentActivityCallback(
        call: PluginCall?,
        result: ActivityResult,
    ) {
        if (call == null) {
            throw Exception("[CapacitorCalendar.${::createEventWithPrompt.name}] Call is not defined")
        }

        val newIdsArray = JSArray()
        if (!eventIdOptional) {
            val newEventIds = implementation.getNewEventIds(implementation.fetchCalendarEventIDs(context))
            newEventIds.forEach { id -> newIdsArray.put(id.toString()) }
        }
        val ret = JSObject()
        ret.put("result", newIdsArray)
        call.resolve(ret)
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun modifyEventWithPrompt(call: PluginCall) {
        try {
            val stringId =
                call.getString("id") ?: throw Exception("[CapacitorCalendar.${::modifyEventWithPrompt.name}] Event ID not defined")
            val update = call.getObject("update")
            val uri: Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, stringId.toLong())
            val intent =
                Intent(Intent.ACTION_EDIT)
                    .setData(uri)

            if (update != null) {
                val title = update.getString("title")
                val calendarId = update.getString("calendarId")
                val location = update.getString("location")
                val startDate = update.getLong("startDate")
                val endDate = update.getLong("endDate")
                val isAllDay = update.getBoolean("isAllDay")
                val url = update.getString("url")
                val notes = update.getString("notes")

                intent.putExtra(CalendarContract.Events.TITLE, title)
                calendarId?.let { intent.putExtra(CalendarContract.Events.CALENDAR_ID, it) }
                location?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                startDate?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                endDate?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
                isAllDay?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, it) }
                intent.putExtra(CalendarContract.Events.DESCRIPTION, listOfNotNull(notes, url?.let { "URL: $it" }).joinToString("\n"))
            }

            return startActivityForResult(
                call,
                intent,
                "openEventEditIntentActivityCallback",
            )
        } catch (error: Exception) {
            call.reject("", error.message)
            return
        }
    }

    @ActivityCallback
    private fun openEventEditIntentActivityCallback(
        call: PluginCall?,
        result: ActivityResult,
    ) {
        if (call == null) {
            throw Exception("[CapacitorCalendar.${::createEventWithPrompt.name}] Call is not defined")
        }
        val ret = JSObject()
        ret.put("result", JSArray())
        call.resolve(ret)
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun modifyEvent(call: PluginCall) {
        try {
            val stringId = call.getString("id") ?: throw Exception("[CapacitorCalendar.${::modifyEvent.name}] Event ID not defined")
            val update = call.getObject("update") ?: throw Exception("[CapacitorCalendar.${::modifyEvent.name}] Update not provided")
            val updated = implementation.modifyEvent(context, stringId.toLong(), update)
            if (updated) {
                call.resolve()
            } else {
                throw Exception("[CapacitorCalendar.${::modifyEvent.name}] Event not updated")
            }
        } catch (error: Exception) {
            call.reject("", error.message)
            return
        }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun checkPermission(call: PluginCall) {
        try {
            val permissionName =
                call.getString("alias")
                    ?: throw Exception("[CapacitorCalendar.${::checkPermission.name}] Permission name is not defined")
            val permissionState =
                getPermissionState(permissionName)
                    ?: throw Exception(
                        "[CapacitorCalendar.${::checkPermission.name}] Could not determine the status of the requested permission",
                    )
            val ret = JSObject()
            ret.put("result", permissionState)
            call.resolve(ret)
        } catch (error: Exception) {
            call.reject("", error.message)
            return
        }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun checkAllPermissions(call: PluginCall) {
        try {
            return checkPermissions(call)
        } catch (_: Exception) {
            call.reject("", "[CapacitorCalendar.${::checkAllPermissions.name}] Could not determine the status of the requested permissions")
            return
        }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun requestPermission(call: PluginCall) {
        try {
            val alias =
                call.getString("alias")
                    ?: throw Exception("[CapacitorCalendar.${::requestPermission.name}] Permission name is not defined")
            return requestPermissionForAlias(
                alias,
                call,
                "requestPermissionCallback",
            )
        } catch (error: Exception) {
            call.reject("", error.message)
            return
        }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun requestWriteOnlyCalendarAccess(call: PluginCall) {
        val permissionName = "writeCalendar"
        try {
            return requestPermissionForAlias(
                permissionName,
                call,
                "requestWriteOnlyCalendarAccessCallback",
            )
        } catch (_: Exception) {
            throw Exception("${::requestPermissionCallback.name} Could not authorize $permissionName")
        }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun requestReadOnlyCalendarAccess(call: PluginCall) {
        val permissionName = "readCalendar"
        try {
            return requestPermissionForAlias(
                permissionName,
                call,
                "requestReadOnlyCalendarAccessCallback",
            )
        } catch (_: Exception) {
            throw Exception("${::requestPermissionCallback.name} Could not authorize $permissionName")
        }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun requestFullCalendarAccess(call: PluginCall) {
        val permissionName = "readWriteCalendar"
        try {
            return requestPermissionForAlias(
                permissionName,
                call,
                "requestFullCalendarAccessCallback",
            )
        } catch (_: Exception) {
            throw Exception("${::requestPermissionCallback.name} Could not authorize $permissionName")
        }
    }

    @PermissionCallback
    private fun requestPermissionCallback(call: PluginCall) {
        val permissionName = call.getString("alias")
        try {
            val ret = JSObject()
            ret.put("result", getPermissionState(permissionName))
            call.resolve(ret)
        } catch (_: Exception) {
            throw Exception("${::requestPermissionCallback.name} Could not authorize $permissionName")
        }
    }

    @PermissionCallback
    private fun requestWriteOnlyCalendarAccessCallback(call: PluginCall) {
        val permissionName = "writeCalendar"
        try {
            val ret = JSObject()
            ret.put("result", getPermissionState(permissionName))
            call.resolve(ret)
        } catch (_: Exception) {
            throw Exception("${::requestPermissionCallback.name} Could not authorize $permissionName")
        }
    }

    @PermissionCallback
    private fun requestReadOnlyCalendarAccessCallback(call: PluginCall) {
        val permissionName = "readCalendar"
        try {
            val ret = JSObject()
            ret.put("result", getPermissionState(permissionName))
            call.resolve(ret)
        } catch (_: Exception) {
            throw Exception("${::requestPermissionCallback.name} Could not authorize $permissionName")
        }
    }

    @PermissionCallback
    private fun requestFullCalendarAccessCallback(call: PluginCall) {
        val permissionName = "readWriteCalendar"
        try {
            val ret = JSObject()
            ret.put("result", getPermissionState(permissionName))
            call.resolve(ret)
        } catch (_: Exception) {
            throw Exception("${::requestPermissionCallback.name} Could not authorize $permissionName")
        }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun requestAllPermissions(call: PluginCall) {
        try {
            return requestPermissions(call)
        } catch (_: Exception) {
            call.reject("", "[CapacitorCalendar.requestAllPermissions] Could not request permissions")
            return
        }
    }

    @PluginMethod
    fun selectCalendarsWithPrompt(call: PluginCall) {
        call.unimplemented("[CapacitorCalendar.${::selectCalendarsWithPrompt.name}] Not implemented on Android")
        return
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun listCalendars(call: PluginCall) {
        try {
            val calendars = implementation.listCalendars(context)
            val ret = JSObject()
            ret.put("result", calendars)
            call.resolve(ret)
        } catch (_: Exception) {
            call.reject("", "[CapacitorCalendar.${::listCalendars.name}] Failed to get the list of calendars")
        }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun getDefaultCalendar(call: PluginCall) {
        try {
            val primaryCalendar = implementation.getDefaultCalendar(context)
            val ret = JSObject()
            ret.put("result", primaryCalendar)
            call.resolve(ret)
        } catch (_: Exception) {
            call.reject("", "[CapacitorCalendar.${::getDefaultCalendar.name}] No default calendar found")
        }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun createEvent(call: PluginCall) {
        try {
            val title =
                call.getString("title")
                    ?: throw Exception("[CapacitorCalendar.${::createEvent.name}] A title for the event was not provided")
            val calendarId = call.getString("calendarId")
            val location = call.getString("location")
            val startDate = call.getLong("startDate")
            val endDate = call.getLong("endDate")
            val isAllDay = call.getBoolean("isAllDay", false)
            val alertOffsetInMinutesSingle = call.getFloat("alertOffsetInMinutes")
            val alertOffsetInMinutesMultiple = call.getArray("alertOffsetInMinutes")
            val url = call.getString("url")
            val notes = call.getString("notes")

            val recurrenceRule = call.getObject("recurrence")?.let { RecurrenceRule(it) }

            val eventUri =
                implementation.createEvent(
                    context,
                    title,
                    calendarId,
                    location,
                    startDate,
                    endDate,
                    isAllDay,
                    alertOffsetInMinutesSingle,
                    alertOffsetInMinutesMultiple,
                    url,
                    notes,
                    recurrenceRule
                )

            val id = eventUri?.lastPathSegment ?: throw IllegalArgumentException("Failed to insert event into calendar")
            val ret = JSObject()
            ret.put("result", id)
            call.resolve(ret)
        } catch (error: Exception) {
            call.reject(
                "",
                "[CapacitorCalendar.${::createEvent.name}] Unable to create event",
                error
            )
            return
        }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun createCalendar(call: PluginCall) {
        try {
            val name = call.getString("title")
                ?: throw IllegalArgumentException("[CapacitorCalendar.${::createCalendar.name}] A title for the calendar was not provided")
            val color = call.getString("color")

            val calendarUri = implementation.createCalendar(context, name, color)

            val calendarId = calendarUri.lastPathSegment
                ?: throw IllegalArgumentException("Calendar id could not be found")

            val ret = JSObject()
            ret.put("result", calendarId)
            call.resolve(ret)
            return
        } catch (error: Exception) {
            call.reject(
                "",
                "[CapacitorCalendar.${::createCalendar.name}] Unable to create calendar",
                error
            )
            return
        }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun deleteCalendar(call: PluginCall) {
        try {
            val id = call.getString("id")?.toLong()
                ?: throw IllegalArgumentException("[CapacitorCalendar.${::deleteCalendar.name}] The provided id is not a valid long")

            when {
                implementation.deleteCalendar(context, id) -> {
                    call.resolve()
                }

                else -> {
                    throw Exception("[CapacitorCalendar.${::deleteCalendar.name}] Calendar was not deleted")
                }
            }
        } catch (error: Exception) {
            call.reject(
                "",
                "[CapacitorCalendar.${::deleteCalendar.name}] Unable to delete calendar",
                error
            )
            return
        }
    }

    @PluginMethod
    fun getDefaultRemindersList(call: PluginCall) {
        call.unimplemented("[CapacitorCalendar.${::getDefaultRemindersList.name}] Not implemented on Android")
        return
    }

    @PluginMethod
    fun getRemindersLists(call: PluginCall) {
        call.unimplemented("[CapacitorCalendar.${::getRemindersLists.name}] Not implemented on Android")
        return
    }

    @PluginMethod
    fun createReminder(call: PluginCall) {
        call.unimplemented("[CapacitorCalendar.${::createReminder.name}] Not implemented on Android")
        return
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun openCalendar(call: PluginCall) {
        val timestamp = call.getLong("date") ?: System.currentTimeMillis()
        try {
            return activity.startActivity(implementation.openCalendar(timestamp))
        } catch (error: Exception) {
            call.reject("", "[CapacitorCalendar.${::openCalendar.name}] Unable to open calendar")
            return
        }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun listEventsInRange(call: PluginCall) {
        try {
            val startDate =
                call.getLong("startDate")
                    ?: throw Exception("[CapacitorCalendar.${::listEventsInRange.name}] A start date was not provided")
            val endDate =
                call.getLong("endDate")
                    ?: throw Exception("[CapacitorCalendar.${::listEventsInRange.name}] An end date was not provided")
            val ret = JSObject()
            ret.put("result", implementation.listEventsInRange(context, startDate, endDate))
            call.resolve(ret)
        } catch (error: Exception) {
            call.reject("", "[CapacitorCalendar.${::listEventsInRange.name}] Could not get the list of events in requested range")
            return
        }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun deleteEventsById(call: PluginCall) {
        try {
            val ids =
                call.getArray("ids")
                    ?: throw Exception("[CapacitorCalendar.${::deleteEventsById.name}] Event ids were not provided")
            val ret = JSObject()
            ret.put("result", implementation.deleteEventsById(context, ids))
            call.resolve(ret)
        } catch (error: Exception) {
            call.reject("", "[CapacitorCalendar.${::deleteEventsById.name}] Could not delete events")
            return
        }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun getRemindersFromLists(call: PluginCall) {
        call.unimplemented("[CapacitorCalendar.${::getRemindersFromLists.name}] Not implemented on Android")
        return
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun deleteRemindersById(call: PluginCall) {
        call.unimplemented("[CapacitorCalendar.${::deleteRemindersById.name}] Not implemented on Android")
        return
    }
}
