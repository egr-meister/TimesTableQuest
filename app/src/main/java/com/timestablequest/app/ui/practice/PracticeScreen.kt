package com.timestablequest.app.ui.practice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timestablequest.app.data.repository.QuestionView
import com.timestablequest.app.data.repository.SessionDetail
import com.timestablequest.app.data.repository.SessionType
import com.timestablequest.app.domain.facts.Facts
import com.timestablequest.app.domain.generation.Explanations
import com.timestablequest.app.ui.appViewModel
import com.timestablequest.app.ui.common.AppTopBar
import com.timestablequest.app.ui.common.ConfirmDialog
import com.timestablequest.app.ui.common.PaperCard
import com.timestablequest.app.ui.theme.Atlas
import com.timestablequest.app.ui.theme.LocalReducedMotion

@Composable
fun PracticeScreen(
    onBack: () -> Unit,
    onFinished: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onBackToMap: () -> Unit,
) {
    val vm = appViewModel { c, h -> PracticeViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmEnd by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished(vm.sessionId)
    }

    if (confirmEnd) {
        ConfirmDialog(
            title = "End this session?",
            text = "Questions you already answered are kept in your results. Unanswered questions are not counted.",
            confirmLabel = "End session",
            onConfirm = {
                confirmEnd = false
                vm.endSession { kept -> if (kept) onFinished(vm.sessionId) else onBack() }
            },
            onDismiss = { confirmEnd = false },
        )
    }

    val detail = state.detail
    Scaffold(
        topBar = {
            AppTopBar(detail?.let { titleFor(it) } ?: "Practice", onBack) {
                if (detail != null && !state.newDay) {
                    TextButton(onClick = { confirmEnd = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("End") }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                !state.loaded -> Unit
                state.missing -> Text(
                    "This session is no longer available.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
                state.newDay -> NewDayCard(
                    onStartToday = { vm.openTodaysDaily(onOpenSession) },
                    onBackToMap = onBackToMap,
                )
                detail != null && state.question != null -> QuestionContent(
                    detail = detail,
                    question = state.question!!,
                    position = state.position,
                    isLast = state.isLast,
                    busy = state.busy,
                    onAnswer = vm::answer,
                    onHint = vm::showHint,
                    onNext = vm::next,
                    onPrevious = vm::previous,
                )
            }
        }
    }
}

private fun titleFor(d: SessionDetail): String = when (d.type) {
    SessionType.ROW_CHECK -> "Check ${Facts.rowsLabel(d.rows)}"
    SessionType.MIXED -> "Mixed practice"
    SessionType.REVIEW -> "Needs Practice"
    SessionType.DAILY -> "Daily 10"
}

@Composable
private fun QuestionContent(
    detail: SessionDetail,
    question: QuestionView,
    position: Int,
    isLast: Boolean,
    busy: Boolean,
    onAnswer: (Int) -> Unit,
    onHint: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val f = question.fact
    val total = detail.questions.size
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            when (detail.type) {
                SessionType.ROW_CHECK -> Facts.rowTitle(f.row)
                SessionType.REVIEW -> "Needs Practice review"
                else -> detail.rowsLabel
            },
            style = MaterialTheme.typography.titleSmall,
            color = Atlas.TextMuted,
        )
        Text(
            "Question ${position + 1} of $total",
            style = MaterialTheme.typography.titleMedium,
            color = Atlas.Navy,
            modifier = Modifier.semantics { heading() },
        )
        LinearProgressIndicator(
            progress = { (position + 1).toFloat() / total.coerceAtLeast(1) },
            color = Atlas.Teal,
            trackColor = Atlas.GridLine,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Question ${position + 1} of $total" },
        )
        PaperCard(Modifier.fillMaxWidth(), color = Atlas.rowFill(f.row)) {
            Text(
                "${f.expression} = ?",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Atlas.Navy,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 12.dp)
                    .semantics { contentDescription = "What is ${f.spoken}?" },
            )
        }

        // Four options in a 2 × 2 grid.
        question.options.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { option ->
                    OptionButton(
                        value = option,
                        question = question,
                        enabled = !question.answered && !busy,
                        onClick = { onAnswer(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (!question.answered) {
            if (question.hintUsed) {
                HintCard(Explanations.hint(f))
            } else {
                OutlinedButton(onClick = onHint, modifier = Modifier.heightIn(min = 48.dp)) { Text("Hint") }
            }
        } else {
            Feedback(detail.type, question)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (position > 0) {
                    OutlinedButton(onClick = onPrevious, modifier = Modifier.heightIn(min = 52.dp)) { Text("‹ Back") }
                }
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = Atlas.Navy),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) { Text(if (isLast) "See results" else "Next") }
            }
        }
    }
}

@Composable
private fun OptionButton(value: Int, question: QuestionView, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val answered = question.answered
    val isCorrect = value == question.correctAnswer
    val isSelected = value == question.selectedAnswer
    val (container, border, mark, state) = when {
        !answered -> Quad(Atlas.PaperCard, Atlas.LavenderDeep, "", "")
        isCorrect -> Quad(Atlas.CorrectSoft, Atlas.Correct, "✓ ", if (isSelected) "your answer, correct" else "correct answer")
        isSelected -> Quad(Atlas.IncorrectSoft, Atlas.Incorrect, "✗ ", "your answer, incorrect")
        else -> Quad(Atlas.PaperCard, Atlas.GridLine, "", "")
    }
    // Decorative colour transition; instant when "Reduced decorative animation" is on.
    val reduced = LocalReducedMotion.current
    val animatedContainer by animateColorAsState(
        targetValue = container,
        animationSpec = if (reduced) snap() else tween(durationMillis = 250),
        label = "option",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = animatedContainer,
        border = BorderStroke(if (answered && (isCorrect || isSelected)) 3.dp else 1.5.dp, border),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .heightIn(min = 64.dp)
            .semantics {
                contentDescription = "$value"
                if (state.isNotEmpty()) stateDescription = state
            },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(12.dp)) {
            Text(
                "$mark$value",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Atlas.Navy,
            )
        }
    }
}

