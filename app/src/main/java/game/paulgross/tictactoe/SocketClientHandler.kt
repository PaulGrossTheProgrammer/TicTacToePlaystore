package game.paulgross.tictactoe

import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean


class SocketClientHandler(private val socket: Socket, private val gameRequestQ: BlockingQueue<GameServer.ClientRequest>, private val socketServer: SocketServer) : Thread() {

    private val responseQ: BlockingQueue<String> = LinkedBlockingQueue()

    private val working = AtomicBoolean(true)
    override fun run() {
        val input = BufferedReader(InputStreamReader(DataInputStream(socket.getInputStream())))
        val output = BufferedWriter(OutputStreamWriter(DataOutputStream(socket.getOutputStream())))

        Log.d(TAG, "New client connection handler started ...")
        while (working.get()) {
            try {
                Log.d(TAG, "Waiting for client data ...")
                // Wait here for new client data
                val data = input.readLine()
                Log.d(TAG, "Got data = [$data]")

                // FIXME - data is a null pointer when client  closes socket. Why?
                if (data != null) {
                    // TODO - make sure 'exit:' text is coded in both Handler and GameServer classes
                    if (data == "exit:") {
                        working.set(false)
                        socketServer.removeClientHandler(this)
                    }

                    gameRequestQ.add(GameServer.ClientRequest(data, responseQ))

                    // Wait here for the GameService to respond to the request.
                    val response = responseQ.take()
                    output.write("Response = \"$response\"")
                    output.flush()

                    if (response == "shutdown:") {
                        socket.close()
                    }
                } else {
                    // FIXME: Empty data from client. Why? Is it correct to this socket???
                    working.set(false)
                }
            } catch (e: IOException) {
                if (!working.get()) {
                    // the socket is closed because this handler is shutting down
                    Log.d(TAG, "The Socket has closed and we are shutting down.")
                } else {
                    working.set(false)
                    e.printStackTrace()
                }
                try {
                    input.close()
                    output.close()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            } catch (e: InterruptedException) {
                working.set(false)
                e.printStackTrace()
                try {
                    input.close()
                    output.close()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            }
        }
        input.close()
        output.close()

        socket.close()
        Log.d(TAG, "The Client Socket Handler has shut down.")
    }

    fun shutdown() {
        working.set(false)
        socket.close()  // FIXME: Does this work???

        // This will release the thread if it's waiting on the queue.
        responseQ.add("shutdown:")
    }

    companion object {
        private val TAG = SocketClientHandler::class.java.simpleName
    }
}