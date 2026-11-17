package pl.liczniki.app

import android.app.*
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.Context
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

class MainActivity : Activity() {
    data class Snapshot(val g: Int, val p: Int, val s: Int, val t: Int)
    data class Counter(var name: String, var color: Int, var g: Int = 0, var p: Int = 0, var s: Int = 0, var t: Int = 0, val history: MutableList<Snapshot> = mutableListOf())
    private data class CounterViews(
        val name: TextView, val general: TextView, val physical: TextView,
        val taken: TextView, val total: TextView, val add: Button,
        val subtract: Button, val szymon: Button, val exchange: Button, val undo: Button
    )

    private val items = mutableListOf<Counter>()
    private val views = java.util.IdentityHashMap<Counter, CounterViews>()
    private var shown = 6
    private var plus = 1
    private var minus = 5
    private val dp get() = resources.displayMetrics.density
    private fun d(v: Int) = (v * dp).toInt()
    private fun shape(color: Int, r: Int = 12, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = d(r).toFloat()
        if (stroke != 0) setStroke(d(1), stroke)
    }
    private fun text(value: String, size: Float = 15f, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        gravity = Gravity.CENTER
        if (bold) setTypeface(null, Typeface.BOLD)
        setTextColor(Color.rgb(35, 42, 53))
    }
    private fun vertical(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private fun horizontal(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    private fun pad(v: View, l: Int = 8, t: Int = 8, r: Int = 8, b: Int = 8) {
        v.setPadding(d(l), d(t), d(r), d(b))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        render()
    }

    private fun load() {
        val defaults = listOf("522694320 - ALL", "522694330 - JEEP", "522694570 - FIAT/ALFA", "522694340 - JEEP", "522694580 - FIAT", "522694770 - ALFA")
        val colors = listOf(0xFF159447.toInt(), 0xFFE4B500.toInt(), 0xFF2474D2.toInt(), 0xFFE4B500.toInt(), 0xFF2474D2.toInt(), 0xFFD93691.toInt())
        val prefs = getSharedPreferences("liczniki", Context.MODE_PRIVATE)
        shown = prefs.getInt("shown", 6).coerceIn(1, 20)
        plus = prefs.getInt("plus", 1).coerceAtLeast(1)
        minus = prefs.getInt("minus", 5).coerceAtLeast(1)
        val raw = prefs.getString("items", null)
        if (raw != null) try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                items.add(Counter(o.optString("name", "Zestaw ${i + 1}"), o.optInt("color", colors[i % 6]), o.optInt("g"), o.optInt("p"), o.optInt("s"), o.optInt("t")))
            }
        } catch (_: Exception) {}
        while (items.size < 20) {
            val i = items.size
            items.add(Counter(defaults.getOrElse(i) { "Zestaw ${i + 1}" }, colors[i % 6]))
        }
    }

    private fun save() {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("name", it.name).put("color", it.color).put("g", it.g).put("p", it.p).put("s", it.s).put("t", it.t)) }
        getSharedPreferences("liczniki", Context.MODE_PRIVATE).edit()
            .putString("items", a.toString()).putInt("shown", shown)
            .putInt("plus", plus).putInt("minus", minus).apply()
    }

    // No render() here: update only the numbers and buttons in the changed card.
    private fun mutate(c: Counter, change: () -> Unit) {
        c.history.add(Snapshot(c.g, c.p, c.s, c.t))
        change()
        save()
        updateCard(c)
    }
