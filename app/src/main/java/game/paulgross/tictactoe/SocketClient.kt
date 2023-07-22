package game.paulgross.tictactoe

import android.util.Log
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class SocketClient(private val server: String, private val port: Int, private val gameRequestQ: BlockingQueue<GameServer.ClientRequest>): Thread() {
    private var clientSocket: Socket = Socket(server, port)
    private val working = AtomicBoolean(true)

    lateinit var output: PrintWriter
    lateinit var input: BufferedReader

    private val clientQ: BlockingQueue<String> = LinkedBlockingQueue()

    private val fromGameServerQ: BlockingQueue<String> = LinkedBlockingQueue()

    override fun run() {
        Log.i(TAG, "Client connected...")
        output = PrintWriter(clientSocket.getOutputStream());
        input = BufferedReader(InputStreamReader(clientSocket.getInputStream()));

        while (working.get()) {
            // TODO - read the queue from the GameServer
            var gameMessage = fromGameServerQ.poll()

            if (gameMessage != null && gameMessage == "shutdown") {
                working.set(false)
                output.println("exit:")  // This signals the remote server that we are disconnecting
                output.flush()
            } else {
                if (gameMessage == null) {
                    // TODO - don't overdo these status requests. Flag if we are waiting from the prev status.
                    // Maybe don't make status requests if there is still no response from the last request.
                    // To avoid making the remote server too busy.
                    // But after a while give up waiting, and ask for a new status request anyway...?
                    gameMessage = "status:"
                    Log.i(TAG, "About to send status request ...")
                }

                output.println(gameMessage)
                output.flush()

                // TODO - determine if this still works on a different thread...
                Log.i(TAG, "About to get server response ...")
                var response = input.readLine()
                Log.i(TAG, "Server response [$response]")
                // TODO - design for long responses that are split across multiple lines by the server.
                // This is to avoid overrunning the TCPIP buffer.
                // In this case, loop multiple readLine() calls here to assemble the entire response.

//            GameServer.queueClientRequest(GameServer.ClientRequest(response, clientQ))
                // FIXME: If the server suddenly stops we get a null pointer exception here
                gameRequestQ.add(GameServer.ClientRequest(response, clientQ))
                sleep(200L)  // Pause for a short time...
            }
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
        fromGameServerQ.add("shutdown")
    }

    companion object {
        private val TAG = SocketClient::class.java.simpleName
    }
}