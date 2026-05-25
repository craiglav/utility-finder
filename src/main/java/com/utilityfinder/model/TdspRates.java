package com.utilityfinder.model;

import java.time.LocalDate;

/** Parsed rate data returned by the PUCT PDF fetcher before the user confirms. */
public record TdspRates(double baseCharge, double perKwhCharge, LocalDate effectiveDate) {}
