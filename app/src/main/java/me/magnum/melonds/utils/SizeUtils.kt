package me.magnum.melonds.utils

import android.content.Context
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.SizeUnit
import java.math.BigDecimal
import java.math.RoundingMode

typealias SizeRepresentation = Pair<Double, String>

object SizeUtils {

    fun getBestSizeStringRepresentation(context: Context, size: SizeUnit, decimalPlaces: Int = 0): String {
        val (sizeValue, sizeUnits) = getBestSizeRepresentation(context, size)
        val sizeDecimal = BigDecimal(sizeValue).setScale(decimalPlaces, RoundingMode.HALF_EVEN)
        return "${sizeDecimal}${sizeUnits}"
    }

    fun getBestSizeRepresentation(context: Context, size: SizeUnit): SizeRepresentation {
        return when(size.toBestRepresentation()) {
            is SizeUnit.Bytes -> size.toBytes().toDouble() to context.getString(R.string.size_bytes)
            is SizeUnit.KB -> size.toKB() to context.getString(R.string.size_kb)
            is SizeUnit.MB -> size.toMB() to context.getString(R.string.size_mb)
            is SizeUnit.GB -> size.toGB() to context.getString(R.string.size_gb)
        }
    }
}