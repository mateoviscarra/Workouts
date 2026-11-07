package com.mateoviscarra.doitall.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mateoviscarra.doitall.data.PersonalRecord
import com.mateoviscarra.doitall.data.WorkoutHistoryRepository
import com.mateoviscarra.doitall.data.WorkoutSessionRecord
import kotlinx.coroutines.flow.StateFlow

class HistoryViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: WorkoutHistoryRepository = WorkoutHistoryRepository(application)
) : AndroidViewModel(application) {

    val sessions: StateFlow<List<WorkoutSessionRecord>> = repository.sessions
    val personalRecords: StateFlow<Map<String, PersonalRecord>> = repository.personalRecords

    fun recordSession(session: WorkoutSessionRecord): WorkoutSessionRecord =
        repository.recordSession(session)

    fun deleteSession(sessionId: String) =
        repository.deleteSession(sessionId)

    fun addOrUpdatePR(pr: PersonalRecord) =
        repository.addOrUpdatePR(pr)

    fun deletePR(exerciseId: String) =
        repository.deletePR(exerciseId)

    fun getPR(exerciseId: String): PersonalRecord? =
        repository.getPR(exerciseId)
}

