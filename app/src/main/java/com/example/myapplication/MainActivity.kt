package com.example.myapplication

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SoundManager(private val context: Context) {
    private var moveSound: Int = R.raw.move_sound
    private var rotateSound: Int = R.raw.rotate_sound
    private var dropSound: Int = R.raw.drop_sound
    private var landSound: Int = R.raw.land_sound
    private var clearSound: Int = R.raw.clear_line_sound
    private var gameOverSound: Int = R.raw.game_over_sound
    
    var soundEnabled by mutableStateOf(true)
        private set
    
    fun playMove() {
        if (soundEnabled) playSound(moveSound)
    }
    
    fun playRotate() {
        if (soundEnabled) playSound(rotateSound)
    }
    
    fun playDrop() {
        if (soundEnabled) playSound(dropSound)
    }
    
    fun playLand() {
        if (soundEnabled) playSound(landSound)
    }
    
    fun playClear(linesCleared: Int = 1) {
        if (soundEnabled) {
            val pitch = when (linesCleared) {
                1 -> 1.0f
                2 -> 1.2f
                3 -> 1.5f
                4 -> 2.0f
                else -> 1.0f
            }
            playSoundWithPitch(clearSound, pitch)
        }
    }
    
    fun playGameOver() {
        if (soundEnabled) playSound(gameOverSound)
    }
    
    private fun playSound(resourceId: Int) {
        MediaPlayer.create(context, resourceId).apply {
            setOnCompletionListener { release() }
            start()
        }
    }
    
    private fun playSoundWithPitch(resourceId: Int, pitch: Float) {
        val mediaPlayer = MediaPlayer.create(context, resourceId)
        mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(pitch)
        mediaPlayer.setOnCompletionListener { release() }
        mediaPlayer.start()
    }
    
    fun toggleSound() {
        soundEnabled = !soundEnabled
    }
    
    fun release() {
    }
}

fun performHapticFeedback(context: Context) {
    try {
        val activity = context as? ComponentActivity
        activity?.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } catch (e: Exception) {
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        
        setContent {
            MyApplicationTheme {
                TetrisGameScreen()
            }
        }
    }
}

@Composable
fun TetrisGameScreen() {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context) }
    val game = remember { TetrisGame() }
    var dropJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    DisposableEffect(Unit) {
        game.setSoundManager(soundManager)
        onDispose {
            soundManager.release()
            dropJob?.cancel()
        }
    }
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(150)
            game.executeQueuedMoves()
        }
    }
    
    LaunchedEffect(game.gameOver, game.isPaused) {
        dropJob?.cancel()
        
        if (!game.gameOver && !game.isPaused) {
            dropJob = launch {
                while (true) {
                    delay(800)
                    if (!game.gameOver && !game.isPaused) {
                        game.moveDown()
                    } else {
                        break
                    }
                }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val boardWidthPx = size.width
            val boardHeightPx = size.height
            val cellWidth = boardWidthPx / 10
            val cellHeight = boardHeightPx / 20
            
            game.board.forEachIndexed { y, row ->
                row.forEachIndexed { x, cell ->
                    if (cell) {
                        drawRect(
                            color = Color.Blue,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                x * cellWidth,
                                y * cellHeight
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                cellWidth - 1,
                                cellHeight - 1
                            )
                        )
                    }
                }
            }
            
            game.currentPiece?.let { piece ->
                piece.shape.forEachIndexed { y, row ->
                    row.forEachIndexed { x, cell ->
                        if (cell) {
                            drawRect(
                                color = Color.Cyan,
                                topLeft = androidx.compose.ui.geometry.Offset(
                                    (piece.position.x + x) * cellWidth,
                                    (piece.position.y + y) * cellHeight
                                ),
                                size = androidx.compose.ui.geometry.Size(
                                    cellWidth - 1,
                                    cellHeight - 1
                                )
                            )
                        }
                    }
                }
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text("分数: ${game.score}", 
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }

        var lastMoveTime by remember { mutableStateOf(0L) }
        var pressStartTime by remember { mutableStateOf(0L) }
        var pressPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            if (game.isPaused || game.gameOver) return@detectTapGestures
                            
                            val screenWidth = size.width
                            val screenHeight = size.height
                            val x = offset.x
                            val y = offset.y
                            
                            pressStartTime = System.currentTimeMillis()
                            pressPosition = Pair(x, y)
                            
                            tryAwaitRelease()
                            
                            val pressDuration = System.currentTimeMillis() - pressStartTime
                            
                            when {
                                y < screenHeight * 0.1 -> {
                                }
                                y > screenHeight * 0.83 -> {
                                    game.drop()
                                    soundManager.playDrop()
                                    performHapticFeedback(context)
                                }
                                x < screenWidth * 0.33 -> {
                                    if (pressDuration > 200) {
                                        val repeatCount = (pressDuration / 60).toInt()
                                        repeat(repeatCount) {
                                            game.queueMoveLeft()
                                        }
                                    } else {
                                        game.queueMoveLeft()
                                    }
                                }
                                x > screenWidth * 0.66 -> {
                                    if (pressDuration > 200) {
                                        val repeatCount = (pressDuration / 60).toInt()
                                        repeat(repeatCount) {
                                            game.queueMoveRight()
                                        }
                                    } else {
                                        game.queueMoveRight()
                                    }
                                }
                                else -> {
                                    if (pressDuration > 200) {
                                        val repeatCount = (pressDuration / 150).toInt()
                                        repeat(repeatCount) {
                                            game.queueRotate()
                                        }
                                    } else {
                                        game.queueRotate()
                                    }
                                }
                            }
                            pressPosition = null
                        },
                        onDoubleTap = { offset ->
                            val screenHeight = size.height
                            val y = offset.y
                            
                            if (y < screenHeight * 0.15) {
                                if (game.togglePause()) {
                                    soundManager.playGameOver()
                                } else {
                                    soundManager.playMove()
                                }
                                performHapticFeedback(context)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            if (game.isPaused || game.gameOver) return@detectHorizontalDragGestures
                            
                            val currentTime = System.currentTimeMillis()
                            if (dragAmount > 20 && currentTime - lastMoveTime > 60) {
                                game.queueMoveRight()
                                change.consume()
                                lastMoveTime = currentTime
                            } else if (dragAmount < -20 && currentTime - lastMoveTime > 60) {
                                game.queueMoveLeft()
                                change.consume()
                                lastMoveTime = currentTime
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            if (game.isPaused || game.gameOver) return@detectVerticalDragGestures
                            
                            val currentTime = System.currentTimeMillis()
                            if (dragAmount > 10 && currentTime - lastMoveTime > 30) {
                                game.moveDown()
                                soundManager.playMove()
                                performHapticFeedback(context)
                                change.consume()
                                lastMoveTime = currentTime
                            }
                        }
                    )
                }
        ) {
        }
        
        Box(
            modifier = Modifier
                .padding(16.dp)
                .size(48.dp)
                .clickable { soundManager.toggleSound() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (soundManager.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                contentDescription = if (soundManager.soundEnabled) "关闭声音" else "开启声音",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        
        if (game.gameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("游戏结束!", 
                        style = MaterialTheme.typography.headlineLarge, 
                        color = Color.Red
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(onClick = { 
                        game.restart()
                        soundManager.playGameOver()
                    }) {
                        Text("重新开始")
                    }
                }
            }
        }
        
        if (game.isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text("已暂停", 
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TetrisGamePreview() {
    MyApplicationTheme {
        TetrisGameScreen()
    }
}