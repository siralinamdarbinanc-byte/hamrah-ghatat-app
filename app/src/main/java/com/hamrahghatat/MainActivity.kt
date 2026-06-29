package com.hamrahghatat

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SearchView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.DecimalFormat

data class ApiResponse(val success: Boolean, val data: List<Product>, val count: Int = 0)
data class Product(val name: String, val brand: String, val price: Long, val code: String = "")

class MainActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var downloadBtn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private val allProducts = mutableListOf<Product>()
    private lateinit var adapter: ProductAdapter
    private val fmt = DecimalFormat("#,##0")
    private var isOfflineMode = false
    
    companion object {
        private const val PREFS_NAME = "YadakMarketPrefs"
        private const val KEY_PRODUCTS = "cached_products_json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // بررسی کش موجود
        if (loadFromCache()) {
            isOfflineMode = true
        }

        // ساخت UI
        val rootLayout = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL 
        }
        
        searchView = SearchView(this).apply { 
            queryHint = "جستجوی آنلاین قطعه..."
            isIconified = false
        }
        
        // بخش دانلود آفلاین
        val downloadSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 8)
            
            downloadBtn = Button(this@MainActivity).apply {
                text = if (isOfflineMode) "آپدیت لیست آفلاین" else "دانلود لیست آفلاین"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            progressBar = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                visibility = View.GONE
                max = 100
            }
            
            progressText = TextView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_HEIGHT)
                textSize = 12f
                visibility = View.GONE
            }
            
            addView(downloadBtn)
            addView(progressBar)
            addView(progressText)
        }
        
        listView = ListView(this)
        
        rootLayout.addView(searchView)
        rootLayout.addView(downloadSection)
        rootLayout.addView(listView)
        
        setContentView(rootLayout)
        
        adapter = ProductAdapter(this, emptyList())
        listView.adapter = adapter
        
        // اگر کش داشتیم، نشون بده
        if (isOfflineMode) {
            showAllProducts()
            searchView.queryHint = "جستجو در لیست آفلاین (${allProducts.size} قطعه)"
        }
        
        // رویداد دکمه دانلود
        downloadBtn.setOnClickListener { startOfflineDownload() }
        
        // رویداد سرچ
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (isOfflineMode) performSmartSearch(query ?: "")
                else fetchOnlineSearch(query ?: "")
                searchView.clearFocus()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) {
                    if (isOfflineMode) showAllProducts()
                } else {
                    if (isOfflineMode) performSmartSearch(newText)
                    // در حالت آنلاین، سرچ آنی نداریم تا ترافیک هدر نره
                }
                return true
            }
        })
    }

    // --- مدیریت کش ---
    private fun loadFromCache(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PRODUCTS, null)
        if (!json.isNullOrEmpty()) {
            try {
                val gson = Gson()
                val type = object : TypeToken<List<Product>>() {}.type
                val cached: List<Product> = gson.fromJson(json, type)
                allProducts.clear()
                allProducts.addAll(cached)
                return true
            } catch (e: Exception) {}
        }
        return false
    }

    private fun saveToCache(products: List<Product>) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(products)
        prefs.edit().putString(KEY_PRODUCTS, json).apply()
    }

    // --- دانلود آفلاین با نوار پیشرفت ---
    private fun startOfflineDownload() {
        downloadBtn.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        progressText.text = "در حال اتصال..."
        
        val client = OkHttpClient()
        val fetched = mutableListOf<Product>()
        var offset = 0
        val limit = 200
        
        fun fetchPage() {
            val url = "https://price-api-v2.aliinndd2.workers.dev/search?q=&offset=$offset&limit=$limit"
            client.newCall(Request.Builder().url(url).build()).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    runOnUiThread { 
                        progressText.text = "خطا در دانلود!"
                        downloadBtn.isEnabled = true
                    }
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val body = response.body?.string() ?: return
                    try {
                        val gson = Gson()
                        val apiResp = gson.fromJson(body, ApiResponse::class.java)
                        
                        if (apiResp.success && apiResp.data.isNotEmpty()) {
                            fetched.addAll(apiResp.data)
                            offset += limit
                            
                            // آپدیت نوار پیشرفت
                            val total = apiResp.count.coerceAtLeast(3637) // تخمین کل
                            val percent = ((fetched.size.toFloat() / total) * 100).toInt().coerceAtMost(100)
                            
                            runOnUiThread {
                                progressBar.progress = percent
                                progressText.text = "$percent% (${fetched.size} قطعه)"
                            }
                            
                            if (apiResp.data.size >= limit) {
                                fetchPage() // صفحه بعدی
                            } else {
                                // دانلود تمام شد
                                runOnUiThread {
                                    allProducts.clear()
                                    allProducts.addAll(fetched)
                                    saveToCache(allProducts)
                                    isOfflineMode = true
                                    
                                    progressBar.visibility = View.GONE
                                    progressText.visibility = View.GONE
                                    downloadBtn.text = "آپدیت لیست آفلاین"
                                    downloadBtn.isEnabled = true
                                    searchView.queryHint = "جستجو در لیست آفلاین (${allProducts.size} قطعه)"
                                    showAllProducts()
                                }
                            }
                        } else {
                            // پایان دیتا
                            runOnUiThread {
                                if (fetched.isNotEmpty()) {
                                    allProducts.clear(); allProducts.addAll(fetched)
                                    saveToCache(allProducts); isOfflineMode = true
                                    showAllProducts()
                                }
                                progressBar.visibility = View.GONE
                                progressText.visibility = View.GONE
                                downloadBtn.text = "آپدیت لیست آفلاین"
                                downloadBtn.isEnabled = true
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread { 
                            progressText.text = "خطا در پردازش!"
                            downloadBtn.isEnabled = true
                        }
                    }
                }
            })
        }
        fetchPage()
    }

    // --- سرچ آنلاین ---
    private fun fetchOnlineSearch(query: String) {
        if (query.length < 2) return
        adapter.updateData(listOf(Product("در حال جستجو...", "", 0)))
        
        val url = "https://price-api-v2.aliinndd2.workers.dev/search?q=$query"
        OkHttpClient().newCall(Request.Builder().url(url).build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread { adapter.updateData(listOf(Product("خطا در اتصال", "", 0))) }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: return
                try {
                    val gson = Gson()
                    val apiResp = gson.fromJson(body, ApiResponse::class.java)
                    runOnUiThread {
                        if (apiResp.success) {
                            adapter.updateData(apiResp.data)
                        } else {
                            adapter.updateData(listOf(Product("نتیجه‌ای یافت نشد", "", 0)))
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { adapter.updateData(listOf(Product("خطا در پردازش", "", 0))) }
                }
            }
        })
    }

    // --- توابع کمکی UI ---
    private fun getBrandColor(brand: String): Int {
        if (brand.isBlank()) return android.graphics.Color.GRAY
        val hash = brand.hashCode()
        val r = ((hash and 0xFF0000) shr 16) or 0x40 
        val g = ((hash and 0x00FF00) shr 8) or 0x40
        val b = (hash and 0x0000FF) or 0x40
        return android.graphics.Color.rgb(r, g, b)
    }

    private inner class ProductAdapter(ctx: Context, products: List<Product>) : android.widget.BaseAdapter() {
        private val items = products.toMutableList()
        private val inflater = android.view.LayoutInflater.from(ctx)
        override fun getCount(): Int = items.size
        override fun getItem(p: Int): Product = items[p]
        override fun getItemId(p: Int): Long = p.toLong()
        override fun getView(pos: Int, cv: View?, parent: android.view.ViewGroup?): View {
            val view = cv ?: inflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            val p = items[pos]
            val t1 = view.findViewById<TextView>(android.R.id.text1)
            val t2 = view.findViewById<TextView>(android.R.id.text2)
            
            t1.text = "${p.name} (${p.brand})"
            t1.textSize = 16f
            val span = android.text.SpannableString(t1.text)
            val start = p.name.length + 2
            val end = span.length - 1
            if (end > start) span.setSpan(android.text.style.ForegroundColorSpan(getBrandColor(p.brand)), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            t1.text = span
            
            t2.text = "${fmt.format(p.price)} تومان"
            t2.setTextColor(android.graphics.Color.RED)
            t2.textSize = 14f
            return view
        }
        fun updateData(new: List<Product>) { items.clear(); items.addAll(new); notifyDataSetChanged() }
    }

    private fun showAllProducts() { adapter.updateData(allProducts) }
    
    private fun performSmartSearch(q: String) {
        if (allProducts.isEmpty()) return
        val keys = q.split(" ").filter { it.length > 1 }.map { it.trim() }
        if (keys.isEmpty()) { showAllProducts(); return }
        val scored = allProducts.mapNotNull { pr ->
            val ln = pr.name.lowercase()
            val sc = keys.count { k -> ln.contains(k.lowercase()) }
            if (sc > 0) Pair(sc, pr) else null
        }.sortedByDescending { it.first }
        
        adapter.updateData(if (scored.isEmpty()) listOf(Product("یافت نشد", "", 0)) else scored.map { it.second })
    }
}