package com.t1bers.duraak

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.random.Random

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
    var showBenchmark by remember { mutableStateOf(false) }
    var benchmarkText by remember { mutableStateOf("Run the test to compare both algorithms.") }
    var pendingTransfer by remember { mutableStateOf<Card?>(null) }
    var gameMessage by remember { mutableStateOf("Your turn") }
    val history = remember { mutableStateListOf<GameState>() }
    val budget = listOf(24, 65, 125)[difficulty]

    LaunchedEffect(showBenchmark) {
        if (showBenchmark) benchmarkText = withContext(Dispatchers.Default) { AlgorithmBenchmark.run() }
    }

    LaunchedEffect(revision, game?.actor, game?.over) {
        val current = game ?: return@LaunchedEffect
        if (current.over) return@LaunchedEffect

        if (current.actor != 0) {
            val player = current.actor
            val snapshot = current.copyState()
            delay(400)
            val move = withContext(Dispatchers.Default) {
                val bluffCards = snapshot.cheatCandidates(player)
                val legalAttacks = snapshot.legalMoves(player).filterIsInstance<Move.Attack>()
                val bluffChance = if (legalAttacks.isEmpty()) 0.18 else 0.07
                if (bluffCards.isNotEmpty() && Random.nextDouble() < bluffChance) {
                    Move.CheatAttack(bluffCards.random())
                } else {
                    MonteCarloAI.choose(snapshot, player, budget)
                }
            }
            val latest = game
            if (latest != null && !latest.over && latest.actor == player) {
                if (latest.apply(move)) {
                    gameMessage = when (move) {
                        is Move.CheatAttack -> "Bot $player may be bluffing — tap a suspicious card to challenge"
                        is Move.Attack -> "Bot $player attacks with ${move.card.label}"
                        is Move.Defend -> "Bot $player beats with ${move.card.label}"
                        is Move.Transfer -> "Bot $player transfers the attack"
                        Move.Take -> "Bot $player takes the cards"
                        Move.Pass -> "Bot $player ends the round"
                    }
                    latest.finishIfNoMoreAttacks()
                    revision++
                }
            }
        } else {
            current.finishIfNoMoreAttacks()
            if (current.over || current.actor != 0) revision++
        }
    }

    Box(Modifier.fillMaxSize().background(Felt)) {
        if (game == null) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Durak", fontSize = 54.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = White)
                Text("vs Monte Carlo AI", color = White.copy(alpha = .8f), fontSize = 16.sp)
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
                    listOf("Easy", "Medium", "Hard").forEachIndexed { index, label ->
                        ChoiceButton(label, difficulty == index) { difficulty = index }
                    }
                }
                Spacer(Modifier.height(30.dp))
                Button(
                    onClick = {
                        history.clear()
                        game = GameState.new(opponents + 1)
                        gameMessage = "Your turn"
                        revision++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                    modifier = Modifier.width(200.dp).height(52.dp).clip(RoundedCornerShape(26.dp))
                ) { Text("Play", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                TextButton(onClick = { showBenchmark = true }) { Text("Sorting Test", color = White.copy(alpha = .85f)) }
            }
            IconButton(
                onClick = { showInfo = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 18.dp)
                    .size(50.dp).background(Color.Black.copy(alpha = .18f), CircleShape)
            ) { Text("ⓘ", color = White, fontSize = 27.sp) }
        } else {
            val state = game!!
            GameScreen(
                state = state,
                revision = revision,
                message = gameMessage,
                onMenu = { game = null; history.clear(); pendingTransfer = null; revision++ },
                onSettings = { showSettings = true },
                onUndo = {
                    if (history.isNotEmpty()) {
                        game = history.removeAt(history.lastIndex).copyState()
                        gameMessage = "Move undone"
                        pendingTransfer = null
                        revision++
                    }
                },
                canUndo = history.isNotEmpty() && !state.over,
                onSelectCard = { card ->
                    val transfer = state.legalMoves(0).filterIsInstance<Move.Transfer>().firstOrNull { it.card == card }
                    if (transfer != null) pendingTransfer = card else {
                        val move = state.legalMoves(0).firstOrNull {
                            (it is Move.Attack && it.card == card) || (it is Move.Defend && it.card == card)
                        }
                        if (move != null) {
                            history.add(state.copyState())
                            state.apply(move)
                            gameMessage = if (move is Move.Attack) "You attack with ${card.label}" else "You defend with ${card.label}"
                            state.finishIfNoMoreAttacks()
                            revision++
                        }
                    }
                },
                onTake = {
                    history.add(state.copyState())
                    state.apply(Move.Take)
                    gameMessage = "You take the cards"
                    revision++
                },
                onPass = {
                    history.add(state.copyState())
                    state.apply(Move.Pass)
                    gameMessage = "Round finished"
                    revision++
                },
                onChallenge = { index ->
                    history.add(state.copyState())
                    if (state.challengeCheat(index)) {
                        gameMessage = "Caught the bluff! The false card is returned to the bot."
                        state.finishIfNoMoreAttacks()
                    } else {
                        history.removeAt(history.lastIndex)
                        gameMessage = "That card is not a bluff."
                    }
                    revision++
                },
                onRestart = {
                    history.clear()
                    pendingTransfer = null
                    game = GameState.new(opponents + 1)
                    gameMessage = "New game"
                    revision++
                }
            )
        }
    }

    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { Text("Settings") },
        text = {
            Column {
                Text("Game settings")
                TextButton(onClick = { showSettings = false; showInfo = true }) {
                    Text("Info & How to Play")
                }
                Text("Difficulty and opponent count can be changed from the main menu.")
            }
        },
        confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Close") } }
    )
    if (showInfo) InfoDialog { showInfo = false }
    if (showBenchmark) AlertDialog(
        onDismissRequest = { showBenchmark = false },
        title = { Text("Merge Sort vs Counting Sort") },
        text = { Text(benchmarkText) },
        confirmButton = { TextButton(onClick = { showBenchmark = false }) { Text("Close") } }
    )
    if (pendingTransfer != null && game != null) {
        val card = pendingTransfer!!
        AlertDialog(
            onDismissRequest = { pendingTransfer = null },
            title = { Text("Transfer attack") },
            text = { Text("Choose where to place ${card.label} beside the matching card.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val state = game!!
                        val move = state.legalMoves(0).filterIsInstance<Move.Transfer>()
                            .firstOrNull { it.card == card && it.side == TransferSide.LEFT }
                        if (move != null) {
                            history.add(state.copyState())
                            state.apply(move)
                            gameMessage = "You transfer the attack to the next player (left)"
                            revision++
                        }
                        pendingTransfer = null
                    }) { Text("Place left") }
                    TextButton(onClick = {
                        val state = game!!
                        val move = state.legalMoves(0).filterIsInstance<Move.Transfer>()
                            .firstOrNull { it.card == card && it.side == TransferSide.RIGHT }
                        if (move != null) {
                            history.add(state.copyState())
                            state.apply(move)
                            gameMessage = "You transfer the attack to the next player (right)"
                            revision++
                        }
                        pendingTransfer = null
                    }) { Text("Place right") }
                }
            }
        )
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) Gold else Color.Black.copy(alpha = .24f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.Black else White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to Play") },
        text = {
            Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Durak is a card game for 2–4 players. Get rid of all your cards. The last player holding cards is the Durak.\n\n" +
                    "1. Deal — Each player starts with six cards. One card shows the trump suit.\n\n" +
                    "2. Attack — The attacker plays a card. Beat it with a higher card of the same suit or a trump.\n\n" +
                    "3. Add cards — Add cards matching any rank already on the table. The number of attacks cannot exceed the defender's hand size.\n\n" +
                    "4. Transfer — If you have a card with the same rank as a card on the table, you may transfer the attack to the next player, if they have enough cards. Choose left or right to place it.\n\n" +
                    "5. Take or defend — Beat each attack, or take all cards on the table. If all attacks are beaten, finish the round.\n\n" +
                    "6. Refill — Players draw back up to six cards. The attacker draws first and the defender last.\n\n" +
                    "7. Bluff — Bots may occasionally try an illegal extra card. Tap a suspicious, un-beaten table card to challenge it.\n\n" +
                    "8. Win — When the deck is empty, players with no cards leave. The last player holding cards loses."
                )
                Spacer(Modifier.height(14.dp))
                Divider()
                Spacer(Modifier.height(10.dp))
                Text("Developers", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Alan · 250444")
                Text("Shoxrux · 250272")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/")))
                    }) { Text("◎ Instagram") }
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/")))
                    }) { Text("➤ Telegram") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun GameScreen(
    state: GameState,
    revision: Int,
    message: String,
    onMenu: () -> Unit,
    onSettings: () -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
    onSelectCard: (Card) -> Unit,
    onTake: () -> Unit,
    onPass: () -> Unit,
    onChallenge: (Int) -> Unit,
    onRestart: () -> Unit
) {
    val legalCards = state.legalMoves(0).mapNotNull {
        when (it) {
            is Move.Attack -> it.card
            is Move.Defend -> it.card
            is Move.Transfer -> it.card
            else -> null
        }
    }.toSet()
    val orientation = LocalConfiguration.current.orientation
    val horizontal = orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onMenu) { Text("‹ Menu", color = White) }
            Spacer(Modifier.weight(1f))
            Text(
                "Trump: ${state.trump.symbol}",
                color = White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color.Black.copy(alpha = .25f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            (1 until state.hands.size).forEach { player ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val out = state.hands[player].isEmpty() && state.deckCount == 0
                    Box(Modifier.size(46.dp).background(Color.Black.copy(alpha = .35f), CircleShape)
                        .border(3.dp, if (state.actor == player) Gold else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text(if (out) "✓" else "🤖", fontSize = 22.sp)
                    }
                    Text(
                        "AI $player" + if (!state.over && state.actor == player) " · Turn" else
                            if (!state.over && state.attacker == player) " ⚔️" else
                            if (!state.over && state.defender == player) " 🛡️" else "",
                        color = White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy((-20).dp)) {
                        repeat(minOf(state.hands[player].size, 8)) { CardBack(30) }
                    }
                    Text("${state.hands[player].size}", color = White.copy(alpha = .85f), fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.deckCount > 0) {
                    Box {
                        CardBack(48)
                        state.faceUpTrump?.let { PlayingCard(it, 48, false) }
                    }
                    Text("Deck ${state.deckCount}", color = White, fontSize = 11.sp)
                } else Text("Deck empty", color = White, fontSize = 11.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Table · ${state.table.size}/${state.roundLimit}", color = White, fontWeight = FontWeight.Bold)
                state.table.chunked(if (horizontal) 5 else 3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { _, pair ->
                            val index = state.table.indexOf(pair)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.width(76.dp).height(105.dp)) {
                                    PlayingCard(pair.attack, 54, false,
                                        onClick = { onChallenge(index) },
                                        badge = if (pair.cheatedBy != null) "?" else null)
                                    pair.defense?.let {
                                        Box(Modifier.offset(x = 20.dp, y = 14.dp)) { PlayingCard(it, 54, false) }
                                    }
                                }
                                if (pair.transferSide != null) {
                                    Text(if (pair.transferSide == TransferSide.LEFT) "← Transfer" else "Transfer →",
                                        color = Gold, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
                if (state.table.any { it.cheatedBy != null }) {
                    Text("Tap a suspicious card (?) to challenge the bot", color = Gold, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))

        Text(
            when {
                state.over && state.durak == 0 -> "You are the Durak"
                state.over && state.durak == null -> "Draw"
                state.over -> "You win!"
                state.actor == 0 -> message.ifBlank { "Your turn" }
                else -> "Bot ${state.actor} is thinking…"
            },
            color = White,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            maxLines = 2
        )
        Spacer(Modifier.height(6.dp))

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
        Spacer(Modifier.height(6.dp))
                HandView(
            cards = Sorts.mergeSort(state.hands[0]) { state.sortKey(it) },
            legal = legalCards,
            enabled = state.actor == 0 && !state.over,
            landscape = horizontal,
            onPlay = onSelectCard
        )
    }
    if (state.over) AlertDialog(
        onDismissRequest = {},
        title = { Text(if (state.durak == 0) "You are the Durak!" else if (state.durak == null) "Draw" else "You win!") },
        text = { Text(if (state.durak == null) "The game ended in a draw." else "The game is over.") },
        confirmButton = { TextButton(onClick = onRestart) { Text("New game") } }
    )
}

@Composable
private fun HandView(
    cards: List<Card>,
    legal: Set<Card>,
    enabled: Boolean,
    landscape: Boolean,
    onPlay: (Card) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val count = cards.size.coerceAtLeast(1)
        val cardWidth = if (landscape && cards.size > 7) {
            (maxWidth.value / (count * 0.78f)).coerceIn(38f, 64f)
        } else 64f
        val gap = if (landscape && cards.size > 7) {
            ((maxWidth.value - cardWidth) / (count - 1).coerceAtLeast(1) - cardWidth).coerceIn(-8f, 14f)
        } else -20f

        Row(
            Modifier.fillMaxWidth()
                .then(if (!landscape || cards.size <= 7) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(gap.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            cards.forEach { card ->
                PlayingCard(
                    card = card,
                    width = cardWidth.toInt(),
                    enabled = enabled && card in legal,
                    onClick = { onPlay(card) },
                    onDragPlay = { onPlay(card) }
                )
            }
        }
    }
}

@Composable
private fun PlayingCard(
    card: Card,
    width: Int,
    enabled: Boolean,
    onClick: () -> Unit = {},
    onDragPlay: () -> Unit = onClick,
    badge: String? = null
) {
    val ink = if (card.suit.isRed) Color(0xFFD21F2B) else Color(0xFF171717)
    var dragX by remember(card.id) { mutableFloatStateOf(0f) }
    var dragY by remember(card.id) { mutableFloatStateOf(0f) }

    Box(
        Modifier.width(width.dp).height((width * 1.45f).dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White)
            .border(1.dp, Color.Black.copy(alpha = .35f), RoundedCornerShape(7.dp))
            .then(if (enabled) Modifier
                .clickable(onClick = onClick)
                .pointerInput(card.id) {
                    detectDragGestures(
                        onDragStart = { dragX = 0f; dragY = 0f },
                        onDragEnd = {
                            // Only a deliberate upward drag plays a card; horizontal scrolling won't.
                            if (dragY < -28f && abs(dragY) > abs(dragX) * 0.55f) onDragPlay()
                            dragX = 0f
                            dragY = 0f
                        },
                        onDragCancel = { dragX = 0f; dragY = 0f },
                        onDrag = { change, amount ->
                            dragX += amount.x
                            dragY += amount.y
                            change.consume()
                        }
                    )
                } else Modifier
            ).padding(5.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(card.rankLabel, color = ink, fontWeight = FontWeight.Bold, fontSize = (width * .22).sp)
                Text(card.suit.symbol, color = ink, fontSize = (width * .20).sp)
            }
            Spacer(Modifier.weight(1f))
            Text(card.suit.symbol, color = ink, fontSize = (width * .48).sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.weight(1f))
        }
        if (badge != null) {
            Text(badge, color = Color.White, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp)
                    .background(Color(0xFFB71C1C), CircleShape).padding(horizontal = 5.dp, vertical = 1.dp))
        }
    }
}

@Composable
private fun CardBack(width: Int) {
    Box(
        Modifier.width(width.dp).height((width * 1.45f).dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF354F9C), Color(0xFF101D50))))
            .border(1.dp, White.copy(alpha = .65f), RoundedCornerShape(6.dp))
    ) {
        Box(Modifier.fillMaxSize().padding(4.dp).border(1.dp, White.copy(alpha = .45f), RoundedCornerShape(4.dp)))
    }
}
