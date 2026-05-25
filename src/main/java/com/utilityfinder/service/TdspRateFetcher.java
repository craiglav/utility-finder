package com.utilityfinder.service;

import com.utilityfinder.model.TdspRates;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches the current delivery rates for a TDSP from PUCT's published rate-report PDFs.
 * PUCT hosts these at a predictable HTTPS URL; they are updated on each rate case (typically
 * March 1 and September 1).
 */
public class TdspRateFetcher {

    private static final String BASE_URL =
            "https://ftp.puc.texas.gov/public/puct-info/industry/electric/rates/tdr/tdu/";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    // PUCT rate-report PDFs share a consistent table layout (verified May 2026 across
    // CenterPoint, Oncor, and AEP).  Residential rates always appear first.
    //
    // Example rows:
    //   Customer Charge             per Customer per Month  $                  2.11
    //   Residential Metering Charge per Meter    per Month  $                  2.79
    //   Volumetric Charge           per kWh                 $            0.049993
    //
    // Base charge = Customer Charge + Residential Metering Charge (both fixed monthly).
    // Per-kWh  = first Volumetric Charge per kWh row (= residential class).

    private static final Pattern CUSTOMER_CHARGE = Pattern.compile(
            "(?im)^Customer\\s+Charge[^\\r\\n]*\\$\\s*([\\d.]+)");

    private static final Pattern METERING_CHARGE = Pattern.compile(
            "(?im)Residential\\s+Metering\\s+Charge[^\\r\\n]*\\$\\s*([\\d.]+)");

    // "Volumetric Charge" or "Volumetric Charges" depending on TDSP
    private static final Pattern VOLUMETRIC_KWH = Pattern.compile(
            "(?im)^Volumetric\\s+Charges?\\s+per\\s+kWh[^\\r\\n]*\\$\\s*([\\d.]+)");

    /**
     * Fetches the PDF for {@code pdfFilename} and parses the customer base charge and
     * per-kWh distribution charge.  Returns empty if the PDF cannot be fetched or parsed.
     *
     * @throws IOException          on network failure
     * @throws InterruptedException if the calling thread is interrupted
     */
    public Optional<TdspRates> fetch(String pdfFilename) throws IOException, InterruptedException {
        byte[] pdfBytes = downloadPdf(pdfFilename);
        String text = extractText(pdfBytes);
        return parseRates(text);
    }

    // ── Network ───────────────────────────────────────────────────────────────

    private byte[] downloadPdf(String filename) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + filename))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new IOException("HTTP " + resp.statusCode() + " fetching " + filename);
        }
        return resp.body();
    }

    // ── PDF text extraction ───────────────────────────────────────────────────

    private String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    // ── Rate parsing ──────────────────────────────────────────────────────────

    Optional<TdspRates> parseRates(String text) {
        Optional<Double> customer = firstMatch(CUSTOMER_CHARGE, text);
        Optional<Double> metering = firstMatch(METERING_CHARGE, text);
        Optional<Double> kwh      = firstMatch(VOLUMETRIC_KWH,  text);

        if (customer.isEmpty() || kwh.isEmpty()) return Optional.empty();
        double base = customer.get() + metering.orElse(0.0);
        return Optional.of(new TdspRates(base, kwh.get(), LocalDate.now()));
    }

    private static Optional<Double> firstMatch(Pattern p, String text) {
        Matcher m = p.matcher(text);
        if (!m.find()) return Optional.empty();
        try { return Optional.of(Double.parseDouble(m.group(1))); }
        catch (NumberFormatException ignored) { return Optional.empty(); }
    }
}
