package dev.dotscale.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Los permisos que hacen falta para buscar y conectar una báscula (en Android 11 o anterior, la ubicación). */
val BLUETOOTH_PERMISSIONS: Array<String> =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
  } else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
  }

fun Context.hasBluetoothPermissions(): Boolean =
  BLUETOOTH_PERMISSIONS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
