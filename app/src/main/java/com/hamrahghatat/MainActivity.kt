package com.hamrahghatat
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class Product(val name: String, val brand: String, val price: Int, val car: String)

class MainActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private val productList = mutableListOf<Product>()
    private val adapter by lazy { ArrayAdapter(this, android.R.layout.simple_list_item_1, productList.map { "${it.name} - ${it.brand}: ${it.price} تومان" }) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listView = ListView(this).apply { id = 1 }
        searchView = SearchView(this).apply { id = 2; queryHint = "جستجوی قطعه..." }
        val layout = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; addView(searchView); addView(listView) }
        setContentView(layout)
        listView.adapter = adapter
        fetchProducts()
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                val filtered = productList.filter { it.name.contains(newText ?: "", ignoreCase = true) }
                adapter.clear(); adapter.addAll(filtered.map { "${it.name} - ${it.brand}: ${it.price} تومان" })
                return true
            }
        })
    }

    private fun fetchProducts() {
        OkHttpClient().newCall(Request.Builder().url("https://price-api-v2.aliinndd2.workers.dev/search?q=a").build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) { runOnUiThread { adapter.add("خطا در اتصال: ${e.message}") } }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: return
                try {
                    val gson = Gson()
                    val type = object : TypeToken<Map<String, List<Product>>>() {}.type
                    val result: Map<String, List<Product>> = gson.fromJson(body, type)
                    val products = result["data"] ?: emptyList()
                    runOnUiThread { productList.clear(); productList.addAll(products); adapter.clear(); adapter.addAll(products.map { "${it.name} - ${it.brand}: ${it.price} تومان" }) }
                } catch (e: Exception) { runOnUiThread { adapter.add("خطا در پردازش: ${e.message}") } }
            }
        })
    }
}
