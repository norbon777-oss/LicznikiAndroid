package pl.liczniki.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("liczniki_saved_data", MODE_PRIVATE) }

    inner class StorageBridge {
        @JavascriptInterface
        fun load(): String = prefs.getString("state", "") ?: ""

        @JavascriptInterface
        fun save(json: String) {
            prefs.edit().putString("state", json).commit()
        }
    }

    inner class ReportBridge {
        @JavascriptInterface
        fun sendExcel() {
            runOnUiThread {
                try {
                    val file = File(cacheDir, "LICZNIKI_RAPORT.xlsx")
                    generateExcel(file)
                    val uri = Uri.parse("content://${packageName}.reports/LICZNIKI_RAPORT.xlsx")
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "LICZNIKI - raport Excel")
                        putExtra(Intent.EXTRA_TEXT, "W załączniku raport LICZNIKI z aktualnymi danymi.")
                        clipData = ClipData.newUri(contentResolver, "LICZNIKI_RAPORT.xlsx", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Wyślij raport Excel"))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Nie udało się utworzyć raportu: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun generateExcel(output: File) {
        val saved = prefs.getString("state", "") ?: ""
        val json = if (saved.isNotBlank()) JSONObject(saved) else JSONObject()
        val items = json.optJSONArray("items")
        val fallback = arrayOf("522694320 - ALL", "522694330 - JEEP", "522694570 - FIAT/ALFA", "522694340 - JEEP", "522694580 - FIAT", "522694770 - ALFA")
        val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        // Szablon XLSX jest dostarczany razem z aplikacją (zaufany zasób).
        // Android nie obsługuje funkcji Xerces disallow-doctype-decl.
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }
        assets.open("raport_wzor.xlsx").use { template ->
            ZipInputStream(template).use { input ->
                ZipOutputStream(output.outputStream()).use { zip ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        zip.putNextEntry(ZipEntry(entry.name))
                        if (entry.name == "xl/worksheets/sheet1.xml") {
                            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(input.readBytes()))
                            val cells = doc.getElementsByTagNameNS(ns, "c")
                            val index = mutableMapOf<String, org.w3c.dom.Element>()
                            for (i in 0 until cells.length) {
                                val cell = cells.item(i) as org.w3c.dom.Element
                                index[cell.getAttribute("r")] = cell
                            }
                            fun writeCell(address: String, value: String, numeric: Boolean) {
                                val cell = index[address] ?: return
                                cell.setAttribute("t", if (numeric) "n" else "str")
                                val nodes = cell.getElementsByTagNameNS(ns, "v")
                                val v = if (nodes.length > 0) nodes.item(0) else doc.createElementNS(ns, "v").also { cell.appendChild(it) }
                                v.textContent = value
                            }
                            for (i in 0 until 6) {
                                val item = items?.optJSONObject(i)
                                val col = ('A'.code + i).toChar().toString()
                                writeCell("${col}5", item?.optString("name", fallback[i]) ?: fallback[i], false)
                                for ((row, key) in listOf(7 to "g", 10 to "p", 13 to "s", 16 to "t")) {
                                    writeCell("$col$row", (item?.optLong(key, 0L) ?: 0L).toString(), true)
                                }
                            }
                            writeCell("A3", "6 POZYCJI   •   AKTUALNE STANY LICZNIKÓW   •   DANE Z APLIKACJI", false)
                            writeCell("A18", "RAPORT WYGENEROWANY AUTOMATYCZNIE Z APLIKACJI LICZNIKI", false)
                            val bytes = ByteArrayOutputStream()
                            TransformerFactory.newInstance().newTransformer().transform(DOMSource(doc), StreamResult(bytes))
                            zip.write(bytes.toByteArray())
                        } else {
                            input.copyTo(zip)
                        }
                        zip.closeEntry()
                        input.closeEntry()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(0, 27, 66)
        window.navigationBarColor = Color.rgb(0, 27, 66)
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(StorageBridge(), "AndroidStorage")
        web.addJavascriptInterface(ReportBridge(), "AndroidReport")
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        web.setBackgroundColor(Color.rgb(0, 32, 78))
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }
}
