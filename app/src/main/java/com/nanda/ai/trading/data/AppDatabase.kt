package com.nanda.ai.trading.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [TradeEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tradeDao(): TradeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nanda_ai_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey
    @ColumnInfo(name = "order_id") val orderId: String,
    @ColumnInfo(name = "symbol") val symbol: String,
    @ColumnInfo(name = "side") val side: String,
    @ColumnInfo(name = "entry_price") val entryPrice: String,
    @ColumnInfo(name = "qty") val qty: String,
    @ColumnInfo(name = "take_profit") val takeProfit: String,
    @ColumnInfo(name = "stop_loss") val stopLoss: String,
    @ColumnInfo(name = "status") val status: String, // EXECUTED, WIN, LOSS, FAILED, CANCELLED
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long
)

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY timestamp DESC")
    fun getAllTrades(): List<TradeEntity>

    @Query("SELECT * FROM trades ORDER BY timestamp DESC")
    suspend fun getAllTradesSync(): List<TradeEntity>

    @Query("SELECT * FROM trades WHERE symbol = :symbol ORDER BY timestamp DESC")
    suspend fun getTradesBySymbol(symbol: String): List<TradeEntity>

    @Query("SELECT * FROM trades WHERE status = :status ORDER BY timestamp DESC")
    suspend fun getTradesByStatus(status: String): List<TradeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: TradeEntity)

    @Query("DELETE FROM trades WHERE order_id = :orderId")
    suspend fun deleteTrade(orderId: String)

    @Query("DELETE FROM trades")
    suspend fun deleteAllTrades()

    @Query("SELECT COUNT(*) FROM trades")
    suspend fun getTradeCount(): Int
}
