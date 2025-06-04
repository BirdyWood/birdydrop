package io.github.birdywood.birdydrop.utils

import io.github.birdywood.birdydrop.core.DeviceBluetooth
import org.json.JSONObject

interface BirdydropUpdateListener {
    fun onDataUpdated(data: String?) {

    }

    fun onReceiveInfo(msg: String) {

    }
    fun onReceiveInfo(json: JSONObject) {

    }

    fun onNewDevice(devices: List<DeviceBluetooth>) {

    }

    fun sendMessage(msg: String) {

    }
}