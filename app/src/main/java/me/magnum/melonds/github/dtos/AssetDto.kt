package me.magnum.melonds.github.dtos

import com.google.gson.annotations.SerializedName

data class AssetDto(
    @SerializedName("id") val id: Long,
    @SerializedName("browser_download_url") val url: String,
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long,
    @SerializedName("content_type") val contentType: String
)