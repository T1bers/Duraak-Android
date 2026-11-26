package com.t1bers.duraak

import kotlin.random.Random

enum class Suit(val symbol: String, val isRed: Boolean) {
    SPADES("♠", false), CLUBS("♣", false), DIAMONDS("♦", true), HEARTS("♥", true)
}

data class Card(val id: Int) {
    init { require(id in 0..35) }
    val suit: Suit get() = Suit.entries[id / 9]
    val rank: Int get() = id % 9 + 6
    val rankLabel: String get() = when (rank) {
        11 -> "J"; 12 -> "Q"; 13 -> "K"; 14 -> "A"; else -> rank.toString()
    }
    val label: String get() = rankLabel + suit.symbol
}

enum class TransferSide { LEFT, RIGHT }

data class TablePair(
    val attack: Card,
    val defense: Card? = null,
    val transferAnchorId: Int? = null,
    val transferSide: TransferSide? = null,
    val cheatedBy: Int? = null
)

sealed interface Move {
    data class Attack(val card: Card) : Move
    data class Defend(val tableIndex: Int, val card: Card) : Move
    data class Transfer(val tableIndex: Int, val card: Card, val side: TransferSide) : Move
    data class CheatAttack(val card: Card) : Move
    data object Take : Move
    data object Pass : Move
}

