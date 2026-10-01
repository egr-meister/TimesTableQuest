package com.timestablequest.app.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timestablequest.app.data.repository.SessionStatus
import com.timestablequest.app.domain.facts.Facts
import com.timestablequest.app.domain.progress.RowStats
import com.timestablequest.app.ui.appViewModel
import com.timestablequest.app.ui.common.AppTopBar
import com.timestablequest.app.ui.theme.Atlas

@Composable
fun MapScreen(
    onOpenRow: (Int) -> Unit,
    onStudyRow: (Int) -> Unit,
    onOpenSession: (Long) -> Unit,
    onMixed: () -> Unit,
    onNeedsPractice: () -> Unit,
    onSettings: () -> Unit,
) {
    val vm = appViewModel { c, _ -> MapViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Scaffold(
        topBar = {
            AppTopBar("TimesTable Quest", onBack = null) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings for grown-ups")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            GridPaper(Modifier.fillMaxSize())
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DailyStrip(state.daily, onOpen = { vm.openDaily(onOpenSession) })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PracticeRail(
                        needsPractice = state.needsPractice,
                        onMixed = onMixed,
                        onNeedsPractice = onNeedsPractice,
                    )
                }
                state.active?.let { active ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ResumeCard(
                            label = "${active.type.label} · ${active.rowsLabel}",
                            progress = "${active.answered} of ${active.questions.size} answered",
                            onResume = { onOpenSession(active.id) },
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Multiplication map",
                        style = MaterialTheme.typography.titleMedium,
                        color = Atlas.Navy,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .semantics { heading() },
                    )
                }
                items(state.rows, key = { it.row }) { row ->
                    RowTile(row, onOpen = { onOpenRow(row.row) }, onStudy = { onStudyRow(row.row) })
                }
            }
        }
    }
}

/** Subtle atlas grid lines behind the map. Decorative only. */
@Composable
private fun GridPaper(modifier: Modifier) {
    Canvas(modifier) {
        val step = 24.dp.toPx()
        val color = Atlas.GridLine.copy(alpha = 0.45f)
        var x = 0f
        while (x < size.width) {
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
}

@Composable
private fun DailyStrip(daily: DailyStripState, onOpen: () -> Unit) {
    val (headline, detail, action) = when {
        daily.answered == null -> Triple("Daily 10", "Ten questions for today · ${daily.rowsLabel}", "Start")
        daily.status == SessionStatus.COMPLETED -> Triple("Daily 10 · done", "${daily.correct} of ${daily.total} correct today", "See results")
        daily.status == SessionStatus.ACTIVE -> Triple("Daily 10", "${daily.answered} of ${daily.total} answered", "Continue")
        else -> Triple("Daily 10", "Today's set was ended (${daily.answered} answered)", "See results")
    }
    Surface(
        onClick = onOpen,
        color = Atlas.YellowSoft,
        border = BorderStroke(2.dp, Atlas.Yellow),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = "$headline. $detail. $action." },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.titleMedium, color = Atlas.Navy)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = Atlas.TextMuted)
            }
            Text("$action ›", style = MaterialTheme.typography.labelLarge, color = Atlas.Navy)
        }
    }
}

@Composable
private fun PracticeRail(needsPractice: Int, onMixed: () -> Unit, onNeedsPractice: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        RailButton(
            title = "Mixed Practice",
            subtitle = "10 questions",
            onClick = onMixed,
            modifier = Modifier.weight(1f),
        )
        RailButton(
            title = "Needs Practice",
            subtitle = if (needsPractice == 0) "Nothing waiting" else "$needsPractice to review",
            onClick = onNeedsPractice,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RailButton(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        color = Atlas.Lavender,
        border = BorderStroke(1.dp, Atlas.LavenderDeep.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics { contentDescription = "$title. $subtitle." },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Atlas.LavenderDeep, maxLines = 2)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Atlas.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ResumeCard(label: String, progress: String, onResume: () -> Unit) {
    Surface(
        color = Atlas.PaperCard,
        border = BorderStroke(1.dp, Atlas.GridLine),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Unfinished: $label", style = MaterialTheme.typography.titleSmall, color = Atlas.Navy)
                Text(progress, style = MaterialTheme.typography.bodySmall, color = Atlas.TextMuted)
            }
            OutlinedButton(onClick = onResume, modifier = Modifier.heightIn(min = 48.dp)) { Text("Resume") }
        }
    }
}

@Composable
private fun RowTile(row: RowStats, onOpen: () -> Unit, onStudy: () -> Unit) {
    val accent = Atlas.rowAccent(row.row)
    val segments = remember12(row)
    val label = buildString {
        append("Row ${row.row}, ${Facts.rowTitle(row.row).lowercase()}. ")
        append("${row.correctAtLeastOnce} of 12 answered correctly")
        if (row.confident > 0) append(", ${row.confident} confident")
        if (row.needsPractice > 0) append(", ${row.needsPractice} need practice")
        append(". Opens the row.")
    }
    Surface(
        onClick = onOpen,
        color = Atlas.rowFill(row.row),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .segmentedBorder(segments, accent, cornerRadius = 18.dp)
            .semantics { contentDescription = label },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("×${row.row}", style = MaterialTheme.typography.headlineMedium, color = Atlas.Navy, fontWeight = FontWeight.Bold)
            Text(Facts.rowTitle(row.row), style = MaterialTheme.typography.bodySmall, color = Atlas.TextMuted)
            Text(
                "✓ ${row.correctAtLeastOnce} of 12 correct",
                style = MaterialTheme.typography.labelMedium,
                color = Atlas.Navy,
            )
            Text(
                if (row.confident > 0) "★ ${row.confident} confident" else " ",
                style = MaterialTheme.typography.labelMedium,
                color = Atlas.Correct,
            )
            OutlinedButton(
                onClick = onStudy,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Study the ${row.row} times table" },
            ) { Text("Study") }
        }
    }
}

private fun remember12(row: RowStats): List<Segment> {
    // Segment i represents one fact; filled count mirrors the row metrics (correct at least once,
    // confident highlighted). Exact per-fact detail is on the row screen.
    return List(12) { i ->
        when {
            i < row.confident -> Segment.CONFIDENT
            i < row.correctAtLeastOnce -> Segment.CORRECT
            else -> Segment.EMPTY
        }
    }
}
