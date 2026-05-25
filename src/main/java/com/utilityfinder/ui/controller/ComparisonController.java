package com.utilityfinder.ui.controller;

import com.utilityfinder.app.Services;
import com.utilityfinder.model.MonthlyEstimate;
import com.utilityfinder.model.PlanSummary;
import com.utilityfinder.model.RatePlan;
import com.utilityfinder.model.Tdsp;
import com.utilityfinder.model.TierDiscount;
import com.utilityfinder.model.Workspace;
import com.utilityfinder.service.ComparisonService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ComparisonController implements WorkspaceAware {

    // ── Overview FXML ─────────────────────────────────────────────────────────
    @FXML private VBox      overviewPane;
    @FXML private Label     usageInfoLabel;
    @FXML private Label     warningBanner;
    @FXML private GridPane  comparisonGrid;
    @FXML private CheckBox  chkDelivery;
    @FXML private Label     deliveryInfoLabel;
    @FXML private Label     lawNoteLabel;

    // ── Detail FXML ───────────────────────────────────────────────────────────
    @FXML private VBox      detailPane;
    @FXML private Label     detailTitle;
    @FXML private Label     detailPlanInfo;
    @FXML private TableView<MonthlyEstimate>           detailTable;
    @FXML private TableColumn<MonthlyEstimate, String> colDetailMonth;
    @FXML private TableColumn<MonthlyEstimate, String> colDetailKwh;
    @FXML private TableColumn<MonthlyEstimate, String> colDetailBase;
    @FXML private TableColumn<MonthlyEstimate, String> colDetailEnergy;
    @FXML private TableColumn<MonthlyEstimate, String> colDetailDiscount;
    @FXML private TableColumn<MonthlyEstimate, String> colDetailDelivery;
    @FXML private TableColumn<MonthlyEstimate, String> colDetailTotal;
    @FXML private HBox      chartArea;
    @FXML private Label     detailFootnote;
    @FXML private Label     detailAnnualTotal;

    // ── State ─────────────────────────────────────────────────────────────────
    private List<PlanSummary> summaries;
    private PlanSummary currentDetail;
    private Workspace workspace;
    private Tdsp workspaceTdsp;
    private final ComparisonService service = Services.get().comparison;

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupDetailTable();
        // Esc anywhere in the view returns to overview when detail is showing
        detailPane.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) {
                scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                    if (e.getCode() == KeyCode.ESCAPE && detailPane.isVisible()) {
                        showOverview();
                        e.consume();
                    }
                });
            }
        });
    }

    @Override
    public void setWorkspace(Workspace workspace) {
        this.workspace    = workspace;
        this.workspaceTdsp = Services.get().tdsp.findForWorkspace(workspace.getId()).orElse(null);
        if (chkDelivery != null) chkDelivery.setDisable(workspaceTdsp == null);
        loadData();
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadData() {
        if (workspace == null) return;

        // Precondition checks before running comparison
        boolean hasUsage = Services.get().intervals.hasData(workspace.getId());
        if (!hasUsage) {
            clearMeta();
            showInfoGrid("No usage data found. Import interval data in the Usage Data view first.");
            return;
        }
        boolean hasPlans = !Services.get().ratePlans.findByWorkspace(workspace.getId()).isEmpty();
        if (!hasPlans) {
            clearMeta();
            showInfoGrid("No rate plans found. Add rate plans in the Rate Plans view first.");
            return;
        }

        summaries = service.compare(workspace.getId()).stream()
                .sorted(Comparator.comparing(s -> !s.plan().isCurrent()))
                .toList();

        // Usage info label
        List<Integer> years = Services.get().intervals.getDistinctYears(workspace.getId());
        usageInfoLabel.setText("Based on " + years.size() + " year"
                + (years.size() == 1 ? "" : "s") + " of data"
                + (years.isEmpty() ? "" : "  (" + years.get(0) + " – " + years.get(years.size() - 1) + ")"));

        // Warning banner for estimated months
        List<String> estimatedNames = summaries.isEmpty() ? List.of()
                : summaries.get(0).estimatedMonthNames();
        if (!estimatedNames.isEmpty()) {
            warningBanner.setText("⚠   Estimated usage used for: "
                    + String.join(", ", estimatedNames)
                    + " — no usage records found for those months.");
            setVisible(warningBanner, true);
        } else {
            setVisible(warningBanner, false);
        }

        buildComparisonGrid(summaries);
    }

    private void clearMeta() {
        usageInfoLabel.setText("");
        setVisible(warningBanner, false);
    }

    // ── Comparison grid ───────────────────────────────────────────────────────

    private void buildComparisonGrid(List<PlanSummary> summaries) {
        comparisonGrid.getChildren().clear();
        comparisonGrid.getColumnConstraints().clear();
        comparisonGrid.getRowConstraints().clear();

        if (summaries.isEmpty()) {
            showInfoGrid("No plans to compare.");
            return;
        }

        boolean showDelivery = workspaceTdsp != null
                && chkDelivery != null && chkDelivery.isSelected();
        updateDeliveryInfoLabel(showDelivery, summaries);

        int n = summaries.size();

        comparisonGrid.getColumnConstraints().add(colConstraint(155, Priority.NEVER));
        for (int i = 0; i < n; i++) {
            comparisonGrid.getColumnConstraints().add(colConstraint(190, Priority.SOMETIMES));
        }

        // Delivery-adjusted annual cost per plan (delivery is identical across plans for
        // the same usage data, so it only shifts the absolute numbers, not the rankings).
        double[] annualTotals = new double[n];
        for (int i = 0; i < n; i++) {
            annualTotals[i] = summaries.get(i).annualCost()
                    + (showDelivery ? annualDeliveryCost(summaries.get(i)) : 0);
        }

        // Current plan context
        OptionalInt currentOptIdx = IntStream.range(0, n)
                .filter(i -> summaries.get(i).plan().isCurrent())
                .findFirst();
        long remainingMonths = 0;
        double currentRemainingCost = Double.NaN;
        double exitFee = 0;
        if (currentOptIdx.isPresent()) {
            PlanSummary cp = summaries.get(currentOptIdx.getAsInt());
            if (cp.plan().getContractEndDate() != null) {
                remainingMonths = Math.max(0, ChronoUnit.MONTHS.between(
                        YearMonth.now(), YearMonth.from(cp.plan().getContractEndDate())));
            }
            currentRemainingCost = cp.remainingMonthsCost();
            exitFee = cp.terminationFee();
        }
        double currentAnnual = currentOptIdx.isPresent()
                ? annualTotals[currentOptIdx.getAsInt()] : Double.NaN;

        // Header row (row 0)
        comparisonGrid.add(cornerCell(), 0, 0);
        for (int i = 0; i < n; i++) {
            comparisonGrid.add(planHeaderCell(summaries.get(i)), i + 1, 0);
        }

        // Row labels — same regardless of delivery toggle
        String remainingLabel = remainingMonths > 0
                ? "Cost (" + remainingMonths + " mo)" : "Cost (N mo)";
        String[] labels = {"Annual Cost", "vs. Current", "Exit Fee", remainingLabel,
                           "Switch Savings", "Avg ¢/kWh", "Highest Month", "Lowest Month", "Renewable"};
        for (int r = 0; r < labels.length; r++) {
            comparisonGrid.add(rowLabelCell(labels[r]), 0, r + 1);
        }

        // Best-column indices (delivery is equal across plans so rankings are unchanged)
        int bestAnnual    = argMin(summaries, s -> annualTotals[summaries.indexOf(s)]);
        int bestRemaining = remainingMonths > 0 ? argMin(summaries, s -> s.remainingMonthsCost()) : -1;
        int bestRate      = argMin(summaries, s -> s.effectiveAvgPerKwh());
        int bestHighest   = argMin(summaries, s -> s.highestMonth().totalCost()
                + (showDelivery ? deliveryCostForMonth(s.highestMonth()) : 0));
        int bestLowest    = argMin(summaries, s -> s.lowestMonth().totalCost()
                + (showDelivery ? deliveryCostForMonth(s.lowestMonth()) : 0));
        boolean anyRenewable = summaries.stream().anyMatch(s -> s.plan().getRenewablePercent() != null);
        int bestRenewable = anyRenewable
                ? argMax(summaries, s -> s.plan().getRenewablePercent() != null
                        ? s.plan().getRenewablePercent() : -1.0)
                : -1;

        final long rm = remainingMonths;
        final double crc = currentRemainingCost;
        final double ef  = exitFee;

        for (int i = 0; i < n; i++) {
            PlanSummary s   = summaries.get(i);
            int         col = i + 1;

            // Annual Cost (energy + delivery when checked)
            comparisonGrid.add(dataCell(formatDollars(annualTotals[i]), i == bestAnnual), col, 1);

            // vs. Current (delivery cancels in the delta since all plans share the same TDSP)
            if (s.plan().isCurrent() || Double.isNaN(currentAnnual)) {
                comparisonGrid.add(dataCell("—", false), col, 2);
            } else {
                comparisonGrid.add(deltaCell(annualTotals[i] - currentAnnual), col, 2);
            }

            // Exit Fee
            comparisonGrid.add(exitFeeCell(s), col, 3);

            // Cost (N mo) — remaining energy cost + proportional delivery
            double adjRemaining = s.remainingMonthsCost()
                    + (showDelivery ? annualDeliveryCost(s) / 12.0 * rm : 0);
            String remText = rm > 0 ? formatDollars(adjRemaining) : "—";
            comparisonGrid.add(dataCell(remText, rm > 0 && i == bestRemaining), col, 4);

            // Switch Savings — delivery is equal for all plans so it cancels; use energy-only delta
            comparisonGrid.add(switchSavingsCell(s, crc, ef, rm), col, 5);

            // Avg ¢/kWh (energy rate, not blended with delivery)
            comparisonGrid.add(dataCell(formatCents(s.effectiveAvgPerKwh()), i == bestRate), col, 6);

            // Highest / Lowest Month (delivery added when checked)
            double hmTotal = s.highestMonth().totalCost()
                    + (showDelivery ? deliveryCostForMonth(s.highestMonth()) : 0);
            double lmTotal = s.lowestMonth().totalCost()
                    + (showDelivery ? deliveryCostForMonth(s.lowestMonth()) : 0);
            comparisonGrid.add(dataCell(monthCostLabel(s.highestMonth(), hmTotal), i == bestHighest), col, 7);
            comparisonGrid.add(dataCell(monthCostLabel(s.lowestMonth(),  lmTotal), i == bestLowest),  col, 8);

            // Renewable
            Double pct = s.plan().getRenewablePercent();
            comparisonGrid.add(dataCell(pct == null ? "—" : String.format("%.0f%%", pct),
                    i == bestRenewable && pct != null), col, 9);
        }

        // 14-day law note — show when the current plan's ETF is waived by Texas law
        if (currentOptIdx.isPresent()) {
            RatePlan cp = summaries.get(currentOptIdx.getAsInt()).plan();
            boolean hasEtf = cp.getTerminationFeeFlat() != null || cp.getTerminationFeePerMonth() != null;
            boolean within14Days = cp.getContractEndDate() != null
                    && !cp.getContractEndDate().isAfter(LocalDate.now().plusDays(14));
            if (hasEtf && within14Days && lawNoteLabel != null) {
                lawNoteLabel.setText("No early termination fee applies — your contract ends within"
                        + " 14 days and Texas law (PUCT §25.272) waives the ETF during this window.");
                setVisible(lawNoteLabel, true);
            } else if (lawNoteLabel != null) {
                setVisible(lawNoteLabel, false);
            }
        } else if (lawNoteLabel != null) {
            setVisible(lawNoteLabel, false);
        }
    }

    // ── Delivery helpers ──────────────────────────────────────────────────────

    private double deliveryCostForMonth(MonthlyEstimate est) {
        if (workspaceTdsp == null) return 0;
        return workspaceTdsp.getBaseCharge() + workspaceTdsp.getPerKwhCharge() * est.avgKwh();
    }

    private double annualDeliveryCost(PlanSummary s) {
        return s.monthlyEstimates().stream()
                .mapToDouble(this::deliveryCostForMonth)
                .sum();
    }

    private void updateDeliveryInfoLabel(boolean showDelivery, List<PlanSummary> summaries) {
        if (!showDelivery || workspaceTdsp == null) {
            setVisible(deliveryInfoLabel, false);
            return;
        }
        double avgMonthly = summaries.isEmpty() ? 0
                : summaries.get(0).monthlyEstimates().stream()
                        .mapToDouble(this::deliveryCostForMonth)
                        .average()
                        .orElse(0);
        deliveryInfoLabel.setText(String.format(
                "Delivery: %s  ·  $%.2f/mo base + %.4f¢/kWh  ·  avg %s/mo based on your usage",
                workspaceTdsp.getName(),
                workspaceTdsp.getBaseCharge(),
                workspaceTdsp.getPerKwhCharge() * 100,
                formatDollars(avgMonthly)));
        setVisible(deliveryInfoLabel, true);
    }

    private Node cornerCell() {
        Label l = new Label();
        l.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        l.setStyle("-fx-background-color: #2c3e50;");
        return l;
    }

    private Node planHeaderCell(PlanSummary summary) {
        RatePlan plan = summary.plan();
        VBox cell = new VBox(5);
        cell.setPadding(new Insets(10, 14, 10, 14));
        cell.setStyle("-fx-background-color: #2c3e50;");
        cell.setAlignment(Pos.TOP_LEFT);

        String providerText = plan.isCurrent() ? "★  " + plan.getProviderName() : plan.getProviderName();
        Label providerLabel = new Label(providerText);
        providerLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        String planText = plan.getPlanName()
                + (plan.getContractTermMonths() != null ? "  (" + plan.getContractTermMonths() + " mo)" : "");
        Label planLabel = new Label(planText);
        planLabel.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 11;");

        Button detailBtn = new Button("Detail ▶");
        applyDetailBtnStyle(detailBtn, false);
        detailBtn.setOnAction(e -> showDetail(summary));
        detailBtn.setOnMouseEntered(e -> applyDetailBtnStyle(detailBtn, true));
        detailBtn.setOnMouseExited(e -> applyDetailBtnStyle(detailBtn, false));

        cell.getChildren().addAll(providerLabel, planLabel, detailBtn);
        return cell;
    }

    private void applyDetailBtnStyle(Button btn, boolean hover) {
        btn.setStyle("-fx-background-color: " + (hover ? "#2980b9" : "#3d566e") + "; "
                + "-fx-text-fill: white; -fx-font-size: 10; "
                + "-fx-padding: 3 8; -fx-cursor: hand; -fx-background-radius: 3;");
    }

    private Node rowLabelCell(String text) {
        Label l = new Label(text);
        l.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        l.setPadding(new Insets(10, 14, 10, 14));
        l.setStyle("-fx-background-color: #f4f6f8; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        return l;
    }

    private Node dataCell(String text, boolean best) {
        Label l = new Label(best ? text + "  ◀" : text);
        l.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        l.setPadding(new Insets(10, 14, 10, 14));
        l.setStyle(best
                ? "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;"
                : "-fx-background-color: white; -fx-text-fill: #2c3e50;");
        return l;
    }

    private Node deltaCell(double delta) {
        Label l = new Label();
        l.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        l.setPadding(new Insets(10, 14, 10, 14));
        if (delta < 0) {
            l.setText(String.format("−$%,.2f ▼", -delta));
            l.setStyle("-fx-background-color: white; -fx-text-fill: #27ae60; -fx-font-weight: bold;");
        } else if (delta > 0) {
            l.setText(String.format("+$%,.2f ▲", delta));
            l.setStyle("-fx-background-color: white; -fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        } else {
            l.setText("—");
            l.setStyle("-fx-background-color: white; -fx-text-fill: #2c3e50;");
        }
        return l;
    }

    private Node exitFeeCell(PlanSummary s) {
        if (s.plan().isCurrent() && s.terminationFee() > 0) {
            return dataCell(formatDollars(s.terminationFee()), false);
        }
        return dataCell("—", false);
    }

    private Node switchSavingsCell(PlanSummary s, double currentRemainingCost, double exitFee, long remainingMonths) {
        if (s.plan().isCurrent() || Double.isNaN(currentRemainingCost) || remainingMonths <= 0) {
            return dataCell("—", false);
        }
        double savings = currentRemainingCost - (s.remainingMonthsCost() + exitFee);
        Label l = new Label();
        l.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        l.setPadding(new Insets(10, 14, 10, 14));
        if (savings > 0) {
            l.setText(String.format("+$%,.2f", savings));
            l.setStyle("-fx-background-color: white; -fx-text-fill: #27ae60; -fx-font-weight: bold;");
        } else if (savings < 0) {
            l.setText(String.format("−$%,.2f", -savings));
            l.setStyle("-fx-background-color: white; -fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        } else {
            l.setText("—");
            l.setStyle("-fx-background-color: white; -fx-text-fill: #2c3e50;");
        }
        return l;
    }

    private void showInfoGrid(String message) {
        comparisonGrid.getChildren().clear();
        comparisonGrid.getColumnConstraints().clear();
        comparisonGrid.getRowConstraints().clear();
        Label l = new Label(message);
        l.setStyle("-fx-text-fill: #7f8c8d; -fx-padding: 20; -fx-font-size: 13;");
        comparisonGrid.add(l, 0, 0);
    }

    // ── Detail view ───────────────────────────────────────────────────────────

    private void showDetail(PlanSummary summary) {
        currentDetail = summary;
        RatePlan plan = summary.plan();

        detailTitle.setText(plan.getProviderName() + "  —  " + plan.getPlanName());

        // Build plan info line
        StringBuilder info = new StringBuilder();
        info.append(String.format("Base: $%.2f/mo", plan.getBaseCharge()));
        info.append(String.format("    Rate: %s", formatCents(plan.getRatePerKwh())));
        for (TierDiscount d : plan.getDiscounts()) {
            info.append(String.format("    $%.0f off when ≥ %,.0f kWh",
                    d.getDiscountAmt(), d.getThresholdKwh()));
        }
        if (plan.getNotes() != null && !plan.getNotes().isBlank()) {
            info.append("    Notes: ").append(plan.getNotes());
        }
        detailPlanInfo.setText(info.toString());

        detailTable.setItems(FXCollections.observableArrayList(summary.monthlyEstimates()));
        detailTable.refresh();

        boolean showDelivery = workspaceTdsp != null
                && chkDelivery != null && chkDelivery.isSelected();
        colDetailDelivery.setVisible(showDelivery);

        double annualTotal = summary.annualCost();
        if (showDelivery) annualTotal += annualDeliveryCost(summary);
        detailAnnualTotal.setText(
                (showDelivery ? "Annual Total (incl. delivery):   " : "Annual Total:   ")
                + formatDollars(annualTotal));
        buildCharts(summary);

        boolean hasEstimates = !summary.estimatedMonthNames().isEmpty();
        detailFootnote.setVisible(hasEstimates);
        detailFootnote.setManaged(hasEstimates);
        if (hasEstimates) {
            detailFootnote.setText("~  Estimated usage (global average substituted) for: "
                    + String.join(", ", summary.estimatedMonthNames()));
        }

        setVisible(overviewPane, false);
        setVisible(detailPane, true);
    }

    private void showOverview() {
        setVisible(detailPane, false);
        setVisible(overviewPane, true);
        currentDetail = null;
    }

    // ── Detail table ──────────────────────────────────────────────────────────

    private void setupDetailTable() {
        colDetailMonth.setCellValueFactory(c -> new SimpleStringProperty(
                Month.of(c.getValue().month())
                        .getDisplayName(TextStyle.FULL, Locale.getDefault())));

        colDetailKwh.setCellValueFactory(c -> {
            MonthlyEstimate e = c.getValue();
            return new SimpleStringProperty(
                    (e.estimated() ? "~" : "") + String.format("%,.1f", e.avgKwh()));
        });

        colDetailBase.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("$%.2f", c.getValue().baseCost())));

        colDetailEnergy.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("$%.2f", c.getValue().energyCost())));

        colDetailDiscount.setCellValueFactory(c -> {
            double d = c.getValue().discountsApplied();
            return new SimpleStringProperty(d > 0 ? String.format("-$%.2f", d) : "—");
        });

        colDetailDelivery.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("$%.2f", deliveryCostForMonth(c.getValue()))));

        colDetailTotal.setCellValueFactory(c -> {
            MonthlyEstimate e = c.getValue();
            boolean showDelivery = workspaceTdsp != null
                    && chkDelivery != null && chkDelivery.isSelected();
            double total = e.totalCost() + (showDelivery ? deliveryCostForMonth(e) : 0);
            return new SimpleStringProperty(String.format("$%.2f", total));
        });

        // Highlight highest-cost row (red) and lowest-cost row (green);
        // listen to selectedProperty so style stays correct when row is clicked.
        detailTable.setRowFactory(tv -> new TableRow<>() {
            {
                selectedProperty().addListener((obs, old, sel) -> applyStyle());
            }

            @Override
            protected void updateItem(MonthlyEstimate item, boolean empty) {
                super.updateItem(item, empty);
                applyStyle();
            }

            private void applyStyle() {
                MonthlyEstimate item = getItem();
                if (isEmpty() || item == null || currentDetail == null) {
                    setStyle("");
                } else if (item.month() == currentDetail.highestMonth().month()) {
                    setStyle(isSelected()
                            ? "-fx-background-color: #c0392b; -fx-text-fill: white;"
                            : "-fx-background-color: #fadbd8; -fx-text-fill: #922b21;");
                } else if (item.month() == currentDetail.lowestMonth().month()) {
                    setStyle(isSelected()
                            ? "-fx-background-color: #1e8449; -fx-text-fill: white;"
                            : "-fx-background-color: #d5f5e3; -fx-text-fill: #1a5e35;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    // ── Charts ────────────────────────────────────────────────────────────────

    private void buildCharts(PlanSummary summary) {
        List<MonthlyEstimate> estimates = summary.monthlyEstimates();
        List<String> monthAbbrs = estimates.stream()
                .map(e -> Month.of(e.month()).getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                .collect(Collectors.toList());

        // Primary chart: cost on left y-axis
        CategoryAxis xAxis1 = new CategoryAxis();
        xAxis1.setCategories(FXCollections.observableArrayList(monthAbbrs));
        NumberAxis yAxis1 = new NumberAxis(0, 300, 50);
        yAxis1.setLabel("Cost ($)");
        LineChart<String, Number> costChart = new LineChart<>(xAxis1, yAxis1);
        costChart.setAnimated(false);
        costChart.setLegendVisible(false);
        costChart.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        costChart.getStyleClass().add("chart-cost");
        boolean showDelivery = workspaceTdsp != null
                && chkDelivery != null && chkDelivery.isSelected();
        XYChart.Series<String, Number> costSeries = new XYChart.Series<>();
        estimates.forEach(e -> costSeries.getData().add(new XYChart.Data<>(
                Month.of(e.month()).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                e.totalCost() + (showDelivery ? deliveryCostForMonth(e) : 0))));
        costChart.getData().add(costSeries);

        // Overlay chart: kWh on right y-axis, transparent background
        CategoryAxis xAxis2 = new CategoryAxis();
        xAxis2.setCategories(FXCollections.observableArrayList(monthAbbrs));
        xAxis2.setStyle("-fx-opacity: 0;");  // invisible but preserves layout space for alignment
        NumberAxis yAxis2 = new NumberAxis();
        yAxis2.setLabel("kWh");
        yAxis2.setSide(Side.RIGHT);
        yAxis2.setForceZeroInRange(true);
        LineChart<String, Number> usageChart = new LineChart<>(xAxis2, yAxis2);
        usageChart.setAnimated(false);
        usageChart.setLegendVisible(false);
        usageChart.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        usageChart.getStyleClass().add("chart-overlay");
        XYChart.Series<String, Number> usageSeries = new XYChart.Series<>();
        estimates.forEach(e -> usageSeries.getData().add(new XYChart.Data<>(
                Month.of(e.month()).getDisplayName(TextStyle.SHORT, Locale.getDefault()), e.avgKwh())));
        usageChart.getData().add(usageSeries);

        // Cross-pad to align plot areas: primary's left y-axis pushes the plot right;
        // overlay's right y-axis pushes the plot left. Balance them with matching padding.
        yAxis1.widthProperty().addListener((obs, old, w) ->
                usageChart.setPadding(new Insets(0, 0, 0, w.doubleValue())));
        yAxis2.widthProperty().addListener((obs, old, w) ->
                costChart.setPadding(new Insets(0, w.doubleValue(), 0, 0)));

        StackPane combined = new StackPane(costChart, usageChart);
        combined.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        HBox legend = new HBox(20, legendItem("#2980b9", "Cost ($)"), legendItem("#e67e22", "Avg kWh"));
        legend.setAlignment(Pos.CENTER);
        legend.setPadding(new Insets(4, 0, 0, 0));

        VBox wrapper = new VBox(legend, combined);
        VBox.setVgrow(combined, Priority.ALWAYS);
        wrapper.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        HBox.setHgrow(wrapper, Priority.ALWAYS);
        chartArea.getChildren().setAll(wrapper);
    }

    private static HBox legendItem(String color, String name) {
        Region swatch = new Region();
        swatch.setStyle(String.format(
                "-fx-background-color: %s; -fx-min-width: 16; -fx-max-width: 16; -fx-min-height: 3; -fx-max-height: 3;", color));
        Label lbl = new Label(name);
        lbl.setStyle("-fx-font-size: 11;");
        HBox item = new HBox(6, swatch, lbl);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    @FXML private void handleRefresh()       { loadData(); }
    @FXML private void handleBack()          { showOverview(); }
    @FXML private void handleDeliveryToggle() {
        if (summaries != null) buildComparisonGrid(summaries);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Returns the index of the plan with the minimum value of {@code fn}. */
    private static int argMin(List<PlanSummary> list,
                               java.util.function.ToDoubleFunction<PlanSummary> fn) {
        return IntStream.range(0, list.size())
                .reduce((a, b) -> fn.applyAsDouble(list.get(a)) <= fn.applyAsDouble(list.get(b)) ? a : b)
                .orElse(-1);
    }

    /** Returns the index of the plan with the maximum value of {@code fn}. */
    private static int argMax(List<PlanSummary> list,
                               java.util.function.ToDoubleFunction<PlanSummary> fn) {
        return IntStream.range(0, list.size())
                .reduce((a, b) -> fn.applyAsDouble(list.get(a)) >= fn.applyAsDouble(list.get(b)) ? a : b)
                .orElse(-1);
    }

    private static ColumnConstraints colConstraint(double pref, Priority hgrow) {
        ColumnConstraints cc = new ColumnConstraints();
        cc.setPrefWidth(pref);
        cc.setHgrow(hgrow);
        return cc;
    }

    /** Toggles both visible and managed together — avoids leaving layout gaps. */
    private static void setVisible(Region node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static String formatDollars(double amount) {
        return String.format("$%,.2f", amount);
    }

    /** Converts $/kWh to a human-readable ¢/kWh string, stripping trailing zeros. */
    private static String formatCents(double ratePerKwh) {
        return String.format("%.4f", ratePerKwh * 100)
                .replaceAll("0+$", "").replaceAll("\\.$", "") + "¢/kWh";
    }

    private static String monthCostLabel(MonthlyEstimate est) {
        return monthCostLabel(est, est.totalCost());
    }

    private static String monthCostLabel(MonthlyEstimate est, double total) {
        String abbr = Month.of(est.month()).getDisplayName(TextStyle.SHORT, Locale.getDefault());
        return abbr + "  " + String.format("$%,.2f", total);
    }
}
