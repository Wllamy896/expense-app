package com.example.expensemanager

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

private val GREEN = Color.parseColor("#12B76A")
private val RED = Color.parseColor("#EF4444")
private val NAVY = Color.parseColor("#14284B")
private val GRAY = Color.parseColor("#9CA3AF")

fun money(v: Double): String =
    if (v == floor(v)) String.format(Locale.US, "%,.0f", v)
    else String.format(Locale.US, "%,.2f", v)

fun plain(v: Double): String =
    if (v == floor(v)) v.toLong().toString() else v.toString()

fun dayKey(t: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))

fun signed(e: Expense): Double = if (e.isIncome) e.amount else -e.amount

fun iconOf(cat: String): String = when (cat) {
    "طعام" -> "🍽️"
    "مواصلات" -> "🚗"
    "فواتير" -> "🧾"
    "تسوق" -> "🛍️"
    "صحة" -> "💊"
    "ترفيه" -> "🎮"
    "راتب" -> "💰"
    else -> "📌"
}

fun bindBadge(tv: TextView, paid: Boolean) {
    tv.text = if (paid) "مكتمل" else "قيد الانتظار"
    tv.setTextColor(Color.WHITE)
    tv.background = GradientDrawable().apply {
        cornerRadius = 100f
        setColor(Color.parseColor(if (paid) "#12B76A" else "#F59E0B"))
    }
}

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
    private lateinit var tvStatIncome: TextView
    private lateinit var tvChgIncome: TextView
    private lateinit var tvStatDue: TextView
    private lateinit var tvSubDue: TextView
    private lateinit var tvStatExpense: TextView
    private lateinit var tvChgExpense: TextView
    private lateinit var tvStatBalance: TextView
    private lateinit var tvSubBalance: TextView
    private lateinit var emptyBox: View
    private lateinit var dashboard: View
    private lateinit var recycler: RecyclerView
    private lateinit var journalBox: View
    private lateinit var lineChart: LineChartView
    private lateinit var donut: DonutView
    private lateinit var legendBox: LinearLayout
    private lateinit var txnContainer: LinearLayout

    private var tabIndex = 0
    private var distIncome = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        months = resources.getStringArray(R.array.months)
        tvMonth = findViewById(R.id.tvMonth)
        tvStatIncome = findViewById(R.id.tvStatIncome)
        tvChgIncome = findViewById(R.id.tvChgIncome)
        tvStatDue = findViewById(R.id.tvStatDue)
        tvSubDue = findViewById(R.id.tvSubDue)
        tvStatExpense = findViewById(R.id.tvStatExpense)
        tvChgExpense = findViewById(R.id.tvChgExpense)
        tvStatBalance = findViewById(R.id.tvStatBalance)
        tvSubBalance = findViewById(R.id.tvSubBalance)
        emptyBox = findViewById(R.id.emptyBox)
        dashboard = findViewById(R.id.dashboard)
        recycler = findViewById(R.id.recycler)
        journalBox = findViewById(R.id.journalBox)
        lineChart = findViewById(R.id.lineChart)
        donut = findViewById(R.id.donut)
        legendBox = findViewById(R.id.legendBox)
        txnContainer = findViewById(R.id.txnContainer)

        items = Storage.load(this)

        listAdapter = ExpenseAdapter(
            shown,
            { showEntryDialog(it) },
            { confirmDelete(it) },
            { togglePaid(it) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = listAdapter

        dayAdapter = DayAdapter(dayRows) { showDayDialog(it) }
        val recyclerDays = findViewById<RecyclerView>(R.id.recyclerDays)
        recyclerDays.layoutManager = LinearLayoutManager(this)
        recyclerDays.adapter = dayAdapter

        findViewById<TabLayout>(R.id.tabs).addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    tabIndex = tab?.position ?: 0
                    refresh()
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })

        val toggleDist = findViewById<MaterialButtonToggleGroup>(R.id.toggleDist)
        toggleDist.check(R.id.btnDistExpense)
        toggleDist.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                distIncome = (checkedId == R.id.btnDistIncome)
                refresh()
            }
        }

        findViewById<Button>(R.id.btnPrev).setOnClickListener {
            monthCal.add(Calendar.MONTH, -1)
            refresh()
        }
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            monthCal.add(Calendar.MONTH, 1)
            refresh()
        }
        findViewById<View>(R.id.fabAdd).setOnClickListener { showEntryDialog(null) }

        refresh()
    }

    private fun monthRange(y: Int, m: Int): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.clear()
        c.set(y, m, 1, 0, 0, 0)
        val s = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return Pair(s, c.timeInMillis)
    }

    private fun sumIn(r: Pair<Long, Long>, income: Boolean): Double =
        items.filter { it.isIncome == income && it.date >= r.first && it.date < r.second }
            .sumOf { it.amount }

    private fun setChange(tv: TextView, cur: Double, prev: Double, upIsGood: Boolean) {
        if (prev <= 0.0) {
            tv.text = "—"
            tv.setTextColor(GRAY)
            return
        }
        val p = (cur - prev) / prev * 100.0
        tv.text = (if (p >= 0) "▲ " else "▼ ") + String.format(Locale.US, "%.1f", abs(p)) + "%"
        val good = if (upIsGood) p >= 0 else p <= 0
        tv.setTextColor(if (good) GREEN else RED)
    }

    private fun refresh() {
        val y = monthCal.get(Calendar.YEAR)
        val m = monthCal.get(Calendar.MONTH)
        tvMonth.text = months[m] + " " + y

        val range = monthRange(y, m)
        val start = range.first
        val end = range.second

        val monthItems = items.filter { it.date >= start && it.date < end }
            .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })

        val total = items.sumOf { signed(it) }
        val inc = monthItems.filter { it.isIncome }.sumOf { it.amount }
        val exp = monthItems.filter { !it.isIncome }.sumOf { it.amount }

        // ---- Stat cards ----
        val prevCal = monthCal.clone() as Calendar
        prevCal.add(Calendar.MONTH, -1)
        val prevRange = monthRange(prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH))
        val prevInc = sumIn(prevRange, true)
        val prevExp = sumIn(prevRange, false)

        tvStatIncome.text = money(inc)
        setChange(tvChgIncome, inc, prevInc, true)

        val pendingIncome = monthItems.filter { it.isIncome && !it.paid }
        tvStatDue.text = money(pendingIncome.sumOf { it.amount })
        tvSubDue.text = "معلّق: " + pendingIncome.size
        tvSubDue.setTextColor(Color.parseColor("#F59E0B"))

        tvStatExpense.text = money(exp)
        setChange(tvChgExpense, exp, prevExp, false)

        tvStatBalance.text = money(total)
        tvStatBalance.setTextColor(if (total < 0) RED else NAVY)
        tvSubBalance.text = "كل الأشهر"
        tvSubBalance.setTextColor(GRAY)

        // ---- Line chart: last 6 months ----
        val labels = ArrayList<String>()
        val incSeries = ArrayList<Double>()
        val expSeries = ArrayList<Double>()
        for (k in 5 downTo 0) {
            val c = monthCal.clone() as Calendar
            c.add(Calendar.MONTH, -k)
            val r = monthRange(c.get(Calendar.YEAR), c.get(Calendar.MONTH))
            labels.add(months[c.get(Calendar.MONTH)])
            incSeries.add(sumIn(r, true))
            expSeries.add(sumIn(r, false))
        }
        lineChart.setData(labels, incSeries, expSeries)

        // ---- Donut: distribution by category ----
        val distItems = monthItems.filter { it.isIncome == distIncome }
        val byCat = distItems.groupBy { it.category }
            .map { entry -> Pair(entry.key, entry.value.sumOf { x -> x.amount }) }
            .sortedByDescending { it.second }
        val sumCat = byCat.sumOf { it.second }
        donut.setData(
            byCat.map { it.second },
            compactNum(sumCat),
            if (distIncome) "إيراد" else "مصروف"
        )
        legendBox.removeAllViews()
        val dp = resources.displayMetrics.density
        if (byCat.isEmpty()) {
            val tv = TextView(this)
            tv.text = "لا توجد بيانات"
            tv.setTextColor(GRAY)
            tv.gravity = android.view.Gravity.CENTER
            legendBox.addView(tv)
        }
        byCat.forEachIndexed { i, pair ->
            val pct = pair.second / sumCat * 100.0
            val s = SpannableString(
                "● " + pair.first + "   " + String.format(Locale.US, "%.0f", pct) + "%   " + money(pair.second)
            )
            s.setSpan(ForegroundColorSpan(donut.colorAt(i)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val tv = TextView(this)
            tv.text = s
            tv.textSize = 13f
            tv.setTextColor(Color.parseColor("#1F2937"))
            tv.setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            legendBox.addView(tv)
        }

        // ---- Latest transactions ----
        txnContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val dateFmt = SimpleDateFormat("dd/MM", Locale.US)
        for (e in monthItems.take(5)) {
            val row = inflater.inflate(R.layout.item_txn, txnContainer, false)
            row.findViewById<TextView>(R.id.tvTxTitle).text = iconOf(e.category) + " " + e.title
            val amountTv = row.findViewById<TextView>(R.id.tvTxAmount)
            amountTv.text = (if (e.isIncome) "+" else "-") + money(e.amount)
            amountTv.setTextColor(if (e.isIncome) GREEN else RED)
            row.findViewById<TextView>(R.id.tvTxDate).text = dateFmt.format(Date(e.date))
            val badge = row.findViewById<TextView>(R.id.tvTxStatus)
            bindBadge(badge, e.paid)
            badge.setOnClickListener { togglePaid(e) }
            row.setOnClickListener { showEntryDialog(e) }
            txnContainer.addView(row)
        }
        if (monthItems.isEmpty()) {
            val tv = TextView(this)
            tv.text = "لا توجد عمليات في هذا الشهر"
            tv.setTextColor(GRAY)
            tv.gravity = android.view.Gravity.CENTER
            tv.setPadding(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
            txnContainer.addView(tv)
        }

        // ---- Entries list ----
        shown.clear()
        shown.addAll(monthItems)
        listAdapter.notifyDataSetChanged()

        // ---- Daily journal ----
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

        // ---- Tab visibility ----
        dashboard.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        recycler.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        journalBox.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        emptyBox.visibility =
            if (tabIndex == 1 && monthItems.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun togglePaid(e: Expense) {
        val i = items.indexOfFirst { it.id == e.id }
        if (i >= 0) {
            items[i] = items[i].copy(paid = !items[i].paid)
            Storage.save(this, items)
            refresh()
        }
    }

    private fun showEntryDialog(existing: Expense?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add, null)
        val toggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleType)
        val toggleStatus = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleStatus)
        val etTitle = view.findViewById<EditText>(R.id.etTitle)
        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val sp = view.findViewById<Spinner>(R.id.spCategory)
        val btnDate = view.findViewById<Button>(R.id.btnDate)

        toggle.check(if (existing?.isIncome == true) R.id.btnIncome else R.id.btnExpense)
        toggleStatus.check(if (existing?.paid == false) R.id.btnPending else R.id.btnPaid)

        val chosen = Calendar.getInstance()
        if (existing != null) {
            chosen.timeInMillis = existing.date
            etTitle.setText(existing.title)
            etAmount.setText(plain(existing.amount))
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

        val dialog = MaterialAlertDialogBuilder(this)
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
                    toggle.checkedButtonId == R.id.btnIncome,
                    sp.selectedItem.toString(),
                    chosen.timeInMillis,
                    toggleStatus.checkedButtonId == R.id.btnPaid
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
        MaterialAlertDialogBuilder(this)
            .setTitle(row.label)
            .setItems(lines, null)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun confirmDelete(e: Expense) {
        MaterialAlertDialogBuilder(this)
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
    private val onLongClick: (Expense) -> Unit,
    private val onToggle: (Expense) -> Unit
) : RecyclerView.Adapter<ExpenseAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: TextView = v.findViewById(R.id.tvIcon)
        val title: TextView = v.findViewById(R.id.tvTitle)
        val meta: TextView = v.findViewById(R.id.tvMeta)
        val amount: TextView = v.findViewById(R.id.tvAmount)
        val status: TextView = v.findViewById(R.id.tvStatus)
    }

    private val df = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_expense, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val e = data[position]
        h.icon.text = iconOf(e.category)
        h.icon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(if (e.isIncome) "#E8F5E9" else "#FFEBEE"))
        }
        h.title.text = e.title
        h.meta.text = e.category + " • " + df.format(Date(e.date))
        h.amount.text = (if (e.isIncome) "+" else "-") + money(e.amount)
        h.amount.setTextColor(if (e.isIncome) GREEN else RED)
        bindBadge(h.status, e.paid)
        h.status.setOnClickListener { onToggle(e) }
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

        h.day.setTextColor(Color.parseColor("#1F2937"))
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
            when {
                r.kind != 0 -> Color.parseColor("#E8EEF9")
                position % 2 == 0 -> Color.WHITE
                else -> Color.parseColor("#F7F9FC")
            }
        )
        h.itemView.setOnClickListener { onClick(r) }
    }

    override fun getItemCount() = data.size
}
