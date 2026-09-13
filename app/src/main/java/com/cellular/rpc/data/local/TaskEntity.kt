package com.cellular.rpc.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["listId"]), Index(value = ["dueDateMs"])]
)
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String = "",
    val listId: String = "inbox", // "inbox", "today", "upcoming", or project id
    val priority: Int = 0, // 0 = None, 1 = Low, 2 = Medium, 3 = High
    val isCompleted: Boolean = false,
    val dueDateMs: Long? = null,
    val subtasksJson: String = "[]", // List of {title, isCompleted}
    val tagsJson: String = "[]", // List of Strings
    val recurrenceRule: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, priority DESC, dueDateMs ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE listId = :listId ORDER BY isCompleted ASC, priority DESC, dueDateMs ASC")
    fun getTasksByList(listId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE dueDateMs >= :startOfDay AND dueDateMs <= :endOfDay ORDER BY isCompleted ASC, priority DESC, dueDateMs ASC")
    fun getTasksForDateRange(startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET isCompleted = :isCompleted WHERE id = :taskId")
    suspend fun updateCompletionStatus(taskId: String, isCompleted: Boolean)

    @Delete
    suspend fun deleteTask(task: TaskEntity)
    
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)
}
