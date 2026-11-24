package com.prautotaskbot.data.db

import androidx.room.*
import com.prautotaskbot.data.model.Task
import com.prautotaskbot.data.model.TaskStatus
import com.prautotaskbot.data.model.TaskType
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY foundAt DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY foundAt ASC")
    fun getTasksByStatus(status: TaskStatus): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE platform = :platform AND status = :status ORDER BY foundAt ASC")
    fun getTasksByPlatformAndStatus(platform: String, status: TaskStatus): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE type = :type AND status = :status ORDER BY foundAt ASC")
    fun getTasksByTypeAndStatus(type: TaskType, status: TaskStatus): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<Task>)

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("DELETE FROM tasks WHERE status IN (:statuses)")
    suspend fun deleteByStatuses(statuses: List<TaskStatus>)

    @Query("SELECT COUNT(*) FROM tasks WHERE status = :status")
    fun countByStatus(status: TaskStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE platform = :platform AND status = :status")
    fun countByPlatformAndStatus(platform: String, status: TaskStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE type = :type AND status = :status")
    fun countByTypeAndStatus(type: TaskType, status: TaskStatus): Flow<Int>

    @Query("SELECT * FROM tasks WHERE status = 'PENDING' ORDER BY CASE type WHEN 'FOLLOW' THEN 1 WHEN 'LIKE' THEN 2 WHEN 'COMMENT' THEN 3 ELSE 4 END, foundAt ASC LIMIT 1")
    suspend fun getNextPendingTaskDefaultPriority(): Task?

    @Query("SELECT * FROM tasks ORDER BY finishedAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 100): Flow<List<Task>>
}
