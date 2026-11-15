package com.example.expensemanager

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Expense(
    val id: Long,
    val title: String,
    val amount: Double,
    val isIncome: Boolean,
    val category: String,
    val date: Long,
    val paid: Boolean = true
)

object Storage {
    private const val PREFS = "expenses_prefs"
    private const val KEY = "items"

    fun load(context: Context): MutableList<Expense> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return mutableListOf()
        val list = mutableListOf<Expense>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Expense(
                        o.getLong("id"),
                        o.getString("title"),
                        o.getDouble("amount"),
                        o.getBoolean("isIncome"),
                        o.getString("category"),
                        o.getLong("date"),
                        o.optBoolean("paid", true)
                    )
                )
            }
        } catch (e: Exception) {
            // corrupted data: start fresh
        }
        return list
    }

    fun save(context: Context, items: List<Expense>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("amount", it.amount)
                    .put("isIncome", it.isIncome)
                    .put("category", it.category)
                    .put("date", it.date)
                    .put("paid", it.paid)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
