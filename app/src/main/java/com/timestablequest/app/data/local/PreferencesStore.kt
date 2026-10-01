package com.timestablequest.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.timestablequest.app.domain.facts.FACTORS
import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.facts.Facts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    /** Rows used for the next daily set. Never empty. */
    val dailyRows: Set<Int> = ALL_ROWS,
    /** Default rows preselected for Mixed Practice. Never empty. */
    val mixedRows: Set<Int> = ALL_ROWS,
    val reducedMotion: Boolean = false,
) {
    companion object {
        val ALL_ROWS: Set<Int> = FACTORS.toSortedSet()
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "timestablequest_prefs")

/** DataStore for preferences, the calculator draft and lightweight UI state (study visits). */
class PreferencesStore(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val dailyRows = stringPreferencesKey("daily_rows")
        val mixedRows = stringPreferencesKey("mixed_rows")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val calculatorDraft = stringPreferencesKey("calculator_draft")
        val studyVisited = stringSetPreferencesKey("study_visited")
    }

    private fun rowsOrAll(text: String?): Set<Int> = Facts.decodeRows(text).ifEmpty { AppSettings.ALL_ROWS }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            dailyRows = rowsOrAll(p[Keys.dailyRows]),
            mixedRows = rowsOrAll(p[Keys.mixedRows]),
            reducedMotion = p[Keys.reducedMotion] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    /** Requires at least one row; an empty selection is ignored. */
    suspend fun setDailyRows(rows: Set<Int>) {
        if (rows.any { it in FACTORS }) store.edit { it[Keys.dailyRows] = Facts.encodeRows(rows) }
    }

    suspend fun setMixedRows(rows: Set<Int>) {
        if (rows.any { it in FACTORS }) store.edit { it[Keys.mixedRows] = Facts.encodeRows(rows) }
    }

    suspend fun setReducedMotion(v: Boolean) = store.edit { it[Keys.reducedMotion] = v }

    val calculatorDraft: Flow<String?> = store.data.map { it[Keys.calculatorDraft] }
    suspend fun setCalculatorDraft(encoded: String) = store.edit { it[Keys.calculatorDraft] = encoded }
    suspend fun clearCalculatorDraft() = store.edit { it.remove(Keys.calculatorDraft) }

    /** Facts opened in Study mode. Tracked separately from scored accuracy; never counts as correct. */
    val studyVisited: Flow<Set<Fact>> = store.data.map { p ->
        p[Keys.studyVisited].orEmpty().mapNotNull(Fact::parseKey).toSet()
    }

    suspend fun markStudied(fact: Fact) = store.edit { p ->
        p[Keys.studyVisited] = p[Keys.studyVisited].orEmpty() + fact.key
    }

    suspend fun clearStudyVisitsForRow(row: Int) = store.edit { p ->
        p[Keys.studyVisited] = p[Keys.studyVisited].orEmpty().filterNot { Fact.parseKey(it)?.row == row }.toSet()
    }

    suspend fun clearStudyVisits() = store.edit { it.remove(Keys.studyVisited) }

    suspend fun clearAll() = store.edit { it.clear() }
}
