package game.paulgross.tictactoe

import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean


class SocketClientHandler(private val socket: Socket, private val socketServer: SocketServer): Thread() {

    private val sendToThisHandlerQ: BlockingQueue<String> = LinkedBlockingQueue()

    private val listeningToGameServer = AtomicBoolean(true)
    private var listeningToSocket = AtomicBoolean(true)

    override fun run() {
        val output = BufferedWriter(OutputStreamWriter(DataOutputStream(socket.getOutputStream())))

        SocketReaderThread(socket, sendToThisHandlerQ, listeningToSocket).start()

        Log.d(TAG, "New client connection handler started ...")
        while (listeningToGameServer.get()) {
            // Wait here for any messages from the GameServer.
            val message = sendToThisHandlerQ.take()  // Blocked until we get data.

            // Pass on to the remote SocketClient
            output.write(message)
            output.write("\n")  // TODO: Maybe use a PrintWriter??
            output.flush()

            // Special case: GameServer wants to shutdown this Handler.
            if (message == "shutdown") {
                shutdown()
            }
        }
        output.close()

        Log.d(TAG, "The Client Socket Writer has shut down.")
    }

    private fun shutdown() {
        listeningToSocket.set(false)
        listeningToGameServer.set(false)
        socket.close()

        // Maybe do this with a static call?
        socketServer.removeClientHandler(this)  // Can't the SocketServer do this by itself???
    }

    /**
    // This function is only called by the SocketServer Thread.
    */
    fun shutdownRequest() {
        sendToThisHandlerQ?.add("shutdown")
    }

    private class SocketReaderThread(private val socket: Socket, private val sendToThisHandlerQ: BlockingQueue<String>,
                                     private var listeningToSocket: AtomicBoolean): Thread() {

        val input = BufferedReader(InputStreamReader(DataInputStream(socket.getInputStream())))

        override fun run() {
            try {
                while (listeningToSocket.get()) {
                    Log.d(TAG, "Waiting for client data ...")
                    val data = input.readLine()  // Blocked until we get a line of data.
                    // TODO - determine if data == null when the remote socket closes...
                    if (data != null) {
                        Log.d(TAG, "Got remote data = [$data]")
                        GameServer.queueClientHandlerRequest(data, sendToThisHandlerQ)
//                        sendToGameServerQ.add(GameServer.ClientRequest(data, sendToThisHandlerQ))
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
            Log.d(TAG, "The Client Socket Listener has shut down.")
        }
    }

    companion object {
        private val TAG = SocketClientHandler::class.java.simpleName
    }
}