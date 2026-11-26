package com.t1bers.duraak

import kotlin.random.Random

enum class Suit(val symbol: String, val red: Boolean) {
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

data class TablePair(val attack: Card, var defense: Card? = null)

class GameState private constructor(
    val hands: MutableList<MutableList<Card>>,
    val deck: MutableList<Card>,
    val trump: Suit,
    val order: MutableList<Int>
) {
    val table = mutableListOf<TablePair>()
    val discard = mutableListOf<Card>()
    var defenderTaking = false
    var roundLimit = 6
    var over = false
    var durak: Int? = null
    var message = "Твой ход"

    init { roundLimit = minOf(6, hands[defender].size) }

    val attacker: Int get() = order[0]
    val defender: Int get() = order[1]
    val undefendedCount: Int get() = table.count { it.defense == null }
    val actor: Int get() = when {
        over -> -1
        defenderTaking -> attacker
        undefendedCount > 0 -> defender
        else -> attacker
    }

    fun beats(card: Card, attack: Card): Boolean =
        if (card.suit == attack.suit) card.rank > attack.rank
        else card.suit == trump && attack.suit != trump

    fun legalAttacks(player: Int = attacker): List<Card> {
        if (table.isEmpty()) return hands[player].toList()
        if (table.size >= roundLimit) return emptyList()
        val ranks = table.flatMap { listOfNotNull(it.attack.rank, it.defense?.rank) }.toSet()
        return hands[player].filter { it.rank in ranks }
    }

    fun canDefend(player: Int = defender): List<Pair<Int, Card>> {
        val i = table.indexOfFirst { it.defense == null }
        if (i < 0) return emptyList()
        return hands[player].filter { beats(it, table[i].attack) }.map { i to it }
    }

    fun play(card: Card): Boolean {
        if (over || actor != 0) return false
        return if (actor == defender && undefendedCount > 0) {
            val i = table.indexOfFirst { it.defense == null }
            if (!beats(card, table[i].attack) || card !in hands[defender]) false
            else {
                hands[defender].remove(card)
                table[i].defense = card
                message = "Ты отбился картой ${card.label}"
                true
            }
        } else {
            if (card !in legalAttacks(attacker)) false
            else {
                hands[attacker].remove(card)
                table.add(TablePair(card))
                message = "Ты атаковал картой ${card.label}"
                true
            }
        }
    }

    fun take() {
        if (actor != 0 || undefendedCount == 0 || over) return
        defenderTaking = true
        message = "Ты берёшь карты"
    }

    fun pass() {
        if (actor != 0 || over) return
        if (table.isEmpty()) return
        finishRound()
    }

    fun botMove(player: Int) {
        if (over || actor != player) return
        if (player == defender && undefendedCount > 0 && !defenderTaking) {
            val options = canDefend(player)
            if (options.isNotEmpty()) {
                val chosen = options.minByOrNull { (_, c) -> if (c.suit == trump) 100 + c.rank else c.rank }!!
                hands[player].remove(chosen.second)
                table[chosen.first].defense = chosen.second
                message = "Игрок $player отбился"
            } else {
                defenderTaking = true
                message = "Игрок $player берёт карты"
            }
        } else {
            val options = legalAttacks(player)
            if (options.isNotEmpty() && (table.isEmpty() || Random.nextFloat() < 0.65f)) {
                val c = options.minByOrNull { it.rank + if (it.suit == trump) 20 else 0 }!!
                hands[player].remove(c)
                table.add(TablePair(c))
                message = "Игрок $player атакует"
            } else {
                finishRound()
            }
        }
    }

    private fun finishRound() {
        val a = attacker
        val d = defender
        if (defenderTaking) {
            table.forEach {
                hands[d].add(it.attack)
                it.defense?.let(hands[d]::add)
            }
        } else {
            table.forEach {
                discard.add(it.attack)
                it.defense?.let(discard::add)
            }
        }
        table.clear()
        defenderTaking = false

        val drawOrder = mutableListOf(a)
        for (i in 2 until order.size) drawOrder.add(order[i])
        drawOrder.add(d)
        drawOrder.forEach { p ->
            while (hands[p].size < 6 && deck.isNotEmpty()) hands[p].add(deck.removeAt(deck.lastIndex))
        }

        if (order.isNotEmpty()) {
            val shift = if (defenderTaking) 2 else 1
            repeat(minOf(shift, order.size)) { order.add(order.removeAt(0)) }
        }
        if (deck.isEmpty()) order.removeAll { hands[it].isEmpty() }
        if (order.size <= 1) {
            over = true
            durak = order.firstOrNull()
            message = if (durak == 0) "Ты — дурак!" else if (durak == null) "Ничья" else "Ты победил!"
        } else {
            roundLimit = minOf(6, hands[order[1]].size)
            message = if (defenderTaking) "Карты взяты" else "Все карты отбиты"
        }
    }

    companion object {
        fun new(players: Int): GameState {
            val deck = (0 until 36).map(::Card).shuffled().toMutableList()
            val trump = deck.first().suit
            val hands = MutableList(players) { mutableListOf<Card>() }
            repeat(6) { for (p in 0 until players) hands[p].add(deck.removeAt(deck.lastIndex)) }
            var first = -1
            var best = 99
            for (p in 0 until players) for (c in hands[p]) {
                if (c.suit == trump && c.rank < best) { best = c.rank; first = p }
            }
            if (first < 0) first = Random.nextInt(players)
            return GameState(hands, deck, trump, MutableList(players) { (first + it) % players })
        }
    }
}
