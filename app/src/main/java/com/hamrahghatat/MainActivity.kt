package com.hamrahghatat

import android.content.Context
import android.os.Bundle
import android.widget.ListView
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.DecimalFormat

data class ApiResponse(val success: Boolean, val data: List<Product>)
data class Product(val name: String, val brand: String, val price: Long, val code: String = "")

class MainActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private val allProducts = mutableListOf<Product>()
    private lateinit var adapter: ProductAdapter
    private val fmt = DecimalFormat("#,##0")
    
    // کلید ذخیره‌سازی در SharedPreferences
    companion object {
        private const val PREFS_NAME = "YadakMarketPrefs"
        private const val KEY_PRODUCTS = "cached_products_json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listView = ListView(this).apply { id = 1 }
        searchView = SearchView(this).apply { 
            id = 2
            queryHint = "جستجوی قطعه (مثال: چراغ جلوی پژو)..." 
        }
        
        val layout = android.widget.LinearLayout(this).apply { 
            orientation = android.widget.LinearLayout.VERTICAL
            addView(searchView)
            addView(listView) 
        }
        setContentView(layout)
        
        adapter = ProductAdapter(this, emptyList())
        listView.adapter = adapter
        
        // ۱. اول لود از کش (آفلاین)
        loadFromCache()
        
        // ۲. بعد سینک با سرور (آنلاین - در پس‌زمینه)
        fetchAndSyncProducts()
        
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { performSmartSearch(it) }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) showAllProducts() 
                else performSmartSearch(newText)
                return true
            }
        })
    }

    // --- مدیریت کش و سینک ---
    private fun loadFromCache() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PRODUCTS, null)
        if (!json.isNullOrEmpty()) {
            try {
                val gson = Gson()
                val type = object : TypeToken<List<Product>>() {}.type
                val cached: List<Product> = gson.fromJson(json, type)
                allProducts.clear()
                allProducts.addAll(cached)
                showAllProducts()
            } catch (e: Exception) { /* کش خرابه، نادیده بگیر */ }
        }
    }

    private fun saveToCache(products: List<Product>) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(products)
        prefs.edit().putString(KEY_PRODUCTS, json).apply()
    }

    private fun fetchAndSyncProducts() {
        // فقط اگر لیست خالی بود پیام لودینگ نشون بده
        if (allProducts.isEmpty()) {
            adapter.updateData(listOf(Product("در حال دریافت اطلاعات...", "", 0)))
        }

        OkHttpClient().newCall(
            Request.Builder().url("https://price-api-v2.aliinndd2.workers.dev/search?q=a").build()
        ).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                // اگر اینترنت نبود و کش هم نداشتیم
                runOnUiThread { 
                    if (allProducts.isEmpty()) {
                        adapter.updateData(listOf(Product("بدون اینترنت و بدون کش!", "", 0)))
                    }
                }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: return
                try {
                    val gson = Gson()
                    val apiResponse = gson.fromJson(body, ApiResponse::class.java)
                    if (apiResponse.success && apiResponse.data.isNotEmpty()) {
                        runOnUiThread {
                            allProducts.clear()
                            allProducts.addAll(apiResponse.data)
                            saveToCache(allProducts) // ✅ ذخیره در کش
                            showAllProducts()
                        }
                    }
                } catch (e: Exception) { /* خطا در پارس، کش قبلی حفظ میشه */ }
            }
        })
    }

    // --- رابط کاربری و جستجو ---
    private fun getBrandColor(brand: String): Int {
        if (brand.isBlank()) return android.graphics.Color.GRAY
        val hash = brand.hashCode()
        val r = ((hash and 0xFF0000) shr 16) or 0x40 
        val g = ((hash and 0x00FF00) shr 8) or 0x40
        val b = (hash and 0x0000FF) or 0x40
        return android.graphics.Color.rgb(r, g, b)
    }

    private inner class ProductAdapter(
        context: android.content.Context, 
        products: List<Product>
    ) : android.widget.BaseAdapter() {
        private val items = products.toMutableList()
        private val inflater = android.view.LayoutInflater.from(context)

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Product = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup?): android.view.View {
            val view = convertView ?: inflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            val product = items[position]
            
            val text1 = view.findViewById<android.widget.TextView>(android.R.id.text1)
            val text2 = view.findViewById<android.widget.TextView>(android.R.id.text2)
            
            val nameText = product.name
            val brandText = product.brand
            text1.text = "$nameText ($brandText)"
            text1.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            
            val spannable = android.text.SpannableString(text1.text)
            val brandStart = nameText.length + 2
            val brandEnd = spannable.length - 1
            if (brandEnd > brandStart) {
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(getBrandColor(brandText)),
                    brandStart, brandEnd,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            text1.text = spannable
            
            text2.text = "${fmt.format(product.price)} تومان"
            text2.setTextColor(android.graphics.Color.RED)
            text2.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            
            return view
        }
        
        fun updateData(newProducts: List<Product>) {
            items.clear()
            items.addAll(newProducts)
            notifyDataSetChanged()
        }
    }

    private fun showAllProducts() {
        adapter.updateData(allProducts)
    }

    private fun performSmartSearch(query: String) {
        if (allProducts.isEmpty()) return
        val keywords = query.split(" ").filter { it.length > 1 }.map { it.trim() }
        if (keywords.isEmpty()) { showAllProducts(); return }

        val scoredProducts = allProducts.mapNotNull { product ->
            val lowerName = product.name.lowercase()
            val score = keywords.count { keyword -> lowerName.contains(keyword.lowercase()) }
            if (score > 0) Pair(score, product) else null
        }

        val sorted = scoredProducts.sortedByDescending { it.first }
        if (sorted.isEmpty()) {
            adapter.updateData(listOf(Product("محصولی یافت نشد", "", 0)))
        } else {
            adapter.updateData(sorted.map { it.second })
        }
    }
}