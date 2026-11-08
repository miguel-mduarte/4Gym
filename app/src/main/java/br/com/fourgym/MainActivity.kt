package br.com.fourgym

import android.app.Application
import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import br.com.fourgym.data.PlanDraft
import br.com.fourgym.data.RoutineDetail
import br.com.fourgym.data.TrainingDatabase
import br.com.fourgym.data.TrainingRepository
import br.com.fourgym.data.WorkoutReview
import br.com.fourgym.ui.FourGym
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class FourGymApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository by lazy {
        TrainingRepository(Room.databaseBuilder(this, TrainingDatabase::class.java, "4gym.db").build())
    }
    private val reminders by lazy { WorkoutReminders(this, repository) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            combine(repository.plans, repository.routines, repository.history, repository.reminderSettings) { _, _, _, _ -> Unit }
                .collect { reminders.refresh() }
        }
    }

    internal fun refreshReminders(intent: Intent? = null, onFinish: () -> Unit = {}) {
        appScope.launch {
            try {
                withTimeoutOrNull(8_000) { reminders.refresh(intent) }
            } finally {
                onFinish()
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        (application as FourGymApplication).refreshReminders()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val repository = (application as FourGymApplication).repository
        val model = ViewModelProvider(this, WorkoutViewModel.factory(repository))[WorkoutViewModel::class.java]
        setContent { FourGym(model) }
    }
}

class WorkoutViewModel(val repository: TrainingRepository) : ViewModel() {
    private val actions = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    val error = MutableStateFlow<String?>(null)
    val routineDraft = MutableStateFlow<RoutineDetail?>(null)
    val planDraft = MutableStateFlow<PlanDraft?>(null)
    val workoutReview = MutableStateFlow<WorkoutReview?>(null)

    init {
        submit { repository.initialize() }
        viewModelScope.launch { repository.monitorTimedSets() }
        viewModelScope.launch {
            for (action in actions) {
                try {
                    action()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e("4Gym", "Falha em operação de treino", failure)
                    error.value = if (failure is IllegalStateException) {
                        failure.message ?: "Não foi possível concluir a ação."
                    } else {
                        "Não foi possível concluir a ação. Tente novamente."
                    }
                }
            }
        }
    }

    fun submit(action: suspend () -> Unit) {
        actions.trySend(action)
    }

    companion object {
        fun factory(repository: TrainingRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkoutViewModel(repository) as T
            }
    }
}
