package com.example.myapplication

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TetrisGame {
    private val boardWidth = 10
    private val boardHeight = 20
    
    var board by mutableStateOf(List(boardHeight) { List(boardWidth) { false } })
        private set
    
    var currentPiece by mutableStateOf<Tetromino?>(null)
        private set
    
    var score by mutableStateOf(0)
        private set
    
    var gameOver by mutableStateOf(false)
        private set
    
    var isPaused by mutableStateOf(false)
        private set
    
    private var rotationLock by mutableStateOf(false)
        private set
    
    private var rotationJob: Job? = null
    
    private var soundManager: SoundManager? = null
    
    private var moveLeftCount = 0
    private var moveRightCount = 0
    private var lastMoveClickTime = 0L
    
    private var rotateCount = 0
    private var lastRotateClickTime = 0L
    
    init {
        spawnPiece()
    }
    
    fun setSoundManager(manager: SoundManager) {
        soundManager = manager
    }
    
    fun spawnPiece() {
        val type = TetrominoShapes.random()
        val shape = TetrominoShapes.getShape(type)
        val piece = Tetromino(
            type = type,
            position = Position(boardWidth / 2 - shape[0].size / 2, 0),
            shape = shape
        )
        
        if (!isValidPosition(piece, piece.position)) {
            gameOver = true
            return
        }
        
        currentPiece = piece
    }
    
    fun pause() {
        if (!gameOver) {
            isPaused = true
        }
    }
    
    fun resume() {
        isPaused = false
    }
    
    fun togglePause(): Boolean {
        if (gameOver) return isPaused
        isPaused = !isPaused
        return isPaused
    }
    
    fun moveDown() {
        if (isPaused || gameOver || rotationLock) return
        
        currentPiece?.let { piece ->
            val newPos = Position(piece.position.x, piece.position.y + 1)
            if (isValidPosition(piece, newPos)) {
                currentPiece = piece.copy(position = newPos)
            } else {
                placePiece()
                soundManager?.playLand()
                clearLines()
                spawnPiece()
            }
        }
    }
    
    fun moveLeft() {
        if (isPaused || gameOver) return
        
        currentPiece?.let { piece ->
            val newPos = Position(piece.position.x - 1, piece.position.y)
            if (isValidPosition(piece, newPos)) {
                currentPiece = piece.copy(position = newPos)
            }
        }
    }
    
    fun moveRight() {
        if (isPaused || gameOver) return
        
        currentPiece?.let { piece ->
            val newPos = Position(piece.position.x + 1, piece.position.y)
            if (isValidPosition(piece, newPos)) {
                currentPiece = piece.copy(position = newPos)
            }
        }
    }
    
    fun queueMoveLeft() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastMoveClickTime > 200) {
            moveLeftCount = 0
            moveRightCount = 0
        }
        moveLeftCount++
        moveRightCount = 0
        lastMoveClickTime = currentTime
    }
    
    fun queueMoveRight() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastMoveClickTime > 200) {
            moveLeftCount = 0
            moveRightCount = 0
        }
        moveRightCount++
        moveLeftCount = 0
        lastMoveClickTime = currentTime
    }
    
    fun executeQueuedMoves() {
        if (moveLeftCount > 0) {
            repeat(moveLeftCount) {
                moveLeft()
            }
            moveLeftCount = 0
            soundManager?.playMove()
        } else if (moveRightCount > 0) {
            repeat(moveRightCount) {
                moveRight()
            }
            moveRightCount = 0
            soundManager?.playMove()
        }
        
        if (rotateCount > 0) {
            repeat(rotateCount) {
                rotateInternal()
            }
            rotateCount = 0
            rotationLock = false
            rotationJob?.cancel()
            soundManager?.playRotate()
        }
    }
    
    fun rotate() {
        queueRotate()
    }
    
    private fun rotateInternal() {
        if (isPaused || gameOver) return
        
        currentPiece?.let { piece ->
            val rotated = piece.rotate()
            if (isValidPosition(rotated, rotated.position)) {
                currentPiece = rotated
            }
        }
    }
    
    fun queueRotate() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRotateClickTime > 150) {
            rotateCount = 0
        }
        rotateCount++
        lastRotateClickTime = currentTime
    }
    
    fun drop() {
        if (isPaused || gameOver) return
        
        currentPiece?.let { piece ->
            var newY = piece.position.y
            while (isValidPosition(piece, Position(piece.position.x, newY + 1))) {
                newY++
            }
            currentPiece = piece.copy(position = Position(piece.position.x, newY))
            placePiece()
            soundManager?.playLand()
            clearLines()
            spawnPiece()
        }
    }
    
    private fun isValidPosition(piece: Tetromino, position: Position): Boolean {
        for (y in piece.shape.indices) {
            for (x in piece.shape[y].indices) {
                if (piece.shape[y][x]) {
                    val newX = position.x + x
                    val newY = position.y + y
                    
                    if (newX < 0 || newX >= boardWidth || newY >= boardHeight) {
                        return false
                    }
                    
                    if (newY >= 0 && board[newY][newX]) {
                        return false
                    }
                }
            }
        }
        return true
    }
    
    private fun placePiece() {
        currentPiece?.let { piece ->
            val newBoard = board.toMutableList()
            for (y in piece.shape.indices) {
                for (x in piece.shape[y].indices) {
                    if (piece.shape[y][x]) {
                        val boardY = piece.position.y + y
                        val boardX = piece.position.x + x
                        if (boardY >= 0 && boardY < boardHeight && boardX >= 0 && boardX < boardWidth) {
                            val row = newBoard[boardY].toMutableList()
                            row[boardX] = true
                            newBoard[boardY] = row
                        }
                    }
                }
            }
            board = newBoard
        }
    }
    
    private fun clearLines() {
        val newBoard = board.filter { row -> !row.all { it } }.toMutableList()
        val linesCleared = boardHeight - newBoard.size
        
        if (linesCleared > 0) {
            score += linesCleared * 100
            repeat(linesCleared) {
                newBoard.add(0, List(boardWidth) { false })
            }
            board = newBoard
            soundManager?.playClear(linesCleared)
        }
    }

    fun restart() {
        board = List(boardHeight) { List(boardWidth) { false } }
        score = 0
        gameOver = false
        isPaused = false
        rotationLock = false
        rotationJob?.cancel()
        spawnPiece()
    }
}
