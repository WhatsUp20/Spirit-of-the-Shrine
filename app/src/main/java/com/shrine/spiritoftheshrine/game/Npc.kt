package com.shrine.spiritoftheshrine.game

import java.util.Locale

enum class NpcKind { VILLAGER, ELDER }

/** Who's speaking a given [DialogueLine] - picks the portrait and the name label shown above it. */
enum class Speaker { VILLAGER, ELDER, PLAYER }

data class DialogueLine(val speaker: Speaker, val ru: String, val en: String) {
    fun text(): String = if (Locale.getDefault().language == "ru") ru else en
}

/** Each NPC carries its own script now that there's more than one voice in the village. */
class Npc(val row: Float, val col: Float, val kind: NpcKind, val dialogue: List<DialogueLine>)

/** Short ambient lines for the background villagers - they bow, they don't have much to say. */
object VillagerDialogue {
    fun lines(): List<DialogueLine> = listOf(
        DialogueLine(Speaker.VILLAGER, "Добро пожаловать домой, путник.", "Welcome home, traveler."),
        DialogueLine(Speaker.VILLAGER, "Мы ждали тебя...", "We've been waiting for you..."),
        DialogueLine(Speaker.VILLAGER, "Старейшина знает больше, чем мы.", "The elder knows more than we do."),
    )
}

/** One villager (the rightmost one in the village) keeps the original warning about the
 * temple instead of the generic "welcome home" flavor lines. */
object WarningVillagerDialogue {
    fun lines(): List<DialogueLine> = listOf(
        DialogueLine(Speaker.VILLAGER, "Добро пожаловать в нашу деревню, путник.", "Welcome to our village, traveler."),
        DialogueLine(
            Speaker.VILLAGER,
            "Слышал, храм на юге охраняет злой дух...",
            "I've heard the temple to the south is guarded by an evil spirit...",
        ),
        DialogueLine(Speaker.VILLAGER, "Будь осторожен, если пойдёшь туда.", "Be careful if you go there."),
    )
}

/** The prologue's big reveal - the player's own lines are narrated the same way, alternating
 * speaker with the elder. */
object ElderDialogue {
    fun lines(): List<DialogueLine> = listOf(
        DialogueLine(Speaker.ELDER, "Добро пожаловать домой.", "Welcome home."),
        DialogueLine(Speaker.PLAYER, "Простите... мы знакомы?", "I'm sorry... do we know each other?"),
        DialogueLine(Speaker.ELDER, "Нет. Но ты уже был здесь.", "No. But you've been here before."),
        DialogueLine(Speaker.PLAYER, "Я не помню ничего.", "I don't remember anything."),
        DialogueLine(
            Speaker.ELDER,
            "Мы знаем. Ты сам попросил нас забрать твою память.",
            "We know. You asked us to take your memory away yourself.",
        ),
        DialogueLine(
            Speaker.ELDER,
            "Раз в несколько десятилетий на остров приходит человек из внешнего мира. Не по своей воле - духи приводят его.",
            "Once every few decades, someone from the outside world comes to this island. Not by their own will - the spirits bring them.",
        ),
        DialogueLine(
            Speaker.ELDER,
            "Этот человек становится Паломником Святилища. Его задача - восстановить равновесие между миром людей и миром духов.",
            "That person becomes the Pilgrim of the Shrine. Their task is to restore the balance between the world of people and the world of spirits.",
        ),
        DialogueLine(Speaker.ELDER, "Но никто не возвращается домой.", "But no one ever goes home."),
        DialogueLine(Speaker.PLAYER, "И я тоже?", "Me too?"),
        DialogueLine(Speaker.ELDER, "Ты уже возвращался.", "You already went back once."),
        DialogueLine(Speaker.PLAYER, "Тогда почему я снова здесь?", "Then why am I here again?"),
        DialogueLine(Speaker.ELDER, "Потому что у тебя... не получилось.", "Because you... failed."),
        DialogueLine(
            Speaker.ELDER,
            "В прошлый раз ты почти завершил ритуал. Не хватило одного шага.",
            "Last time, you nearly completed the ritual. One step was missing.",
        ),
        DialogueLine(
            Speaker.ELDER,
            "Но в последний момент ты сам отказался его завершать. И попросил духов стереть тебе память.",
            "But at the last moment, you refused to finish it yourself. And asked the spirits to erase your memory.",
        ),
        DialogueLine(Speaker.PLAYER, "Почему?", "Why?"),
        DialogueLine(
            Speaker.ELDER,
            "Никто не знает. Даже я. Когда ты уходил, ты сказал лишь одну фразу.",
            "No one knows. Not even me. When you left, you said only one thing.",
        ),
        DialogueLine(Speaker.PLAYER, "Какую?", "What was it?"),
        DialogueLine(
            Speaker.ELDER,
            "\"Если я вспомню - я снова сделаю тот же выбор.\"",
            "\"If I remember, I will make the same choice again.\"",
        ),
    )
}
