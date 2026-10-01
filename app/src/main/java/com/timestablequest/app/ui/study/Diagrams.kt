package com.timestablequest.app.ui.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.generation.Explanations
import com.timestablequest.app.ui.theme.Atlas
import kotlin.math.min

/**
 * Native diagram for a fact: a dot array (c rows of r dots) for manageable sizes, otherwise a
 * compact area model split into two labelled parts. Both carry a text alternative.
 */
@Composable
fun FactDiagram(fact: Fact, showProduct: Boolean, modifier: Modifier = Modifier) {
    if (Explanations.usesDotArray(fact)) {
        DotArray(fact, showProduct, modifier)
    } else {
        AreaModel(fact, showProduct, modifier)
    }
}

@Composable
private fun DotArray(fact: Fact, showProduct: Boolean, modifier: Modifier) {
    val cols = fact.row // dots per row
    val rows = fact.column // number of groups
    val description = if (showProduct) {
        Explanations.arrayDescription(fact)
    } else {
        "An array of ${fact.column} rows with ${fact.row} dots in each row. Count them to check your answer."
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(cols.toFloat() / rows.toFloat())
                .semantics { contentDescription = description },
        ) {
            val cell = min(size.width / cols, size.height / rows)
            val r = cell * 0.34f
            val offsetX = (size.width - cell * cols) / 2f
            val offsetY = (size.height - cell * rows) / 2f
            for (g in 0 until rows) {
                // Alternate group colours so each group of `cols` dots reads as one group.
                val color = if (g % 2 == 0) Atlas.Teal else Atlas.Blue
                for (d in 0 until cols) {
                    drawCircle(
                        color = color,
                        radius = r,
                        center = Offset(offsetX + cell * (d + 0.5f), offsetY + cell * (g + 0.5f)),
                    )
                }
            }
        }
        Text(
            "${fact.column} rows of ${fact.row}",
            style = MaterialTheme.typography.bodySmall,
            color = Atlas.TextMuted,
            modifier = Modifier
                .padding(top = 4.dp)
                .clearAndSetSemantics { },
        )
    }
}

@Composable
private fun AreaModel(fact: Fact, showProduct: Boolean, modifier: Modifier) {
    val split = Explanations.split(fact)
    val description = if (showProduct) {
        Explanations.areaDescription(fact)
    } else {
        "A rectangle ${fact.row} wide and ${fact.column} long. The answer is hidden."
    }
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .height(120.dp)
                .clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (split == null) {
                AreaPart("${fact.row} × ${fact.column}", if (showProduct) "${fact.product}" else "?", Modifier.weight(1f), Atlas.TealTile)
            } else {
                AreaPart(
                    "${fact.row} × ${split.first}",
                    if (showProduct) "${fact.row * split.first}" else "?",
                    Modifier.weight(split.first.toFloat()),
                    Atlas.TealTile,
                )
                AreaPart(
                    "${fact.row} × ${split.second}",
                    if (showProduct) "${fact.row * split.second}" else "?",
                    Modifier.weight(split.second.toFloat()),
                    Atlas.BlueTile,
                )
            }
        }
        Text(
            if (split == null) "Area model: ${fact.row} wide, ${fact.column} long" else "Area model: ${fact.column} split into ${split.first} + ${split.second}",
            style = MaterialTheme.typography.bodySmall,
            color = Atlas.TextMuted,
            modifier = Modifier
                .padding(top = 4.dp)
                .clearAndSetSemantics { },
        )
    }
}

@Composable
private fun AreaPart(top: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color,
        border = BorderStroke(1.5.dp, Atlas.Navy),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(top, style = MaterialTheme.typography.bodyMedium, color = Atlas.Navy, textAlign = TextAlign.Center)
                Text(value, style = MaterialTheme.typography.titleLarge, color = Atlas.Navy, textAlign = TextAlign.Center)
            }
        }
    }
}
