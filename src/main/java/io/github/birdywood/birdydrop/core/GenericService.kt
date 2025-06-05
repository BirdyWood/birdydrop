package io.github.birdywood.birdydrop.core

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

fun log(msg:String) {
    Log.i("GenericService","${Thread.currentThread().stackTrace[3].className}:${Thread.currentThread().stackTrace[3].methodName.split("$")[0]} - $msg")
}


fun ByteArray.getIntAt(i: Int): Int {
    return (this[i].toInt() and 0xFF) or
            ((this[i + 1].toInt() and 0xFF) shl 8) or
            ((this[i + 2].toInt() and 0xFF) shl 16) or
            ((this[i + 3].toInt() and 0xFF) shl 24)
}
private fun Int.toByteArray(): ByteArray {
    return byteArrayOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 24) and 0xFF).toByte()
    )
}
fun ByteArray.getLongAt(i: Int): Long {
    return (this[i].toLong() and 0xFF) or
            ((this[i + 1].toLong() and 0xFF) shl 8) or
            ((this[i + 2].toLong() and 0xFF) shl 16) or
            ((this[i + 3].toLong() and 0xFF) shl 24) or
            ((this[i + 4].toLong() and 0xFF) shl 32) or
            ((this[i + 5].toLong() and 0xFF) shl 40) or
            ((this[i + 6].toLong() and 0xFF) shl 48) or
            ((this[i + 7].toLong() and 0xFF) shl 56)
}
private fun Long.toByteArray(): ByteArray {
    return byteArrayOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 24) and 0xFF).toByte(),
        ((this shr 32) and 0xFF).toByte(),
        ((this shr 40) and 0xFF).toByte(),
        ((this shr 48) and 0xFF).toByte(),
        ((this shr 56) and 0xFF).toByte()
    )
}

/**
 * A generic service class for handling data transmission with listeners.
 *
 * This class provides a framework for sending and receiving data in chunks,
 * handling message assembly, and notifying listeners about various events.
 * It supports data compression using zLib.
 *
 * @property context The application context.
 * @property lister The listener interface for handling service events.
 */
open class GenericService(ctx: Activity, val lister: Listener) {
    val context = ctx
    open val limitSize = 128
    private val openMessage:MutableMap<Long, DataMessage> = mutableMapOf()
    var nameDevice: String? = null

