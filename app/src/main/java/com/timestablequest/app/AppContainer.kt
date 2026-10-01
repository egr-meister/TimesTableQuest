package com.timestablequest.app

import android.content.Context
import com.timestablequest.app.data.local.AppDatabase
import com.timestablequest.app.data.local.PreferencesStore
import com.timestablequest.app.data.repository.CalculatorRepository
import com.timestablequest.app.data.repository.DataResetter
import com.timestablequest.app.data.repository.PracticeRepository
import com.timestablequest.app.data.repository.PracticeService
import com.timestablequest.app.data.repository.RoomPracticeStore
import com.timestablequest.app.domain.progress.Clock
import com.timestablequest.app.domain.progress.DateProvider
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import kotlin.random.Random

/** Manual dependency injection. Clock, date and random sources are injected for testability. */
class AppContainer(context: Context) {
    private val io = Dispatchers.IO
    val clock: Clock = Clock { System.currentTimeMillis() }
    val dates: DateProvider = DateProvider { LocalDate.now() }

    val database: AppDatabase by lazy { AppDatabase.build(context) }
    val preferences: PreferencesStore by lazy { PreferencesStore(context) }

    val practice: PracticeRepository by lazy {
        PracticeRepository(PracticeService(RoomPracticeStore(database), Random.Default, clock, dates), io)
    }
    val calculator: CalculatorRepository by lazy { CalculatorRepository(database.calculationDao(), clock, io) }
    val resetter: DataResetter by lazy { DataResetter(database, preferences, practice, calculator, io) }
}
