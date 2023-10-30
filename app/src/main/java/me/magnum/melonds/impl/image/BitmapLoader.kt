package me.magnum.melonds.impl.image

import android.graphics.Bitmap
import android.net.Uri

interface BitmapLoader {
    fun loadAsBitmap(imageUri: Uri): Bitmap?
}