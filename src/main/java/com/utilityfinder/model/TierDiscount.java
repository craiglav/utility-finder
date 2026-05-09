package com.utilityfinder.model;

public class TierDiscount {

    private Long id;
    private Long ratePlanId;
    private double thresholdKwh;
    private double discountAmt;
    private int sortOrder;

    public TierDiscount() {}

    public TierDiscount(double thresholdKwh, double discountAmt, int sortOrder) {
        this.thresholdKwh = thresholdKwh;
        this.discountAmt = discountAmt;
        this.sortOrder = sortOrder;
    }

    public Long getId()                           { return id; }
    public void setId(Long id)                   { this.id = id; }

    public Long getRatePlanId()                   { return ratePlanId; }
    public void setRatePlanId(Long ratePlanId)   { this.ratePlanId = ratePlanId; }

    public double getThresholdKwh()               { return thresholdKwh; }
    public void setThresholdKwh(double t)        { this.thresholdKwh = t; }

    public double getDiscountAmt()                { return discountAmt; }
    public void setDiscountAmt(double d)         { this.discountAmt = d; }

    public int getSortOrder()                     { return sortOrder; }
    public void setSortOrder(int sortOrder)      { this.sortOrder = sortOrder; }
}
