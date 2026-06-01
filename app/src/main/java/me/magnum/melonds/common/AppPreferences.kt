package me.magnum.melonds.common

import android.content.Context
import android.content.SharedPreferences

fun appSharedPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
}
