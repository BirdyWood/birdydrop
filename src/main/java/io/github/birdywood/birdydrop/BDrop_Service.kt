package io.github.birdywood.birdydrop

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import io.github.birdywood.birdydrop.core.BluetoothService
import io.github.birdywood.birdydrop.core.GenericService
import io.github.birdywood.birdydrop.utils.BirdydropUpdateListener
import org.json.JSONObject


class BDrop_Service : Service() {
    var birdydropUpdateListener: MutableList<BirdydropUpdateListener> = mutableListOf()

    // Instance of the local binder
    private val binder: IBinder = LocalBinder(this)
    var ble: BluetoothService? = null


    fun addBirdydropUpdateListener(listener: BirdydropUpdateListener) {
        this.birdydropUpdateListener.add(listener)
    }

    private fun sendDataToActivity(data: String) {
        birdydropUpdateListener.onEach { it.onDataUpdated(data) }
    }

    fun MutableList<BirdydropUpdateListener>.onEach(each: (listener: BirdydropUpdateListener) -> Unit) {
        for (listener in this) {
            each(listener)
        }
    }

    override fun onCreate() {
        super.onCreate()
        log("BackgroundTaskService is ready to conquer!")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("BackgroundTaskService is performing a task! Don't disturb, please!")
        ble = BluetoothService(this.applicationContext, object : GenericService.Listener {
            override fun onNewDevice() {
                update()
            }

            override fun onNewMessage(msg: String) {
                log("onNewMessage: $msg")
                birdydropUpdateListener.onEach { it.onReceiveInfo(msg) }
            }
            override fun onNewMessage(json: JSONObject) {
                log("onNewMessage: $json")
                birdydropUpdateListener.onEach { it.onReceiveInfo(json) }
            }
        })
        ble!!.preProcess()
        ble!!.sendPing()
        return START_STICKY // If the service is killed, it will be automatically restarted
    }

    private fun update() {
        log("New device")
        ble?.listUsers?.let { users ->
            birdydropUpdateListener.onEach { it.onNewDevice(users) }
        }
    }

    private fun performLongTask() {
        // Imagine doing something that takes a long time here
        Thread.sleep(5000)

    }

    override fun onDestroy() {
        super.onDestroy()
        ble?.stopService()
        log("BackgroundTaskService says goodbye!")
    }

    class LocalBinder(val service: BDrop_Service) : Binder()

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun log(str: String) {
        Log.d("BDrop_Service", str)
    }
}
/*
val builder = AlertDialog.Builder(
    ContextThemeWrapper(
        this,
        R.style.Theme_DeviceDefault
    )
)
builder.setTitle("Androidly Alert")
builder.setMessage("We have a message")
builder.setCancelable(false)
//builder.setPositiveButton("OK", DialogInterface.OnClickListener(function = x))

builder.setPositiveButton(android.R.string.yes) { dialog, which ->
    Toast.makeText(
        applicationContext,
        android.R.string.yes, Toast.LENGTH_SHORT
    ).show()
}

builder.setNegativeButton(android.R.string.no) { dialog, which ->
    Toast.makeText(
        applicationContext,
        android.R.string.no, Toast.LENGTH_SHORT
    ).show()
}

builder.show()
 */