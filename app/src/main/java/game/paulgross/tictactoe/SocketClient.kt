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
//    private val working = AtomicBoolean(true)

    lateinit var output: PrintWriter
//    lateinit var input: BufferedReader

//    private val clientQ: BlockingQueue<String> = LinkedBlockingQueue()

    private val fromGameServerQ: BlockingQueue<String> = LinkedBlockingQueue()

    private val listeningToGameServer = AtomicBoolean(true)
    private var listeningToSocket = AtomicBoolean(true)

    override fun run() {
        Log.i(TAG, "Client connected...")
        output = PrintWriter(clientSocket.getOutputStream());
        // input = BufferedReader(InputStreamReader(clientSocket.getInputStream()));

        SocketReaderThread(clientSocket, fromGameServerQ, listeningToSocket).start()

        while (listeningToGameServer.get()) {
            var gameMessage = fromGameServerQ.take()  // Blocked until we get data.

            output.println(gameMessage)
            output.flush()

            if (gameMessage == "shutdown") {
                shutdown()
            }

            // FIXME - THIS IS LIKELY BROKEN PAST HERE...
            // FIXME - rethink the treatment of null here...
/*
            if (gameMessage != null && gameMessage == "shutdown") {
                working.set(false)
                output.println("shutdown")  // This signals the remote server that we are disconnecting
                output.flush()
            } else {
                if (gameMessage == null) {
                    // TODO - don't overdo these status requests. Flag if we are waiting from the prev status.
                    // Maybe don't make status requests if there is still no response from the last request.
                    // To avoid making the remote server too busy.
                    // But after a while give up waiting, and ask for a new status request anyway...?
                    gameMessage = "status:"
                }

                output.println(gameMessage)
                output.flush()
*/

                // TODO - design for long responses that are split across multiple lines by the server.
                // TODO - determine if this still works on a different thread...
//                var response = input.readLine()  // null if socket unexpectedly closes.
//
//                // FIXME: If the server suddenly stops we get a null pointer exception here
//                if (response != null) {
//                    GameServer.queueClientRequest(response, clientQ)
//                } else {
//                    Log.e(TAG, "Server socket unexpectedly closed.")
//                    working.set(false)
//                }
//                sleep(5000L)  // Pause for a short time...
        }

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

    fun shutdown() {
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