package com.hamrahghatat

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView

    private val client = OkHttpClient()
    private val gson = Gson()
    private lateinit var adapter: ProductAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        searchInput = findViewById(R.id.searchInput)
        searchButton = findViewById(R.id.searchButton)
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        errorText = findViewById(R.id.errorText)

        adapter = ProductAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                searchProducts(query)
            } else {
                Toast.makeText(this, "لطفاً نام قطعه را وارد کنید", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchProducts(query: String) {
        progressBar.visibility = View.VISIBLE
        errorText.visibility = View.GONE
        recyclerView.visibility = View.GONE

        val url = "https://price-api-v2.aliinndd2.workers.dev/search?q=$query"

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    errorText.visibility = View.VISIBLE
                    errorText.text = "خطا در اتصال به اینترنت"
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) {
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            errorText.visibility = View.VISIBLE
                            errorText.text = "خطا در دریافت اطلاعات"
                        }
                        return
                    }

                    val body = it.body?.string() ?: ""
                    val responseData = gson.fromJson(body, SearchResponse::class.java)

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        if (responseData.success && responseData.data.isNotEmpty()) {
                            recyclerView.visibility = View.VISIBLE
                            adapter.updateData(responseData.data)
                        } else {
                            errorText.visibility = View.VISIBLE
                            errorText.text = "نتیجه‌ای یافت نشد"
                        }
                    }
                }
            }
        })
    }
}

data class SearchResponse(
    val success: Boolean,
    val query: String,
    val count: Int,
    val data: List<Product>
)

data class Product(
    val id: Int,
    val name: String,
    val brand: String,
    val price: Int
)

class ProductAdapter(private var products: List<Product>) :
    RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]
        holder.bind(product)
    }

    override fun getItemCount() = products.size

    fun updateData(newProducts: List<Product>) {
        products = newProducts
        notifyDataSetChanged()
    }

    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.productName)
        private val brandText: TextView = itemView.findViewById(R.id.productBrand)
        private val priceText: TextView = itemView.findViewById(R.id.productPrice)

        fun bind(product: Product) {
            nameText.text = product.name
            brandText.text = "برند: ${product.brand}"
            priceText.text = formatPrice(product.price)
        }

        private fun formatPrice(price: Int): String {
            return String.format("%,d ریال", price)
        }
    }
}
