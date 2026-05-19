package com.utilityfinder.service;

import com.utilityfinder.model.ImportResult;
import com.utilityfinder.model.IntervalRecord;
import com.utilityfinder.repository.IntervalRepository;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IntervalImportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final IntervalRepository repo;

    public IntervalImportService(IntervalRepository repo) {
        this.repo = repo;
    }

    /**
     * Parses a Smart Meter Texas 15-minute interval CSV and upserts the records
     * for the given workspace.  Surplus generation rows and blank-kWh rows
     * (DST spring-forward phantoms) are skipped.  Duplicate (date, startMinute)
     * keys — which arise during DST fall-back when the 1–2 AM hour repeats —
     * are deduplicated by keeping the last occurrence.
     */
    public ImportResult importCsv(long workspaceId, File file) throws IOException {
        // Keyed on "date:startMinute" so DST fall-back duplicates are collapsed.
        // LinkedHashMap preserves insertion order; later occurrences overwrite earlier ones.
        Map<String, IntervalRecord> recordMap = new LinkedHashMap<>();
        String esiid = null;

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            reader.readLine(); // skip header

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] parts = line.split(",", -1);
                if (parts.length < 8) continue;

                // Skip solar surplus export rows
                if (!"Consumption".equalsIgnoreCase(parts[7].strip())) continue;

                // SMT prefixes the ESIID with a single-quote to stop Excel from
                // treating the long number as scientific notation
                String rowEsiid = parts[0].strip().replaceFirst("^'", "");
                if (esiid == null) esiid = rowEsiid;

                // DST spring-forward: the non-existent 2–3 AM slots have a blank kWh
                String kwhStr = parts[5].strip();
                if (kwhStr.isEmpty()) continue;

                LocalDate date = LocalDate.parse(parts[1].strip(), DATE_FMT);

                // start_minute: derive from USAGE_START_TIME "HH:MM"
                String[] timeParts = parts[3].strip().split(":");
                int startMinute = Integer.parseInt(timeParts[0]) * 60
                        + Integer.parseInt(timeParts[1]);

                double kwh       = Double.parseDouble(kwhStr);
                boolean estimated = "E".equalsIgnoreCase(parts[6].strip());

                IntervalRecord r = new IntervalRecord();
                r.setWorkspaceId(workspaceId);
                r.setEsiid(rowEsiid);
                r.setReadingDate(date);
                r.setStartMinute(startMinute);
                r.setKwh(kwh);
                r.setEstimated(estimated);

                // Overwrite duplicate keys (DST fall-back: last occurrence wins)
                recordMap.put(date + ":" + startMinute, r);
            }
        }

        List<IntervalRecord> records = new ArrayList<>(recordMap.values());
        int[] counts = repo.saveBatch(workspaceId, records);

        LocalDate from = records.stream()
                .map(IntervalRecord::getReadingDate)
                .min(Comparator.naturalOrder())
                .orElse(null);
        LocalDate to = records.stream()
                .map(IntervalRecord::getReadingDate)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new ImportResult(esiid, counts[0], counts[1], from, to);
    }
}