private data class Quad(val a: Color, val b: Color, val c: String, val d: String)

@Composable
private fun HintCard(text: String) {
    PaperCard(Modifier.fillMaxWidth(), color = Atlas.Lavender, border = Atlas.LavenderDeep.copy(alpha = 0.4f)) {
        Column(
            Modifier
                .padding(14.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text("Hint", style = MaterialTheme.typography.titleSmall, color = Atlas.LavenderDeep)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Feedback(type: SessionType, q: QuestionView) {
    val f = q.fact
    val correct = q.isCorrect == true
    val headline = when {
        correct && type == SessionType.REVIEW -> "✓ Correct! This fact leaves the Needs Practice list."
        correct && q.hintUsed -> "✓ Correct, with a hint."
        correct -> "✓ Correct!"
        type == SessionType.REVIEW -> "✗ Not quite. ${f.expression} = ${f.product}. It stays on the Needs Practice list."
        else -> "✗ Not quite. ${f.expression} = ${f.product}. It's added to Needs Practice."
    }
    PaperCard(
        Modifier.fillMaxWidth(),
        color = if (correct) Atlas.CorrectSoft else Atlas.IncorrectSoft,
        border = if (correct) Atlas.Correct else Atlas.Incorrect,
    ) {
        Column(
            Modifier
                .padding(14.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(headline, style = MaterialTheme.typography.titleMedium, color = if (correct) Atlas.Correct else Atlas.Incorrect)
            Text(Explanations.equalGroups(f), style = MaterialTheme.typography.bodyLarge)
            Explanations.splitSentence(f)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                ?: Explanations.repeatedAddition(f)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun NewDayCard(onStartToday: () -> Unit, onBackToMap: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 560.dp)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("A new day has started", style = MaterialTheme.typography.headlineSmall, color = Atlas.Navy)
        Text(
            "Yesterday's Daily 10 is saved as incomplete. Unanswered questions are not counted as mistakes.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onStartToday,
            colors = ButtonDefaults.buttonColors(containerColor = Atlas.Navy),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) { Text("Open today's Daily 10") }
        OutlinedButton(onClick = onBackToMap, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Back to map") }
    }
}
