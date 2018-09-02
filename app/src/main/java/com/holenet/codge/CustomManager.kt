package com.holenet.codge

import android.content.Context
import android.graphics.Color
import android.text.TextUtils

object CustomManager {
    enum class CustomType {
        PlayerBaseColor, PlayerPatternColor, PlayerPatternShape, BallColor
    }

    private const val prefName = "custom"
    private val defaultColors = listOf(
            "#FF292929".toColorInt(),
            "#FFFFCC33".toColorInt(),
            "#FF333333".toColorInt(),
            "#FFF2F2F2".toColorInt()
    )
    private val colorsSet = HashMap<CustomType, MutableList<Int>>()

    private fun Int.toColorString(): String {
        var long = this.toLong()
        if (long < 0) {
            long += 4294967296
        }
        return '#' + long.toString(16).toUpperCase()
    }

    private fun String.toColorInt(): Int {
        return try {
            Color.parseColor(this)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            Color.TRANSPARENT
        }
    }

    private fun serializeColors(colors: List<Int>): String {
        return TextUtils.join("-", colors.map { it.toColorString() })
    }

    private fun deserializeColors(serial: String): List<Int> {
        return serial.split("-").map { it.toColorInt() }
    }

    fun loadColors(context: Context) {
        val pref = context.getSharedPreferences(prefName, 0)
        for (type in CustomType.values()) {
            val colorsString = pref.getString(type.toString(), null)
            colorsSet[type] =  if (colorsString == null) defaultColors.toMutableList() else deserializeColors(colorsString).toMutableList()
        }
    }

    fun getColors(type: CustomType): MutableList<Int> {
        return colorsSet[type]!!
    }

    fun saveColors(context: Context) {
        val pref = context.getSharedPreferences(prefName, 0)
        val editor = pref.edit()
        for (type in CustomType.values()) {
            editor.putString(type.toString(), serializeColors(colorsSet[type]!!))
        }
        editor.apply()
    }
}
