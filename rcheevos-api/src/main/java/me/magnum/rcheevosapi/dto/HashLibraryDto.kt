package me.magnum.rcheevosapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class HashLibraryDto(
    @SerialName("MD5List")
    val md5List: Map<String, Long>,
)
