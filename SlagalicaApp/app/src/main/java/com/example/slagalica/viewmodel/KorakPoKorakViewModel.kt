package com.example.slagalica.viewmodel

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.slagalica.model.StepData

class KorakPoKorakViewModel : ViewModel() {

    private val _currentStep = MutableLiveData<Int>(1)
    val currentStep: LiveData<Int> = _currentStep

    private val _remainingTime = MutableLiveData<Int>(10)
    val remainingTime: LiveData<Int> = _remainingTime

    private val _possiblePoints = MutableLiveData<Int>(20)
    val possiblePoints: LiveData<Int> = _possiblePoints

    private val _points = MutableLiveData<Int>(0)
    val points: LiveData<Int> = _points

    private val _opponentPoints = MutableLiveData<Int>(0)
    val opponentPoints: LiveData<Int> = _opponentPoints

    private val _currentHint = MutableLiveData<String>()
    val currentHint: LiveData<String> = _currentHint

    private val _previousHints = MutableLiveData<List<String>>(emptyList())
    val previousHints: LiveData<List<String>> = _previousHints

    private val _gameFinished = MutableLiveData<Boolean>(false)
    val gameFinished: LiveData<Boolean> = _gameFinished

    private val _guessed = MutableLiveData<Boolean?>(null)
    val guessed: LiveData<Boolean?> = _guessed

    private val _round = MutableLiveData<Int>(1)
    val round: LiveData<Int> = _round

    private var targetWord: String = ""
    private var currentGame: StepData? = null
    private var previousHintsList = mutableListOf<String>()
    private var timer: CountDownTimer? = null

    private val sampleGames = listOf(
        StepData(1L, "TESLA", listOf("Rođen 1856. godine", "Srpskog porijekla", "Radio u kompaniji Edison", "Izmislio je izmjenični struju", "Naučnik i izumitelj", "Ime mu je Nikola", "Tesla Motors nosi njegovo ime")),
        StepData(2L, "BEOGRAD", listOf("Na ušću dvije rijeke", "Sadrži čuvenu tvrđavu", "Historijska utvrda Kalemegdan", "Sava i Dunav se sreću ovdje", "Više od 1,5 miliona stanovnika", "Nalazi se u centralnoj Srbiji", "Glavni grad Srbije"))
    )

    fun startRound(round: Int) {
        _round.value = round
        currentGame = sampleGames.random()
        targetWord = currentGame!!.targetWord
        _currentStep.value = 1
        _possiblePoints.value = 20
        _gameFinished.value = false
        _guessed.value = null
        if (round == 1) _opponentPoints.value = 0
        previousHintsList.clear()
        _previousHints.value = emptyList()
        showNextHint()
    }

    private fun showNextHint() {
        val step = _currentStep.value ?: 1
        val game = currentGame ?: return
        if (step <= game.hints.size) {
            _currentHint.value = game.hints[step - 1]
            _possiblePoints.value = 20 - (step - 1) * 2
            startTimer()
        } else {
            finishGame(false)
        }
    }

    private fun startTimer() {
        timer?.cancel()
        _remainingTime.value = 10
        timer = object : CountDownTimer(10_000L, 1_000L) {
            override fun onTick(ms: Long) { _remainingTime.value = (ms / 1_000).toInt() }
            override fun onFinish() { _remainingTime.value = 0; goToNextStep() }
        }.start()
    }

    fun goToNextStep() {
        timer?.cancel()
        val step = _currentStep.value ?: return
        val game = currentGame ?: return
        game.hints.getOrNull(step - 1)?.let {
            previousHintsList.add(it)
            _previousHints.value = previousHintsList.toList()
        }
        if (step >= game.hints.size) finishGame(false)
        else { _currentStep.value = step + 1; showNextHint() }
    }

    fun tryGuess(input: String): Boolean {
        val correct = input.trim().uppercase() == targetWord.uppercase()
        if (correct) {
            timer?.cancel()
            _points.value = (_points.value ?: 0) + (_possiblePoints.value ?: 0)
            _guessed.value = true
            finishGame(true)
        }
        return correct
    }

    private fun finishGame(guessed: Boolean) {
        timer?.cancel()
        _guessed.value = guessed
        if (!guessed) {
            val opponentGain = listOf(4, 6, 8, 10, 12, 14, 16, 18, 20).random()
            _opponentPoints.value = (_opponentPoints.value ?: 0) + opponentGain
        }
        _gameFinished.value = true
    }

    override fun onCleared() {
        super.onCleared()
        timer?.cancel()
    }
}
