package com.utilityfinder.model;

public class TouWindow {

    private Long id;
    private Long ratePlanId;
    private int startHour;   // 0–23, inclusive start of window
    private int endHour;     // 0–23, exclusive end (wraps midnight when startHour > endHour)
    private double ratePerKwh; // $/kWh; 0.0 = free during this window
    private int sortOrder;

    public TouWindow() {}

    public TouWindow(int startHour, int endHour, double ratePerKwh, int sortOrder) {
        this.startHour  = startHour;
        this.endHour    = endHour;
        this.ratePerKwh = ratePerKwh;
        this.sortOrder  = sortOrder;
    }

    public Long getId()                         { return id; }
    public void setId(Long id)                 { this.id = id; }

    public Long getRatePlanId()                 { return ratePlanId; }
    public void setRatePlanId(Long id)         { this.ratePlanId = id; }

    public int getStartHour()                   { return startHour; }
    public void setStartHour(int h)            { this.startHour = h; }

    public int getEndHour()                     { return endHour; }
    public void setEndHour(int h)              { this.endHour = h; }

    public double getRatePerKwh()               { return ratePerKwh; }
    public void setRatePerKwh(double r)        { this.ratePerKwh = r; }

    public int getSortOrder()                   { return sortOrder; }
    public void setSortOrder(int o)            { this.sortOrder = o; }

    /**
     * Returns true if the given hour (0–23) falls within this window.
     * When startHour > endHour the window spans midnight (e.g., 21–7 covers 9 pm – 7 am).
     */
    public boolean containsHour(int hour) {
        if (startHour == endHour) return false;
        if (startHour < endHour)  return hour >= startHour && hour < endHour;
        return hour >= startHour || hour < endHour;
    }
}
