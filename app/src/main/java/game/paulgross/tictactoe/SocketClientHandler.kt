package game.paulgross.tictactoe

import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue


class SocketClientHandler(private val dataInputStream: DataInputStream, private val dataOutputStream: DataOutputStream, private val gameRequestQ: BlockingQueue<GameService.ClientRequest>) : Thread() {

    private val responseQ: BlockingQueue<String> = LinkedBlockingQueue()

    override fun run() {
        val input = BufferedReader(InputStreamReader(dataInputStream))
        val output = BufferedWriter(OutputStreamWriter(dataOutputStream))

        Log.d(TAG, "New client connection handler started ...")
        var running = true
        while (running) {
            try {
                Log.d(TAG, "Waiting for client data ...")
                // Blocking read - wait here for new client data
                val data = input.readLine()
                Log.d(TAG, "Got data = [$data]")

                // FIXME - data is a null pointer when client  closes socket. Why?
                if (data != null) {
                    if (data == "exit") {
                        running = false
                    }

                    gameRequestQ.add(GameService.ClientRequest(data, responseQ))

                    // Wait here for the GameService to respond to the request.
                    val response = responseQ.take()
                    output.write("Response = \"$response\"")
                    output.flush()
                } else {
                    running = false
                }
            } catch (e: IOException) {
                running = false
                e.printStackTrace()
                try {
                    dataInputStream.close()
                    dataOutputStream.close()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            } catch (e: InterruptedException) {
                running = false
                e.printStackTrace()
                try {
                    dataInputStream.close()
                    dataOutputStream.close()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            }
        }
        dataInputStream.close()
        dataOutputStream.close()
    }

    companion object {
        private val TAG = SocketClientHandler::class.java.simpleName
    }
}