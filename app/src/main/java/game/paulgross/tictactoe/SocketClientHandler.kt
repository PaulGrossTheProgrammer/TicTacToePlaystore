package game.paulgross.tictactoe

import android.util.Log
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader


class SocketClientHandler(private val dataInputStream: DataInputStream, private val dataOutputStream: DataOutputStream) : Thread() {
    override fun run() {
        val d = BufferedReader(InputStreamReader(dataInputStream))
        Log.d(TAG, "New client connection handler started ...")
        var running = true
        while (running) {
            try {


                Log.d(TAG, "Ready to receive data ...")
                val available = dataInputStream.available()
                Log.d(TAG, "Available data count = $available")

                if(available > 0){
                    Log.d(TAG, "Received data ...")
                    val data = d.readLine()
                    Log.d(TAG, "Data = $data")
//                    var bytesData = dataInputStream.readBytes()

                    // val receivedData = dataInputStream.readUTF()

                    // Log.i(TAG, "Received: $receivedData")
                    dataOutputStream.writeUTF("Hello Client")

                    running = false
                }
                sleep(2000L) // WHY???
            } catch (e: IOException) {
                running = false
                e.printStackTrace()
                try {
                    dataInputStream.close()
                    dataOutputStream.close()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            } catch (e: InterruptedException) {
                running = false
                e.printStackTrace()
                try {
                    dataInputStream.close()
                    dataOutputStream.close()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            }
        }
    }

    companion object {
        private val TAG = SocketClientHandler::class.java.simpleName
    }
}