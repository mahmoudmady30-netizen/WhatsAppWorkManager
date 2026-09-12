package com.whatsappworkmanager.app.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Durable fallback queue for scheduled sends that cannot use WhatsApp's notification RemoteInput.
 * The AccessibilityService consumes this queue only when WhatsApp's UI is actually available.
 * Nothing is sent unless the target chat is positively matched first. */
object ScheduledSendQueue {
    private const val PREFS = "scheduled_send_ui_queue"
    private const val KEY = "pending"

    data class Job(
        val id: Long,
        val text: String,
        val recipientName: String?,
        val phoneNumber: String?,
        val packageName: String?,
        val platform: String = "whatsapp"
    )

    fun enqueue(context: Context, job: Job) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until arr.length()) if (arr.optLong(i, -1L) == job.id) return
        arr.put(JSONObject().apply {
            put("id", job.id)
            put("text", job.text)
            put("recipient", job.recipientName)
            put("phone", job.phoneNumber)
            put("package", job.packageName)
            put("platform", job.platform)
        })
        while (arr.length() > 20) arr.remove(0)
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun peek(context: Context): Job? = runCatching {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        Job(
            id = o.optLong("id", -1L),
            text = o.optString("text"),
            recipientName = o.optString("recipient").ifBlank { null },
            phoneNumber = o.optString("phone").ifBlank { null },
            packageName = o.optString("package").ifBlank { null },
            platform = o.optString("platform").ifBlank { "whatsapp" }
        )
    }.getOrNull()

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching {
            val arr = JSONArray(prefs.getString(KEY, "[]"))
            if (arr.length() > 0) arr.remove(0)
            prefs.edit().putString(KEY, arr.toString()).apply()
        }
    }

    fun remove(context: Context, id: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching {
            val old = JSONArray(prefs.getString(KEY, "[]"))
            val fresh = JSONArray()
            for (i in 0 until old.length()) {
                val o = old.optJSONObject(i) ?: continue
                if (o.optLong("id", -1L) != id) fresh.put(o)
            }
            prefs.edit().putString(KEY, fresh.toString()).apply()
        }
    }
}
