package com.holenet.codge

import android.content.Context
import java.io.*

enum class Input {
    TURN, JUMP_ON, JUMP_OFF
}

class Record(val recordedAtMillis: Long, val seed: Long = recordedAtMillis, val firstDirection: Direction) : Serializable {
    val inputList: ArrayList<Pair<Int, Input>> = ArrayList()
}

object RecordManager {
    fun saveRecord(context: Context, record: Record) {
        val file = File(context.filesDir, "${record.recordedAtMillis}.rec")
        val outStream = ObjectOutputStream(FileOutputStream(file))
        outStream.writeObject(record)
        outStream.close()
    }

    fun loadRecordList(context: Context): List<Record> {
        val files = context.filesDir.listFiles { _, name -> name.contains(".rec") }
        val recordList = ArrayList<Record>()
        for (file in files) {
            try {
                val inStream = ObjectInputStream(FileInputStream(file))
                val record = inStream.readObject() as Record
                inStream.close()
                recordList.add(record)
            } catch (e: Exception) {}
        }
        return recordList
    }
}
