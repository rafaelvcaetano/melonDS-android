package me.magnum.rcheevosapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class UserUnlocksDto(
    @SerialName("UserUnlocks")
    val userUnlocks: List<Long>,
)