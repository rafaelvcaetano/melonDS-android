package me.magnum.melonds.github.dtos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String,
    @SerialName("body") val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("assets") val assets: List<AssetDto>
)