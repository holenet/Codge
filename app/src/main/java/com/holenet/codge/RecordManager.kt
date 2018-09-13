package com.holenet.codge

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import android.content.Context
import android.os.AsyncTask
import android.text.TextUtils

enum class Input {
    TURN, JUMP_ON, JUMP_OFF, KILL_SELF;
    companion object {
        val values = values()
    }
}

class Converters {
    companion object {
        @TypeConverter @JvmStatic
        fun serializeDirection(direction: Direction) = direction.ordinal
        @TypeConverter @JvmStatic
        fun deserializeDirection(ordinal: Int) = Direction.values[ordinal]

        @TypeConverter @JvmStatic
        fun serializeInputList(inputs: MutableList<Pair<Int, Input>>) = TextUtils.join("-", inputs.map { (a, b) -> "$a:${b.ordinal}" })!!
        @TypeConverter @JvmStatic
        fun deserializeInputList(string: String) = if (string.isEmpty()) mutableListOf() else
            string.split("-").map {
                with (it.split(":")) {
                    get(0).toInt() to Input.values[get(1).toInt()]
                }
            }.toMutableList()
    }
}

@Entity(tableName = "records")
class Record(@PrimaryKey(autoGenerate = true) var id: Long? = null,
             var recordedAtMillis: Long,
             var seed: Long = recordedAtMillis,
             var firstDirection: Direction
) {
    var inputList: MutableList<Pair<Int, Input>> = ArrayList()
    var score = 0
}

@Dao
interface RecordDao {
    @Query("SELECT * from records ORDER BY score DESC") fun getAllByScore(): LiveData<List<Record>>
    @Query("SELECT * from records ORDER BY recordedAtMillis DESC") fun getAllByRecordedTime(): LiveData<List<Record>>
    @Insert fun insert(record: Record)
    @Delete fun delete(record: Record)
    @Query("DELETE FROM records") fun deleteAll()
}

@Database(entities = [Record::class], version = 2)
@TypeConverters(Converters::class)
abstract class RecordDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao

    companion object {
        private var INSTANCE: RecordDatabase? = null

        fun getInstance(context: Context): RecordDatabase {
            if (INSTANCE == null) {
                synchronized(RecordDatabase::class) {
                    INSTANCE = Room.databaseBuilder(context.applicationContext, RecordDatabase::class.java, "record.db").fallbackToDestructiveMigration().build()
                }
            }
            return INSTANCE!!
        }
    }
}

class RecordRepository(application: Application) {
    private val dao = RecordDatabase.getInstance(application).recordDao()
    val allRecords = dao.getAllByScore()

    fun insert(record: Record) { InsertTask(dao).execute(record) }

    class InsertTask(val dao: RecordDao) : AsyncTask<Record, Void, Void>() {
        override fun doInBackground(vararg params: Record?): Void? {
            dao.insert(params[0]!!)
            return null
        }
    }
}

class RecordViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = RecordRepository(application)
    val allRecords = repo.allRecords

    fun insert(record: Record) = repo.insert(record)
}
