package me.magnum.melonds.utils

import android.net.Uri
import androidx.core.net.toUri
import com.google.gson.*
import java.lang.reflect.Type
import kotlin.jvm.Throws


class UriTypeHierarchyAdapter : JsonDeserializer<Uri?>, JsonSerializer<Uri?> {
    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?): Uri {
        return json.asString.toUri()
    }

    override fun serialize(src: Uri?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(src.toString())
    }
}