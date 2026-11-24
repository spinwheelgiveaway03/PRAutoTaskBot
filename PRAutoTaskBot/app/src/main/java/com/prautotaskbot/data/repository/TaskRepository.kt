package com.prautotaskbot.data.repository

import com.prautotaskbot.data.db.TaskDao
import com.prautotaskbot.data.model.Task
import com.prautotaskbot.data.model.TaskStatus
import com.prautotaskbot.data.model.TaskType
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {

    fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks()

    fun getTasksByStatus(status: TaskStatus): Flow<List<Task>> = taskDao.getTasksByStatus(status)

    fun getRecentHistory(limit: Int = 100): Flow<List<Task>> = taskDao.getRecentHistory(limit)

    fun countByStatus(status: TaskStatus): Flow<Int> = taskDao.countByStatus(status)

    fun countByTypeAndStatus(type: TaskType, status: TaskStatus): Flow<Int> =
        taskDao.countByTypeAndStatus(type, status)

    suspend fun insert(task: Task): Long = taskDao.insert(task)

    suspend fun insertAll(tasks: List<Task>) = taskDao.insertAll(tasks)

    suspend fun update(task: Task) = taskDao.update(task)

    suspend fun getTaskById(id: Long): Task? = taskDao.getTaskById(id)

    suspend fun getNextPendingDefault(): Task? = taskDao.getNextPendingTaskDefaultPriority()

    suspend fun markInProgress(task: Task) {
        taskDao.update(
            task.copy(
                status = TaskStatus.IN_PROGRESS,
                startedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markCompleted(task: Task, note: String? = null) {
        taskDao.update(
            task.copy(
                status = TaskStatus.COMPLETED,
                finishedAt = System.currentTimeMillis(),
                note = note
            )
        )
    }

    suspend fun markFailed(task: Task, note: String? = null) {
        taskDao.update(
            task.copy(
                status = TaskStatus.FAILED,
                finishedAt = System.currentTimeMillis(),
                note = note
            )
        )
    }

    suspend fun markSkipped(task: Task, note: String? = null) {
        taskDao.update(
            task.copy(
                status = TaskStatus.SKIPPED,
                finishedAt = System.currentTimeMillis(),
                note = note
            )
        )
    }

    suspend fun clearCompletedAndFailed() {
        taskDao.deleteByStatuses(listOf(TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.SKIPPED))
    }
}
