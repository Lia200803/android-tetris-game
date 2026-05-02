
package com.example.myapplication

data class Position(val x: Int, val y: Int)

enum class TetrominoType {
    I, O, T, S, Z, J, L
}

data class Tetromino(
    val type: TetrominoType,
    val position: Position,
    val shape: List<List<Boolean>>
) {
    fun rotate(): Tetromino {
        val rows = shape.size
        val cols = shape[0].size
        val rotated = List(cols) { x ->
            List(rows) { y ->
                shape[rows - 1 - y][x]
            }
        }
        return copy(shape = rotated)
    }
}

object TetrominoShapes {
    fun getShape(type: TetrominoType): List<List<Boolean>> {
        return when (type) {
            TetrominoType.I -> listOf(
                listOf(true, true, true, true)
            )
            TetrominoType.O -> listOf(
                listOf(true, true),
                listOf(true, true)
            )
            TetrominoType.T -> listOf(
                listOf(false, true, false),
                listOf(true, true, true)
            )
            TetrominoType.S -> listOf(
                listOf(false, true, true),
                listOf(true, true, false)
            )
            TetrominoType.Z -> listOf(
                listOf(true, true, false),
                listOf(false, true, true)
            )
            TetrominoType.J -> listOf(
                listOf(true, false, false),
                listOf(true, true, true)
            )
            TetrominoType.L -> listOf(
                listOf(false, false, true),
                listOf(true, true, true)
            )
        }
    }
    
    fun random(): TetrominoType {
        val types = TetrominoType.values()
        return types.random()
    }
}
