package game.paulgross.tictactoe

import android.util.Log
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class SocketClient(private val server: String, private val port: Int): Thread() {
    private var clientSocket: Socket = Socket(server, port)

    lateinit var output: PrintWriter

    private val fromGameServerQ: BlockingQueue<String> = LinkedBlockingQueue()

    private val listeningToGameServer = AtomicBoolean(true)
    private var listeningToSocket = AtomicBoolean(true)

    override fun run() {
        Log.i(TAG, "Client connected...")
        output = PrintWriter(clientSocket.getOutputStream());

        SocketReaderThread(clientSocket, fromGameServerQ, listeningToSocket).start()

        while (listeningToGameServer.get()) {
            var gameMessage = fromGameServerQ.take()  // Blocked until we get data.

            if (gameMessage == "abandoned") {
                Log.d(TAG, "Remote socket abandoned. Shutting down.")
                shutdown()
            } else {
                if (gameMessage == "shutdown") {
                    shutdown()
                }
                output.println(gameMessage)
                output.flush()
            }
        }

        // FIXME - I should catch socket exception here in case the output write fails...

        try {
            if (clientSocket.isConnected) {
                clientSocket.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        Log.i(TAG, "SocketClient has shut down.")
    }

    fun messageFromGameServer(message: String) {
        fromGameServerQ.add(message)
    }

    private fun shutdown() {
        listeningToSocket.set(false)
        listeningToGameServer.set(false)
        clientSocket.close()
    }

    fun shutdownRequest() {
        fromGameServerQ?.add("shutdown")
    }

    private class SocketReaderThread(private val socket: Socket, private val sendToThisHandlerQ: BlockingQueue<String>,
                                     private var listeningToSocket: AtomicBoolean): Thread() {

        val input = BufferedReader(InputStreamReader(DataInputStream(socket.getInputStream())))

        override fun run() {
            try {
                while (listeningToSocket.get()) {
                    // TODO - design for long responses that are split across multiple lines by the server.
                    val data = input.readLine()  // Blocked until we get a line of data.
                    if (data == null) {
                        Log.d(TAG, "ERROR: Remote data from Socket was unexpected NULL - abandoning socket Listener.")
                        listeningToSocket.set(false)
                        GameServer.queueClientRequest("abandoned", sendToThisHandlerQ)
                    }

                    if (data != null) {
                        GameServer.queueClientRequest(data, sendToThisHandlerQ)
                    }
                }
            } catch (e: SocketException) {
                if (listeningToSocket.get()) {
                    listeningToSocket.set(false)  // Unexpected Exception while listening
                    e.printStackTrace()
                }
            }
            // TODO - do I need IOException too???
            input.close()
            Log.d(TAG, "The Listener has shut down.")
        }
    }

    companion object {
        private val TAG = SocketClient::class.java.simpleName
    }
}