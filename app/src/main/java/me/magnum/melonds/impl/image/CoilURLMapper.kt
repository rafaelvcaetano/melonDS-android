package me.magnum.melonds.impl.image

import android.net.Uri
import androidx.core.net.toUri
import coil.map.Mapper
import coil.request.Options
import java.net.URL

class CoilURLMapper : Mapper<URL, Uri> {

    override fun map(data: URL, options: Options): Uri {
        return data.toString().toUri()
    }
}