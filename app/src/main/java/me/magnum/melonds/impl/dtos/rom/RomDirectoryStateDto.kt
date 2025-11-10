package me.magnum.melonds.impl.dtos.rom

import com.google.gson.annotations.SerializedName

data class RomDirectoryStateDto(
    @SerializedName("directoryUri")
    val directoryUri: String,
    @SerializedName("hash")
    val hash: String,
    @SerializedName("lastScanned")
    val lastScanned: Long = 0,
    @SerializedName("files")
    val files: List<RomDirectoryFileDto>
)

data class RomDirectoryFileDto(
    @SerializedName("uri")
    val uri: String,
    @SerializedName("lastModified")
    val lastModified: Long,
    @SerializedName("size")
    val size: Long
)
