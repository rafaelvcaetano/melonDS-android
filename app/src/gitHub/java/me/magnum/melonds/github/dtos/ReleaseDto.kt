package me.magnum.melonds.github.dtos

import com.google.gson.annotations.SerializedName

data class ReleaseDto(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String,
    @SerializedName("body") val body: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("assets") val assets: List<AssetDto>
)