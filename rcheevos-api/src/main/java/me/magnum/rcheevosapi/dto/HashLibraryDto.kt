package me.magnum.rcheevosapi.dto

import com.google.gson.annotations.SerializedName

internal data class HashLibraryDto(
    @SerializedName("MD5List")
    val md5List: Map<String, Long>,
)
