package com.t1bers.duraak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Felt = Brush.radialGradient(listOf(Color(0xFF197344), Color(0xFF052F1C)))
private val Gold = Color(0xFFFFD54F)
private val White = Color.White

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { DurakApp() } }
    }
}

@Composable
fun DurakApp() {
    var opponents by remember { mutableIntStateOf(1) }
    var difficulty by remember { mutableIntStateOf(1) }
    var game by remember { mutableStateOf<GameState?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<GameState>() }
    val budget = listOf(20, 55, 110)[difficulty]

    LaunchedEffect(revision, game?.actor, game?.over) {
        val current = game ?: return@LaunchedEffect
        if (!current.over && current.actor != 0) {
            val player = current.actor
            val snapshot = current.copyState()
            delay(450)
            val move = withContext(Dispatchers.Default) { MonteCarloAI.choose(snapshot, player, budget) }
            val latest = game
            if (latest != null && !latest.over && latest.actor == player) {
                latest.apply(move)
                latest.finishIfNoMoreAttacks()
                revision++
            }
        } else if (!current.over && current.actor == 0 && current.actor == current.attacker &&
            current.table.isNotEmpty() && current.legalMoves(0).none { it is Move.Attack }) {
            current.finishIfNoMoreAttacks()
            revision++
        }
    }

    Box(Modifier.fillMaxSize().background(Felt)) {
        if (game == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text("Durak", fontSize = 58.sp, fontWeight = FontWeight.Black, color = White)
                Text("vs Monte Carlo AI", color = White.copy(alpha = .82f), fontSize = 17.sp)
                Spacer(Modifier.height(34.dp))
                Text("Opponents", color = White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { n -> ChoiceButton("$n", opponents == n) { opponents = n } }
                }
                Spacer(Modifier.height(22.dp))
                Text("Difficulty", color = White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Easy", "Medium", "Hard").forEachIndexed { i, label ->
                        ChoiceButton(label, difficulty == i) { difficulty = i }
                    }
                }
                Spacer(Modifier.height(30.dp))
                Button(onClick = { history.clear(); game = GameState.new(opponents + 1); revision++ },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                    modifier = Modifier.width(220.dp).height(56.dp)) {
                    Text("Play", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { showSettings = true }) { Text("Settings", color = White) }
            }
        } else {
            val state = game!!
            GameScreen(
                state, revision,
                onMenu = { game = null; history.clear(); revision++ },
                onSettings = { showSettings = true },
                onUndo = { if (history.isNotEmpty()) { game = history.removeAt(history.lastIndex).copyState(); revision++ } },
                canUndo = history.isNotEmpty() && !state.over,
                onPlay = { card ->
                    val move = state.legalMoves(0).firstOrNull {
                        (it is Move.Attack && it.card == card) || (it is Move.Defend && it.card == card)
                    }
                    if (move != null) {
                        history.add(state.copyState())
                        state.apply(move)
                        state.finishIfNoMoreAttacks()
                        revision++
                    }
                },
                onTake = { history.add(state.copyState()); state.apply(Move.Take); revision++ },
                onPass = { history.add(state.copyState()); state.apply(Move.Pass); revision++ },
                onRestart = { history.clear(); game = GameState.new(opponents + 1); revision++ }
            )
        }
    }
    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { Text("Settings") },
        text = { TextButton(onClick = { showSettings = false; showInfo = true }) { Text("Info & How to Play") } },
        confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Close") } }
    )
    if (showInfo) InfoDialog { showInfo = false }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(
        containerColor = if (selected) Gold else Color.Black.copy(alpha = .25f),
        contentColor = if (selected) Color.Black else White
    ), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to Play") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text("Durak is a card game for 2–4 players. The goal is to get rid of all your cards. The last player left holding cards is the Durak.\n\n" +
                    "1. Deal — Each player starts with 6 cards. One card shows the trump suit.\n\n" +
                    "2. Attack — The attacker plays a card. The defender beats it with a higher card of the same suit or any trump card.\n\n" +
                    "3. Add cards — Add cards matching any rank already on the table. Attacks cannot exceed the defender's hand size.\n\n" +
                    "4. Defend or take — If you cannot or do not want to defend, take all cards on the table. If all attacks are beaten, finish the round.\n\n" +
                    "5. Refill — Players draw back up to six cards. The attacker draws first and the defender last.\n\n" +
                    "6. Win — When the deck is empty, players with no cards leave. The last player holding cards loses.")
                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(10.dp))
                Text("Developers", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Alan · 250444")
                Text("Shoxrux · 250272")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun GameScreen(
    state: GameState, revision: Int,
    onMenu: () -> Unit, onSettings: () -> Unit, onUndo: () -> Unit,
    canUndo: Boolean, onPlay: (Card) -> Unit, onTake: () -> Unit,
    onPass: () -> Unit, onRestart: () -> Unit
) {
    val legal = state.legalMoves(0).mapNotNull {
        when (it) { is Move.Attack -> it.card; is Move.Defend -> it.card; else -> null }
    }.toSet()

    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onMenu) { Text("‹ Menu", color = White) }
            Spacer(Modifier.weight(1f))
            Text("Trump: ${state.trump.symbol}", color = White, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color.Black.copy(alpha = .25f), CircleShape).padding(horizontal = 14.dp, vertical = 7.dp))
            TextButton(onClick = onSettings) { Text("Settings", color = White) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            (1 until state.hands.size).forEach { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (state.actor == p) "🤖 Bot $p · Turn" else "🤖 Bot $p", color = if (state.actor == p) Gold else White,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy((-18).dp)) {
                        repeat(minOf(state.hands[p].size, 7)) { CardBack(30) }
                    }
                    Text("${state.hands[p].size} cards", color = White.copy(alpha = .8f), fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.deckCount > 0) {
                    Box { CardBack(48); state.faceUpTrump?.let { PlayingCard(it, 48, false) } }
                    Text("Deck ${state.deckCount}", color = White, fontSize = 11.sp)
                } else Text("Deck empty", color = White, fontSize = 11.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Table · ${state.table.size}/${state.roundLimit}", color = White, fontWeight = FontWeight.Bold)
                state.table.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { pair ->
                            Box(Modifier.width(76.dp).height(105.dp)) {
                                PlayingCard(pair.attack, 54, false)
                                pair.defense?.let { Box(Modifier.offset(x = 20.dp, y = 14.dp)) { PlayingCard(it, 54, false) } }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(when {
            state.over && state.durak == 0 -> "You are the Durak"
            state.over && state.durak == null -> "Draw"
            state.over -> "You win!"
            state.actor == 0 -> "Your turn"
            else -> "Bot ${state.actor} is thinking…"
        }, color = White, textAlign = TextAlign.Center, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUndo, enabled = canUndo && state.actor == 0) { Text("↶ Undo") }
            if (!state.over && state.actor == 0 && state.legalMoves(0).contains(Move.Take)) {
                Button(onClick = onTake, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726))) {
                    Text("Take", color = Color.Black)
                }
            }
            if (!state.over && state.actor == 0 && state.legalMoves(0).contains(Move.Pass)) {
                Button(onClick = onPass, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) {
                    Text(if (state.defenderTaking) "Finish attack" else "Done")
                }
            }
            if (state.over) Button(onClick = onRestart, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) {
                Text("Play again")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Your cards · ${state.hands[0].size}", color = White, fontWeight = FontWeight.Bold)
        HandView(state.hands[0].sortedWith(compareBy<Card> { if (it.suit == state.trump) 4 else it.suit.ordinal }.thenBy { it.rank }),
            legal, state.actor == 0 && !state.over, onPlay)
    }
    if (state.over) AlertDialog(onDismissRequest = {}, title = {
        Text(if (state.durak == 0) "You are the Durak!" else if (state.durak == null) "Draw" else "You win!")
    }, text = { Text("The game is over.") }, confirmButton = { TextButton(onClick = onRestart) { Text("New game") } })
}

@Composable
private fun HandView(cards: List<Card>, legal: Set<Card>, enabled: Boolean, onPlay: (Card) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth > maxHeight && cards.size > 7
        val width = if (compact && cards.isNotEmpty()) (maxWidth.value / cards.size * 1.35f).coerceIn(38f, 64f) else 64f
        val gap = if (compact && cards.size > 1) ((maxWidth.value - width) / (cards.size - 1) - width).coerceAtLeast(-8f) else -20f
        Row(Modifier.fillMaxWidth().then(if (!compact) Modifier.horizontalScroll(rememberScrollState()) else Modifier).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(gap.dp), verticalAlignment = Alignment.Bottom) {
            cards.forEach { card ->
                PlayingCard(card, width.toInt(), enabled && card in legal, { onPlay(card) }, { onPlay(card) })
            }
        }
    }
}

