package com.timestablequest.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.timestablequest.app.AppContainer
import com.timestablequest.app.data.local.AppSettings
import com.timestablequest.app.ui.appViewModel
import com.timestablequest.app.ui.common.AdultGateDialog
import com.timestablequest.app.ui.common.AppTopBar
import com.timestablequest.app.ui.common.PaperCard
import com.timestablequest.app.ui.common.RowSelector
import com.timestablequest.app.ui.common.SectionTitle
import com.timestablequest.app.ui.theme.Atlas
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(container: AppContainer) : ViewModel() {
    private val prefs = container.preferences
    private val resetter = container.resetter

    val settings: StateFlow<AppSettings> =
        prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setDailyRows(rows: Set<Int>) = viewModelScope.launch { prefs.setDailyRows(rows) }
    fun setMixedRows(rows: Set<Int>) = viewModelScope.launch { prefs.setMixedRows(rows) }
    fun setReducedMotion(v: Boolean) = viewModelScope.launch { prefs.setReducedMotion(v) }

    fun resetRow(row: Int, clearHistory: Boolean, done: () -> Unit) = viewModelScope.launch {
        resetter.resetRow(row, clearHistory)
        done()
    }

    fun resetAll(clearHistory: Boolean, done: () -> Unit) = viewModelScope.launch {
        resetter.resetAllProgress(clearHistory)
        done()
    }

    fun clearCalculator(done: () -> Unit) = viewModelScope.launch {
        resetter.clearCalculatorHistory()
        done()
    }

    fun clearAll(done: () -> Unit) = viewModelScope.launch {
        resetter.clearAllLocalData()
        done()
    }
}

private enum class Pending { RESET_ROW, RESET_ALL, CLEAR_CALC, CLEAR_ALL }

@Composable
fun SettingsScreen(onBack: () -> Unit, onPrivacy: () -> Unit) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var pending by rememberSaveable { mutableStateOf<Pending?>(null) }
    var resetRowChoice by rememberSaveable { mutableIntStateOf(1) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    when (pending) {
        Pending.RESET_ROW -> AdultGateDialog(
            title = "Reset row ×$resetRowChoice?",
            explanation = "This clears the statistics and Needs Practice list for the $resetRowChoice times table and ends any " +
                "unfinished session that contains it. Saved session results stay as read-only history unless you also delete them.",
            confirmLabel = "Reset row",
            historyOptionLabel = "Also delete saved sessions that include this row",
            onConfirm = { clear ->
                vm.resetRow(resetRowChoice, clear) { message = "Row ×$resetRowChoice was reset." }
                pending = null
            },
            onDismiss = { pending = null },
        )
        Pending.RESET_ALL -> AdultGateDialog(
            title = "Reset all multiplication progress?",
            explanation = "This clears every fact's statistics, Confident status and the Needs Practice list, and ends unfinished " +
                "sessions. Saved session results stay as read-only history unless you also delete them.",
            confirmLabel = "Reset all",
            historyOptionLabel = "Also delete all saved session history",
            onConfirm = { clear ->
                vm.resetAll(clear) { message = "All multiplication progress was reset." }
                pending = null
            },
            onDismiss = { pending = null },
        )
        Pending.CLEAR_CALC -> AdultGateDialog(
            title = "Clear calculator history?",
            explanation = "This deletes the saved list of calculations. It does not affect multiplication progress.",
            confirmLabel = "Clear",
            onConfirm = {
                vm.clearCalculator { message = "Calculator history cleared." }
                pending = null
            },
            onDismiss = { pending = null },
        )
        Pending.CLEAR_ALL -> AdultGateDialog(
            title = "Clear all local data?",
            explanation = "This permanently deletes all progress, session history, calculator history and settings stored on this " +
                "device. It cannot be undone.",
            confirmLabel = "Delete everything",
            onConfirm = {
                vm.clearAll { message = "All local data was deleted." }
                pending = null
            },
            onDismiss = { pending = null },
        )
        null -> Unit
    }

    Scaffold(
        topBar = { AppTopBar("Settings", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                message?.let {
                    PaperCard(Modifier.fillMaxWidth(), color = Atlas.CorrectSoft, border = Atlas.Correct) {
                        Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }

                SectionTitle("Daily 10 rows")
                Text(
                    "Rows used for the daily set. Changes apply to the next day's set; today's set stays as it is.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Atlas.TextMuted,
                )
                RowSelector(selected = settings.dailyRows, onChange = { vm.setDailyRows(it) })

                HorizontalDivider(color = Atlas.GridLine)
                SectionTitle("Mixed Practice default rows")
                Text(
                    "Preselected when starting Mixed Practice. An unfinished session is never changed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Atlas.TextMuted,
                )
                RowSelector(selected = settings.mixedRows, onChange = { vm.setMixedRows(it) })

                HorizontalDivider(color = Atlas.GridLine)
                SectionTitle("Display")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = settings.reducedMotion, role = Role.Switch, onValueChange = { vm.setReducedMotion(it) }),
                ) {
                    Text("Reduce decorative animation", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = settings.reducedMotion, onCheckedChange = null)
                }

                HorizontalDivider(color = Atlas.GridLine)
                SectionTitle("Grown-up actions")
                Text(
                    "These actions ask a grown-up question first to prevent accidental taps. It is not a password.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Atlas.TextMuted,
                )
                Text("Reset one row", style = MaterialTheme.typography.titleSmall)
                RowPicker(selected = resetRowChoice, onSelect = { resetRowChoice = it })
                ActionButton("Reset row ×$resetRowChoice progress") { pending = Pending.RESET_ROW }
                ActionButton("Reset all multiplication progress") { pending = Pending.RESET_ALL }
                ActionButton("Clear calculator history") { pending = Pending.CLEAR_CALC }
                ActionButton("Clear all local data") { pending = Pending.CLEAR_ALL }

                HorizontalDivider(color = Atlas.GridLine)
                SectionTitle("About")
                ActionButton("Privacy information", onClick = onPrivacy)
            }
        }
    }
}

@Composable
private fun RowPicker(selected: Int, onSelect: (Int) -> Unit) {
    // Single-choice variant of the row chips.
    RowSelector(
        selected = setOf(selected),
        onChange = { rows ->
            val added = rows - selected
            if (added.isNotEmpty()) onSelect(added.first())
        },
        singleChoice = true,
    )
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) { Text(label) }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { AppTopBar("Privacy", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrivacyParagraph(
                    "Everything stays on this device",
                    "TimesTable Quest works fully offline. It has no account, no internet permission, no advertising, no analytics, " +
                        "no payments and no cloud sync. Nothing you do in the app is sent anywhere.",
                )
                PrivacyParagraph(
                    "What is stored",
                    "Practice sessions and answers, fact progress, the Needs Practice list, calculator history (latest 50) and " +
                        "settings. They are kept in the app's private storage, which other apps cannot read.",
                )
                PrivacyParagraph(
                    "Backups",
                    "Cloud backup and device-to-device transfer are turned off for this app, so its data is not copied off the device.",
                )
                PrivacyParagraph(
                    "No personal information",
                    "The app never asks for a name, email, age, photo, location or contacts, and it requests no permissions.",
                )
                PrivacyParagraph(
                    "Deleting data",
                    "A grown-up can reset progress or delete all local data in Settings. Uninstalling the app also deletes it.",
                )
            }
        }
    }
}

@Composable
private fun PrivacyParagraph(title: String, body: String) {
    PaperCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Atlas.Navy)
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
