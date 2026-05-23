package com.utilityfinder.model;

import java.time.LocalDate;
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
    private Double renewablePercent;
    private Double terminationFeeFlat;
    private Double terminationFeePerMonth;
    private LocalDate contractEndDate;
    private List<TierDiscount> discounts   = new ArrayList<>();
    private List<TouWindow>    touWindows  = new ArrayList<>();

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

    public Double getRenewablePercent()                       { return renewablePercent; }
    public void setRenewablePercent(Double renewablePercent) { this.renewablePercent = renewablePercent; }

    public Double getTerminationFeeFlat()                             { return terminationFeeFlat; }
    public void setTerminationFeeFlat(Double terminationFeeFlat)     { this.terminationFeeFlat = terminationFeeFlat; }

    public Double getTerminationFeePerMonth()                                 { return terminationFeePerMonth; }
    public void setTerminationFeePerMonth(Double terminationFeePerMonth)     { this.terminationFeePerMonth = terminationFeePerMonth; }

    public LocalDate getContractEndDate()                            { return contractEndDate; }
    public void setContractEndDate(LocalDate contractEndDate)       { this.contractEndDate = contractEndDate; }

    public List<TierDiscount> getDiscounts()               { return discounts; }
    public void setDiscounts(List<TierDiscount> discounts) { this.discounts = discounts; }

    public List<TouWindow> getTouWindows()                { return touWindows; }
    public void setTouWindows(List<TouWindow> windows)   { this.touWindows = windows; }
    public boolean hasTouWindows()                        { return !touWindows.isEmpty(); }
}
