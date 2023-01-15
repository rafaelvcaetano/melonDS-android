package me.magnum.rcheevosapi.dto

import com.google.gson.annotations.SerializedName

internal data class UserUnlocksDto(
    @SerializedName("UserUnlocks")
    val userUnlocks: List<Long>,
)