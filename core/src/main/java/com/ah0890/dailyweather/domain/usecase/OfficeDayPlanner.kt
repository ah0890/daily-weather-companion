package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.CommuteTimes
import com.ah0890.dailyweather.domain.model.OfficeDay
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.UserPreferences
import com.ah0890.dailyweather.domain.model.WeatherForecast
import java.time.DayOfWeek

/** Combines the commute, travel advice, score and checklist for the next office day. */
object OfficeDayPlanner {

    /**
     * @param forecast weather at home, where the morning trip starts.
     * @param officeForecast weather at the office: the evening trip and the working day.
     * @return null when no workday is set or the forecast doesn't cover the next commute.
     */
    fun plan(
        forecast: WeatherForecast,
        times: CommuteTimes,
        mode: TransportMode,
        workdays: Set<DayOfWeek> = UserPreferences.EVERY_DAY,
        officeForecast: WeatherForecast = forecast,
    ): OfficeDay? {
        val summary = CommuteAnalysis.summary(forecast, times, workdays, officeForecast) ?: return null
        val workday = CommuteAnalysis.workdayHours(officeForecast, summary)
        return OfficeDay(
            summary = summary,
            advice = summary.legs.map { TravelAdvisor.advise(it, mode) },
            score = OfficeScore.of(summary, workday),
            checklist = TravelChecklist.entries(summary, workday, mode),
        )
    }
}
