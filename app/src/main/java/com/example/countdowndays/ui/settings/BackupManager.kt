package com.example.countdowndays.ui.settings

import android.content.Context
import android.net.Uri
import com.example.countdowndays.data.CategoryEntity
import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.data.EventRepository
import com.example.countdowndays.data.EventWithNodes
import com.example.countdowndays.data.TimelineNodeEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 本地备份：把事件、时间线、分类和配图打成一个 zip，走系统文件选择器保存 / 恢复。
 * 只在本机生成文件，不涉及任何服务器。
 */
object BackupManager {

    private const val ENTRY_EVENTS = "events.json"
    private const val ENTRY_CATEGORIES = "categories.json"
    private const val IMAGE_DIR = "images/"

    suspend fun export(context: Context, repo: EventRepository, uri: Uri): Boolean = runCatching {
        val events = repo.getAllEvents()
        val categories = repo.getCategories()

        context.contentResolver.openOutputStream(uri)?.use { stream ->
            ZipOutputStream(stream).use { zip ->
                val eventArray = JSONArray()
                events.forEach { item ->
                    val e = item.event
                    val imageName = e.imagePath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            zip.putNextEntry(ZipEntry(IMAGE_DIR + file.name))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                            file.name
                        } else null
                    }
                    eventArray.put(e.toJson(imageName, item.nodes))
                }
                zip.putNextEntry(ZipEntry(ENTRY_EVENTS))
                zip.write(eventArray.toString().toByteArray())
                zip.closeEntry()

                val catArray = JSONArray()
                categories.forEach { c ->
                    catArray.put(
                        JSONObject().apply {
                            put("id", c.id)
                            put("name", c.name)
                            put("colorIndex", c.colorIndex)
                            put("sortOrder", c.sortOrder)
                        }
                    )
                }
                zip.putNextEntry(ZipEntry(ENTRY_CATEGORIES))
                zip.write(catArray.toString().toByteArray())
                zip.closeEntry()
            }
        } ?: return false
        true
    }.getOrDefault(false)

    suspend fun import(context: Context, repo: EventRepository, uri: Uri): Int = runCatching {
        var eventsJson: String? = null
        var categoriesJson: String? = null
        val images = mutableMapOf<String, File>()
        val imageDir = File(context.filesDir, "images").apply { mkdirs() }

        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == ENTRY_EVENTS -> eventsJson = zip.readBytes().toString(Charsets.UTF_8)
                        name == ENTRY_CATEGORIES -> categoriesJson = zip.readBytes().toString(Charsets.UTF_8)
                        name.startsWith(IMAGE_DIR) -> {
                            val target = File(imageDir, File(name).name)
                            target.outputStream().use { out -> zip.copyTo(out) }
                            images[File(name).name] = target
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val eventJson = eventsJson ?: return 0
        val events = mutableListOf<EventWithNodes>()
        val arr = JSONArray(eventJson)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val imageName = if (obj.isNull("imageName")) null else obj.optString("imageName")
            val resolved = imageName?.let { images[File(it).name]?.absolutePath }
            events += obj.toEventWithNodes(resolved)
        }

        val categories = mutableListOf<CategoryEntity>()
        categoriesJson?.let { json ->
            val catArr = JSONArray(json)
            for (i in 0 until catArr.length()) {
                val o = catArr.getJSONObject(i)
                categories += CategoryEntity(
                    id = o.optLong("id"),
                    name = o.optString("name"),
                    colorIndex = o.optInt("colorIndex"),
                    sortOrder = o.optInt("sortOrder")
                )
            }
        }

        if (events.isEmpty()) return 0
        repo.importData(events, categories)
        events.size
    }.getOrDefault(0)

    private fun EventEntity.toJson(imageName: String?, nodes: List<TimelineNodeEntity>): JSONObject =
        JSONObject().apply {
            put("name", name)
            put("description", description)
            put("date", date)
            put("imageName", imageName ?: JSONObject.NULL)
            put("isPinned", isPinned)
            put("createdAt", createdAt)
            put("repeatType", repeatType)
            put("repeatEndDate", repeatEndDate ?: JSONObject.NULL)
            put("isLunar", isLunar)
            put("lunarMonth", lunarMonth)
            put("lunarDay", lunarDay)
            put("categoryId", categoryId ?: JSONObject.NULL)
            put("archived", archived)
            put("colorIndex", colorIndex)
            put("sortOrder", sortOrder)
            put("reminderEnabled", reminderEnabled)
            put("reminderAdvanceDays", reminderAdvanceDays)
            put("reminderHour", reminderHour)
            put("reminderMinute", reminderMinute)
            put(
                "nodes",
                JSONArray().apply {
                    nodes.forEach { n ->
                        put(
                            JSONObject().apply {
                                put("name", n.name)
                                put("time", n.time)
                            }
                        )
                    }
                }
            )
        }

    private fun JSONObject.toEventWithNodes(resolvedImagePath: String?): EventWithNodes {
        val event = EventEntity(
            name = optString("name"),
            description = optString("description"),
            date = optLong("date"),
            imagePath = resolvedImagePath,
            isPinned = optBoolean("isPinned"),
            createdAt = optLong("createdAt"),
            repeatType = optString("repeatType", "NONE"),
            repeatEndDate = if (isNull("repeatEndDate")) null else optLong("repeatEndDate"),
            isLunar = optBoolean("isLunar"),
            lunarMonth = optInt("lunarMonth"),
            lunarDay = optInt("lunarDay"),
            categoryId = if (isNull("categoryId")) null else optLong("categoryId"),
            archived = optBoolean("archived"),
            colorIndex = optInt("colorIndex"),
            sortOrder = optInt("sortOrder"),
            reminderEnabled = optBoolean("reminderEnabled"),
            reminderAdvanceDays = optInt("reminderAdvanceDays"),
            reminderHour = optInt("reminderHour", 9),
            reminderMinute = optInt("reminderMinute")
        )
        val nodes = mutableListOf<TimelineNodeEntity>()
        val nodeArray = optJSONArray("nodes")
        if (nodeArray != null) {
            for (i in 0 until nodeArray.length()) {
                val n = nodeArray.getJSONObject(i)
                nodes += TimelineNodeEntity(
                    eventId = 0,
                    name = n.optString("name"),
                    time = n.optLong("time")
                )
            }
        }
        return EventWithNodes(event, nodes)
    }
}
