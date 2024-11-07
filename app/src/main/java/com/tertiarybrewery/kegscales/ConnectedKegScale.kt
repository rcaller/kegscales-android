package com.tertiarybrewery.kegscales

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.util.Log
import java.lang.Boolean.FALSE
import java.util.LinkedList
import java.util.Queue

class ConnectedKegScale(val id:String) {
    var connected: Boolean = FALSE
    var device: BluetoothDevice? = null
    var gatt: BluetoothGatt? = null
    var services: List<BluetoothGattService> = emptyList()
    var volume: Int = 20000
    var name: String = "KegScale"
    private val commandQueue: Queue<Runnable> = LinkedList<Runnable>()
    private var commandQueueBusy: Boolean = false
    fun addCommand(r: Runnable) {
        commandQueue.add(r)
        nextCommand()
    }
    fun nextCommand() {
        if(commandQueueBusy) {
            return
        }
        if (gatt == null) {
            Log.e("ConntectedKegScale", "Disconnected, clearing queue")
            commandQueue.clear()
            commandQueueBusy = false
            return
        }
        if (commandQueue.size >0) {

            val command: Runnable? = commandQueue.poll()
            Log.v("ConnectedKegScale", "Running command: "+command.toString())
            commandQueueBusy = true
            command?.run()
        }

    }

    fun completedCommand() {
        commandQueueBusy = false
        nextCommand()
    }
}