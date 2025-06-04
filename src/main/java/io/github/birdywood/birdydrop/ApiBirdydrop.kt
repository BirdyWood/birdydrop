package io.github.birdywood.birdydrop

import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import io.github.birdywood.birdydrop.core.BluetoothService
import io.github.birdywood.birdydrop.utils.BirdydropUpdateListener


/**
 * Manages the connection and communication with the BDrop_Service.
 *
 * This class handles starting, stopping, and binding to the BDrop_Service,
 * as well as sending messages and managing lifecycle events.
 *
 * @property ctx The application context.
 * @property listener The primary DataUpdateListener to receive updates from the service.
 */
class ApiBirdydrop(val ctx: Context, listener: BirdydropUpdateListener) {
    var bDropServiceInstance: BDrop_Service? = null
    var isServiceBound = false
    var isSheetBound = false
    var isServiceLaunched = true
    var ble: BluetoothService? = null

    var connectedFn = {}
    fun onStart(connected: () -> Unit) {
        connectedFn = connected
        val serviceIntent = Intent(
            ctx,
            BDrop_Service::class.java
        )
        ctx.startService(serviceIntent)
        ctx.bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
    }

    fun onStop() {
        if (isServiceBound) {
            ctx.unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    /* Function API */
    fun sendMsg(msg: String) {
        Log.d("BDrop_Service", "Sending message: $msg")
        bDropServiceInstance?.birdydropUpdateListener?.onEach {
            Log.d(
                "BDrop_Service",
                "Sending message: $msg"
            ); it.sendMessage(msg)
        }
    }

    fun refresh() {
        ble?.sendPing()
    }

    fun onResume() {
        Log.d("BoundService", "Serice resume: $isServiceLaunched")
        if (!isServiceLaunched) {
            isServiceLaunched = true
            ble?.preProcess()
            ble?.sendPing()
        }
    }

    fun onPause() {
        Log.d("BoundService", "Serice pause: $isServiceLaunched")
        if (isServiceLaunched) {
            ble?.stopService()
            isServiceLaunched = false
        }
    }


    fun boundSheet(listener: BirdydropUpdateListener) {
        if (isSheetBound) {
            return
        }
        isSheetBound = true
        bDropServiceInstance?.addBirdydropUpdateListener(listener)
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            val binder: BDrop_Service.LocalBinder = iBinder as BDrop_Service.LocalBinder
            bDropServiceInstance = binder.service
            bDropServiceInstance!!.addBirdydropUpdateListener(listener)
            isServiceBound = true
            ble = bDropServiceInstance!!.ble
            connectedFn()
            Log.d("BDrop_Service", "Service connected")
            listener.onNewDevice(listOf())
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bDropServiceInstance = null
            isServiceBound = false
            ble = null
            listener.onNewDevice(listOf())
        }
    }
}