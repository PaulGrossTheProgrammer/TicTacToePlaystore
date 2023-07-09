package game.paulgross.tictactoe

import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class SocketServer(private val gameRequestQ: BlockingQueue<GameService.ClientRequest>): Thread() {

    private var serverSocket: ServerSocket? = null
    private val working = AtomicBoolean(true)

    override fun run() {
        var socket: Socket? = null
        try {
            serverSocket = ServerSocket(PORT)

            Log.d(TAG, "Created ServerSocket for ${serverSocket!!.localSocketAddress}")

            while (working.get()) {
                if (serverSocket != null) {
                    Log.i(TAG, "Waiting for new client sockets...")
                    socket = serverSocket!!.accept()
                    Log.i(TAG, "New client: $socket")
                    val dataInputStream = DataInputStream(socket.getInputStream())
                    val dataOutputStream = DataOutputStream(socket.getOutputStream())

                    // Use threads for each client to communicate with them simultaneously
                    // TODO - create a return queue for the client handler to get the response.
                    // One queue per client thread
                    val t: Thread = SocketClientHandler(dataInputStream, dataOutputStream, gameRequestQ)
                    t.start()
                } else {
                    Log.e(TAG, "Couldn't create ServerSocket!")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            try {
                socket?.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }

    fun stopServer() {
        // Call this from the game server if required
        working.set(false)
    }

    companion object {
        private val TAG = SocketServer::class.java.simpleName
        private const val PORT = 6868
    }
}