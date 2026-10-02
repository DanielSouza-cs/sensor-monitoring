package com.sensormonitoring.warehouse.ingestion;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the sensor wire format {@code sensor_id=<id>; value=<decimal>}. Whitespace around tokens is tolerated;
 * the value is bounded so it always fits a finite double.
 */
final class MeasurementParser {

    private static final Pattern FORMAT = Pattern.compile(
            "\\s*sensor_id\\s*=\\s*([\\w.-]{1,64})\\s*;\\s*value\\s*=\\s*([+-]?\\d{1,9}(?:\\.\\d{1,9})?)\\s*");

    private MeasurementParser() {
    }

    static Optional<Reading> parse(String line) {
        Matcher matcher = FORMAT.matcher(line);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new Reading(matcher.group(1), Double.parseDouble(matcher.group(2))));
    }

    record Reading(String sensorId, double value) {
    }
}
