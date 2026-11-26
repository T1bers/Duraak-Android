package com.t1bers.duraak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Felt = Brush.radialGradient(listOf(Color(0xFF197344), Color(0xFF052F1C)))
private val Gold = Color(0xFFFFD54F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { DurakApp() } }
    }
}

@Composable
fun DurakApp() {
    var players by remember { mutableIntStateOf(2) }
    var difficulty by remember { mutableIntStateOf(1) }
    var game by remember { mutableStateOf<GameState?>(null) }
    var showInfo by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Felt)) {
        if (game == null) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Durak", fontSize = 54.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("vs Monte Carlo AI", color = Color.White.copy(alpha = .8f), fontSize = 17.sp)
                Spacer(Modifier.height(34.dp))
                Text("Соперники", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { n ->
                        ChoiceButton("$n", players == n + 1) { players = n + 1 }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text("Сложность", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Лёгкая", "Средняя", "Сложная").forEachIndexed { i, label ->
                        ChoiceButton(label, difficulty == i) { difficulty = i }
                    }
                }
                Spacer(Modifier.height(30.dp))
                Button(
                    onClick = { game = GameState.new(players) },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                    modifier = Modifier.width(210.dp).height(54.dp)
                ) { Text("Играть", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                TextButton(onClick = { showInfo = true }) { Text("Правила и информация", color = Color.White) }
            }
        } else {
            GameScreen(game!!, onExit = { game = null }, onRestart = { game = GameState.new(players) })
        }
        TextButton(
            onClick = { showInfo = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) { Text("ⓘ", color = Color.White, fontSize = 26.sp) }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Как играть") },
            text = {
                Text("Дурак — карточная игра для 2–4 игроков. У каждого по 6 карт. Атакующий кладёт карту, защищающийся бьёт её картой старше той же масти или козырем. Можно подкидывать карты того же достоинства, что уже есть на столе. Если не отбиться — бери карты. После раунда игроки добирают до шести карт: атакующий первым, защищающийся последним. Последний игрок с картами проигрывает.")
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Закрыть") } }
        )
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Gold else Color.Black.copy(alpha = .25f),
            contentColor = if (selected) Color.Black else Color.White
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
fun GameScreen(initial: GameState, onExit: () -> Unit, onRestart: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val state = initial
    LaunchedEffect(tick) {
        if (!state.over && state.actor != 0) {
            delay(650)
            state.botMove(state.actor)
            tick++
        } else if (!state.over && state.actor == 0 && state.table.isNotEmpty() &&
            state.legalAttacks(0).isEmpty() && state.undefendedCount == 0 && !state.defenderTaking) {
            delay(550)
            state.pass()
            tick++
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) { Text("‹ Меню", color = Color.White) }
            Spacer(Modifier.weight(1f))
            Text("Козырь: ${state.trump.symbol}", color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color.Black.copy(alpha = .25f), CircleShape).padding(horizontal = 14.dp, vertical = 7.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            (1 until state.hands.size).forEach { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🤖 Игрок $p", color = Color.White, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy((-18).dp)) {
                        repeat(minOf(state.hands[p].size, 6)) { CardBack(34) }
                    }
                    Text("${state.hands[p].size} карт", color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.deck.isNotEmpty()) {
                    CardBack(48)
                    Text("Колода ${state.deck.size}", color = Color.White, fontSize = 11.sp)
                } else {
                    Text("Колода пуста", color = Color.White, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Стол", color = Color.White.copy(alpha = .85f), fontWeight = FontWeight.Bold)
                state.table.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { pair ->
                            Row {
                                PlayingCard(pair.attack, 54)
                                if (pair.defense != null) {
                                    Spacer(Modifier.width((-20).dp))
                                    Box(Modifier.padding(top = 13.dp)) { PlayingCard(pair.defense!!, 54) }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(if (state.over) state.message else state.message + if (state.actor == 0) " · Твой ход" else " · Ход соперника",
            color = Color.White, textAlign = TextAlign.Center, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!state.over && state.actor == 0 && state.undefendedCount > 0 && !state.defenderTaking) {
                Button(onClick = { state.take(); tick++ }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726))) { Text("Взять") }
            }
            if (!state.over && state.actor == 0 && state.table.isNotEmpty() &&
                (state.undefendedCount == 0 || state.defenderTaking)) {
                Button(onClick = { state.pass(); tick++ }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) {
                    Text(if (state.defenderTaking) "Закончить атаку" else "Бито")
                }
            }
            if (state.over) {
                Button(onClick = onRestart, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) { Text("Ещё раз") }
                Button(onClick = onExit) { Text("Меню") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Твои карты · ${state.hands[0].size}", color = Color.White, fontWeight = FontWeight.Bold)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy((-18).dp),
            verticalAlignment = Alignment.Bottom
        ) {
            state.hands[0].sortedWith(compareBy<Card> { if (it.suit == state.trump) 4 else it.suit.ordinal }.thenBy { it.rank })
                .forEach { card ->
                    PlayingCard(card, 66, enabled = state.actor == 0 && !state.over) {
                        if (state.play(card)) tick++
                    }
                }
        }
    }
    if (state.over) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(state.message) },
            text = { Text(if (state.durak == 0) "В этот раз последним остался ты." else "Партия завершена.") },
            confirmButton = { TextButton(onClick = onRestart) { Text("Новая игра") } }
        )
    }
}

@Composable
fun PlayingCard(card: Card, width: Int, enabled: Boolean = false, onClick: () -> Unit = {}) {
    val color = if (card.suit.red) Color(0xFFD21F2B) else Color(0xFF171717)
    Column(
        Modifier.width(width.dp).height((width * 1.45f).dp)
            .background(Color.White, RoundedCornerShape(7.dp))
            .border(1.dp, Color.Black.copy(alpha = .35f), RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(card.rankLabel, color = color, fontWeight = FontWeight.Bold, fontSize = (width * .22).sp)
            Spacer(Modifier.width(2.dp))
            Text(card.suit.symbol, color = color, fontSize = (width * .20).sp)
        }
        Spacer(Modifier.weight(1f))
        Text(card.suit.symbol, color = color, fontSize = (width * .48).sp)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun CardBack(width: Int) {
    Box(
        Modifier.width(width.dp).height((width * 1.45f).dp)
            .background(Brush.linearGradient(listOf(Color(0xFF354F9C), Color(0xFF101D50))), RoundedCornerShape(6.dp))
            .border(1.dp, Color.White.copy(alpha = .65f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().padding(4.dp).border(1.dp, Color.White.copy(alpha = .45f), RoundedCornerShape(4.dp)))
    }
}
