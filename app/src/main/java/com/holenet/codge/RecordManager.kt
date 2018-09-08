package com.holenet.codge

import android.content.Context
import java.io.*

enum class Input {
    TURN, JUMP_ON, JUMP_OFF
}

class Record(val recordedAtMillis: Long, val seed: Long = recordedAtMillis, val firstDirection: Direction) : Serializable {
    val inputList: ArrayList<Pair<Int, Input>> = ArrayList()
    var score = 0
}

object RecordManager {
    var recordList = ArrayList<Record>(); private set

    private fun getFile(context: Context) = File(context.filesDir, "record.data")

    fun saveRecord(context: Context, record: Record) {
        Thread {
            recordList.add(record)
            val file = getFile(context)
            val outStream = ObjectOutputStream(FileOutputStream(file))
            outStream.writeObject(recordList)
            outStream.close()
        }.start()
    }

    fun loadRecordList(context: Context) {
        recordList.clear()
        val file = File(context.filesDir, "record.data")
        if (file.createNewFile()) return

        val inStream = ObjectInputStream(FileInputStream(file))
        try {
            for (record in inStream.readObject() as List<Record>) {
                recordList.add(record)
            }
        } catch (e: Exception) {}
        inStream.close()
    }
}
