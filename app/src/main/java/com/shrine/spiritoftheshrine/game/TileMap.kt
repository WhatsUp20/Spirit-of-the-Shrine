package com.shrine.spiritoftheshrine.game

enum class TileType(val walkable: Boolean) {
    GRASS(true),
    TREE(false),
    VILLAGE_FLOOR(true),
    HOUSE(false),
    DUNGEON_FLOOR(true),
    DUNGEON_WALL(false),
    TEMPLE_FLOOR(true),
    TEMPLE_WALL(false),
    TEMPLE_GATE(false),
    WATER(false),
}

enum class MarkerType {
    PLAYER_SPAWN,
    CHEST,
    SLIME_SPAWN,
    SPIRIT_SPAWN,
    BOSS_SPAWN,
    NPC_SPAWN,
    POTION_PICKUP,
    KEY_PICKUP,
}

data class SpawnPoint(val marker: MarkerType, val row: Int, val col: Int)

/**
 * Hand-authored 54x54 world: a 2-tile ocean ring wraps the original 50x50 island (village
 * top-left, forest connecting corridors, a small dungeon room and a temple room with a
 * locked gate). One character = one tile. See [charToTile] / [charToMarker] for the legend.
 */
private val RAW_MAP = """
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
~~##################################################~~
~~#.................#........#......#.....#....##..#~~
~~#.VVVVVVVVVVVVVVVVVV........#..........#.........#~~
~~#.VHHHVVVHHHVVVHHHVV.....##.....##...#....#......#~~
~~#.VHHHVVVHHHVVVHHHVV......#.....#...#............#~~
~~#.VVVVVVVVVVVVVVVVVV.#.......................#...#~~
~~#.VVVVVVVVVVVVVVVVVV......#.#.#..................#~~
~~#.VVVVVVVVVVVVVVVVVV#..........#...............#.#~~
~~#.VVVVVVVVVVVVVVVVVV#..........#..#..............#~~
~~#.VVHHHVVVVVVVHHHVVV........#.###....#......#.##.#~~
~~#.VVHHHVVVVVVVHHHVVV..#...#..................#..##~~
~~#.VVVVVVVVPVVNVVVVVV...#..#.....#.........#.....##~~
~~#.VVVVVVVVVVVVVVVVVV.......#...#.#.#......#......#~~
~~#.VVVVVVVVVVVVVVVVVV.......#......#...........#..#~~
~~#......#....#............#..#..........#...#.....#~~
~~#..#......#..##.........#.....#.........##.......#~~
~~#.........#................#..............#......#~~
~~#.#........#..............#..........#...#####...#~~
~~#.#.....#.....#.#.......#.#....#................##~~
~~##....#............#D...###.........XXXXXXXXXXX..#~~
~~#............#............#.........DDDDDDDDDDX..#~~
~~#.#.......#...#....#.......#........XDDDDDDDDDX..#~~
~~#......#..#....#......#.#.#........#XDDDCDDDDDX..#~~
~~#.........##.#..............#...#.#.XDDDDDDDDDX..#~~
~~#......#.....#......####.......#...#XDDDDDsDDDX..#~~
~~#....................#.....#.#.....#XDDqDDDDDDX..#~~
~~#.....#...#..###.......#.#..#.#.....XDDDDDDKDDX..#~~
~~#........##.........#..........#....XXXXXXXXXXX.##~~
~~#.....#....#.#......#.#............#....#....##..#~~
~~#...#...#......##........#........##.#.......#...#~~
~~#.....#....#..#..................#.....#.........#~~
~~#....#...........#..........##....#.#..........###~~
~~#...#.#..............#......##..............#..#.#~~
~~#..##..#.....#..............#.............#...##.#~~
~~#......#.....#..#................#.....#.........#~~
~~#.#.....#.........#.#..................#.#.......#~~
~~#......#...##.#....##....#.###...................#~~
~~#...#...#.#.....#.#...#.......#.#.#..#.........#.#~~
~~##..#WWWWWGGGWWWWWW......#..#.......##...#.#..#..#~~
~~#....WTTTTTTTTTTTTW....#............#........#...#~~
~~#.##.WTTTTTTTTTTTTW.......................#..#..##~~
~~##...WTTTTTTTTTTTTW....#..#.......##......#.....##~~
~~#...#WTTTTTTTTTTTTW.........#....#.#...#..#...#..#~~
~~#....WTTTTTTTTTTTTW#.............#.............L.#~~
~~#...#WTTTTTTTTTTTTW....#..................##..##.#~~
~~#..##WTTTTTBTTTTTTW#....#..#.#........#..........#~~
~~#....WTTTTTTTTTTTTW.#.........................#..#~~
~~#....WWWWWWWWWWWWWW.#...#................#..##...#~~
~~##............##..#....##..#..##......##...#.#...#~~
~~##################################################~~
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
""".trimIndent()

private fun charToTile(c: Char): TileType = when (c) {
    '#' -> TileType.TREE
    'V', 'P', 'N' -> TileType.VILLAGE_FLOOR
    'H' -> TileType.HOUSE
    'D', 'C', 's', 'q', 'K' -> TileType.DUNGEON_FLOOR
    'X' -> TileType.DUNGEON_WALL
    'T', 'B' -> TileType.TEMPLE_FLOOR
    'W' -> TileType.TEMPLE_WALL
    'G' -> TileType.TEMPLE_GATE
    '~' -> TileType.WATER
    else -> TileType.GRASS
}

private fun charToMarker(c: Char): MarkerType? = when (c) {
    'P' -> MarkerType.PLAYER_SPAWN
    'C' -> MarkerType.CHEST
    's' -> MarkerType.SLIME_SPAWN
    'q' -> MarkerType.SPIRIT_SPAWN
    'B' -> MarkerType.BOSS_SPAWN
    'N' -> MarkerType.NPC_SPAWN
    'L' -> MarkerType.POTION_PICKUP
    'K' -> MarkerType.KEY_PICKUP
    else -> null
}

class TileMap private constructor(
    val width: Int,
    val height: Int,
    private val tiles: Array<TileType>,
    val spawnPoints: List<SpawnPoint>,
) {
    fun tileAt(row: Int, col: Int): TileType {
        if (row < 0 || row >= height || col < 0 || col >= width) return TileType.WATER
        return tiles[row * width + col]
    }

    fun isWalkable(row: Int, col: Int): Boolean = tileAt(row, col).walkable

    companion object {
        fun load(): TileMap {
            val rows = RAW_MAP.lines()
            val height = rows.size
            val width = rows.first().length
            val tiles = Array(width * height) { TileType.GRASS }
            val spawns = mutableListOf<SpawnPoint>()
            for (row in 0 until height) {
                val line = rows[row]
                for (col in 0 until width) {
                    val c = line[col]
                    tiles[row * width + col] = charToTile(c)
                    charToMarker(c)?.let { spawns.add(SpawnPoint(it, row, col)) }
                }
            }
            return TileMap(width, height, tiles, spawns)
        }
    }
}
