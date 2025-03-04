package me.magnum.melonds.parcelables

import android.os.Parcelable
import androidx.core.net.toUri
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.magnum.melonds.domain.model.Background
import java.util.UUID

@Serializable
@Parcelize
class BackgroundParcelable(
    @SerialName("uuid")
    private val uuid: String,
    @SerialName("name")
    private val name: String,
    @SerialName("uri")
    private val uri: String,
) : Parcelable {

    fun toBackground() = Background(UUID.fromString(uuid), name, uri.toUri())

    companion object {
        fun fromBackground(background: Background): BackgroundParcelable {
            return BackgroundParcelable(
                uuid = background.id.toString(),
                name = background.name,
                uri = background.uri.toString(),
            )
        }
    }
}