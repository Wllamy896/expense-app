package com.example.expensemanager

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.floor

private val GREEN = Color.parseColor("#2E7D32")
private val RED = Color.parseColor("#C62828")

fun money(v: Double): String =
    if (v == floor(v)) String.format(Locale.US, "%,.0f", v)
    else String.format(Locale.US, "%,.2f", v)

fun plain(v: Double): String =
    if (v == floor(v)) v.toLong().toString() else v.toString()

fun dayKey(t: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))

fun signed(e: Expense): Double = if (e.isIncome) e.amount else -e.amount

data class DayRow(
    val label: String,
    val income: Double,
    val expense: Double,
    val net: Double,
    val balance: Double,
    val entries: List<Expense>,
    val kind: Int // 0 = day, 1 = opening balance, 2 = total
)

class MainActivity : AppCompatActivity() {

    private lateinit var items: MutableList<Expense>
    private val monthCal: Calendar = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private val shown = mutableListOf<Expense>()
    private val dayRows = mutableListOf<DayRow>()
    private lateinit var listAdapter: ExpenseAdapter
    private lateinit var dayAdapter: DayAdapter
    private lateinit var months: Array<String>

    private lateinit var tvMonth: TextView
    private lateinit var tvBalance: TextView
    private lateinit var tvIncome: TextView
    private lateinit var tvExpense: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var journalBox: LinearLayout
    private var journalMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        months = resources.getStringArray(R.array.months)
        tvMonth = findViewById(R.id.tvMonth)
        tvBalance = findViewById(R.id.tvBalance)
        tvIncome = findViewById(R.id.tvIncome)
        tvExpense = findViewById(R.id.tvExpense)
        tvEmpty = findViewById(R.id.tvEmpty)
        recycler = findViewById(R.id.recycler)
        journalBox = findViewById(R.id.journalBox)

        items = Storage.load(this)

        listAdapter = ExpenseAdapter(shown, { showEntryDialog(it) }, { confirmDelete(it) })
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = listAdapter

        dayAdapter = DayAdapter(dayRows) { showDayDialog(it) }
        val recyclerDays = findViewById<RecyclerView>(R.id.recyclerDays)
        recyclerDays.layoutManager = LinearLayoutManager(this)
        recyclerDays.adapter = dayAdapter

