package com.utilityfinder.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class IntervalRecord {

    private Long id;
    private Long workspaceId;
    private String esiid;
    private LocalDate readingDate;
    private int startMinute;   // minutes from midnight: 0, 15, 30, …, 1425
    private double kwh;
    private boolean estimated;
    private LocalDateTime importedAt;

    public IntervalRecord() {}

    public Long getId()                              { return id; }
    public void setId(Long id)                      { this.id = id; }

    public Long getWorkspaceId()                     { return workspaceId; }
    public void setWorkspaceId(Long workspaceId)    { this.workspaceId = workspaceId; }

    public String getEsiid()                         { return esiid; }
    public void setEsiid(String esiid)              { this.esiid = esiid; }

    public LocalDate getReadingDate()                { return readingDate; }
    public void setReadingDate(LocalDate readingDate){ this.readingDate = readingDate; }

    public int getStartMinute()                      { return startMinute; }
    public void setStartMinute(int startMinute)     { this.startMinute = startMinute; }

    public double getKwh()                           { return kwh; }
    public void setKwh(double kwh)                  { this.kwh = kwh; }

    public boolean isEstimated()                     { return estimated; }
    public void setEstimated(boolean estimated)     { this.estimated = estimated; }

    public LocalDateTime getImportedAt()             { return importedAt; }
    public void setImportedAt(LocalDateTime t)      { this.importedAt = t; }
}
