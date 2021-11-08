package me.magnum.melonds.utils

import me.magnum.melonds.domain.model.SizeUnit
import java.math.BigDecimal
import java.math.RoundingMode

typealias SizeRepresentation = Pair<Double, String>

object SizeUtils {

    fun getBestSizeStringRepresentation(size: SizeUnit, decimalPlaces: Int = 0): String {
        val (sizeValue, sizeUnits) = getBestSizeRepresentation(size)
        val sizeDecimal = BigDecimal(sizeValue).setScale(decimalPlaces, RoundingMode.HALF_EVEN)
        return "${sizeDecimal}${sizeUnits}"
    }

    fun getBestSizeRepresentation(size: SizeUnit): SizeRepresentation {
        return when(size.toBestRepresentation()) {
            is SizeUnit.Bytes -> size.toBytes().toDouble() to "B"
            is SizeUnit.KB -> size.toKB() to "KB"
            is SizeUnit.MB -> size.toMB() to "MB"
            is SizeUnit.GB -> size.toGB() to "GB"
        }
    }
}