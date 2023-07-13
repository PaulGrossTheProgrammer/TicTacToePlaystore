package game.paulgross.tictactoe

import android.util.Log
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class SocketServer(private val gameRequestQ: BlockingQueue<GameServer.ClientRequest>): Thread() {

    private lateinit var serverSocket: ServerSocket
    private val working = AtomicBoolean(true)
    private val clientHandlers: MutableList<SocketClientHandler> = arrayListOf()

    override fun run() {
        var socket: Socket? = null
        try {
            serverSocket = ServerSocket(PORT)
            Log.d(TAG, "Created ServerSocket for ${serverSocket.localSocketAddress}")

            while (working.get()) {
                Log.i(TAG, "Waiting for new client sockets...")
                socket = serverSocket.accept()
                Log.i(TAG, "New client: $socket")

                // Create a new Thread for each client.
                val t = SocketClientHandler(socket, gameRequestQ, this)
                t.start()

                // This list allows us to remove each Handler if the client disconnects
                // by calling removeClientHandler().
                clientHandlers.add(t)
            }
        } catch (e: IOException) {
            if (!working.get()){
                Log.d(TAG, "The Socket Server has been forced to stop listening for connections.")
            } else {
                e.printStackTrace()
            }
            try {
                socket?.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
        Log.d(TAG, "The Socket Server has shut down.")
    }

    fun shutdown() {
        Log.d(TAG, "The Socket Server is shutting down ...")
        working.set(false)
        serverSocket.close()

        Log.d(TAG, "The Socket Server has ${clientHandlers.size} open Client Handlers.")
        clientHandlers.forEach {handler ->
            handler.shutdown()
        }
    }

    fun removeClientHandler(socketClientHandler: SocketClientHandler) {
        clientHandlers.remove(socketClientHandler)
    }

    companion object {
        private val TAG = SocketServer::class.java.simpleName
        private const val PORT = 6868
    }
}