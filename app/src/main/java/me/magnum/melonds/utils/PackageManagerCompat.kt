package me.magnum.melonds.utils

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

object PackageManagerCompat {

    fun getPackageInfo(packageManager: PackageManager, packageName: String, flags: Int): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            packageManager.getPackageInfo(packageName, flags)
        }
    }
}