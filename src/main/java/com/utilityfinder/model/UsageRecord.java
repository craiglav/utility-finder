package com.utilityfinder.model;

public class UsageRecord {

    private Long id;
    private Long workspaceId;
    private int year;
    private int month; // 1–12
    private double kwhUsed;

    public UsageRecord() {}

    public UsageRecord(Long workspaceId, int year, int month, double kwhUsed) {
        this.workspaceId = workspaceId;
        this.year = year;
        this.month = month;
        this.kwhUsed = kwhUsed;
    }

    public UsageRecord(Long id, Long workspaceId, int year, int month, double kwhUsed) {
        this(workspaceId, year, month, kwhUsed);
        this.id = id;
    }

    public Long getId()                       { return id; }
    public void setId(Long id)               { this.id = id; }

    public Long getWorkspaceId()              { return workspaceId; }
    public void setWorkspaceId(Long wid)     { this.workspaceId = wid; }

    public int getYear()                      { return year; }
    public void setYear(int year)            { this.year = year; }

    public int getMonth()                     { return month; }
    public void setMonth(int month)          { this.month = month; }

    public double getKwhUsed()                { return kwhUsed; }
    public void setKwhUsed(double kwhUsed)   { this.kwhUsed = kwhUsed; }
}
