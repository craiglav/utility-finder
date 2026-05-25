package com.utilityfinder.model;

import java.time.LocalDate;

public class Tdsp {

    private Long      id;
    private String    name;
    private String    esiidPrefix;
    private String    pdfFilename;
    private double    baseCharge;
    private double    perKwhCharge;
    private LocalDate effectiveDate;
    private LocalDate lastVerified;

    public Tdsp() {}

    public Long      getId()                           { return id; }
    public void      setId(Long id)                   { this.id = id; }

    public String    getName()                         { return name; }
    public void      setName(String name)             { this.name = name; }

    public String    getEsiidPrefix()                  { return esiidPrefix; }
    public void      setEsiidPrefix(String p)         { this.esiidPrefix = p; }

    /** Filename of the PUCT rate-report PDF, e.g. "CenterPoint_Rate_Report.pdf". Null = no auto-fetch. */
    public String    getPdfFilename()                  { return pdfFilename; }
    public void      setPdfFilename(String f)         { this.pdfFilename = f; }

    public double    getBaseCharge()                   { return baseCharge; }
    public void      setBaseCharge(double v)          { this.baseCharge = v; }

    public double    getPerKwhCharge()                 { return perKwhCharge; }
    public void      setPerKwhCharge(double v)        { this.perKwhCharge = v; }

    public LocalDate getEffectiveDate()                { return effectiveDate; }
    public void      setEffectiveDate(LocalDate d)    { this.effectiveDate = d; }

    public LocalDate getLastVerified()                 { return lastVerified; }
    public void      setLastVerified(LocalDate d)     { this.lastVerified = d; }

    @Override
    public String toString() { return name; }
}
