package com.hamrahghatat

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.DecimalFormat

data class ApiResponse(val success: Boolean, val data: List<Product>)
data class Product(val name: String, val brand: String, val price: Long, val code: String = "")

class MainActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private val allProducts = mutableListOf<Product>() // لیست کامل دانلود شده
    private val displayList = mutableListOf<String>() // لیست نمایشی
    private val adapter by lazy { ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList) }
    private val fmt = DecimalFormat("#,##0")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listView = ListView(this).apply { 
            id = 1 
            typeface = android.graphics.Typeface.MONOSPACE 
        }
        searchView = SearchView(this).apply { id = 2; queryHint = "جستجوی قطعه (مثال: چراغ جلوی پژو)..." }
        
        val layout = android.widget.LinearLayout(this).apply { 
            orientation = android.widget.LinearLayout.VERTICAL
            addView(searchView); addView(listView) 
        }
        setContentView(layout)
        listView.adapter = adapter
        
        // دانلود اولیه همه محصولات
        fetchAllProducts()
        
        // تنظیم جستجوی هوشمند لوکال
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { performSmartSearch(it) }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) {
                    showAllProducts()
                } else {
                    performSmartSearch(newText)
                }
                return true
            }
        })
    }

    private fun formatProduct(p: Product): String {
        // استفاده از فونت Monospace برای تراز شدن ستون‌ها
        return String.format("%-40s | %-15s %s تومان", p.name, p.brand, fmt.format(p.price))
    }

    private fun showAllProducts() {
        displayList.clear()
        displayList.addAll(allProducts.map { formatProduct(it) })
        adapter.notifyDataSetChanged()
    }

    // موتور جستجوی هوشمند با امتیازدهی
    private fun performSmartSearch(query: String) {
        if (allProducts.isEmpty()) return
        
        // جدا کردن کلمات و حذف کلمات کوتاه/بی‌معنی
        val keywords = query.split(" ").filter { it.length > 1 }.map { it.trim() }
        if (keywords.isEmpty()) { showAllProducts(); return }

        // محاسبه امتیاز برای هر محصول
        val scoredProducts = allProducts.mapNotNull { product ->
            val lowerName = product.name.lowercase()
            val score = keywords.count { keyword -> lowerName.contains(keyword.lowercase()) }
            if (score > 0) Pair(score, product) else null
        }

        // مرتب‌سازی: بیشترین امتیاز اول
        val sorted = scoredProducts.sortedByDescending { it.first }
        
        displayList.clear()
        if (sorted.isEmpty()) {
            displayList.add("محصولی با این مشخصات یافت نشد")
        } else {
            displayList.addAll(sorted.map { formatProduct(it.second) })
        }
        adapter.notifyDataSetChanged()
    }

    private fun fetchAllProducts() {
        displayList.clear()
        displayList.add("در حال بارگذاری catalog...")
        adapter.notifyDataSetChanged()

        // استفاده از کاراکتر رایج برای گرفتن حداکثر رکورد
        OkHttpClient().newCall(
            Request.Builder().url("https://price-api-v2.aliinndd2.workers.dev/search?q=a").build()
        ).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread { 
                    displayList.clear()
                    displayList.add("خطا در اتصال به سرور: ${e.message}") 
                    adapter.notifyDataSetChanged()
                }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: return
                try {
                    val gson = Gson()
                    val apiResponse = gson.fromJson(body, ApiResponse::class.java)
                    
                    if (apiResponse.success) {
                        runOnUiThread {
                            allProducts.clear()
                            allProducts.addAll(apiResponse.data)
                            showAllProducts() // نمایش همه بعد از دانلود موفق
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { 
                        displayList.clear()
                        displayList.add("خطا در پردازش دیتا: ${e.message}") 
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        })
    }
}