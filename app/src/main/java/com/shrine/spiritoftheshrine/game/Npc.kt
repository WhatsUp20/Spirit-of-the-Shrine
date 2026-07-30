package com.shrine.spiritoftheshrine.game

import java.util.Locale

class Npc(val row: Float, val col: Float)

/** Single hard-coded villager for now - revisit as a per-NPC data source once there's more than one. */
object NpcDialogue {
    private val linesRu = listOf(
        "Добро пожаловать в нашу деревню, путник.",
        "Слышал, храм на юге охраняет злой дух...",
        "Будь осторожен, если пойдёшь туда.",
    )
    private val linesEn = listOf(
        "Welcome to our village, traveler.",
        "I've heard the temple to the south is guarded by an evil spirit...",
        "Be careful if you go there.",
    )

    fun lines(): List<String> = if (Locale.getDefault().language == "ru") linesRu else linesEn
}
