package com.example.secretary

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.*

class CalendarManager(context: Context) {
    private val TAG = "CalendarManager"
    private val appContext = context.applicationContext
    
    private val MY_EMAILS = listOf("hutrat05@gmail.com", "marek@designleaf.co.uk", "hutrat@seznam.cz")

    private fun hasReadPermission() = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    private fun hasWritePermission() = ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun addEvent(summary: String, startTimeMillis: Long, endTimeMillis: Long): Boolean {
        if (!hasWritePermission()) {
            Log.e(TAG, "ERR: Chybí právo zápisu")
            return false
        }
        return try {
            val calendarId = getDefaultCalendarId() ?: return false
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startTimeMillis)
                put(CalendarContract.Events.DTEND, endTimeMillis)
                put(CalendarContract.Events.TITLE, summary)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            val uri = appContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            Log.d(TAG, "OK: Událost zapsána do kalendáře $calendarId (URI: $uri)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "ERR: Zápis selhal: ${e.message}")
            false
        }
    }

    fun deleteEvent(eventId: Long): Boolean {
        if (!hasWritePermission()) return false
        return try {
            val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows = appContext.contentResolver.delete(deleteUri, null, null)
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "ERR: Delete failed", e)
            false
        }
    }

    fun deleteEventByName(title: String): Boolean {
        if (!hasWritePermission() || !hasReadPermission()) return false
        return try {
            val calendarId = getDefaultCalendarId() ?: return false
            val cursor = appContext.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID),
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} = ?",
                arrayOf(calendarId.toString(), title),
                null
            )
            var deleted = false
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    if (deleteEvent(id)) deleted = true
                }
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "ERR: Delete by name failed", e)
            false
        }
    }

    fun updateEvent(eventId: Long, title: String?, start: Long?, end: Long?): Boolean {
        if (!hasWritePermission()) return false
        return try {
            val values = ContentValues()
            title?.let { values.put(CalendarContract.Events.TITLE, it) }
            start?.let { values.put(CalendarContract.Events.DTSTART, it) }
            end?.let { values.put(CalendarContract.Events.DTEND, it) }
            
            val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows = appContext.contentResolver.update(updateUri, values, null, null)
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "ERR: Update failed", e)
            false
        }
    }

    fun getEventsInRange(startMillis: Long, endMillis: Long): List<CalendarEvent> {
        if (!hasReadPermission()) return emptyList()
        val excluded = getExcludedCalendarIds()
        val result = mutableListOf<CalendarEvent>()
        try {
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startMillis)
            ContentUris.appendId(builder, endMillis)
            appContext.contentResolver.query(
                builder.build(),
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.CALENDAR_ID
                ),
                null, null, CalendarContract.Instances.BEGIN + " ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val calId = cursor.getLong(5)
                    if (calId in excluded) continue
                    result.add(CalendarEvent(
                        id = cursor.getLong(0),
                        title = cursor.getString(1) ?: "Bez názvu",
                        startMillis = cursor.getLong(2),
                        endMillis = cursor.getLong(3),
                        allDay = cursor.getInt(4) != 0
                    ))
                }
            }
        } catch (e: Exception) { Log.e(TAG, "getEventsInRange failed", e) }
        return result
    }

    fun getCalendarContext(days: Int = 7): String {
        if (!hasReadPermission()) return "SYSTÉM: Chybí oprávnění ke kalendáři."

        val excludedCalendars = getExcludedCalendarIds()
        val events = mutableListOf<String>()
        try {
            val now = Calendar.getInstance()
            val beginTime = now.timeInMillis
            val endTime = beginTime + (days * 24 * 60 * 60 * 1000L)
            
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, beginTime)
            ContentUris.appendId(builder, endTime)

            appContext.contentResolver.query(
                builder.build(),
                arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.CALENDAR_ID),
                null, null,
                CalendarContract.Instances.BEGIN + " ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val calId = cursor.getLong(3)
                    if (calId in excludedCalendars) continue
                    val title = cursor.getString(1) ?: "Bez názvu"
                    val start = cursor.getLong(2)
                    val date = java.text.SimpleDateFormat("dd.MM. HH:mm", Locale("cs", "CZ")).format(Date(start))
                    events.add("$title ($date)")
                }
            }
        } catch (e: Exception) { 
            Log.e(TAG, "ERR: Čtení selhalo: ${e.message}")
            return "CHYBA: ${e.message}" 
        }

        return if (events.isEmpty()) "Kalendář je prázdný na příštích $days dní."
        else "Události: " + events.joinToString("; ")
    }

    private fun getExcludedCalendarIds(): Set<Long> {
        val excluded = mutableSetOf<Long>()
        try {
            appContext.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.OWNER_ACCOUNT, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val owner = cursor.getString(1) ?: ""
                    val name = cursor.getString(2) ?: ""
                    if (owner.contains("holiday", true) || owner.contains("birthday", true) || name.contains("svátek", true) || name.contains("holiday", true) || name.contains("narozenin", true) || name.contains("birthday", true) || name.contains("Důležitá data", true)) {
                        excluded.add(id)
                        Log.d(TAG, "Vyloučen kalendář: $name (ID=$id)")
                    }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Chyba pri cteni kalendaru", e) }
        return excluded
    }

    private fun getDefaultCalendarId(): Long? {
        if (!hasReadPermission()) return null
        
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )

        try {
            appContext.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null, null, null
            )?.use { cursor ->
                val allCalendars = mutableListOf<Long>()
                var primaryId: Long? = null

                Log.d(TAG, "--- VÝPIS VŠECH KALENDÁŘŮ ---")
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val acc = cursor.getString(1) ?: "null"
                    val owner = cursor.getString(2) ?: "null"
                    val isPrimary = cursor.getInt(3) > 0
                    val disp = cursor.getString(4) ?: "null"

                    Log.d(TAG, "KALENDÁŘ: ID=$id, Účet=$acc, Majitel=$owner, Primární=$isPrimary, Jméno=$disp")

                    // Kontrola shody - vlastnik musi byt nas email (ne holiday kalendare)
                    val isHoliday = owner.contains("holiday", ignoreCase = true) || owner.contains("birthday", ignoreCase = true) || disp.contains("svátek", ignoreCase = true) || disp.contains("holiday", ignoreCase = true)
                    if (!isHoliday && MY_EMAILS.any { it.equals(owner, ignoreCase = true) }) {
                        Log.d(TAG, ">>> VYBRÁN OSOBNÍ KALENDÁŘ: $owner (ID=$id)")
                        return id
                    }
                    // Kontrola uctu (account_name) - jen pokud neni holiday
                    if (!isHoliday && MY_EMAILS.any { it.equals(acc, ignoreCase = true) }) {
                        Log.d(TAG, ">>> VYBRÁN ÚČET: $acc (ID=$id)")
                        return id
                    }
                    if (isPrimary) primaryId = id
                    allCalendars.add(id)
                }
                
                // Pokud nenašel e-mail, vezmi primární nebo první dostupný
                val finalId = primaryId ?: allCalendars.firstOrNull()
                Log.d(TAG, ">>> FALLBACK: Vybráno ID=$finalId")
                return finalId
            }
        } catch (e: Exception) { 
            Log.e(TAG, "ERR: Detekce selhala", e) 
        }
        return null
    }
}
