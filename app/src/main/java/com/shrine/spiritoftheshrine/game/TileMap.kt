package com.shrine.spiritoftheshrine.game

enum class TileType(val walkable: Boolean) {
    GRASS(true),
    TREE(false),
    PALM(false),
    SAND(true),
    BAMBOO(false),
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
    NPC_WARNING_SPAWN,
    ELDER_SPAWN,
    POTION_PICKUP,
    KEY_PICKUP,
    TORII_LANDMARK,
    SHIPWRECK_DEBRIS,
    EXIT_TO_VILLAGE,
    EXIT_TO_BEACH,
    /** Where the player re-appears on the beach after walking back from the village - not the
     * same spot as PLAYER_SPAWN, which is the one-time shipwreck wake-up point. */
    BEACH_RETURN_SPAWN,
}

data class SpawnPoint(val marker: MarkerType, val row: Int, val col: Int)

/**
 * Beach - its own standalone location (not stitched onto the village map). This is where the
 * player washes ashore and the wake-up cutscene plays; a path breaks through the tree line at
 * the bottom and ends at an EXIT_TO_VILLAGE marker, which is what hands control over to the
 * village map (see [RAW_VILLAGE_MAP]). Walking back up that same path from the village re-enters
 * here at BEACH_RETURN_SPAWN, one tile north of the exit so arriving doesn't immediately bounce
 * back. One character = one tile. See [charToTile] / [charToMarker].
 */
private val RAW_BEACH_MAP = """
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
~~A#SSSSSSSSSSSSSSSSSSSSSSSSSS#A~~
~~A#SSSSSSSSSSSSSSSSSSSSSSSSSSAA~~
~~##SSSSSSSSSSSSSRSSSSSSSSSSSSAA~~
~~A#SSSSSSSSSSSSSSSSSSRSSSSSSS#A~~
~~##SSSSSSSSSSSSSSSSSSSSSSSSSS##~~
~~AASSSSSSSSRSSSPSSSSSSSSSSSSSAA~~
~~AASSSSSSSSSSSSSRSSSSSSSSSSSSAA~~
~~A#SSSSSSSSSRSSSSSSSSSSSRSSSSA#~~
~~A#SSRSSSSSSSSSSSSSSSSSSSSSSSAA~~
~~##SSSSSSSSSSSSSSSSSSSSSSSSSS##~~
~~AASSSSSSSSSSSSSSSSSSSSSSSSSSAA~~
~~#ASSSSSSSSSSS...SSSSSSSSSSSS#A~~
~~##SSSSSSSSSSS...SSSSSSSSSSSSAA~~
~~###AA##AAA##A...AAA#A##A#A#A#A~~
~~A##A#AA##A#AA...#AAAA#A#A#####~~
~~#############...##############~~
~~#############...##############~~
~~#############.I.##############~~
~~#############.Z.##############~~
~~##############################~~
~~##############################~~
~~##############################~~
""".trimIndent()

/**
 * Village, forest, dungeon and temple - unchanged from before except that the old sand cove is
 * gone (it's [RAW_BEACH_MAP] now): this map now opens right at the top of the bamboo-flanked
 * path, a few steps before the old moss-covered torii landmark. PLAYER_SPAWN here (one tile
 * south of the map's edge) is where the player is dropped off after leaving the beach; walking
 * back north one more step onto EXIT_TO_BEACH sends them back the way they came.
 */
private val RAW_VILLAGE_MAP = """
~~######MM..J...MM#######........#..#..............#~~
~~######MM..Y...MM##################################~~
~~######MM......MM#######....#......#.....#....##..#~~
~~######MM..O...MM#######.....#..........#.........#~~
~~######MM......MM#######..##.....##...#....#......#~~
~~######MM......MM#######...#.....#...#............#~~
~~######MM......MM#######......................#...#~~
~~#VVVVVVVVVVVVVVVVVVVVV#.....#.###....#......#.##.#~~
~~#VVVVVVVVNVVVVVVVVVVVV#...#..................#..##~~
~~#VHHHHVVVVHHVVVVVVVVVV##..#.....#.........#.....##~~
~~#VHHHHVVVVHHVVVVHHHVVV#....#...#.#.#......#......#~~
~~#VHHHHVVVVVVVVVVHHHVVV#....#......#...........#..#~~
~~#VVEVVVNVVVVVVVVHHHVVV#..#..#..........#...#.....#~~
~~#VVVVVVVVVVVVVVVVVVVVV#.#.....#.........##.......#~~
~~#VVVHHVVVVVVHHHVVVFVVV#....#..............#......#~~
~~#VVVHHVVVVVVHHHVVVVVVV#...#..........#...#####...#~~
~~#VVVVVVVVVVVVVVVVVVVVV#.#.#....#................##~~
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
    'A' -> TileType.PALM
    'S', 'P', 'R' -> TileType.SAND
    'M' -> TileType.BAMBOO
    'V', 'N', 'E', 'F' -> TileType.VILLAGE_FLOOR
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
    'F' -> MarkerType.NPC_WARNING_SPAWN
    'E' -> MarkerType.ELDER_SPAWN
    'L' -> MarkerType.POTION_PICKUP
    'K' -> MarkerType.KEY_PICKUP
    'O' -> MarkerType.TORII_LANDMARK
    'R' -> MarkerType.SHIPWRECK_DEBRIS
    'Z' -> MarkerType.EXIT_TO_VILLAGE
    'J' -> MarkerType.EXIT_TO_BEACH
    'Y' -> MarkerType.PLAYER_SPAWN
    'I' -> MarkerType.BEACH_RETURN_SPAWN
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
        private fun parse(raw: String): TileMap {
            val rows = raw.lines()
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

        fun loadBeach(): TileMap = parse(RAW_BEACH_MAP)
        fun loadVillage(): TileMap = parse(RAW_VILLAGE_MAP)
    }
}
