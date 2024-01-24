package me.magnum.melonds.domain.model

class DSiWareTitle(
    val name: String,
    val producer: String,
    val titleId: Long,
    val icon: ByteArray,
    val publicSavSize: Long,
    val privateSavSize: Long,
    val appFlags: Int,
) {

    fun hasPublicSavFile() = publicSavSize != 0L

    fun hasPrivateSavFile() = privateSavSize != 0L

    fun hasBannerSavFile() = (appFlags and (0x04)) != 0
}