        findViewById<TabLayout>(R.id.tabs).addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    journalMode = (tab?.position == 1)
                    refresh()
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })

        findViewById<Button>(R.id.btnPrev).setOnClickListener {
            monthCal.add(Calendar.MONTH, -1)
            refresh()
        }
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            monthCal.add(Calendar.MONTH, 1)
            refresh()
        }
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener { showEntryDialog(null) }

        refresh()
    }

    private fun refresh() {
        val y = monthCal.get(Calendar.YEAR)
        val m = monthCal.get(Calendar.MONTH)
        tvMonth.text = months[m] + " " + y

        val startCal = Calendar.getInstance()
        startCal.clear()
        startCal.set(y, m, 1, 0, 0, 0)
        val start = startCal.timeInMillis
        startCal.add(Calendar.MONTH, 1)
        val end = startCal.timeInMillis

        val monthItems = items.filter { it.date >= start && it.date < end }
            .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })

        val total = items.sumOf { signed(it) }
        val inc = monthItems.filter { it.isIncome }.sumOf { it.amount }
        val exp = monthItems.filter { !it.isIncome }.sumOf { it.amount }

        tvBalance.text = money(total)
        tvBalance.setTextColor(if (total < 0) RED else GREEN)
        tvIncome.text = getString(R.string.income) + ": " + money(inc)
        tvExpense.text = getString(R.string.expense) + ": " + money(exp)

        shown.clear()
        shown.addAll(monthItems)
        listAdapter.notifyDataSetChanged()

        // Daily journal
        dayRows.clear()
        val opening = items.filter { it.date < start }.sumOf { signed(it) }
        var running = opening
        if (opening != 0.0) {
            dayRows.add(DayRow(getString(R.string.opening), 0.0, 0.0, 0.0, opening, emptyList(), 1))
        }
        val byDay = monthItems.groupBy { dayKey(it.date) }.toSortedMap()
        val dayFmt = SimpleDateFormat("dd/MM", Locale.US)
        for ((_, list) in byDay) {
            val di = list.filter { it.isIncome }.sumOf { it.amount }
            val de = list.filter { !it.isIncome }.sumOf { it.amount }
            running += di - de
            dayRows.add(DayRow(dayFmt.format(Date(list[0].date)), di, de, di - de, running, list, 0))
        }
        dayRows.add(DayRow(getString(R.string.total), inc, exp, inc - exp, running, emptyList(), 2))
        dayAdapter.notifyDataSetChanged()

        recycler.visibility = if (journalMode) View.GONE else View.VISIBLE
        journalBox.visibility = if (journalMode) View.VISIBLE else View.GONE
        tvEmpty.visibility = if (monthItems.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showEntryDialog(existing: Expense?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add, null)
        val etTitle = view.findViewById<EditText>(R.id.etTitle)
        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val rbIncome = view.findViewById<RadioButton>(R.id.rbIncome)
        val sp = view.findViewById<Spinner>(R.id.spCategory)
        val btnDate = view.findViewById<Button>(R.id.btnDate)

        val chosen = Calendar.getInstance()
        if (existing != null) {
            chosen.timeInMillis = existing.date
            etTitle.setText(existing.title)
            etAmount.setText(plain(existing.amount))
            rbIncome.isChecked = existing.isIncome
            val cats = resources.getStringArray(R.array.categories)
            val idx = cats.indexOf(existing.category)
            if (idx >= 0) sp.setSelection(idx)
        }

        val df = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        fun updateDateText() {
            btnDate.text = getString(R.string.date_label) + ": " + df.format(chosen.time)
        }
        updateDateText()
        btnDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, yy, mm, dd ->
                    chosen.set(yy, mm, dd)
                    updateDateText()
                },
                chosen.get(Calendar.YEAR),
                chosen.get(Calendar.MONTH),
                chosen.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.new_entry else R.string.edit_entry)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = etTitle.text.toString().trim()
                val amount = etAmount.text.toString().toDoubleOrNull()
                if (title.isEmpty() || amount == null || amount <= 0) {
                    Toast.makeText(this, R.string.invalid, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                chosen.set(Calendar.HOUR_OF_DAY, 12)
                chosen.set(Calendar.MINUTE, 0)
                chosen.set(Calendar.SECOND, 0)
                chosen.set(Calendar.MILLISECOND, 0)

                val entry = Expense(
                    existing?.id ?: System.currentTimeMillis(),
                    title,
                    amount,
                    rbIncome.isChecked,
                    sp.selectedItem.toString(),
                    chosen.timeInMillis
                )
                if (existing != null) {
                    val i = items.indexOfFirst { it.id == existing.id }
                    if (i >= 0) items[i] = entry else items.add(entry)
                } else {
                    items.add(entry)
                }
                Storage.save(this, items)

                // jump to the month of the saved entry so it is visible
                monthCal.timeInMillis = chosen.timeInMillis
                monthCal.set(Calendar.DAY_OF_MONTH, 1)
                refresh()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showDayDialog(row: DayRow) {
        if (row.kind != 0) return
        val lines = row.entries.map {
            (if (it.isIncome) "+" else "-") + money(it.amount) + "   " + it.title + " (" + it.category + ")"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(row.label)
            .setItems(lines, null)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun confirmDelete(e: Expense) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                items.remove(e)
                Storage.save(this, items)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

class ExpenseAdapter(
    private val data: List<Expense>,
    private val onClick: (Expense) -> Unit,
    private val onLongClick: (Expense) -> Unit
) : RecyclerView.Adapter<ExpenseAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvTitle)
        val meta: TextView = v.findViewById(R.id.tvMeta)
        val amount: TextView = v.findViewById(R.id.tvAmount)
    }

    private val df = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_expense, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val e = data[position]
        h.title.text = e.title
        h.meta.text = e.category + " • " + df.format(Date(e.date))
        h.amount.text = (if (e.isIncome) "+" else "-") + money(e.amount)
        h.amount.setTextColor(if (e.isIncome) GREEN else RED)
        h.itemView.setOnClickListener { onClick(e) }
        h.itemView.setOnLongClickListener { onLongClick(e); true }
    }

    override fun getItemCount() = data.size
}

class DayAdapter(
    private val data: List<DayRow>,
    private val onClick: (DayRow) -> Unit
) : RecyclerView.Adapter<DayAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val day: TextView = v.findViewById(R.id.tvDay)
        val inc: TextView = v.findViewById(R.id.tvInc)
        val exp: TextView = v.findViewById(R.id.tvExp)
        val net: TextView = v.findViewById(R.id.tvNet)
        val bal: TextView = v.findViewById(R.id.tvBal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_day, parent, false))

    private fun colorOf(v: Double) = if (v < 0) RED else GREEN

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = data[position]
        h.day.text = r.label

        if (r.kind == 1) {
            h.inc.text = ""
            h.exp.text = ""
            h.net.text = ""
        } else {
            h.inc.text = if (r.income != 0.0) money(r.income) else "-"
            h.exp.text = if (r.expense != 0.0) money(r.expense) else "-"
            h.net.text = money(r.net)
        }
        h.bal.text = money(r.balance)

        h.inc.setTextColor(GREEN)
        h.exp.setTextColor(RED)
        h.net.setTextColor(colorOf(r.net))
        h.bal.setTextColor(colorOf(r.balance))

        val style = if (r.kind == 0) Typeface.NORMAL else Typeface.BOLD
        h.day.setTypeface(null, style)
        h.inc.setTypeface(null, style)
        h.exp.setTypeface(null, style)
        h.net.setTypeface(null, style)
        h.bal.setTypeface(null, Typeface.BOLD)

        h.itemView.setBackgroundColor(
            if (r.kind == 0) Color.TRANSPARENT else Color.parseColor("#2200796B")
        )
        h.itemView.setOnClickListener { onClick(r) }
    }

    override fun getItemCount() = data.size
}
