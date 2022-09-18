package me.magnum.melonds.migrations

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import me.magnum.melonds.utils.PackageManagerCompat

class Migrator(private val context: Context, private val sharedPreferences: SharedPreferences) {
    private val migrations = mutableListOf<Migration>()

    fun registerMigration(migration: Migration) {
        if (migrations.find { it.from == migration.from } != null) {
            throw Exception("Migration from version ${migration.from} already exists")
        }

        migrations.add(migration)
    }

    fun performMigrations() {
        if (!mustPerformMigrations())
            return

        getMigrationsToPerform().forEach {
            it.migrate()
        }
        sharedPreferences.edit {
            putLong("last_version", getCurrentVersion())
        }
    }

    private fun mustPerformMigrations(): Boolean {
        return getLastVersion() < getCurrentVersion()
    }

    private fun getMigrationsToPerform(): List<Migration> {
        val fromVersion = getLastVersion()
        val toVersion = getCurrentVersion()

        return migrations
                .sortedBy { it.from }
                .filter { it.from >= fromVersion && it.to <= toVersion }
    }

    private fun getLastVersion(): Long {
        // 6 is the version at which migrations started being supported
        return sharedPreferences.getLong("last_version", 6)
    }

    private fun getCurrentVersion(): Long {
        val packageInfo = PackageManagerCompat.getPackageInfo(context.packageManager, context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(packageInfo)
    }
}