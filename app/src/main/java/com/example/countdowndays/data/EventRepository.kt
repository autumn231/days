package com.example.countdowndays.data

import com.example.countdowndays.util.ImageStorage
import kotlinx.coroutines.flow.Flow

class EventRepository(
    private val dao: EventDao,
    private val categoryDao: CategoryDao
) {

    /** 数据变更后的回调（重排提醒、刷新小组件），由 AppContainer 注入 */
    var onEventsChanged: (() -> Unit)? = null

    fun observeAll(): Flow<List<EventWithNodes>> = dao.observeActiveEvents()

    fun observeEvery(): Flow<List<EventWithNodes>> = dao.observeAllEventsWithNodes()

    fun observeSearch(query: String): Flow<List<EventWithNodes>> = dao.searchEventsWithNodes(query)

    fun observeEvent(id: Long): Flow<EventWithNodes?> = dao.observeEventWithNodes(id)

    suspend fun getEvent(id: Long): EventWithNodes? = dao.getEventWithNodes(id)

    suspend fun getActiveEvents(): List<EventWithNodes> = dao.getActiveEvents()

    suspend fun getAllEvents(): List<EventWithNodes> = dao.getAllEvents()

    suspend fun addEvent(event: EventEntity): Long =
        dao.insertEvent(event).also { notifyChanged() }

    suspend fun updateEvent(event: EventEntity) {
        dao.updateEvent(event)
        notifyChanged()
    }

    /**
     * 删除事件。这里先不删图片文件——列表支持「撤销删除」，
     * 立刻删文件会让撤销后变成白图。真正的清理在启动时按引用关系做（见 cleanupOrphanImages）。
     */
    suspend fun deleteEvent(event: EventEntity) {
        dao.deleteEvent(event)
        notifyChanged()
    }

    /** 撤销删除：按原 id 写回事件与时间线节点 */
    suspend fun restoreEvent(item: EventWithNodes) {
        dao.insertEvent(item.event)
        item.nodes.forEach { dao.insertNode(it) }
        notifyChanged()
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        dao.setPinned(id, pinned)
        notifyChanged()
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        dao.setArchived(id, archived)
        notifyChanged()
    }

    suspend fun addNode(node: TimelineNodeEntity): Long = dao.insertNode(node)

    suspend fun updateNode(node: TimelineNodeEntity) = dao.updateNode(node)

    suspend fun deleteNode(node: TimelineNodeEntity) = dao.deleteNode(node)

    // ---------- 分类 ----------
    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun getCategories(): List<CategoryEntity> = categoryDao.getAll()

    suspend fun saveCategory(category: CategoryEntity): Long = categoryDao.upsert(category)

    suspend fun deleteCategory(id: Long) {
        categoryDao.detachEvents(id)
        categoryDao.delete(id)
    }

    // ---------- 图片清理 ----------
    /** 删除不再被任何事件引用的图片（撤销删除留下的孤儿文件） */
    suspend fun cleanupOrphanImages(context: android.content.Context) {
        val referenced = dao.getAllEvents().mapNotNull { it.event.imagePath }.toSet()
        ImageStorage.cleanupOrphans(context, referenced)
    }

    // ---------- 备份 / 恢复 ----------
    suspend fun importData(events: List<EventWithNodes>, categories: List<CategoryEntity>) {
        categoryDao.insertAll(categories)
        events.forEach { ew ->
            val newId = dao.insertEvent(ew.event.copy(id = 0))
            ew.nodes.forEach { node ->
                dao.insertNode(node.copy(id = 0, eventId = newId))
            }
        }
        notifyChanged()
    }

    private fun notifyChanged() {
        onEventsChanged?.invoke()
    }
}
