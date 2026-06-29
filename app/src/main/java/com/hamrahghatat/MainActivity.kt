package com.hamrahghatat

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SearchView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.opencsv.CSVReader
import java.io.InputStreamReader
import java.text.DecimalFormat

// تابع کمکی بیرون از کلاس برای دسترسی راحت
fun normalizeText(text: String): String {
    return text.trim().lowercase()
        .replace("ي", "ی").replace("ك", "ک")
        .replace(Regex("[\s\.\(\)\-\/,\:]+"), "") // Regex صحیح بدون فرار اضافی
}

data class Product(val name: String, val brand: String, val price: Long, val code: String = "") {
    fun uniqueKey(): String = "${normalizeText(name)}|${normalizeText(brand)}"
}

class MainActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var updateBtn: Button
    private val allProducts = mutableListOf<Product>()
    private lateinit var adapter: ProductAdapter
    private val fmt = DecimalFormat("#,##0")
    
    companion object {
        private const val PREFS_NAME = "YadakMarketPrefs"
        private const val KEY_PRODUCTS = "cached_products_json"
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) processCSVUpdate(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val rootLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        
        searchView = SearchView(this).apply { 
            queryHint = "جستجوی قطعه..."
            isIconified = false
        }
        
        updateBtn = Button(this).apply {
            text = "📂 بروزرسانی از فایل CSV"
            setOnClickListener { filePickerLauncher.launch("text/csv") }
        }
        
        listView = ListView(this)
        
        rootLayout.addView(searchView)
        rootLayout.addView(updateBtn)
        rootLayout.addView(listView)
        setContentView(rootLayout)
        
        adapter = ProductAdapter(this, emptyList())
        listView.adapter = adapter
        
        loadInitialData()
        
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                performSmartSearch(query ?: "")
                searchView.clearFocus()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) showAllProducts() 
                else performSmartSearch(newText)
                return true
            }
        })
    }

    private fun loadInitialData() {
        if (!loadFromCache()) {
            try {
                val inputStream = assets.open("products.csv")
                val reader = CSVReader(InputStreamReader(inputStream, "UTF-8"))
                val rows = reader.readAll()
                for (i in 1 until rows.size) {
                    val row = rows[i]
                    if (row.size >= 3 && row[0].trim().isNotEmpty()) {
                        allProducts.add(parseRow(row))
                    }
                }
                reader.close()
                saveToCache(allProducts)
                showAllProducts()
            } catch (e: Exception) {
                adapter.updateData(listOf(Product("خطا در خواندن فایل پایه", "", 0)).toMutableList())
            }
        }
    }

    private fun processCSVUpdate(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)!!
            val reader = CSVReader(InputStreamReader(inputStream, "UTF-8"))
            val newRows = reader.readAll()
            
            val existingMap = mutableMapOf<String, Int>()
            for ((index, product) in allProducts.withIndex()) {
                existingMap[product.uniqueKey()] = index
            }
            
            var addedCount = 0
            var updatedCount = 0
            
            for (i in 1 until newRows.size) {
                val row = newRows[i]
                if (row.size < 3 || row[0].trim().isEmpty()) continue
                
                val newProduct = parseRow(row)
                val key = newProduct.uniqueKey()
                
                if (existingMap.containsKey(key)) {
                    allProducts[existingMap[key]!!] = newProduct
                    updatedCount++
                } else {
                    allProducts.add(newProduct)
                    addedCount++
                }
            }
            
            reader.close()
            inputStream.close()
            
            allProducts.sortBy { it.name }
            saveToCache(allProducts)
            showAllProducts()
            
            adapter.updateData(
                listOf(
                    Product("✅ بروزرسانی موفق!", "", 0),
                    Product("جدید: $addedCount | آپدیت شده: $updatedCount", "", 0),
                    Product("کل قطعات: ${allProducts.size}", "", 0)
                ).toMutableList()
            )
            
        } catch (e: Exception) {
            adapter.updateData(listOf(Product("❌ خطا: ${e.message}", "", 0)).toMutableList())
        }
    }

    private fun parseRow(row: Array<String>): Product {
        val name = row[0].trim()
        val brand = row[1].trim()
        val priceStr = row[2].trim().replace(",", "").replace(" ", "")
        val price = priceStr.toLongOrNull() ?: 0L
        val code = if (row.size > 3) row[3].trim() else ""
        return Product(name, brand, price, code)
    }

    private fun loadFromCache(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PRODUCTS, null)
        if (!json.isNullOrEmpty()) {
            try {
                val gson = Gson()
                val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, Product::class.java).type
                val cached: List<Product> = gson.fromJson(json, type)
                allProducts.clear()
                allProducts.addAll(cached)
                showAllProducts()
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

    private fun performSmartSearch(q: String) {
        if (allProducts.isEmpty()) return
        val normalizedQuery = normalizeText(q)
        val keywords = normalizedQuery.split(Regex("\s+")).filter { it.isNotEmpty() }
        if (keywords.isEmpty()) { showAllProducts(); return }

        val results = allProducts.mapNotNull { product ->
            val combined = "${normalizeText(product.name)} ${normalizeText(product.brand)}"
            val matchCount = keywords.count { keyword -> combined.contains(keyword) }
            if (matchCount > 0) Pair(matchCount, product) else null
        }
        
        val sorted = results.sortedByDescending { it.first }
        adapter.updateData(
            if (sorted.isEmpty()) listOf(Product("محصولی یافت نشد", "", 0)).toMutableList()
            else sorted.map { it.second }.toMutableList()
        )
    }

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
        override fun getView(pos: Int, cv: android.view.View?, parent: android.view.ViewGroup?): android.view.View {
            val view = cv ?: inflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            val p = items[pos]
            val t1 = view.findViewById<android.widget.TextView>(android.R.id.text1)
            val t2 = view.findViewById<android.widget.TextView>(android.R.id.text2)
            
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
}