class GameState private constructor(
    val hands: MutableList<MutableList<Card>>,
    val deck: MutableList<Card>,
    val trump: Suit,
    val order: MutableList<Int>,
    val table: MutableList<TablePair> = mutableListOf(),
    val discard: Stack<Card> = Stack(),
    var defenderTaking: Boolean = false,
    var roundLimit: Int = 6,
    var over: Boolean = false,
    var durak: Int? = null,
    private var defenderPosition: Int = 1,
    private var trumpCardId: Int = -1,
    private var trumpCardAvailable: Boolean = true
) {
    private val tableRanks = IntHashTable()

    val attacker: Int get() = order.first()
    val defender: Int get() = order[defenderPosition.coerceIn(1, order.lastIndex)]
    val undefendedCount: Int get() = table.count { it.defense == null }
    val actor: Int get() = when {
        over || order.size < 2 -> -1
        defenderTaking -> attacker
        undefendedCount > 0 -> defender
        else -> attacker
    }
    val deckCount: Int get() = deck.size + if (trumpCardAvailable) 1 else 0
    val faceUpTrump: Card? get() = if (trumpCardAvailable) Card(trumpCardId) else null
    val playerCount: Int get() = hands.size

    fun beats(card: Card, attack: Card): Boolean =
        if (card.suit == attack.suit) card.rank > attack.rank
        else card.suit == trump && attack.suit != trump

    fun sortKey(card: Card): Int {
        val suitOrder = if (card.suit == trump) 3 else
            if (card.suit.ordinal < trump.ordinal) card.suit.ordinal else card.suit.ordinal - 1
        return suitOrder * 9 + card.rank - 6
    }

    fun legalMoves(player: Int = actor): List<Move> {
        if (over || player < 0 || order.size < 2) return emptyList()
        if (player == defender && undefendedCount > 0 && !defenderTaking) {
            val moves = mutableListOf<Move>()
            val index = table.indexOfFirst { it.defense == null }
            if (index >= 0) {
                val attack = table[index].attack
                hands[player].filter { beats(it, attack) }.forEach { moves.add(Move.Defend(index, it)) }
            }
            if (canTransferToNextDefender()) {
                val ranks = table.flatMap { listOfNotNull(it.attack.rank, it.defense?.rank) }.toSet()
                hands[player].filter { it.rank in ranks }.forEach { card ->
                    val anchor = table.indexOfFirst { it.attack.rank == card.rank || it.defense?.rank == card.rank }
                    if (anchor >= 0) {
                        moves.add(Move.Transfer(anchor, card, TransferSide.LEFT))
                        moves.add(Move.Transfer(anchor, card, TransferSide.RIGHT))
                    }
                }
            }
            moves.add(Move.Take)
            return moves
        }
        if (player == attacker) {
            val cards = if (table.isEmpty()) hands[player].toList() else {
                if (table.size >= roundLimit) emptyList()
                else hands[player].filter { tableRanks.contains(it.rank) }
            }
            return cards.map { Move.Attack(it) } + if (table.isEmpty()) emptyList() else listOf(Move.Pass)
        }
        return emptyList()
    }

    private fun canTransferToNextDefender(): Boolean {
        if (order.size < 3 || defenderPosition >= order.lastIndex) return false
        val nextDefender = order[defenderPosition + 1]
        return table.size < 6 && table.size + 1 <= hands[nextDefender].size
    }

    fun cheatCandidates(player: Int): List<Card> {
        if (over || player != attacker || table.isEmpty() || table.size >= roundLimit) return emptyList()
        return hands[player].filter { !tableRanks.contains(it.rank) }
    }

    fun apply(move: Move): Boolean {
        if (over) return false
        when (move) {
            is Move.Attack -> {
                if (move !in legalMoves(attacker)) return false
                hands[attacker].remove(move.card)
                table.add(TablePair(move.card))
                tableRanks.increment(move.card.rank)
            }
            is Move.Defend -> {
                if (move !in legalMoves(defender)) return false
                hands[defender].remove(move.card)
                table[move.tableIndex] = table[move.tableIndex].copy(defense = move.card)
                tableRanks.increment(move.card.rank)
            }
            is Move.Transfer -> {
                if (move !in legalMoves(defender)) return false
                val oldDefender = defender
                hands[oldDefender].remove(move.card)
                val anchorId = table[move.tableIndex].attack.id
                table.add(TablePair(move.card, transferAnchorId = anchorId, transferSide = move.side))
                tableRanks.increment(move.card.rank)
                defenderPosition++
                roundLimit = minOf(6, hands[defender].size)
            }
            is Move.CheatAttack -> {
                if (move.card !in cheatCandidates(attacker)) return false
                hands[attacker].remove(move.card)
                table.add(TablePair(move.card, cheatedBy = attacker))
                tableRanks.increment(move.card.rank)
            }
            Move.Take -> {
                if (actor != defender || Move.Take !in legalMoves(defender)) return false
                defenderTaking = true
            }
            Move.Pass -> {
                if (actor != attacker || Move.Pass !in legalMoves(attacker)) return false
                finishRound()
            }
        }
        return true
    }

    /** A successful tap on a bluff removes the false card and returns it to the bot. */
    fun challengeCheat(tableIndex: Int): Boolean {
        if (tableIndex !in table.indices) return false
        val pair = table[tableIndex]
        val cheater = pair.cheatedBy ?: return false
        if (pair.defense != null || cheater !in hands.indices) return false
        hands[cheater].add(pair.attack)
        table.removeAt(tableIndex)
        rebuildTableRanks()
        return true
    }

    fun finishIfNoMoreAttacks() {
        if (!over && actor == attacker && table.isNotEmpty() &&
            legalMoves(attacker).none { it is Move.Attack }) {
            apply(Move.Pass)
        }
    }

    private fun rebuildTableRanks() {
        tableRanks.clear()
        table.forEach { pair ->
            tableRanks.increment(pair.attack.rank)
            pair.defense?.let { tableRanks.increment(it.rank) }
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
                discard.push(pair.attack)
                pair.defense?.let { discard.push(it) }
            }
        }

        table.clear()
        tableRanks.clear()
        defenderTaking = false

        // Every player draws in turn order; the final defender draws last.
        val drawOrder = order.filter { it != oldDefender } + oldDefender
        drawOrder.forEach { player ->
            while (hands[player].size < 6 && (deck.isNotEmpty() || trumpCardAvailable)) {
                if (deck.isNotEmpty()) hands[player].add(deck.removeAt(deck.lastIndex))
                else {
                    hands[player].add(Card(trumpCardId))
                    trumpCardAvailable = false
                }
            }
        }

        // On a successful defense the current defender attacks next; on take, the next
        // player after the defender attacks. This also handles multiple transfers.
        val shift = (defenderPosition + if (wasTaking) 1 else 0) % order.size
        repeat(shift) { order.add(order.removeAt(0)) }
        defenderPosition = 1

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

    fun copyState(): GameState {
        val copiedHands: MutableList<MutableList<Card>> = hands.mapTo(mutableListOf()) {
            DynamicArray<Card>().apply { addAll(it) }
        }
        val copiedDeck: MutableList<Card> = DynamicArray<Card>().apply { addAll(deck) }
        val copiedDiscard = Stack<Card>().apply { discard.toList().forEach(::push) }
        val copy = GameState(
            copiedHands, copiedDeck, trump, order.toMutableList(),
            table.toMutableList(), copiedDiscard, defenderTaking, roundLimit,
            over, durak, defenderPosition, trumpCardId, trumpCardAvailable
        )
        copy.rebuildTableRanks()
        return copy
    }

    companion object {
        fun new(players: Int): GameState {
            require(players in 2..4)
            val shuffled = (0 until 36).map(::Card).toMutableList()
            Sorts.fisherYates(shuffled)
            val trumpCard = shuffled.removeAt(0)
            val trump = trumpCard.suit
            val hands: MutableList<MutableList<Card>> = MutableList(players) { DynamicArray<Card>() }
            repeat(6) { for (player in 0 until players) hands[player].add(shuffled.removeAt(shuffled.lastIndex)) }
            val drawPile: MutableList<Card> = DynamicArray<Card>().apply { addAll(shuffled) }
            var first = -1
            var best = Int.MAX_VALUE
            for (player in 0 until players) for (card in hands[player]) {
                if (card.suit == trump && card.rank < best) {
                    best = card.rank
                    first = player
                }
            }
            if (first < 0) first = Random.nextInt(players)
            val queue = CircularQueue<Int>(players)
            repeat(players) { offset -> queue.enqueue((first + offset) % players) }
            return GameState(hands, drawPile, trump, queue.toList().toMutableList()).also {
                it.trumpCardId = trumpCard.id
            }
        }
    }
}