@Composable
private fun PlayingCard(card: Card, width: Int, enabled: Boolean, onClick: () -> Unit = {}, onDragPlay: () -> Unit = onClick) {
    val ink = if (card.suit.isRed) Color(0xFFD21F2B) else Color(0xFF171717)
    var dragY by remember(card.id) { mutableFloatStateOf(0f) }
    Column(Modifier.width(width.dp).height((width * 1.45f).dp).clip(RoundedCornerShape(7.dp))
        .background(Color.White).border(1.dp, Color.Black.copy(alpha = .35f), RoundedCornerShape(7.dp))
        .then(if (enabled) Modifier.clickable(onClick = onClick).pointerInput(card.id) {
            detectDragGestures(onDragEnd = { if (dragY < -36f) onDragPlay(); dragY = 0f },
                onDragCancel = { dragY = 0f }, onDrag = { change, amount -> dragY += amount.y; change.consume() })
        } else Modifier).padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) {
            Text(card.rankLabel, color = ink, fontWeight = FontWeight.Bold, fontSize = (width * .22).sp)
            Text(card.suit.symbol, color = ink, fontSize = (width * .20).sp)
        }
        Spacer(Modifier.weight(1f))
        Text(card.suit.symbol, color = ink, fontSize = (width * .48).sp)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun CardBack(width: Int) {
    Box(Modifier.width(width.dp).height((width * 1.45f).dp).clip(RoundedCornerShape(6.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF354F9C), Color(0xFF101D50))))
        .border(1.dp, White.copy(alpha = .65f), RoundedCornerShape(6.dp))) {
        Box(Modifier.fillMaxSize().padding(4.dp).border(1.dp, White.copy(alpha = .45f), RoundedCornerShape(4.dp)))
    }
}
