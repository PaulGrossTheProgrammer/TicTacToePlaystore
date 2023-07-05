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
                Log.d(TAG, "Waiting for client data ...")
                val data = d.readLine()  // Blocking read - wait here for new client data
                Log.d(TAG, "Got data = $data")
                dataOutputStream.writeUTF("Hello Client. You said \"$data\"")

                if (data == "bye") {
                    running = false
                }
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
        dataInputStream.close()
        dataOutputStream.close()
    }

    companion object {
        private val TAG = SocketClientHandler::class.java.simpleName
    }
}