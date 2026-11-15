package com.example.expensemanager

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var items: MutableList<Expense>
    private lateinit var adapter: ExpenseAdapter
    private lateinit var tvBalance: TextView
    private lateinit var tvIncome: TextView
    private lateinit var tvExpense: TextView
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvBalance = findViewById(R.id.tvBalance)
        tvIncome = findViewById(R.id.tvIncome)
        tvExpense = findViewById(R.id.tvExpense)
        tvEmpty = findViewById(R.id.tvEmpty)

        items = Storage.load(this)
        adapter = ExpenseAdapter(items) { confirmDelete(it) }

        findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener { showAddDialog() }

        refresh()
    }

    private fun refresh() {
        items.sortByDescending { it.date }
        adapter.notifyDataSetChanged()

        val total = items.sumOf { if (it.isIncome) it.amount else -it.amount }

        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)
        val thisMonth = items.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.date }
            c.get(Calendar.MONTH) == month && c.get(Calendar.YEAR) == year
        }
        val inc = thisMonth.filter { it.isIncome }.sumOf { it.amount }
        val exp = thisMonth.filter { !it.isIncome }.sumOf { it.amount }

        tvBalance.text = fmt(total)
        tvBalance.setTextColor(if (total < 0) Color.parseColor("#C62828") else Color.parseColor("#2E7D32"))
        tvIncome.text = getString(R.string.income) + ": " + fmt(inc)
        tvExpense.text = getString(R.string.expense) + ": " + fmt(exp)
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%,.2f", v)

    private fun showAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add, null)
        val etTitle = view.findViewById<EditText>(R.id.etTitle)
        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val rbIncome = view.findViewById<RadioButton>(R.id.rbIncome)
        val sp = view.findViewById<Spinner>(R.id.spCategory)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.new_entry)
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
                items.add(
                    Expense(
                        System.currentTimeMillis(),
                        title,
                        amount,
                        rbIncome.isChecked,
                        sp.selectedItem.toString(),
                        System.currentTimeMillis()
                    )
                )
                Storage.save(this, items)
                refresh()
                dialog.dismiss()
            }
        }
        dialog.show()
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
    private val onLongClick: (Expense) -> Unit
) : RecyclerView.Adapter<ExpenseAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvTitle)
        val meta: TextView = v.findViewById(R.id.tvMeta)
        val amount: TextView = v.findViewById(R.id.tvAmount)
    }

    private val df = SimpleDateFormat("yyyy/MM/dd", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_expense, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val e = data[position]
        h.title.text = e.title
        h.meta.text = e.category + " • " + df.format(Date(e.date))
        val sign = if (e.isIncome) "+" else "-"
        h.amount.text = sign + String.format(Locale.US, "%,.2f", e.amount)
        h.amount.setTextColor(Color.parseColor(if (e.isIncome) "#2E7D32" else "#C62828"))
        h.itemView.setOnLongClickListener { onLongClick(e); true }
    }

    override fun getItemCount() = data.size
}