    class DataMessage(
        private var data: ByteArray,
        limit: Int,
        val id: Long = System.currentTimeMillis(),
        zipDataOnInit: Boolean = true
    ) {
        enum class DataMessageType(val n: Byte) {
            Packet    (0x00.toByte()),
            NextPacket(0x01.toByte()),
            Error     (0xFF.toByte());

            fun toByteArray(): ByteArray {
                return(ByteArray(1){this.n})
            }

            companion object {
                fun from(s: Byte) = entries.find { it.n == s }
            }
        }
        init{
            if (zipDataOnInit) {
                data = data.zLibCompress()
            }
        }
        private val realLimitSize: Int = limit - 4 - 4 - 4 - 8 - 1
        private val maxN = (data.size / realLimitSize)
        private val part:Array<Boolean> = Array(maxN+1) { false }

        /**
         *
         * Message format :
         *  '- - - 0 - - - - -' '- - - 1 .. 8 - - - - -' '- - - 9 .. 12 - - - - -' '- - - 13 .. 16 - - - - -'  '- - - 17 .. 20 - - - - -' '- - - 21 .. - - - - - - - - - - - - -'
         *  |     Type(Char)  | |    ID (Long)         | | Packet position (Int) | |   Max Data size(Int)   |  | Data Packet size (Int) | | Data  (ByteArray - zLib)            |
         *
         */
        fun getData(): ByteArray {
            return data.zLibDecompress()
        }
        fun getPart(pos: Int): ByteArray {
            var max = pos*realLimitSize+realLimitSize
            if (max>data.size)
                max = data.size
            val min =pos*realLimitSize
            return if (pos<=maxN) {
                part[pos] = true
                DataMessageType.Packet.toByteArray() + id.toByteArray() + pos.toByteArray() + realLimitSize.toByteArray() + data.size.toByteArray() + data.sliceArray(min..<max)
            } else
                DataMessageType.Error.toByteArray() + id.toByteArray() + pos.toByteArray() + realLimitSize.toByteArray() + data.size.toByteArray() + "ERROR".toByteArray()
        }
        fun getNext(): ByteArray? {
            for (n in part.indices)
                if (!part[n])
                    return DataMessageType.NextPacket.toByteArray() + id.toByteArray() + n.toByteArray() + realLimitSize.toByteArray() + data.size.toByteArray()
            return null
        }
        fun isFinished(): Boolean {
            for (n in part)
                if (!n)
                    return false
            return true
        }
        fun getStat(): Float {
            var rtn = 0
            for (n in part)
                if (n)
                    rtn++
            return rtn/maxN.toFloat()
        }

        private fun ByteArray.zLibCompress(): ByteArray {
            val input = this
            val output = ByteArray(input.size * 4)
            val compressor = Deflater().apply {
                setInput(input)
                finish()
            }
            val compressedDataLength: Int = compressor.deflate(output)
            return output.copyOfRange(0, compressedDataLength)
        }

        private fun ByteArray.zLibDecompress(): ByteArray {
            val inflater = Inflater()
            val outputStream = ByteArrayOutputStream()

            return outputStream.use {
                val buffer = ByteArray(1024)

                inflater.setInput(this)

                var count = -1
                while (count != 0) {
                    count = inflater.inflate(buffer)
                    outputStream.write(buffer, 0, count)
                }

                inflater.end()
                outputStream.toByteArray()
            }
        }
        fun addPart(msg: ByteArray): DataMessage {
            val type = getType(msg)
            if (getID(msg) ==id && type == DataMessageType.Packet) {
                val pos = msg.getIntAt(9)
                val realLimitSize = msg.getIntAt(13)
                //val size = msg.getIntAt(17)
                //val dataTmp = ByteArray(size)
                val dataPart = msg.sliceArray(21..<msg.size)
                for (n in dataPart.indices)
                    data[pos*realLimitSize+n] = dataPart[n]
                part[pos] = true
            }
            return this
        }

        fun getNextPart(msg: ByteArray): ByteArray? {
            val type = getType(msg)
            if (type == DataMessageType.NextPacket) {
                //val id = getID(msg)
                val pos = msg.getIntAt(9)
                log("ID: $id Part $pos")
                //val realLimitSize = msg.getIntAt(13)
                //val size = msg.getIntAt(17)
                return getPart(pos)
            }
            else return null
        }

        companion object {
            fun getType(msg: ByteArray): DataMessageType? {
                return DataMessageType.from(msg[0])
            }
            fun getID(msg: ByteArray): Long {
                return msg.getLongAt(1)
            }
            fun fromPart(msg: ByteArray, limitSize: Int): DataMessage? {
                val type = getType(msg)
                if (type == DataMessageType.Packet) {
                    val id = getID(msg)
                    val pos = msg.getIntAt(9)
                    log("ID: $id Part $pos")
                    val realLimitSize = msg.getIntAt(13)
                    val size = msg.getIntAt(17)
                    val data = ByteArray(size)
                    val dataPart = msg.sliceArray(21..<msg.size)
                    for (n in dataPart.indices)
                        data[pos*realLimitSize+n] = dataPart[n]
                    val rtn = DataMessage(data, limitSize,id,zipDataOnInit = false)
                    rtn.part[pos] = true
                    return rtn
                }
                else return null
            }
        }
    }
    /**
     * Interface definition for a callback to be invoked when various events related to messaging and device status occur.
     * This interface provides methods to handle new messages (as String, ByteArray, or JSONObject),
     * new device connections, progress updates for sending and receiving messages, and error notifications.
     */
    interface Listener {
        fun onNewMessage(msg: String) {

        }
        fun onNewMessage(msg: ByteArray) {

        }
        fun onNewMessage(msg: JSONObject) {

        }
        fun onNewDevice() {

        }
        fun onSending(idMessage:Long,progress:Float) {

        }
        fun onReceiving(idMessage:Long,progress:Float) {

        }
        fun onError(msg: String) {

        }

    }
    fun onReceived(fromUser: String, msg:ByteArray) {
        log("RECEIVED FROM $fromUser msg: ${msg.decodeToString()}")
        val msgType = DataMessage.getType(msg)
        val msgId = DataMessage.getID(msg)
        if (msgType== DataMessage.DataMessageType.Packet) {
            val data: DataMessage? =
                if (openMessage.containsKey(msgId))
                    openMessage[msgId]?.addPart(msg)
                else
                    DataMessage.fromPart(msg, limitSize)
            if (data != null) {
                openMessage[msgId]=data
                if (data.isFinished()) {
                    lister.onNewMessage(data.getData().decodeToString())
                    lister.onNewMessage(data.getData())
                    lister.onNewMessage(decodeJSON(data.getData()))
                    openMessage.remove(data.id)
                } else {
                    data.getNext()?.let { sendData(fromUser, it) }
                }
                lister.onReceiving(data.id,data.getStat())
            }
        } else if (msgType== DataMessage.DataMessageType.NextPacket) {
            lister.onNewMessage("Next message requested from $fromUser")
            if (openMessage.containsKey(msgId))
                openMessage[msgId]?.getNextPart(msg)?.let { sendData(fromUser, it) }
            openMessage[msgId]?.let { lister.onSending(it.id,it.getStat()) }
        }
    }
    open fun sendPing() {}
    open fun sendData(usr: String, msg: ByteArray) {}
    fun send(usr: String, msg: ByteArray) {
        val data = DataMessage(encodeJSON(msg), limitSize)
        openMessage[data.id] = data
        sendData(usr, data.getPart(0))
        lister.onSending(data.id,data.getStat())
        if (data.isFinished())
            openMessage.remove(data.id)

        //sendData(usr,msg)
    }

    fun encodeJSON(msg: ByteArray): ByteArray {
        val json = JSONObject()
        json.put("msg", msg.decodeToString())
        json.put("name", nameDevice)
        return json.toString().toByteArray()
    }
    fun decodeJSON(msg: ByteArray): JSONObject {
        val textJSON = msg.decodeToString()
        val decodeMsg: DecodeMessage = Gson().fromJson(textJSON, DecodeMessage::class.java)
        return JSONObject().put("msg", decodeMsg.msg).put("name", decodeMsg.name)
    }

    data class DecodeMessage (val name:String, val msg:String)

}


