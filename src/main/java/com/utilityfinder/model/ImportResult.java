package com.utilityfinder.model;

import java.time.LocalDate;

public record ImportResult(
        String esiid,
        int inserted,
        int overwritten,
        LocalDate from,
        LocalDate to
) {}
