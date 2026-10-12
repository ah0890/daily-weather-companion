package com.ah0890.dailyweather.domain.usecase

import com.ah0890.dailyweather.domain.model.CommuteTimes
import com.ah0890.dailyweather.domain.model.OfficeDay
import com.ah0890.dailyweather.domain.model.TransportMode
import com.ah0890.dailyweather.domain.model.WeatherForecast

/** Combines the commute, travel advice, score and checklist for the next office day. */
object OfficeDayPlanner {

    /** @return null when the forecast doesn't cover the next commute. */
    fun plan(forecast: WeatherForecast, times: CommuteTimes, mode: TransportMode): OfficeDay? {
        val summary = CommuteAnalysis.summary(forecast, times) ?: return null
        val workday = CommuteAnalysis.workdayHours(forecast, summary)
        return OfficeDay(
            summary = summary,
            advice = summary.legs.map { TravelAdvisor.advise(it, mode) },
            score = OfficeScore.of(summary, workday),
            checklist = TravelChecklist.entries(summary, workday, mode),
        )
    }
}
