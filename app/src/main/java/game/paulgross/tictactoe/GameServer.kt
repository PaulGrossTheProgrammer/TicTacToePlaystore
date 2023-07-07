package game.paulgross.tictactoe

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class GameServer: Service() {

    // TODO - create main thread, with request queue,
    //  and then create the socket server thread passing the request queue.
    // The Socket Server thread creates client handler threads, passing the que to them too.
    // When a client needs work done, the request queue is added to by the client handler
    // This Service listens to the request queue, and interacts with the Activity as required.
    // Including updating the user's display of the game.
    private val gameRequestQ: Queue<String> = ConcurrentLinkedQueue()

    private val working = AtomicBoolean(true)
    private val gameThread = Runnable {

        SocketServer(gameRequestQ).start()

        while (working.get()) {
//            Log.d(TAG, "This is the game Thread reporting for duty!")

            val clientData = gameRequestQ.poll()  // Non-blocking read for client requests.
            if (clientData != null) {
                Log.d(TAG, "Got a request!!!")
                Log.d(TAG, "[$clientData]")

                // TODO - make a test change to the model and UI.
                // TODO - get a reference to MainActivity
//                MainActivity.remotePlaySquare(1, "X")

                // TODO - need the response queue to return data to client thread.

            }
            Thread.sleep(1000L)  // Pause for a while...

            // If the game is a player, then this is where the AI is coded.
        }
    }

    override fun onDestroy() {
        working.set(false)
    }

    override fun onBind(p0: Intent?): IBinder? {
        Log.d(GameServer.TAG, "Service is running onBind()...")
        return null
    }

    override fun onCreate() {
        Log.d(TAG, "Service is running onCreate()...")
        Thread(gameThread).start()
    }

    companion object {
        private val TAG = GameServer::class.java.simpleName
    }

}