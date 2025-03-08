package me.magnum.melonds.github.dtos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssetDto(
    @SerialName("id") val id: Long,
    @SerialName("browser_download_url") val url: String,
    @SerialName("name") val name: String,
    @SerialName("size") val size: Long,
    @SerialName("content_type") val contentType: String
)