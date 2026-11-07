package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class FocusTimerServiceTest {
    @TempDir Path root;
    private static final class MutableClock extends Clock {
        Instant time = Instant.parse("2026-10-09T10:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return time; }
        void advance(long seconds) { time = time.plusSeconds(seconds); }
    }

    @Test void countdownUsesRealElapsedTimeAndSurvivesReopeningPauseAndReset() throws Exception {
        var clock = new MutableClock(); Path file = root.resolve("focus.properties");
        var timer = new FocusTimerService(file, clock); assertFalse(Files.exists(file));
        timer.toggle(); clock.advance(45);
        var reopened = new FocusTimerService(file, clock); assertTrue(reopened.running()); assertEquals(1455_000, reopened.remainingMillis());
        reopened.toggle(); clock.advance(120); assertFalse(reopened.running()); assertEquals(1455_000, reopened.remainingMillis());
        var paused = new FocusTimerService(file, clock); assertFalse(paused.running()); assertEquals(1455_000, paused.remainingMillis());
        paused.toggle(); clock.advance(1456); assertFalse(paused.running()); assertEquals(0, paused.remainingMillis());
        paused.toggle(); assertTrue(paused.running()); assertEquals(FocusTimerService.DURATION, paused.remainingMillis());
        paused.reset(); assertFalse(paused.running()); assertEquals(FocusTimerService.DURATION, paused.remainingMillis());
    }

    @Test void failedSaveAndCorruptInputCannotSilentlyResetTheClock() throws Exception {
        var clock = new MutableClock(); Path file = root.resolve("focus.properties"); Files.createDirectory(file);
        var timer = new FocusTimerService(file, clock); assertNotNull(timer.error());
        assertThrows(Exception.class, timer::toggle); assertFalse(timer.running());
        Path corrupt = root.resolve("broken.properties"); Files.writeString(corrupt, "version=99\n");
        var broken = new FocusTimerService(corrupt, clock); assertThrows(Exception.class, broken::reset); assertEquals("version=99\n", Files.readString(corrupt));
    }
}
