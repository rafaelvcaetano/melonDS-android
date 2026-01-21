package me.magnum.melonds.extensions

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.core.os.ParcelCompat

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? {
    return IntentCompat.getParcelableExtra<T>(this, key, T::class.java)
}

inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? {
    return BundleCompat.getParcelable(this, key, T::class.java)
}

inline fun <reified T : Parcelable> Parcel.parcelable(): T? {
    return ParcelCompat.readParcelable<T>(this, T::class.java.classLoader, T::class.java)
}