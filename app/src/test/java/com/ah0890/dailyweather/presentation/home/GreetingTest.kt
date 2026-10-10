package com.ah0890.dailyweather.presentation.home

import com.ah0890.dailyweather.R
import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {

    @Test
    fun `early morning hours greet with good morning`() {
        assertEquals(R.string.home_greeting_morning, greetingFor(5))
        assertEquals(R.string.home_greeting_morning, greetingFor(11))
    }

    @Test
    fun `midday through late afternoon greet with good afternoon`() {
        assertEquals(R.string.home_greeting_afternoon, greetingFor(12))
        assertEquals(R.string.home_greeting_afternoon, greetingFor(16))
    }

    @Test
    fun `evening and night hours greet with good evening`() {
        assertEquals(R.string.home_greeting_evening, greetingFor(17))
        assertEquals(R.string.home_greeting_evening, greetingFor(23))
        assertEquals(R.string.home_greeting_evening, greetingFor(0))
        assertEquals(R.string.home_greeting_evening, greetingFor(4))
    }
}
