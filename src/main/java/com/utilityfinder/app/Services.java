package com.utilityfinder.app;

import com.utilityfinder.repository.InsightsRepository;
import com.utilityfinder.repository.IntervalRepository;
import com.utilityfinder.repository.RatePlanRepository;
import com.utilityfinder.repository.WorkspaceRepository;
import com.utilityfinder.service.ComparisonService;
import com.utilityfinder.service.InsightsService;
import com.utilityfinder.service.IntervalImportService;
import com.utilityfinder.service.IntervalService;
import com.utilityfinder.service.RatePlanService;
import com.utilityfinder.service.WorkspaceService;

/**
 * Application-scoped service locator. All repositories and services are
 * singletons wired here; controllers obtain them via {@code Services.get()}.
 */
public class Services {

    private static final Services INSTANCE = new Services();

    private final WorkspaceRepository workspaceRepo  = new WorkspaceRepository();
    private final IntervalRepository  intervalRepo   = new IntervalRepository();
    private final RatePlanRepository  ratePlanRepo   = new RatePlanRepository();
    private final InsightsRepository  insightsRepo   = new InsightsRepository();

    public final WorkspaceService      workspaces     = new WorkspaceService(workspaceRepo);
    public final IntervalService       intervals      = new IntervalService(intervalRepo);
    public final IntervalImportService intervalImport = new IntervalImportService(intervalRepo);
    public final RatePlanService       ratePlans      = new RatePlanService(ratePlanRepo);
    public final ComparisonService     comparison     = new ComparisonService(intervals, ratePlans);
    public final InsightsService       insights       = new InsightsService(insightsRepo);

    private Services() {}

    public static Services get() { return INSTANCE; }
}
