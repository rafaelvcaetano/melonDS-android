package me.magnum.melonds.ui.emulator.autoflute

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stores flute songs as small JSON files, one per song:
 *
 * ```
 * { "version": 1, "name": "Song of Healing", "events": [[timeMs, type, x, y], ...] }
 * ```
 * where `type` is 0 = stylus down, 1 = stylus move, 2 = stylus up. The files can be copied between devices.
 */
class FluteSongStore(private val directory: File) {
    private companion object {
        const val CALIBRATION_FILE = "calibration.dat"
    }

    fun getSongs(): List<FluteSong> {
        val files = directory.listFiles { file -> file.isFile && file.extension == "json" } ?: return emptyList()
        return files.mapNotNull { file -> runCatching { readSong(file) }.getOrNull() }.sortedBy { it.name.lowercase() }
    }

    fun save(name: String, events: List<FluteEvent>): FluteSong {
        directory.mkdirs()

        val baseName = name.replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().ifEmpty { "song" }
        var id = baseName
        var suffix = 2
        while (File(directory, "$id.json").exists()) {
            id = "$baseName ($suffix)"
            suffix++
        }

        val song = FluteSong(id, name.trim().ifEmpty { baseName }, events)
        File(directory, "$id.json").writeText(toJson(song).toString())
        return song
    }

    fun delete(song: FluteSong) {
        File(directory, "${song.id}.json").delete()
    }

    /**
     * Pipe calibration (which touch position makes which pitch). Not stored as .json so it isn't mistaken for a song.
     */
    fun loadCalibration(): FluteCalibration? {
        return runCatching {
            val pipes = JSONObject(File(directory, CALIBRATION_FILE).readText()).getJSONArray("pipes")
            val points = List(pipes.length()) {
                val p = pipes.getJSONArray(it)
                FluteTouchPoint(p.getInt(0).coerceIn(0, 255), p.getInt(1).coerceIn(0, 191), p.getDouble(2))
            }
            points.takeIf { it.isNotEmpty() }?.let { FluteCalibration(it) }
        }.getOrNull()
    }

    fun saveCalibration(calibration: FluteCalibration) {
        directory.mkdirs()
        val pipes = JSONArray()
        calibration.points.forEach { pipes.put(JSONArray().put(it.x).put(it.y).put(it.semitone)) }
        File(directory, CALIBRATION_FILE).writeText(JSONObject().put("version", 1).put("pipes", pipes).toString())
    }

    private fun readSong(file: File): FluteSong {
        val json = JSONObject(file.readText())
        val eventsJson = json.getJSONArray("events")
        val types = FluteEvent.Type.entries
        val events = List(eventsJson.length()) {
            val e = eventsJson.getJSONArray(it)
            FluteEvent(e.getLong(0), types[e.getInt(1)], e.getInt(2).coerceIn(0, 255), e.getInt(3).coerceIn(0, 191))
        }
        return FluteSong(file.nameWithoutExtension, json.optString("name", file.nameWithoutExtension), events)
    }

    private fun toJson(song: FluteSong): JSONObject {
        val events = JSONArray()
        song.events.forEach {
            events.put(JSONArray().put(it.timeMs).put(it.type.ordinal).put(it.x).put(it.y))
        }
        return JSONObject()
            .put("version", 1)
            .put("name", song.name)
            .put("events", events)
    }
}
