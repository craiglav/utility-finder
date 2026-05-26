package com.utilityfinder.service;

import com.utilityfinder.model.ImportResult;
import com.utilityfinder.repository.IntervalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntervalImportServiceTest {

    @Mock IntervalRepository repo;
    IntervalImportService service;

    @TempDir Path tmp;

    @BeforeEach void setUp() {
        service = new IntervalImportService(repo);
        // Default: return {inserted=1, overwritten=0} unless overridden
        when(repo.saveBatch(anyLong(), anyList())).thenAnswer(inv -> {
            List<?> records = inv.getArgument(1);
            return new int[]{records.size(), 0};
        });
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test void twoRows_parsedAndSaved() throws IOException {
        File csv = csv("""
                ESIID,Date,Label,Start Time,Meter,kWh,Est,Type
                1234567890,05/01/2026,Electric,00:00,meter,1.500,,Consumption
                1234567890,05/01/2026,Electric,00:15,meter,1.250,,Consumption
                """);

        ImportResult result = service.importCsv(1L, csv);

        assertEquals(2, result.inserted());
        assertEquals("1234567890", result.esiid());
    }

    @Test void esiidSingleQuotePrefix_stripped() throws IOException {
        File csv = csv("""
                ESIID,Date,Label,Start Time,Meter,kWh,Est,Type
                '1234567890,05/01/2026,Electric,00:00,meter,1.500,,Consumption
                """);

        ImportResult result = service.importCsv(1L, csv);

        assertEquals("1234567890", result.esiid());
    }

    @Test void estimatedFlag_parsed() throws IOException {
        File csv = csv("""
                ESIID,Date,Label,Start Time,Meter,kWh,Est,Type
                1234567890,05/01/2026,Electric,00:00,meter,1.500,E,Consumption
                """);

        var captor = ArgumentCaptor.forClass(List.class);
        service.importCsv(1L, csv);
        verify(repo).saveBatch(eq(1L), captor.capture());

        var records = captor.getValue();
        assertTrue(((com.utilityfinder.model.IntervalRecord) records.get(0)).isEstimated());
    }

    @Test void startMinute_derivedFromTime() throws IOException {
        // 01:30 → startMinute = 90
        File csv = csv("""
                ESIID,Date,Label,Start Time,Meter,kWh,Est,Type
                1234567890,06/15/2026,Electric,01:30,meter,2.000,,Consumption
                """);

        var captor = ArgumentCaptor.forClass(List.class);
        service.importCsv(1L, csv);
        verify(repo).saveBatch(eq(1L), captor.capture());

        var record = (com.utilityfinder.model.IntervalRecord) captor.getValue().get(0);
        assertEquals(90, record.getStartMinute());
    }

    @Test void dateRange_computedFromRecords() throws IOException {
        File csv = csv("""
                ESIID,Date,Label,Start Time,Meter,kWh,Est,Type
                1234567890,01/01/2026,Electric,00:00,meter,1.0,,Consumption
                1234567890,03/31/2026,Electric,00:00,meter,1.0,,Consumption
                """);

        ImportResult result = service.importCsv(1L, csv);

        assertEquals(LocalDate.of(2026, 1, 1), result.from());
        assertEquals(LocalDate.of(2026, 3, 31), result.to());
    }

    // ── Header and blank line handling ────────────────────────────────────────

    @Test void headerRow_skipped() throws IOException {
        File csv = csv("""
                ESIID,Date,Label,Start Time,Meter,kWh,Est,Type
                1234567890,05/01/2026,Electric,00:00,meter,1.500,,Consumption
                """);

        service.importCsv(1L, csv);

        var captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveBatch(eq(1L), captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    @Test void blankLines_skipped() throws IOException {
        File csv = csv("""
                ESIID,Date,Label,Start Time,Meter,kWh,Est,Type

                1234567890,05/01/2026,Electric,00:00,meter,1.500,,Consumption

                """);

        service.importCsv(1L, csv);

        var captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveBatch(eq(1L), captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    @Test void shortLines_skipped() throws IOException {
        // Line with fewer than 8 columns
        File csv = csv("""
                ESIID,Date,Label,Start Time,Meter,kWh,Est,Type
                1234567890,05/01/2026,Electric,00:00
                1234567890,05/01/2026,Electric,00:00,meter,1.500,,Consumption
                """);

        service.importCsv(1L, csv);

        var captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveBatch(eq(1L), captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    // ── Solar / non-consumption rows ──────────────────────────────────────────

    @Test void solarExportRow_skipped() throws IOException {
        File csv = csv("""
                ESIID,Date,Label,Start Time,Meter,kWh,Est,Type
                1234567890,05/01/2026,Electric,00:00,meter,1.500,,Net Generation
                1234567890,05/01/2026,Electric,00:15,meter,1.250,,Consumption
                """);

        service.importCsv(1L, csv);

        var captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveBatch(eq(1L), captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    @Test void consumptionTypeIsCaseInsensitive() throws IOException {
        File csv = csv("""
                ESIID,Date,Label,Start Time,Meter,kWh,Est,Type
                1234567890,05/01/2026,Electric,00:00,meter,1.500,,CONSUMPTION
                """);

        service.importCsv(1L, csv);

        var captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveBatch(eq(1L), captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    // ── DST spring-forward (blank kWh) ────────────────────────────────────────

    @Test void dstSpringForward_blankKwh_skipped() throws IOException {
        File csv = csv("""
                ESIID,Date,Label,Start Time,Meter,kWh,Est,Type
                1234567890,03/08/2026,Electric,02:00,meter,,,Consumption
                1234567890,03/08/2026,Electric,03:00,meter,1.200,,Consumption
                """);

        service.importCsv(1L, csv);

        var captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveBatch(eq(1L), captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    // ── DST fall-back (duplicate keys) ───────────────────────────────────────

    @Test void dstFallBack_duplicateKey_lastValueWins() throws IOException {
        // Same date + time appears twice — second value should win
        File csv = csv("""
                ESIID,Date,Label,Start Time,Meter,kWh,Est,Type
                1234567890,11/01/2026,Electric,01:00,meter,1.000,,Consumption
                1234567890,11/01/2026,Electric,01:00,meter,2.000,,Consumption
                """);

        var captor = ArgumentCaptor.forClass(List.class);
        service.importCsv(1L, csv);
        verify(repo).saveBatch(eq(1L), captor.capture());

        // Deduplication: only one record, with the last kWh value
        List<?> records = captor.getValue();
        assertEquals(1, records.size());
        var record = (com.utilityfinder.model.IntervalRecord) records.get(0);
        assertEquals(2.000, record.getKwh(), 0.0001);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private File csv(String content) throws IOException {
        Path p = Files.createTempFile(tmp, "smt", ".csv");
        Files.writeString(p, content);
        return p.toFile();
    }
}
