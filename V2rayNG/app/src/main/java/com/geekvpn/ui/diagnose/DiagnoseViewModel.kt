package com.geekvpn.ui.diagnose

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geekvpn.diagnose.AndroidDiagnosePorts
import com.geekvpn.diagnose.Check
import com.geekvpn.diagnose.DiagnosePorts
import com.geekvpn.diagnose.Diagnosis
import com.geekvpn.diagnose.Outcome
import com.geekvpn.diagnose.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiagnoseUiState(
    val running: Boolean = false,
    /** The check running now. */
    val current: Check? = null,
    val results: Map<Check, Outcome> = emptyMap(),
    /** Set once every check has run. */
    val verdict: Verdict? = null,
    /** The ticket line for "ارسال به پشتیبانی"; with [verdict]. */
    val summary: String? = null,
)

/** "عیب‌یاب اتصال": runs [Diagnosis] once on open, and again on request. */
class DiagnoseViewModel(
    private val ports: DiagnosePorts,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {

    private val state = MutableStateFlow(DiagnoseUiState())
    val uiState: StateFlow<DiagnoseUiState> = state.asStateFlow()
    private var job: Job? = null

    init {
        start()
    }

    /** Runs every check again; ignored while a run is going. */
    fun start() {
        if (job?.isActive == true) return
        state.value = DiagnoseUiState(running = true)
        job = viewModelScope.launch {
            val results = Diagnosis.run(ports) { check, outcome ->
                state.update {
                    if (outcome == null) it.copy(current = check)
                    else it.copy(current = null, results = it.results + (check to outcome))
                }
            }
            state.update {
                it.copy(running = false, current = null, verdict = Diagnosis.verdict(results), summary = Diagnosis.summary(results))
            }
        }
    }

    companion object {
        fun factory(application: Application) = viewModelFactory {
            initializer { DiagnoseViewModel(AndroidDiagnosePorts(application)) }
        }
    }
}
