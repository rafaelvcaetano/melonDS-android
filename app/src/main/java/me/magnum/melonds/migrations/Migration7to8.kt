package me.magnum.melonds.migrations

import android.content.Context

class Migration7to8(private val context: Context) : Migration {
    override val from: Int
        get() = 7
    override val to: Int
        get() = 8

    override fun migrate() {
        // Delete cached icons since the directory is now different
        context.externalCacheDir?.let { cacheDir ->
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }
}