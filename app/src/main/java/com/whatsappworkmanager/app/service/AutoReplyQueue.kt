package com.whatsappworkmanager.app.service

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

/** Persistent, single-item queue used to hand a generated reply from the notification listener
 * to the AccessibilityService. Only the latest pending job is kept: this deliberately prevents
 * a burst of messages from turning into a burst of automatic replies. */
object AutoReplyQueue {
    private const val PREFS = "auto_reply_queue"
    private const val KEY = "pending"

    data class Job(
        val groupName: String,
        val incomingText: String,
        val replyText: String,
        val packageName: String,
        val notBefore: Long,
        val messageKey: String,
        val phoneNumber: String? = null
    )

    fun enqueue(context: Context, job: Job) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until arr.length()) if (arr.optJSONObject(i)?.optString("key") == job.messageKey) return
        val json = JSONObject().apply {
            put("group", job.groupName); put("incoming", job.incomingText); put("reply", job.replyText)
            put("package", job.packageName); put("phone", job.phoneNumber); put("notBefore", job.notBefore); put("key", job.messageKey)
        }
        arr.put(json)
        while (arr.length() > 10) arr.remove(0)
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun peek(context: Context): Job? = runCatching {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        Job(
                o.getString("group"), o.getString("incoming"), o.getString("reply"),
                o.getString("package"), o.getLong("notBefore"), o.getString("key"),
                o.optString("phone").ifBlank { null }
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
}
