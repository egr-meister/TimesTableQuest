package com.timestablequest.app.ui.calculator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.timestablequest.app.AppContainer
import com.timestablequest.app.data.repository.CalculationItem
import com.timestablequest.app.domain.calculator.CalcKey
import com.timestablequest.app.domain.calculator.CalcOperator
import com.timestablequest.app.domain.calculator.CalculatorEngine
import com.timestablequest.app.domain.calculator.CalculatorState
import com.timestablequest.app.ui.appViewModel
import com.timestablequest.app.ui.common.AppTopBar
import com.timestablequest.app.ui.theme.Atlas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalculatorViewModel(container: AppContainer) : ViewModel() {
    private val prefs = container.preferences
    private val repo = container.calculator
    private val _state = MutableStateFlow(CalculatorState())
    val state: StateFlow<CalculatorState> = _state.asStateFlow()
    private var restored = false

    val history: StateFlow<List<CalculationItem>> =
        repo.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val draft = prefs.calculatorDraft.first()
            if (!restored) _state.value = CalculatorEngine.decode(draft)
            restored = true
        }
    }

    fun press(key: CalcKey) {
        restored = true
        val out = CalculatorEngine.press(_state.value, key)
        _state.value = out.state
        viewModelScope.launch {
            out.record?.let { repo.add(it) }
            prefs.setCalculatorDraft(CalculatorEngine.encode(out.state))
        }
    }
}

private data class KeySpec(val label: String, val key: CalcKey, val spoken: String, val kind: Int = 0, val weight: Float = 1f)

private val KeyRows: List<List<KeySpec>> = listOf(
    listOf(
        KeySpec("C", CalcKey.Clear, "Clear", 2),
        KeySpec("⌫", CalcKey.Backspace, "Delete last digit", 2),
        KeySpec("±", CalcKey.ToggleSign, "Change sign", 2),
        KeySpec("÷", CalcKey.Operator(CalcOperator.DIVIDE), "Divide", 1),
    ),
    listOf(
        KeySpec("7", CalcKey.Digit(7), "7"), KeySpec("8", CalcKey.Digit(8), "8"), KeySpec("9", CalcKey.Digit(9), "9"),
        KeySpec("×", CalcKey.Operator(CalcOperator.MULTIPLY), "Multiply", 1),
    ),
    listOf(
        KeySpec("4", CalcKey.Digit(4), "4"), KeySpec("5", CalcKey.Digit(5), "5"), KeySpec("6", CalcKey.Digit(6), "6"),
        KeySpec("−", CalcKey.Operator(CalcOperator.SUBTRACT), "Minus", 1),
    ),
    listOf(
        KeySpec("1", CalcKey.Digit(1), "1"), KeySpec("2", CalcKey.Digit(2), "2"), KeySpec("3", CalcKey.Digit(3), "3"),
        KeySpec("+", CalcKey.Operator(CalcOperator.ADD), "Plus", 1),
    ),
    listOf(
        KeySpec("0", CalcKey.Digit(0), "0", weight = 2f),
        KeySpec(".", CalcKey.Decimal, "Decimal point"),
        KeySpec("=", CalcKey.Equals, "Equals", 3),
    ),
)

@Composable
fun CalculatorScreen(
    onHistory: () -> Unit,
    pendingValue: String?,
    onPendingConsumed: () -> Unit,
) {
    val vm = appViewModel { c, _ -> CalculatorViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(pendingValue) {
        if (pendingValue != null) {
            vm.press(CalcKey.UseValue(pendingValue))
            onPendingConsumed()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar("Calculator", onBack = null) {
                TextButton(onClick = onHistory, modifier = Modifier.heightIn(min = 48.dp)) { Text("History") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Display(state)
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KeyRows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { spec ->
                                CalcButton(spec, Modifier.weight(spec.weight)) { vm.press(spec.key) }
                            }
                        }
                    }
                    Text(
                        "Numbers from −1,000,000 to 1,000,000 with up to 6 decimal places. " +
                            "≈ means the answer was rounded. The calculator never changes your multiplication progress.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Atlas.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun Display(state: CalculatorState) {
    Surface(
        color = Atlas.PaperCard,
        border = BorderStroke(2.dp, Atlas.Blue),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                state.expression.ifEmpty { " " },
                style = MaterialTheme.typography.titleMedium,
                color = Atlas.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.error != null) {
                Text(
                    state.error,
                    style = MaterialTheme.typography.titleMedium,
                    color = Atlas.Incorrect,
                    textAlign = TextAlign.End,
                )
            } else {
                Text(
                    (if (state.showsApproximate) "≈ " else "") + state.display,
                    style = MaterialTheme.typography.displaySmall,
                    color = Atlas.Navy,
                    maxLines = 2,
                    textAlign = TextAlign.End,
                    modifier = Modifier.semantics {
                        contentDescription = (if (state.showsApproximate) "approximately " else "") + state.display
                    },
                )
            }
        }
    }
}

@Composable
private fun CalcButton(spec: KeySpec, modifier: Modifier, onClick: () -> Unit) {
    val colors = when (spec.kind) {
        1 -> ButtonDefaults.buttonColors(containerColor = Atlas.Teal)
        2 -> ButtonDefaults.buttonColors(containerColor = Atlas.Lavender, contentColor = Atlas.Navy)
        3 -> ButtonDefaults.buttonColors(containerColor = Atlas.Navy)
        else -> ButtonDefaults.buttonColors(containerColor = Atlas.PaperCard, contentColor = Atlas.Text)
    }
    Button(
        onClick = onClick,
        colors = colors,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(4.dp),
        modifier = modifier
            .heightIn(min = 60.dp)
            .semantics { contentDescription = spec.spoken },
    ) {
        Text(spec.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun CalculatorHistoryScreen(onBack: () -> Unit, onUseResult: (String) -> Unit) {
    val vm = appViewModel { c, _ -> CalculatorViewModel(c) }
    val items by vm.history.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            // Clearing the history is a grown-up action in Settings.
            AppTopBar("Calculation History", onBack)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No calculations yet.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.id }) { item ->
                val r = item.record
                val selected = selectedId == item.id
                Surface(
                    color = Atlas.PaperCard,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Atlas.Teal else Atlas.Blue),
                    shape = RoundedCornerShape(14.dp),
                    onClick = { selectedId = if (selected) null else item.id },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${r.left} ${r.operator.symbol} ${r.right} = ${if (r.rounded) "≈ " else ""}${r.result}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (selected) {
                            OutlinedButton(onClick = { onUseResult(r.result) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Use result")
                            }
                        }
                    }
                }
            }
        }
    }
}
