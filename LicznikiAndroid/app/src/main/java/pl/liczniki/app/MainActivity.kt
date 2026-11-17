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

    private fun btn(label: String, color: Int, enabled: Boolean = true, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = shape(if (enabled) color else 0xFFB9BEC6.toInt(), 10)
        isEnabled = enabled
        setOnClickListener { action() }
    }

    private fun setButtonEnabled(button: Button, enabled: Boolean, color: Int) {
        button.isEnabled = enabled
        button.background = shape(if (enabled) color else 0xFFB9BEC6.toInt(), 10)
    }

    private fun updateCard(c: Counter) {
        val v = views[c] ?: return
        v.name.text = c.name
        v.general.text = c.g.toString()
        v.physical.text = c.p.toString()
        v.taken.text = c.s.toString()
        v.total.text = c.t.toString()
        v.add.text = "+$plus"
        v.subtract.text = "−$minus"
        setButtonEnabled(v.subtract, c.g >= maxOf(5, minus), 0xFFB63C3C.toInt())
        setButtonEnabled(v.szymon, c.p > 0, 0xFF476481.toInt())
        setButtonEnabled(v.exchange, c.s > 0, 0xFFE28A20.toInt())
        setButtonEnabled(v.undo, c.history.isNotEmpty(), 0xFFB63C3C.toInt())
    }

    private fun editNumber(title: String, current: Int, onSet: (Int) -> Unit) {
        val input = EditText(this).apply { inputType = 2; setText(current.toString()); selectAll() }
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setPositiveButton("Zapisz") { _, _ ->
                val n = input.text.toString().toIntOrNull()
                if (n != null && n > 0) {
                    onSet(n)
                    save()
                    render() // Settings change: rebuilding here is fine.
                }
            }.setNegativeButton("Anuluj", null).show()
    }

    private fun render() {
        views.clear()
        val root = vertical().apply { setBackgroundColor(0xFFF3F5F8.toInt()); setPadding(10, 12, 10, 12) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(text("LICZNIKI", 26f, true))
        val options = horizontal()
        pad(options, 0, 12, 0, 8)
        val add = btn("+ $plus", 0xFF159447.toInt()) { editNumber("Wartość dodawania", plus) { plus = it } }
        val sub = btn("− $minus", 0xFFB63C3C.toInt()) { editNumber("Wartość odejmowania", minus) { minus = it } }
        options.addView(add, LinearLayout.LayoutParams(0, d(43), 1f).apply { rightMargin = d(6) })
        options.addView(sub, LinearLayout.LayoutParams(0, d(43), 1f))
        root.addView(options, LinearLayout.LayoutParams(-1, -2))
        val countLabel = text("Liczba pozycji: $shown", 15f, true)
        root.addView(countLabel)
        val slider = SeekBar(this).apply {
            max = 19
            progress = shown - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) countLabel.text = "Liczba pozycji: ${p + 1}"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    shown = (s?.progress ?: 5) + 1
                    save()
                    render()
                }
            })
        }
        root.addView(slider)
        val cards = vertical()
        root.addView(cards, LinearLayout.LayoutParams(-1, -2))
        var i = 0
        while (i < shown) {
            val line = horizontal().apply { gravity = Gravity.TOP }
            line.addView(counterCard(items[i]), LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = d(4) })
            if (i + 1 < shown) {
                line.addView(counterCard(items[i + 1]), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = d(4) })
            } else {
                line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            cards.addView(line, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(9) })
            i += 2
        }
        val footer = text("© M. K. 11.2026r.", 12f, true).apply {
            background = shape(Color.WHITE, 10, 0xFFCCD3DD.toInt())
            pad(this, 18, 12, 18, 12)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setMessage("Jesteś najlepszy")
                    .setPositiveButton("OK", null).show()
            }
        }
        root.addView(footer, LinearLayout.LayoutParams(-2, -2).apply { topMargin = d(8); bottomMargin = d(16) })
    }

    private fun counterCard(c: Counter): LinearLayout {
        val card = vertical().apply { background = shape(Color.WHITE, 12, 0xFFDDE1E8.toInt()); pad(this, 6, 6, 6, 8) }
        val head = text(c.name, 12f, true).apply {
            setTextColor(Color.WHITE)
            background = shape(c.color, 8)
            pad(this, 4, 10, 4, 10)
            setOnClickListener {
                val input = EditText(this@MainActivity).apply { setText(c.name); selectAll() }
                AlertDialog.Builder(this@MainActivity).setTitle("Edytuj nazwę").setView(input)
                    .setPositiveButton("Zapisz") { _, _ ->
                        c.name = input.text.toString()
                        save()
                        updateCard(c)
                    }.setNegativeButton("Anuluj", null).show()
            }
        }
        card.addView(head, LinearLayout.LayoutParams(-1, -2))
        fun label(s: String) = card.addView(text(s, 11f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(7) })
        fun value(n: Int): TextView = text(n.toString(), 25f, true).also { card.addView(it) }

        label("Licznik główny")
        val general = value(c.g)
        val controls = horizontal()
        val a = btn("+$plus", 0xFF159447.toInt()) { mutate(c) { c.g += plus } }
        val b = btn("−$minus", 0xFFB63C3C.toInt(), c.g >= maxOf(5, minus)) { mutate(c) { c.g -= minus; c.p += minus } }
        controls.addView(a, LinearLayout.LayoutParams(0, d(43), 1f).apply { rightMargin = d(3) })
        controls.addView(b, LinearLayout.LayoutParams(0, d(43), 1f))
        card.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(7) })

        label("Fizycznie w pojemniku")
        val physical = value(c.p)
        val szymon = btn("SZYMON", 0xFF476481.toInt(), c.p > 0) {
            mutate(c) { val x = min(5, c.p); c.p -= x; c.s += x }
        }
        card.addView(szymon, LinearLayout.LayoutParams(-1, d(40)).apply { topMargin = d(4) })

        label("Zabrane przez Szymona")
        val taken = value(c.s)
        val exchange = btn("WYMIANA", 0xFFE28A20.toInt(), c.s > 0) {
            mutate(c) { val x = min(5, c.s); c.s -= x; c.t += x }
        }
        card.addView(exchange, LinearLayout.LayoutParams(-1, d(40)).apply { topMargin = d(4) })

        val bottom = horizontal().apply { gravity = Gravity.BOTTOM }
        val sum = vertical().apply {
            background = shape(0xFFF4F5F7.toInt(), 18, 0xFFBBC4D0.toInt())
            pad(this, 10, 4, 10, 4)
        }
        sum.addView(text("SUMA", 10f, true))
        val total = text(c.t.toString(), 17f, true)
        sum.addView(total)
        bottom.addView(sum, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = d(5) })
        val undo = btn("COFNIJ", 0xFFB63C3C.toInt(), c.history.isNotEmpty()) {
            if (c.history.isNotEmpty()) {
                val old = c.history.removeAt(c.history.lastIndex)
                c.g = old.g; c.p = old.p; c.s = old.s; c.t = old.t
                save()
                updateCard(c)
            }
        }
        bottom.addView(undo, LinearLayout.LayoutParams(0, d(43), 1f))
        card.addView(bottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(9) })

        views[c] = CounterViews(head, general, physical, taken, total, a, b, szymon, exchange, undo)
        return card
    }
}
