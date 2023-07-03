package game.paulgross.tictactoe

import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class SocketClientHandler(private val dataInputStream: DataInputStream, private val dataOutputStream: DataOutputStream) : Thread() {
    override fun run() {
        while (true) {
            try {
                if(dataInputStream.available() > 0){
                    // FIXME: This gives an exception when the Python socket client runs.
                    Log.i(TAG, "Received: " + dataInputStream.readUTF())
                    // dataOutputStream.writeUTF("Hello Client")
                    sleep(2000L) // WHY???
                }
            } catch (e: IOException) {
                e.printStackTrace()
                try {
                    dataInputStream.close()
                    dataOutputStream.close()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            } catch (e: InterruptedException) {
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