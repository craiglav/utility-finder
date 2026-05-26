package com.utilityfinder.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TouWindowTest {

    private static TouWindow window(int start, int end) {
        return new TouWindow(start, end, 0.0, 0);
    }

    // ── Same-hour (invalid) ───────────────────────────────────────────────────

    @Test void sameHour_neverContains() {
        TouWindow w = window(9, 9);
        assertFalse(w.containsHour(9));
        assertFalse(w.containsHour(0));
        assertFalse(w.containsHour(23));
    }

    // ── Normal (non-wrapping) windows ─────────────────────────────────────────

    @Test void normal_containsStart() {
        assertTrue(window(9, 17).containsHour(9));
    }

    @Test void normal_containsMiddle() {
        assertTrue(window(9, 17).containsHour(12));
    }

    @Test void normal_doesNotContainEnd() {
        // end is exclusive
        assertFalse(window(9, 17).containsHour(17));
    }

    @Test void normal_doesNotContainBeforeStart() {
        assertFalse(window(9, 17).containsHour(8));
    }

    @Test void normal_doesNotContainAfterEnd() {
        assertFalse(window(9, 17).containsHour(20));
    }

    // ── Midnight-wrapping windows (startHour > endHour) ───────────────────────

    @Test void wrap_containsStartHour() {
        assertTrue(window(21, 7).containsHour(21));
    }

    @Test void wrap_containsHourAfterMidnight() {
        assertTrue(window(21, 7).containsHour(0));
    }

    @Test void wrap_containsHourBeforeEnd() {
        assertTrue(window(21, 7).containsHour(6));
    }

    @Test void wrap_doesNotContainEndHour() {
        assertFalse(window(21, 7).containsHour(7));
    }

    @Test void wrap_doesNotContainHourBeforeStart() {
        assertFalse(window(21, 7).containsHour(20));
    }

    @Test void wrap_doesNotContainMiddleOfDay() {
        assertFalse(window(21, 7).containsHour(14));
    }

    // ── Edge: window ending at midnight (e.g., 22–0) ─────────────────────────

    @Test void endAtMidnight_containsHoursUntilEnd() {
        TouWindow w = window(22, 0);
        assertTrue(w.containsHour(22));
        assertTrue(w.containsHour(23));
        assertFalse(w.containsHour(0));
        assertFalse(w.containsHour(21));
    }

    // ── Edge: window starting at midnight (e.g., 0–6) ────────────────────────

    @Test void startAtMidnight_normalWindow() {
        TouWindow w = window(0, 6);
        assertTrue(w.containsHour(0));
        assertTrue(w.containsHour(5));
        assertFalse(w.containsHour(6));
        assertFalse(w.containsHour(23));
    }
}
