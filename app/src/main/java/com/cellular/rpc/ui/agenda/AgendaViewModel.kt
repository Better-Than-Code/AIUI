package com.cellular.rpc.ui.agenda

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.data.local.CalendarEventEntity
import com.cellular.rpc.data.local.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class AgendaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val taskDao = db.taskDao()
    private val calendarDao = db.calendarEventDao()

    // Filters
    private val _currentListId = MutableStateFlow("today") // "inbox", "today", "upcoming"
    val currentListId: StateFlow<String> = _currentListId

    val allTasks = taskDao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEvents = calendarDao.getAllEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unified agenda view (tasks due + events)
    val agendaItems = combine(taskDao.getAllTasks(), calendarDao.getAllEvents(), _currentListId) { tasks, events, listId ->
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfToday = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val startOfTomorrow = calendar.timeInMillis

        val filteredTasks = when (listId) {
            "inbox" -> tasks.filter { it.listId == "inbox" }
            "today" -> tasks.filter { !it.isCompleted && (it.dueDateMs == null || it.dueDateMs < startOfTomorrow) }
            "upcoming" -> tasks.filter { !it.isCompleted && (it.dueDateMs != null && it.dueDateMs >= startOfTomorrow) }
            else -> tasks.filter { it.listId == listId }
        }

        val filteredEvents = when (listId) {
            "today" -> events.filter { it.startTimeMs >= startOfToday && it.startTimeMs < startOfTomorrow }
            "upcoming" -> events.filter { it.startTimeMs >= startOfTomorrow }
            else -> emptyList() // Inbox doesn't show events
        }

        // Combine and sort
        val items = mutableListOf<AgendaItem>()
        items.addAll(filteredEvents.map { AgendaItem.Event(it) })
        items.addAll(filteredTasks.map { AgendaItem.Task(it) })
        
        items.sortedBy { 
            when (it) {
                is AgendaItem.Event -> it.event.startTimeMs
                is AgendaItem.Task -> it.task.dueDateMs ?: Long.MAX_VALUE
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setListId(listId: String) {
        _currentListId.value = listId
    }

    fun toggleTaskCompletion(taskId: String, currentStatus: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.updateCompletionStatus(taskId, !currentStatus)
        }
    }

    fun addTask(title: String, dueDateMs: Long? = null, priority: Int = 0) {
        if (title.isBlank()) return
        val task = TaskEntity(
            id = "task_${System.currentTimeMillis()}",
            title = title,
            dueDateMs = dueDateMs,
            priority = priority,
            listId = _currentListId.value.let { if (it == "today" || it == "upcoming") "inbox" else it }
        )
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.insertTask(task)
        }
    }
    
    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.deleteTask(task)
        }
    }
}

sealed class AgendaItem {
    data class Task(val task: TaskEntity) : AgendaItem()
    data class Event(val event: CalendarEventEntity) : AgendaItem()
}
