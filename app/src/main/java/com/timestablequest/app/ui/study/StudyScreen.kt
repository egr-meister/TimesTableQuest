package com.timestablequest.app.ui.study

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.generation.Explanations
import com.timestablequest.app.ui.appViewModel
import com.timestablequest.app.ui.common.AppTopBar
import com.timestablequest.app.ui.common.PaperCard
import com.timestablequest.app.ui.theme.Atlas

@Composable
fun StudyScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, h -> StudyViewModel(c, h) }
    val column by vm.column.collectAsStateWithLifecycle()
    val hide by vm.hideAnswer.collectAsStateWithLifecycle()
    val showArray by vm.showArray.collectAsStateWithLifecycle()
    val fact = Fact(vm.row, column.coerceIn(1, 12))

    Scaffold(
        topBar = { AppTopBar("Study ×${vm.row}", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Fact ${fact.column} of 12 · not scored",
                    style = MaterialTheme.typography.labelMedium,
                    color = Atlas.TextMuted,
                )
                Equation(fact, hide)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { vm.go(-1) },
                        enabled = fact.column > 1,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .semantics { contentDescription = "Previous fact" },
                    ) { Text("‹ Previous") }
                    OutlinedButton(
                        onClick = { vm.go(1) },
                        enabled = fact.column < 12,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .semantics { contentDescription = "Next fact" },
                    ) { Text("Next ›") }
                }
                ToggleRow("Hide answer (check yourself)", hide, vm::setHideAnswer)
                if (hide) {
                    Button(
                        onClick = { vm.setHideAnswer(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = Atlas.LavenderDeep),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) { Text("Reveal answer") }
                    Text(
                        "Revealing is for self-checking. It does not count as a scored answer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Atlas.TextMuted,
                    )
                } else {
                    PaperCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier
                                .padding(16.dp)
                                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Explanations.explanation(fact).forEach { line ->
                                Text(line, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                ToggleRow(if (Explanations.usesDotArray(fact)) "Show array" else "Show area model", showArray, vm::setShowArray)
                if (showArray) {
                    FactDiagram(fact, showProduct = !hide, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Equation(fact: Fact, hide: Boolean) {
    val spoken = if (hide) "${fact.spoken} equals what?" else "${fact.spoken} equals ${fact.product}"
    PaperCard(Modifier.fillMaxWidth(), color = Atlas.rowFill(fact.row)) {
        Text(
            if (hide) "${fact.expression} = ?" else "${fact.expression} = ${fact.product}",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = Atlas.Navy,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 12.dp)
                .semantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
