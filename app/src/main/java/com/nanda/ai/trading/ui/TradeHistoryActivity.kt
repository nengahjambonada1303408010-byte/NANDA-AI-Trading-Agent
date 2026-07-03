package com.nanda.ai.trading.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nanda.ai.trading.R
import com.nanda.ai.trading.data.AppDatabase
import com.nanda.ai.trading.data.TradeEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TradeHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: TradeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trade_history)

        supportActionBar?.title = "Riwayat Trading"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recyclerView)
        tvEmpty = findViewById(R.id.tvEmpty)

        adapter = TradeAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadTradeHistory()
    }

    private fun loadTradeHistory() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@TradeHistoryActivity)
            val trades = database.tradeDao().getAllTradesSync()

            if (trades.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                adapter.setTrades(trades)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class TradeAdapter : RecyclerView.Adapter<TradeAdapter.TradeViewHolder>() {

    private var trades = listOf<TradeEntity>()

    fun setTrades(newTrades: List<TradeEntity>) {
        trades = newTrades
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TradeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trade, parent, false)
        return TradeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TradeViewHolder, position: Int) {
        holder.bind(trades[position])
    }

    override fun getItemCount() = trades.size

    class TradeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSymbol: TextView = itemView.findViewById(R.id.tvSymbol)
        private val tvSide: TextView = itemView.findViewById(R.id.tvSide)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        fun bind(trade: TradeEntity) {
            tvSymbol.text = trade.symbol
            tvSide.text = trade.side
            tvSide.setTextColor(
                if (trade.side == "Buy") android.graphics.Color.parseColor("#00B894")
                else android.graphics.Color.parseColor("#FF4757")
            )
            tvPrice.text = "@${trade.entryPrice}"
            tvStatus.text = trade.status
            tvStatus.setTextColor(
                when (trade.status) {
                    "EXECUTED", "WIN" -> android.graphics.Color.parseColor("#00B894")
                    "FAILED" -> android.graphics.Color.parseColor("#FF4757")
                    else -> android.graphics.Color.parseColor("#FFD700")
                }
            )

            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            tvTime.text = sdf.format(Date(trade.timestamp))
        }
    }
}
