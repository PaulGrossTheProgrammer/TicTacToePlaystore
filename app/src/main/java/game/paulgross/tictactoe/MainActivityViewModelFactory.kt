package game.paulgross.tictactoe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainActivityViewModelFactory(private val startingCount : Int): ViewModelProvider.Factory {

    override  fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainActivityViewModel::class.java)){
            return MainActivityViewModel(0) as T
        }
        throw IllegalArgumentException("Unknown View Model Class")
    }
}