package game.paulgross.tictactoe

import android.util.Log
import java.io.BufferedReader
import java.io.DataInputStream
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

    override fun run() {
        Log.i(TAG, "Client connected...")
        output = PrintWriter(clientSocket.getOutputStream());
        input = BufferedReader(InputStreamReader(clientSocket.getInputStream()));

        while (working.get()) {
            Log.i(TAG, "About to send status request ...")
            output.println("status:")
            output.flush()
            Log.i(TAG, "Sent status request...")
            // Maybe swallow status requests if there is still no response from the last request.
            // To avoid making the remote server to busy.
            // But after a while give up waiting, and ask for a new status request???
            // This way requests are responses are not matched, but that shouldn't matter
            // if each response contains an update from the server.
            // Perhaps the game server shouldn't specifically request status, but leave it to this Thread???

            Log.i(TAG, "About to get server response ...")
            var response = input.readLine()
            Log.i(TAG, "Server response [$response]")
            // TODO - design for long responses that are split across multiple lines by the server.
            // This is to avoid overrunning the TCPIP buffer.
            // In this case, loop multiple readLine() calls here to assemble the entire response.

//            clientQ.add(GameServer.ClientRequest(response, clientQ).toString())
//            GameServer.queueClientRequest(GameServer.ClientRequest(response, clientQ))
            // FIXME: If the server stops we get a null pointer exception here
            gameRequestQ.add(GameServer.ClientRequest(response, clientQ))
            sleep(1000L)  // Pause for a short time...
        }
    }

    fun shutdown() {
        working.set(false)
    }

    companion object {
        private val TAG = SocketClient::class.java.simpleName
    }
}