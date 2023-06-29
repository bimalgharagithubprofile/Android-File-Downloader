package com.bimalghara.filedownloader.utils.permissions

import android.app.AlertDialog
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bimalghara.filedownloader.R
import java.lang.ref.WeakReference

class PermissionManager private constructor(private val activity: WeakReference<AppCompatActivity>) {

    private val requiredPermissions = mutableListOf<Permissions>()
    private var rationale: String? = null
    private var callback: (Boolean) -> Unit = {}

    private val permissionCheck =
        activity.get()?.registerForActivityResult(RequestMultiplePermissions()) { grantResults ->
            sendResultAndCleanUp(grantResults)
        }


    companion object {
        fun from(activity: AppCompatActivity) = PermissionManager(WeakReference(activity))
    }

    fun rationale(description: String): PermissionManager {
        rationale = description
        return this
    }

    fun request(vararg permission: Permissions): PermissionManager {
        requiredPermissions.addAll(permission)
        return this
    }

    fun checkPermission(callback: (Boolean) -> Unit) {
        this.callback = callback
        handlePermissionRequest()
    }

    private fun handlePermissionRequest() {
        activity.get()?.let { fragment ->
            when {
                areAllPermissionsGranted(fragment) -> sendPositiveResult()
                shouldShowPermissionRationale(fragment) -> displayRationale(fragment)
                else -> requestPermissions()
            }
        }
    }

    private fun displayRationale(activity: AppCompatActivity) {
        AlertDialog.Builder(activity.applicationContext)
            .setTitle(activity.getString(R.string.dialog_permission_title))
            .setMessage(rationale ?: activity.getString(R.string.dialog_permission_default_message))
            .setCancelable(false)
            .setPositiveButton(activity.getString(R.string.dialog_permission_button_positive)) { _, _ ->
                requestPermissions()
            }
            .show()
    }

    private fun sendPositiveResult() {
        sendResultAndCleanUp(getPermissionList().associate { it to true } )
    }

    private fun sendResultAndCleanUp(grantResults: Map<String, Boolean>) {
        callback(grantResults.all { it.value })
        cleanUp()
    }

    private fun cleanUp() {
        requiredPermissions.clear()
        rationale = null
        callback = {}
    }

    private fun requestPermissions() {
        permissionCheck?.launch(getPermissionList())
    }

    private fun areAllPermissionsGranted(activity: AppCompatActivity) =
        requiredPermissions.all { it.isGranted(activity) }

    private fun shouldShowPermissionRationale(activity: AppCompatActivity) =
        requiredPermissions.any { it.requiresRationale(activity) }

    private fun getPermissionList() =
        requiredPermissions.flatMap { it.permissions.toList() }.toTypedArray()

    private fun Permissions.isGranted(activity: AppCompatActivity) =
        permissions.all { hasPermission(activity, it) }

    private fun Permissions.requiresRationale(activity: AppCompatActivity) =
        permissions.any { activity.shouldShowRequestPermissionRationale(it) }

    private fun hasPermission(activity: AppCompatActivity, permission: String) =
        ContextCompat.checkSelfPermission(
            activity.applicationContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
}