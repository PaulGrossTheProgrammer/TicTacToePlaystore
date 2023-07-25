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
    private val clientSocket: Socket = Socket(server, port)

    private val fromGameServerQ: BlockingQueue<String> = LinkedBlockingQueue()

    private val listeningToGameServer = AtomicBoolean(true)
    private val listeningToSocket = AtomicBoolean(true)

    override fun run() {
        Log.i(TAG, "Client connected...")
        val output = PrintWriter(clientSocket.getOutputStream());

        output.println("Initialise")
        output.flush()

        SocketReaderThread(clientSocket, fromGameServerQ, listeningToSocket).start()

        try {
            while (listeningToGameServer.get()) {
                val gameMessage = fromGameServerQ.take()  // Blocked until we get data.

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
        } catch (e: SocketException) {
            if (listeningToGameServer.get()) {
                Log.d(TAG, "ERROR: Writing to Remote Socket caused unexpected error - abandoning socket.")
                e.printStackTrace()
            }
        } catch (e: IOException) {
            if (listeningToGameServer.get()) {
                Log.d(TAG, "ERROR: Writing to Remote Socket caused unexpected error - abandoning socket.")
                e.printStackTrace()
            }
        }

        output.close()
        shutdown()
        Log.i(TAG, "The Writer has shut down.")
    }

    fun getServer(): String {
        return server
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
        fromGameServerQ.add("shutdown")
    }

    private class SocketReaderThread(private val socket: Socket, private val sendToThisHandlerQ: BlockingQueue<String>,
                                     private var listeningToSocket: AtomicBoolean): Thread() {

        override fun run() {
            val input = BufferedReader(InputStreamReader(DataInputStream(socket.getInputStream())))
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
            } catch (e: IOException) {
                if (listeningToSocket.get()) {
                    listeningToSocket.set(false)  // Unexpected Exception while listening
                    e.printStackTrace()
                }
            }

            input.close()
            Log.d(TAG, "The Listener has shut down.")
        }
    }

    companion object {
        private val TAG = SocketClient::class.java.simpleName
    }
}