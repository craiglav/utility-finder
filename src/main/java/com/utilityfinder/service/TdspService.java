package com.utilityfinder.service;

import com.utilityfinder.model.Tdsp;
import com.utilityfinder.model.TdspRates;
import com.utilityfinder.repository.TdspRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class TdspService {

    private static final String ZIP_RESOURCE =
            "/com/utilityfinder/data/zip_tdsp.properties";

    private final TdspRepository    repo;
    private final TdspRateFetcher   fetcher;
    private final Properties        zipMap;

    public TdspService(TdspRepository repo) {
        this.repo    = repo;
        this.fetcher = new TdspRateFetcher();
        this.zipMap  = loadZipMap();
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<Tdsp> findAll()                               { return repo.findAll(); }
    public Optional<Tdsp> findForWorkspace(long workspaceId) { return repo.findForWorkspace(workspaceId); }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public void setForWorkspace(long workspaceId, Long tdspId) {
        repo.setForWorkspace(workspaceId, tdspId);
    }

    public void applyRates(long tdspId, TdspRates rates) {
        repo.updateRates(tdspId, rates.baseCharge(), rates.perKwhCharge(),
                rates.effectiveDate(), LocalDate.now());
    }

    // ── Auto-detection from ESIID ─────────────────────────────────────────────

    /**
     * If the workspace has no TDSP set, detects one from the ESIID's 4-digit prefix
     * and silently associates it.  Safe to call after every CSV import.
     */
    public void autoDetectIfNeeded(long workspaceId, String esiid) {
        if (esiid == null || esiid.length() < 4) return;
        if (repo.findForWorkspace(workspaceId).isPresent()) return;

        String prefix = esiid.substring(0, 4);
        repo.findByEsiidPrefix(prefix).ifPresent(tdsp ->
                repo.setForWorkspace(workspaceId, tdsp.getId()));
    }

    // ── ZIP lookup ────────────────────────────────────────────────────────────

    /**
     * Looks up the TDSP for a Texas ZIP code using a bundled prefix mapping.
     * Returns empty if the prefix is not in the map (non-deregulated markets,
     * mixed territories, etc.).  Result should be treated as a suggestion only.
     */
    public Optional<Tdsp> lookupByZip(String zip) {
        if (zip == null || zip.length() < 3) return Optional.empty();
        String prefix = zip.strip().substring(0, 3);
        String name   = zipMap.getProperty(prefix);
        if (name == null) return Optional.empty();
        return repo.findByName(name);
    }

    // ── Rate refresh ──────────────────────────────────────────────────────────

    /**
     * Fetches the PUCT PDF for {@code tdsp} and returns the parsed rates.
     * Throws {@link IOException} on network or parse failure.
     * The caller is responsible for confirming with the user before calling
     * {@link #applyRates(long, TdspRates)}.
     */
    public TdspRates fetchUpdatedRates(Tdsp tdsp) throws IOException, InterruptedException {
        String filename = tdsp.getPdfFilename();
        if (filename == null || filename.isBlank()) {
            throw new IOException(tdsp.getName() + " has no PUCT rate-report PDF configured.");
        }
        return fetcher.fetch(filename)
                .orElseThrow(() -> new IOException(
                        "Could not parse rates from " + filename +
                        ". The PUCT PDF format may have changed — please enter rates manually."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Properties loadZipMap() {
        Properties p = new Properties();
        try (InputStream in = TdspService.class.getResourceAsStream(ZIP_RESOURCE)) {
            if (in != null) p.load(in);
        } catch (IOException ignored) {}
        return p;
    }
}
