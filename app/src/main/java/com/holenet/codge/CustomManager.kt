package com.holenet.codge

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.text.TextUtils

fun Int.toColorString(): String {
    var long = this.toLong()
    if (long < 0) {
        long += 4294967296
    }
    return '#' + long.toString(16).toUpperCase()
}

fun String.toColorInt(): Int {
    return try {
        Color.parseColor(this)
    } catch (e: IllegalArgumentException) {
        e.printStackTrace()
        Color.TRANSPARENT
    }
}

enum class CustomType {
    PlayerBaseColor, PlayerPatternColor, PlayerPatternShape, BallColor
}

object CustomManager {
    private const val prefName = "custom"
    private val defaultColors = listOf(
            "#FF292929".toColorInt(),
            "#FFFFCC33".toColorInt(),
            "#FF333333".toColorInt(),
            "#FFF2F2F2".toColorInt(),
            "#FFF46700".toColorInt()
    )
    private val defaultIndices = mapOf(
            CustomType.PlayerBaseColor to 2,
            CustomType.PlayerPatternColor to 1,
            CustomType.PlayerPatternShape to 0,
            CustomType.BallColor to 4
    )
    private val colorsSet = HashMap<CustomType, MutableList<Int>>()
    private val indexSet = HashMap<CustomType, Int>()

    private fun serializeColors(colors: List<Int>): String {
        return TextUtils.join("-", colors.map { it.toColorString() })
    }

    private fun deserializeColors(serial: String): List<Int> {
        return serial.split("-").map { it.toColorInt() }
    }

    private fun getPreferences(context: Context): SharedPreferences = context.getSharedPreferences(prefName, 0)

    fun load(context: Context) {
        val pref = getPreferences(context)
        for (type in CustomType.values()) {
            val colorsString = pref.getString(type.toString(), null)
            colorsSet[type] =  if (colorsString == null) defaultColors.toMutableList() else deserializeColors(colorsString).toMutableList()
            val index = pref.getInt(type.toString() + "index", defaultIndices[type]!!)
            indexSet[type] = index
        }
    }

    fun getColors(type: CustomType): MutableList<Int> {
        return colorsSet[type]!!
    }

    fun getCurrentColor(type: CustomType): Int {
        return colorsSet[type]!![indexSet[type]!!]
    }

    fun updateCurrentColor(type: CustomType, index: Int) {
        indexSet[type] = index
    }

    fun addColor(type: CustomType, color: Int) {
        colorsSet[type]!!.add(color)
    }

    fun save(context: Context, type: CustomType) {
        val pref = getPreferences(context)
        with (pref.edit()) {
            putString(type.toString(), serializeColors(colorsSet[type]!!))
            putInt(type.toString() + "index", indexSet[type]!!)
            apply()
        }
    }
}
