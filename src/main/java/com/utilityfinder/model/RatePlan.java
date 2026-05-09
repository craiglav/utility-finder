package com.utilityfinder.model;

import java.util.ArrayList;
import java.util.List;

public class RatePlan {

    private Long id;
    private Long workspaceId;
    private String providerName;
    private String planName;
    private Integer contractTermMonths;
    private double baseCharge;
    private double ratePerKwh;
    private String notes;
    private boolean current;
    private List<TierDiscount> discounts = new ArrayList<>();

    public RatePlan() {}

    public Long getId()                                   { return id; }
    public void setId(Long id)                           { this.id = id; }

    public Long getWorkspaceId()                          { return workspaceId; }
    public void setWorkspaceId(Long workspaceId)         { this.workspaceId = workspaceId; }

    public String getProviderName()                       { return providerName; }
    public void setProviderName(String providerName)     { this.providerName = providerName; }

    public String getPlanName()                           { return planName; }
    public void setPlanName(String planName)             { this.planName = planName; }

    public Integer getContractTermMonths()                        { return contractTermMonths; }
    public void setContractTermMonths(Integer contractTermMonths) { this.contractTermMonths = contractTermMonths; }

    public double getBaseCharge()                         { return baseCharge; }
    public void setBaseCharge(double baseCharge)         { this.baseCharge = baseCharge; }

    public double getRatePerKwh()                         { return ratePerKwh; }
    public void setRatePerKwh(double ratePerKwh)         { this.ratePerKwh = ratePerKwh; }

    public String getNotes()                              { return notes; }
    public void setNotes(String notes)                   { this.notes = notes; }

    public boolean isCurrent()                            { return current; }
    public void setCurrent(boolean current)              { this.current = current; }

    public List<TierDiscount> getDiscounts()              { return discounts; }
    public void setDiscounts(List<TierDiscount> discounts) { this.discounts = discounts; }
}
