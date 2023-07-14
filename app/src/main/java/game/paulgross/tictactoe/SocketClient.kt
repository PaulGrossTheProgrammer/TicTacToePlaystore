package game.paulgross.tictactoe

import android.util.Log
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class SocketClient(private val server: String, private val port: Int): Thread() {
    private var clientSocket: Socket = Socket(server, port)
    private val working = AtomicBoolean(true)

    lateinit var output: PrintWriter
//    lateinit var input: DataInputStream
    lateinit var input: BufferedReader

    override fun run() {
        Log.i(TAG, "Client connected...")
        output = PrintWriter(clientSocket.getOutputStream());
        input = BufferedReader(InputStreamReader(clientSocket.getInputStream()));

        var requestNumber = 0
        while (working.get()) {
            requestNumber ++
            Log.i(TAG, "About to send status request ...")
            output.println("status:")
            output.flush()
            Log.i(TAG, "Sent status request...")

            Log.i(TAG, "About to get server response ...")
            var response = input.readLine()
            Log.i(TAG, "Server response [$response]")

            sleep(5000L)  // Pause for a short time...
        }
    }

    companion object {
        private val TAG = SocketClient::class.java.simpleName
    }
}