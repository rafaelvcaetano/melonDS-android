package me.magnum.rcheevosapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class LeaderboardDto(
    @SerialName("ID")
    val id: Long,
    @SerialName("Mem")
    val mem: String,
    @SerialName("Format")
    val format: String,
    @SerialName("LowerIsBetter")
    val lowerIsBetter: Boolean,
    @SerialName("Title")
    val title: String,
    @SerialName("Description")
    val description: String,
    @SerialName("Hidden")
    val hidden: Boolean,
)
