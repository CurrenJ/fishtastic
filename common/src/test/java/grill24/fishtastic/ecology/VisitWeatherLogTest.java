package grill24.fishtastic.ecology;

import org.junit.jupiter.api.Test;

import java.util.List;

import static grill24.fishtastic.ecology.VisitWeatherLog.State.CLEAR;
import static grill24.fishtastic.ecology.VisitWeatherLog.State.RAIN;
import static grill24.fishtastic.ecology.VisitWeatherLog.State.THUNDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisitWeatherLogTest {
    @Test
    void unchangedTicksDoNotGrowTheLog() {
        VisitWeatherLog log = new VisitWeatherLog();
        assertTrue(log.record(10, CLEAR));
        for (long t = 11; t < 10_000; t++) assertFalse(log.record(t, CLEAR));
        assertTrue(log.record(10_000, RAIN));
        assertEquals(List.of(new VisitWeatherLog.Entry(10, CLEAR), new VisitWeatherLog.Entry(10_000, RAIN)), log.entries());
    }

    @Test
    void lookupUsesTheLastEntryAtOrBeforeTheWindowStart() {
        VisitWeatherLog log = new VisitWeatherLog(List.of(new VisitWeatherLog.Entry(10, CLEAR),
                new VisitWeatherLog.Entry(20, RAIN), new VisitWeatherLog.Entry(30, THUNDER)));
        assertEquals(new VisitWeatherLog.Sample(CLEAR, false), log.at(19, THUNDER));
        assertEquals(new VisitWeatherLog.Sample(RAIN, false), log.at(20, CLEAR));
        assertEquals(new VisitWeatherLog.Sample(RAIN, false), log.at(29, CLEAR));
        assertEquals(new VisitWeatherLog.Sample(THUNDER, false), log.at(Long.MAX_VALUE, CLEAR));
    }

    @Test
    void unknownHistoryFallsBackExplicitlyToTheCurrentWeather() {
        VisitWeatherLog log = new VisitWeatherLog();
        assertEquals(new VisitWeatherLog.Sample(RAIN, true), log.at(10, RAIN));
        log.record(20, THUNDER);
        assertEquals(new VisitWeatherLog.Sample(CLEAR, true), log.at(19, CLEAR));
        assertEquals(new VisitWeatherLog.Sample(THUNDER, false), log.at(20, CLEAR));
    }

    @Test
    void pruningKeepsTheAnchorAndEveryLookupInsideTheRetainedSpan() {
        VisitWeatherLog log = new VisitWeatherLog(List.of(new VisitWeatherLog.Entry(10, CLEAR),
                new VisitWeatherLog.Entry(20, RAIN), new VisitWeatherLog.Entry(30, THUNDER),
                new VisitWeatherLog.Entry(40, CLEAR)));
        List<VisitWeatherLog.Sample> before = java.util.stream.LongStream.rangeClosed(35, 60)
                .mapToObj(t -> log.at(t, RAIN)).toList();
        assertEquals(2, log.prune(35));
        assertEquals(new VisitWeatherLog.Entry(30, THUNDER), log.entries().get(0));
        assertEquals(before, java.util.stream.LongStream.rangeClosed(35, 60).mapToObj(t -> log.at(t, RAIN)).toList());
        assertEquals(0, log.prune(35));
    }

    @Test
    void pruningAtAChangeKeepsThatExactEntryAndAnIdleWorldKeepsOneAnchor() {
        VisitWeatherLog log = new VisitWeatherLog(List.of(new VisitWeatherLog.Entry(10, CLEAR),
                new VisitWeatherLog.Entry(20, RAIN), new VisitWeatherLog.Entry(30, THUNDER)));
        assertEquals(1, log.prune(20));
        assertEquals(new VisitWeatherLog.Sample(RAIN, false), log.at(20, CLEAR));
        assertEquals(1, log.prune(Long.MAX_VALUE));
        assertEquals(List.of(new VisitWeatherLog.Entry(30, THUNDER)), log.entries());
    }

    @Test
    void repeatedSamplesInOneTickKeepOnlyItsFinalState() {
        VisitWeatherLog log = new VisitWeatherLog();
        log.record(10, CLEAR);
        log.record(20, RAIN);
        log.record(20, THUNDER);
        assertEquals(new VisitWeatherLog.Sample(THUNDER, false), log.at(20, CLEAR));
        log.record(20, CLEAR);
        assertEquals(List.of(new VisitWeatherLog.Entry(10, CLEAR)), log.entries());
    }

    @Test
    void loadingNormalizesOrderAndRedundantStatesWithoutInventingHistory() {
        VisitWeatherLog log = new VisitWeatherLog(List.of(new VisitWeatherLog.Entry(30, RAIN),
                new VisitWeatherLog.Entry(10, CLEAR), new VisitWeatherLog.Entry(20, RAIN)));
        assertEquals(List.of(new VisitWeatherLog.Entry(10, CLEAR), new VisitWeatherLog.Entry(20, RAIN)), log.entries());
        assertTrue(log.at(9, THUNDER).fallback());
        assertEquals(THUNDER, VisitWeatherLog.State.fromFlags(true, true));
        assertEquals(RAIN, VisitWeatherLog.State.fromFlags(true, false));
        assertEquals(CLEAR, VisitWeatherLog.State.fromFlags(false, false));
    }

    @Test
    void evenAChangeEveryTickStaysBoundedByTheRetainedSpan() {
        VisitWeatherLog log = new VisitWeatherLog();
        for (long t = 0; t < 20_000; t++) {
            log.record(t, t % 2 == 0 ? CLEAR : RAIN);
            log.prune(t - 200);
            assertTrue(log.entries().size() <= 201);
        }
        assertEquals(201, log.entries().size());
        assertEquals(new VisitWeatherLog.Entry(19_799, RAIN), log.entries().get(0));
    }

    @Test
    void timeGoingBackDropsTheEntriesAfterItInsteadOfFailing() {
        VisitWeatherLog log = new VisitWeatherLog();
        log.record(100, RAIN);
        log.record(500, THUNDER);
        log.record(900, CLEAR);
        assertTrue(log.record(400, CLEAR));
        assertEquals(List.of(new VisitWeatherLog.Entry(100, RAIN), new VisitWeatherLog.Entry(400, CLEAR)),
                log.entries());
        assertEquals(RAIN, log.at(300, CLEAR).state());
    }
}
