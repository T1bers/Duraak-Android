package com.t1bers.duraak

import kotlin.random.Random

enum class Suit(val symbol: String, val isRed: Boolean) {
    SPADES("♠", false), CLUBS("♣", false), DIAMONDS("♦", true), HEARTS("♥", true)
}

data class Card(val id: Int) {
    val suit: Suit get() = Suit.entries[id / 9]
    val rank: Int get() = id % 9 + 6
    val rankLabel: String get() = when (rank) {
        11 -> "J"; 12 -> "Q"; 13 -> "K"; 14 -> "A"; else -> rank.toString()
    }
    val label: String get() = rankLabel + suit.symbol
}

data class TablePair(val attack: Card, val defense: Card? = null)
sealed interface Move {
    data class Attack(val card: Card) : Move
    data class Defend(val tableIndex: Int, val card: Card) : Move
    data object Take : Move
    data object Pass : Move
}

class GameState private constructor(
    val hands: MutableList<MutableList<Card>>,
    val deck: MutableList<Card>,
    val trump: Suit,
    val order: MutableList<Int>,
    val table: MutableList<TablePair> = mutableListOf(),
    val discard: MutableList<Card> = mutableListOf(),
    var defenderTaking: Boolean = false,
    var roundLimit: Int = 6,
    var over: Boolean = false,
    var durak: Int? = null
) {
    val attacker: Int get() = order.first()
    val defender: Int get() = order[1]
    val undefendedCount: Int get() = table.count { it.defense == null }
    val actor: Int get() = when {
        over -> -1
        defenderTaking -> attacker
        undefendedCount > 0 -> defender
        else -> attacker
    }
    val deckCount: Int get() = deck.size + if (trumpCardAvailable) 1 else 0
    val faceUpTrump: Card? get() = if (trumpCardAvailable) Card(trumpCardId) else null

    fun beats(card: Card, attack: Card): Boolean =
        if (card.suit == attack.suit) card.rank > attack.rank
        else card.suit == trump && attack.suit != trump

    fun legalMoves(player: Int = actor): List<Move> {
        if (over || player < 0 || order.size < 2) return emptyList()
        if (player == defender && undefendedCount > 0 && !defenderTaking) {
            val index = table.indexOfFirst { it.defense == null }
            val attack = table[index].attack
            return hands[player].filter { beats(it, attack) }.map { Move.Defend(index, it) } + Move.Take
        }
        if (player == attacker) {
            val cards = if (table.isEmpty()) hands[player].toList() else {
                if (table.size >= roundLimit) emptyList()
                else {
                    val ranks = table.flatMap { listOfNotNull(it.attack.rank, it.defense?.rank) }.toSet()
                    hands[player].filter { it.rank in ranks }
                }
            }
            return cards.map { Move.Attack(it) } + if (table.isEmpty()) emptyList() else listOf(Move.Pass)
        }
        return emptyList()
    }

    fun apply(move: Move) {
        if (over) return
        when (move) {
            is Move.Attack -> {
                if (actor != attacker || move.card !in hands[attacker] ||
                    legalMoves(attacker).none { it == move }) return
                hands[attacker].remove(move.card)
                table.add(TablePair(move.card))
            }
            is Move.Defend -> {
                if (actor != defender || move.card !in hands[defender] ||
                    legalMoves(defender).none { it == move }) return
                hands[defender].remove(move.card)
                table[move.tableIndex] = table[move.tableIndex].copy(defense = move.card)
            }
            Move.Take -> {
                if (actor == defender && legalMoves(defender).contains(Move.Take)) defenderTaking = true
            }
            Move.Pass -> {
                if (actor == attacker && legalMoves(attacker).contains(Move.Pass)) finishRound()
            }
        }
    }

    fun finishIfNoMoreAttacks() {
        if (!over && actor == attacker && table.isNotEmpty() && legalMoves(attacker).none { it is Move.Attack }) {
            apply(Move.Pass)
        }
    }

    private fun finishRound() {
        val oldAttacker = attacker
        val oldDefender = defender
        val wasTaking = defenderTaking
        if (wasTaking) {
            table.forEach { pair ->
                hands[oldDefender].add(pair.attack)
                pair.defense?.let { hands[oldDefender].add(it) }
            }
        } else {
            table.forEach { pair ->
                discard.add(pair.attack)
                pair.defense?.let { discard.add(it) }
            }
        }
        table.clear()
        defenderTaking = false

        // The face-up trump is kept separately and drawn only after the hidden deck is empty.
        val drawOrder = mutableListOf(oldAttacker)
        for (i in 2 until order.size) drawOrder.add(order[i])
        drawOrder.add(oldDefender)
        drawOrder.forEach { p ->
            while (hands[p].size < 6 && (deck.isNotEmpty() || trumpCardAvailable)) {
                if (deck.isNotEmpty()) hands[p].add(deck.removeAt(deck.lastIndex))
                else {
                    hands[p].add(Card(trumpCardId))
                    trumpCardAvailable = false
                }
            }
        }

        val shift = if (wasTaking) 2 else 1
        repeat(minOf(shift, order.size)) { order.add(order.removeAt(0)) }
        if (deck.isEmpty() && !trumpCardAvailable) {
            order.removeAll { hands[it].isEmpty() }
        }
        if (order.size <= 1) {
            over = true
            durak = order.firstOrNull()
        } else {
            roundLimit = minOf(6, hands[order[1]].size)
        }
    }

    // The trump card is the first card of the shuffled deck and is always the last card drawn.
    private var trumpCardId: Int = -1
    private var trumpCardAvailable: Boolean = true

    fun copyState(): GameState {
        val copy = GameState(
            hands.map { it.toMutableList() }.toMutableList(),
            deck.toMutableList(), trump, order.toMutableList(),
            table.toMutableList(), discard.toMutableList(),
            defenderTaking, roundLimit, over, durak
        )
        copy.trumpCardId = trumpCardId
        copy.trumpCardAvailable = trumpCardAvailable
        return copy
    }

    companion object {
        fun new(players: Int): GameState {
            require(players in 2..4)
            val shuffled = (0 until 36).map(::Card).shuffled().toMutableList()
            val trumpCard = shuffled.removeAt(0)
            val trump = trumpCard.suit
            val hands = MutableList(players) { mutableListOf<Card>() }
            repeat(6) { for (p in 0 until players) hands[p].add(shuffled.removeAt(shuffled.lastIndex)) }
            var first = -1
            var best = Int.MAX_VALUE
            for (p in 0 until players) for (card in hands[p]) {
                if (card.suit == trump && card.rank < best) { best = card.rank; first = p }
            }
            if (first < 0) first = Random.nextInt(players)
            return GameState(hands, shuffled, trump, MutableList(players) { (first + it) % players }).also {
                it.trumpCardId = trumpCard.id
            }
        }
    }
}

