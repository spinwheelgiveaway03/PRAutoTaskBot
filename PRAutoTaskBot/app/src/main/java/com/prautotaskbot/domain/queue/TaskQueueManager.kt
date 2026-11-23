package com.prautotaskbot.domain.queue

import com.prautotaskbot.data.model.Task
import com.prautotaskbot.data.model.TaskStatus
import com.prautotaskbot.data.model.TaskType
import com.prautotaskbot.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Smart Task Queue dengan prioritas default:
 * FOLLOW (1) → LIKE (2) → COMMENT (3) → OTHER (4)
 *
 * Pengguna dapat mengubah urutan prioritas lewat Settings (disimpan di DataStore).
 * Tidak mengulang tugas yang sudah COMPLETED / FAILED / SKIPPED.
 */
class TaskQueueManager(
    private val repository: TaskRepository
) {
    private val mutex = Mutex()

    private val _currentTask = MutableStateFlow<Task?>(null)
    val currentTask: StateFlow<Task?> = _currentTask.asStateFlow()

    private val _queueState = MutableStateFlow(QueueState.IDLE)
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    enum class QueueState {
        IDLE, RUNNING, PAUSED, STOPPED, MONITORING
    }

    // Prioritas numerik (lebih kecil = lebih dulu). Default sesuai permintaan.
    private var priorityMap: Map<TaskType, Int> = mapOf(
        TaskType.FOLLOW to 1,
        TaskType.LIKE to 2,
        TaskType.COMMENT to 3,
        TaskType.OTHER to 4
    )

    fun updatePriorities(follow: Int, like: Int, comment: Int, other: Int) {
        priorityMap = mapOf(
            TaskType.FOLLOW to follow,
            TaskType.LIKE to like,
            TaskType.COMMENT to comment,
            TaskType.OTHER to other
        )
    }

    suspend fun start() {
        mutex.withLock {
            if (_queueState.value == QueueState.RUNNING) return
            _queueState.value = QueueState.RUNNING
            advanceToNext()
        }
    }

    suspend fun pause() {
        mutex.withLock {
            _queueState.value = QueueState.PAUSED
        }
    }

    suspend fun stop() {
        mutex.withLock {
            _currentTask.value?.let { task ->
                if (task.status == TaskStatus.IN_PROGRESS) {
                    repository.markSkipped(task, "Dihentikan oleh pengguna")
                }
            }
            _currentTask.value = null
            _queueState.value = QueueState.STOPPED
        }
    }

    suspend fun resume() {
        mutex.withLock {
            if (_queueState.value == QueueState.PAUSED) {
                _queueState.value = QueueState.RUNNING
                if (_currentTask.value == null) {
                    advanceToNext()
                }
            }
        }
    }

    /**
     * Ambil tugas berikutnya sesuai prioritas.
     * Jika tidak ada, set state MONITORING agar service memulai dynamic refresh.
     */
    private suspend fun advanceToNext() {
        val next = repository.getNextPendingDefault()
        if (next != null) {
            repository.markInProgress(next)
            _currentTask.value = next
            _queueState.value = QueueState.RUNNING
        } else {
            _currentTask.value = null
            _queueState.value = QueueState.MONITORING
        }
    }

    suspend fun markCurrentCompleted(note: String? = null) {
        mutex.withLock {
            val task = _currentTask.value ?: return
            repository.markCompleted(task, note)
            _currentTask.value = null
            if (_queueState.value == QueueState.RUNNING) {
                advanceToNext()
            }
        }
    }

    suspend fun markCurrentFailed(note: String? = null) {
        mutex.withLock {
            val task = _currentTask.value ?: return
            repository.markFailed(task, note)
            _currentTask.value = null
            if (_queueState.value == QueueState.RUNNING) {
                advanceToNext()
            }
        }
    }

    suspend fun markCurrentSkipped(note: String? = null) {
        mutex.withLock {
            val task = _currentTask.value ?: return
            repository.markSkipped(task, note)
            _currentTask.value = null
            if (_queueState.value == QueueState.RUNNING) {
                advanceToNext()
            }
        }
    }

    suspend fun addTask(task: Task): Long {
        return repository.insert(task.copy(status = TaskStatus.PENDING))
    }

    suspend fun addTasks(tasks: List<Task>) {
        repository.insertAll(tasks.map { it.copy(status = TaskStatus.PENDING) })
    }

    fun getState(): QueueState = _queueState.value
}
