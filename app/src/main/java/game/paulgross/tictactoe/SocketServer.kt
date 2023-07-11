package game.paulgross.tictactoe

import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class SocketServer(private val gameRequestQ: BlockingQueue<GameServer.ClientRequest>): Thread() {

    private var serverSocket: ServerSocket? = null
    private val working = AtomicBoolean(true)
    private val clientHandlers: MutableList<SocketClientHandler> = arrayListOf();

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
                    val t = SocketClientHandler(socket, dataInputStream, dataOutputStream, gameRequestQ)
                    t.start()
                    clientHandlers.add(t)
                } else {
                    Log.e(TAG, "Couldn't create ServerSocket!")
                }
            }
        } catch (e: IOException) {
            // Maybe hide this message??? It happens always on shutdown?
            // FIXME: Can I interrupt the accept() wait and handle this better???
            e.printStackTrace()
            try {
                socket?.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
        Log.d(TAG, "The Socket Server has shut down.")
    }

    fun shutdown() {
        // FIXME: This doesn't work properly ... visible Exception ...
        Log.d(TAG, "The Socket Server is shutting down ...")
        working.set(false)

        serverSocket?.close()

        clientHandlers.forEach {handler ->
            handler.shutdown()
        }
    }

    companion object {
        private val TAG = SocketServer::class.java.simpleName
        private const val PORT = 6868
    }
}