package com.example.slagalica.viewmodel

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.slagalica.model.SkockoAttempt
import com.example.slagalica.model.SkockoSymbol

class SkockoViewModel : ViewModel() {

    private var secretCombination: List<SkockoSymbol> = emptyList()

    private val _currentSelection = MutableLiveData<MutableList<SkockoSymbol>>(mutableListOf())
    val currentSelection: LiveData<MutableList<SkockoSymbol>> = _currentSelection

    private val _attempts = MutableLiveData<List<SkockoAttempt>>(emptyList())
    val attempts: LiveData<List<SkockoAttempt>> = _attempts

    private val _attemptCount = MutableLiveData<Int>(1)
    val attemptCount: LiveData<Int> = _attemptCount

    private val _points = MutableLiveData<Int>(0)
    val points: LiveData<Int> = _points

    private val _opponentPoints = MutableLiveData<Int>(0)
    val opponentPoints: LiveData<Int> = _opponentPoints

    private val _remainingTime = MutableLiveData<Int>(30)
    val remainingTime: LiveData<Int> = _remainingTime

    private val _round = MutableLiveData<Int>(1)
    val round: LiveData<Int> = _round

    private val _gameFinished = MutableLiveData<Boolean>(false)
    val gameFinished: LiveData<Boolean> = _gameFinished

    private val _guessed = MutableLiveData<Boolean?>(null)
    val guessed: LiveData<Boolean?> = _guessed

    private var timer: CountDownTimer? = null

    fun startRound(round: Int) {
        _round.value = round
        secretCombination = SkockoSymbol.all().shuffled().take(4)
        _currentSelection.value = mutableListOf()
        _attempts.value = emptyList()
        _attemptCount.value = 1
        _gameFinished.value = false
        _guessed.value = null
        _remainingTime.value = 30
        startTimer()
    }

    fun addSymbol(symbol: SkockoSymbol) {
        val list = _currentSelection.value ?: mutableListOf()
        if (list.size < 4) { list.add(symbol); _currentSelection.value = list }
    }

    fun deleteLastSymbol() {
        val list = _currentSelection.value ?: mutableListOf()
        if (list.isNotEmpty()) { list.removeAt(list.size - 1); _currentSelection.value = list }
    }

    fun checkSelection(): SkockoAttempt? {
        val selection = _currentSelection.value?.toList() ?: return null
        if (selection.size != 4) return null

        val secret = secretCombination
        var correctPosition = 0
        var wrongPosition = 0

        val selectionLeft = selection.toMutableList()
        val secretLeft = secret.toMutableList()

        for (i in 0..3) {
            if (selection[i] == secret[i]) {
                correctPosition++
                selectionLeft[i] = SkockoSymbol.SQUARE
                secretLeft[i] = SkockoSymbol.SQUARE
            }
        }

        for (symbol in selectionLeft) {
            if (symbol != SkockoSymbol.SQUARE && secretLeft.contains(symbol)) {
                wrongPosition++
                secretLeft.remove(symbol)
            }
        }

        val attempt = SkockoAttempt(selection, correctPosition, wrongPosition)
        val list = (_attempts.value ?: emptyList()).toMutableList()
        list.add(attempt)
        _attempts.value = list
        _currentSelection.value = mutableListOf()

        if (correctPosition == 4) {
            timer?.cancel()
            _points.value = (_points.value ?: 0) + pointsForAttempt(_attemptCount.value ?: 1)
            _guessed.value = true
            _gameFinished.value = true
        } else if ((_attemptCount.value ?: 1) >= 6) {
            finishRound(false)
        } else {
            _attemptCount.value = (_attemptCount.value ?: 1) + 1
        }

        return attempt
    }

    fun pointsForAttempt(attempt: Int): Int = when (attempt) { 1, 2 -> 20; 3, 4 -> 15; else -> 10 }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(30_000L, 1_000L) {
            override fun onTick(ms: Long) { _remainingTime.value = (ms / 1_000).toInt() }
            override fun onFinish() { _remainingTime.value = 0; finishRound(false) }
        }.start()
    }

    private fun finishRound(guessed: Boolean) {
        timer?.cancel(); _guessed.value = guessed; _gameFinished.value = true
    }

    override fun onCleared() { super.onCleared(); timer?.cancel() }
}
