package me.magnum.rcheevosapi.dto

import com.google.gson.annotations.SerializedName

internal data class UserLoginDto(
    @SerializedName("Token")
    val token: String,
    @SerializedName("Score")
    val score: Long,
    @SerializedName("SoftcoreScore")
    val softcoreScore: Long,
)