/** Monte Carlo AI only samples unseen cards; it never reads opponents' real cards. */
object MonteCarloAI {
    fun choose(state: GameState, player: Int, budget: Int): Move {
        val moves = state.legalMoves(player)
        if (moves.size <= 1) return moves.firstOrNull() ?: Move.Pass
        val unseen = (0 until 36).map(::Card).filter { card ->
            state.hands[player].none { it.id == card.id } &&
                state.table.none { it.attack.id == card.id || it.defense?.id == card.id } &&
                state.discard.toList().none { it.id == card.id } &&
                state.faceUpTrump?.id != card.id
        }.toMutableList()
        val scores = DoubleArray(moves.size)
        val trials = budget.coerceIn(12, 240)
        repeat(trials) {
            val pool = unseen.shuffled().toMutableList()
            moves.forEachIndexed { index, move ->
                val simulation = determinize(state, player, pool)
                simulation.apply(move)
                scores[index] += playout(simulation, player)
            }
        }
        return moves.indices.maxByOrNull { scores[it] }?.let(moves::get) ?: moves.first()
    }

    private fun determinize(original: GameState, me: Int, shuffledPool: MutableList<Card>): GameState {
        val simulation = original.copyState()
        var cursor = 0
        for (player in simulation.hands.indices) if (player != me) {
            val count = simulation.hands[player].size
            simulation.hands[player].clear()
            repeat(count) { if (cursor < shuffledPool.size) simulation.hands[player].add(shuffledPool[cursor++]) }
        }
        simulation.deck.clear()
        repeat(original.deck.size) {
            if (cursor < shuffledPool.size) simulation.deck.add(shuffledPool[cursor++])
        }
        return simulation
    }

    private fun playout(start: GameState, me: Int): Double {
        val simulation = start.copyState()
        var steps = 0
        while (!simulation.over && steps++ < 300) {
            val actor = simulation.actor
            val moves = simulation.legalMoves(actor)
            if (moves.isEmpty()) break
            val chosen = when {
                moves.size == 1 -> moves[0]
                actor == simulation.defender && simulation.undefendedCount > 0 -> {
                    val defenses = moves.filterIsInstance<Move.Defend>()
                    defenses.minByOrNull {
                        if (it.card.suit == simulation.trump) 30 + it.card.rank else it.card.rank
                    } ?: Move.Take
                }
                else -> {
                    val attacks = moves.filterIsInstance<Move.Attack>()
                    attacks.minByOrNull {
                        it.card.rank + if (it.card.suit == simulation.trump) 20 else 0
                    } ?: Move.Pass
                }
            }
            simulation.apply(chosen)
            simulation.finishIfNoMoreAttacks()
        }
        if (!simulation.over) return 0.5
        return if (simulation.durak == me) 0.0 else 1.0
    }
}