/** Imperfect-information Monte Carlo opponent: unknown cards are randomized for every simulation. */
object MonteCarloAI {
    fun choose(state: GameState, player: Int, budget: Int): Move {
        val moves = state.legalMoves(player)
        if (moves.size <= 1) return moves.firstOrNull() ?: Move.Pass
        val unseen = (0 until 36).map(::Card).filter { candidate ->
            state.hands[player].none { it.id == candidate.id } &&
                state.table.none { it.attack.id == candidate.id || it.defense?.id == candidate.id } &&
                state.discard.none { it.id == candidate.id } &&
                state.faceUpTrump?.id != candidate.id
        }.toMutableList()
        val wins = DoubleArray(moves.size)
        val trials = budget.coerceIn(12, 240)
        repeat(trials) {
            val shuffled = unseen.shuffled().toMutableList()
            moves.forEachIndexed { index, move ->
                val sim = determinize(state, player, shuffled)
                sim.apply(move)
                wins[index] += playout(sim, player)
            }
        }
        return moves.indices.maxByOrNull { wins[it] }?.let { moves[it] } ?: moves.first()
    }

    private fun determinize(original: GameState, me: Int, pool: MutableList<Card>): GameState {
        val s = original.copyState()
        var cursor = 0
        for (p in s.hands.indices) if (p != me) {
            val count = s.hands[p].size
            s.hands[p].clear()
            repeat(count) { if (cursor < pool.size) s.hands[p].add(pool[cursor++]) }
        }
        s.deck.clear()
        // Keep the real remaining deck size, but not its order or identities.
        val needed = original.deck.size
        repeat(needed) { if (cursor < pool.size) s.deck.add(pool[cursor++]) }
        return s
    }

    private fun playout(start: GameState, me: Int): Double {
        val s = start.copyState()
        var steps = 0
        while (!s.over && steps++ < 220) {
            val actor = s.actor
            val moves = s.legalMoves(actor)
            if (moves.isEmpty()) break
            val chosen = when {
                moves.size == 1 -> moves[0]
                actor == s.defender && s.undefendedCount > 0 -> {
                    val defenses = moves.filterIsInstance<Move.Defend>()
                    defenses.minByOrNull { if (it.card.suit == s.trump) 30 + it.card.rank else it.card.rank }
                        ?: Move.Take
                }
                else -> {
                    val attacks = moves.filterIsInstance<Move.Attack>()
                    if (attacks.isNotEmpty()) attacks.minBy { it.card.rank + if (it.card.suit == s.trump) 20 else 0 }
                    else Move.Pass
                }
            }
            s.apply(chosen)
            s.finishIfNoMoreAttacks()
        }
        if (!s.over) return 0.5
        return if (s.durak == me) 0.0 else 1.0
    }
}
