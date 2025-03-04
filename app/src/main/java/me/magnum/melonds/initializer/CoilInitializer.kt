package me.magnum.melonds.initializer

import android.content.Context
import androidx.startup.Initializer
import coil.Coil
import coil.ImageLoader
import me.magnum.melonds.di.entrypoint.InitializerEntryPoint
import javax.inject.Inject

class CoilInitializer : Initializer<Unit> {

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun create(context: Context) {
        InitializerEntryPoint.resolve(context).inject(this)
        Coil.setImageLoader(imageLoader)
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> = mutableListOf()
}