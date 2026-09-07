package com.healthy.app.ui.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.core.HealthyDay
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.entity.Note
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = HealthyDatabase.get(app).noteDao()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    private val _month = MutableStateFlow(YearMonth.from(LocalDate.parse(HealthyDay.today())))
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    /**
     * The carousel contents (spec 15.4).
     *
     * With no search and no day selected it shows every note, newest first.
     * A search wins over a selected day: typing is the more deliberate act.
     */
    val notes: StateFlow<List<Note>> =
        combine(_query, _selectedDate) { q, date -> q to date }
            .flatMapLatest { (q, date) ->
                when {
                    q.isNotBlank() -> dao.search(NoteSearch.toMatchQuery(q))
                    date != null -> dao.observeByDate(date)
                    else -> dao.observeAll()
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Days in the shown month that have a note, for the calendar dots. */
    val daysWithNotes: StateFlow<Set<String>> =
        _month.flatMapLatest { ym ->
            dao.observeDatesWithNotes(
                from = ym.atDay(1).toString(),
                to = ym.atEndOfMonth().toString(),
            ).map { it.toSet() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun selectDate(date: String?) {
        _selectedDate.value = if (_selectedDate.value == date) null else date
    }

    fun showMonth(value: YearMonth) {
        _month.value = value
    }

    fun add(text: String, date: String = HealthyDay.today()) {
        if (text.isBlank()) return
        viewModelScope.launch {
            dao.insert(Note(date = date, text = text.trim(), createdAt = System.currentTimeMillis()))
        }
    }

    fun update(note: Note, text: String) {
        viewModelScope.launch { dao.update(note.copy(text = text.trim())) }
    }

    fun delete(note: Note) {
        viewModelScope.launch { dao.delete(note) }
    }

}
