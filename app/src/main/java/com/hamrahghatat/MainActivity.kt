package com.hamrahghatat

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale

data class ApiResponse(
    val success: Boolean,
    val data: List<Product>
)

data class Product(
    val name: String,
    val brand: String,
    val price: Long,
    val code: String = ""
)

class MainActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private val productList = mutableListOf<Product>()
    private val adapter by lazy { 
        ArrayAdapter(this, android.R.layout.simple_list_item_1, 
            productList.map { formatProduct(it) }) 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listView = ListView(this).apply { id = 1 }
        searchView = SearchView(this).apply { id = 2; queryHint = "جستجوی قطعه..." }
        val layout = android.widget.LinearLayout(this).apply { 
            orientation = android.widget.LinearLayout.VERTICAL
            addView(searchView); addView(listView) 
        }
        setContentView(layout)
        listView.adapter = adapter
        fetchProducts("a")
        
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { fetchProducts(it) }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean = false
        })
    }

    private fun formatProduct(p: Product): String {
        val fmt = NumberFormat.getInstance(Locale("fa", "IR"))
        return "${p.name} | ${p.brand}: ${fmt.format(p.price)} تومان"
    }

    private fun fetchProducts(query: String) {
        adapter.clear()
        adapter.add("در حال دریافت اطلاعات...")
        
        OkHttpClient().newCall(
            Request.Builder().url("https://price-api-v2.aliinndd2.workers.dev/search?q=$query").build()
        ).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread { 
                    adapter.clear()
                    adapter.add("خطا در اتصال: ${e.message}") 
                }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: return
                try {
                    val gson = Gson()
                    val apiResponse = gson.fromJson(body, ApiResponse::class.java)
                    
                    if (apiResponse.success) {
                        runOnUiThread {
                            productList.clear()
                            productList.addAll(apiResponse.data)
                            adapter.clear()
                            if (productList.isEmpty()) {
                                adapter.add("محصولی یافت نشد")
                            } else {
                                adapter.addAll(productList.map { formatProduct(it) })
                            }
                        }
                    } else {
                        runOnUiThread { 
                            adapter.clear()
                            adapter.add("پاسخ نامعتبر از سرور") 
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { 
                        adapter.clear()
                        adapter.add("خطا در پردازش: ${e.message}") 
                    }
                }
            }
        })
    }
}