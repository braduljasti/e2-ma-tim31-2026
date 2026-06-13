package com.example.slagalica.viewmodel

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.data.GameResultRepository
import com.example.slagalica.model.GameType
import com.example.slagalica.model.MyNumberData
import kotlinx.coroutines.launch

class MojBrojViewModel(
    private val resultsRepo: GameResultRepository = GameResultRepository()
) : ViewModel() {

    private val _targetNumber = MutableLiveData<Int?>()
    val targetNumber: LiveData<Int?> = _targetNumber

    private val _availableNumbers = MutableLiveData<List<Int>>(emptyList())
    val availableNumbers: LiveData<List<Int>> = _availableNumbers

    private val _expression = MutableLiveData<String>("")
    val expression: LiveData<String> = _expression

    private val _points = MutableLiveData<Int>(0)
    val points: LiveData<Int> = _points

    private val _opponentPoints = MutableLiveData<Int>(0)
    val opponentPoints: LiveData<Int> = _opponentPoints

    private val _remainingTime = MutableLiveData<Int>(60)
    val remainingTime: LiveData<Int> = _remainingTime

    private val _round = MutableLiveData<Int>(1)
    val round: LiveData<Int> = _round

    private val _numberShown = MutableLiveData<Boolean>(false)
    val numberShown: LiveData<Boolean> = _numberShown

    private val _availableShown = MutableLiveData<Boolean>(false)
    val availableShown: LiveData<Boolean> = _availableShown

    private val _gameFinished = MutableLiveData<Boolean>(false)
    val gameFinished: LiveData<Boolean> = _gameFinished

    private val _checkResult = MutableLiveData<String?>()
    val checkResult: LiveData<String?> = _checkResult

    private val _rotatingNumber = MutableLiveData<Int>(0)
    val rotatingNumber: LiveData<Int> = _rotatingNumber

    private var timer: CountDownTimer? = null
    private var numberTimer: CountDownTimer? = null

    fun startRound(round: Int) {
        _round.value = round
        _targetNumber.value = null
        _availableNumbers.value = emptyList()
        _expression.value = ""
        _numberShown.value = false
        _availableShown.value = false
        _gameFinished.value = false
        _remainingTime.value = 60
        startNumberTimer()
    }

    private fun startNumberTimer() {
        numberTimer = object : CountDownTimer(5_000L, 100L) {
            override fun onTick(ms: Long) { _rotatingNumber.value = (100..999).random() }
            override fun onFinish() { stopNumber() }
        }.start()
    }

    fun stopNumber() {
        numberTimer?.cancel()
        if (_targetNumber.value == null) {
            _targetNumber.value = MyNumberData.randomTarget()
            _numberShown.value = true
        }
    }

    fun stopAvailable() {
        if (_numberShown.value == true && _availableNumbers.value.isNullOrEmpty()) {
            _availableNumbers.value = MyNumberData.randomNumbers()
            _availableShown.value = true
            startMainTimer()
        }
    }

    private fun startMainTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(60_000L, 1_000L) {
            override fun onTick(ms: Long) { _remainingTime.value = (ms / 1_000).toInt() }
            override fun onFinish() { _remainingTime.value = 0; finishRound() }
        }.start()
    }

    fun addNumber(number: Int) {
        if (_availableShown.value == true) _expression.value = "${_expression.value}$number "
    }

    fun addOperator(op: String) {
        if (_availableShown.value == true) _expression.value = "${_expression.value}$op "
    }

    fun deleteLastChar() {
        val current = (_expression.value ?: "").trimEnd()
        if (current.isNotEmpty()) {
            val lastSpace = current.lastIndexOf(' ')
            _expression.value = if (lastSpace >= 0) current.substring(0, lastSpace) + " " else ""
        }
    }

    fun resetExpression() { _expression.value = "" }

    fun checkExpression(): Int {
        val exprStr = (_expression.value ?: "").trim()
        val target = _targetNumber.value ?: return 0
        return try {
            val cleaned = exprStr.replace("×", "*").replace("÷", "/").replace(" ", "")
            val result = evaluate(cleaned)
            val diff = Math.abs(result - target)
            when {
                result == target -> { _points.value = (_points.value ?: 0) + 10; _checkResult.value = "✅ Tačno! +10 bodova"; 10 }
                diff <= 10 -> { _points.value = (_points.value ?: 0) + 5; _checkResult.value = "Blizu! Razlika: $diff. +5 bodova"; 5 }
                else -> { _checkResult.value = "Netačno. Vaš rezultat: $result, traženo: $target"; 0 }
            }
        } catch (e: Exception) {
            _checkResult.value = "Neispravan izraz. Provjerite unos."
            0
        }
    }

    private fun evaluate(expr: String): Int {
        return object : Any() {
            var pos = -1; var ch = ' '
            fun nextChar() { ch = if (++pos < expr.length) expr[pos] else '\u0000' }
            fun eat(c: Char): Boolean { while (ch == ' ') nextChar(); return if (ch == c) { nextChar(); true } else false }
            fun parse(): Double { nextChar(); val x = parseExpr(); if (pos < expr.length) throw RuntimeException(); return x }
            fun parseExpr(): Double { var x = parseTerm(); while (true) x = when { eat('+') -> x + parseTerm(); eat('-') -> x - parseTerm(); else -> return x }; }
            fun parseTerm(): Double { var x = parseFactor(); while (true) x = when { eat('*') -> x * parseFactor(); eat('/') -> x / parseFactor(); else -> return x }; }
            fun parseFactor(): Double {
                if (eat('+')) return parseFactor()
                if (eat('-')) return -parseFactor()
                val start = pos
                return if (eat('(')) { val x = parseExpr(); eat(')'); x }
                else if (ch in '0'..'9' || ch == '.') { while (ch in '0'..'9' || ch == '.') nextChar(); expr.substring(start + 1, pos).toDouble() }
                else throw RuntimeException()
            }
        }.parse().toInt()
    }

    private fun finishRound() {
        timer?.cancel()
        _gameFinished.value = true
        if ((_round.value ?: 1) >= 2) {
            val my = _points.value ?: 0
            val opp = _opponentPoints.value ?: 0
            viewModelScope.launch {
                runCatching { resultsRepo.saveResult(GameType.MOJ_BROJ, my, opp) }
            }
        }
    }

    override fun onCleared() { super.onCleared(); timer?.cancel(); numberTimer?.cancel() }
}
