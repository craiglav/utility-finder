package com.utilityfinder.ui.controller;

import com.utilityfinder.app.Services;
import com.utilityfinder.model.MonthlyEstimate;
import com.utilityfinder.model.PlanSummary;
import com.utilityfinder.model.RatePlan;
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

    // ── Detail FXML ───────────────────────────────────────────────────────────
    @FXML private VBox      detailPane;
    @FXML private Label     detailTitle;
    @FXML private Label     detailPlanInfo;
    @FXML private TableView<MonthlyEstimate>         detailTable;
    @FXML private TableColumn<MonthlyEstimate, String> colDetailMonth;
    @FXML private TableColumn<MonthlyEstimate, String> colDetailKwh;
    @FXML private TableColumn<MonthlyEstimate, String> colDetailBase;
    @FXML private TableColumn<MonthlyEstimate, String> colDetailEnergy;
    @FXML private TableColumn<MonthlyEstimate, String> colDetailDiscount;
    @FXML private TableColumn<MonthlyEstimate, String> colDetailTotal;
    @FXML private HBox      chartArea;
    @FXML private Label     detailFootnote;
    @FXML private Label     detailAnnualTotal;

    // ── State ─────────────────────────────────────────────────────────────────
    private List<PlanSummary> summaries;
    private PlanSummary currentDetail;
    private Workspace workspace;
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
        this.workspace = workspace;
        loadData();
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadData() {
        if (workspace == null) return;

        // Precondition checks before running comparison
        boolean hasUsage = !Services.get().usage.findByWorkspace(workspace.getId()).isEmpty();
        if (!hasUsage) {
            clearMeta();
            showInfoGrid("No usage data found. Add usage records in the Usage Data view first.");
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
        List<Integer> years = Services.get().usage.findByWorkspace(workspace.getId())
                .stream().map(r -> r.getYear()).distinct().sorted().toList();
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

        int n = summaries.size();

        // Column 0: row labels, fixed width; columns 1..n: plan data, flexible
        comparisonGrid.getColumnConstraints().add(colConstraint(155, Priority.NEVER));
        for (int i = 0; i < n; i++) {
            comparisonGrid.getColumnConstraints().add(colConstraint(190, Priority.SOMETIMES));
        }

        // Header row (row 0): corner cell + plan header per column
        comparisonGrid.add(cornerCell(), 0, 0);
        for (int i = 0; i < n; i++) {
            comparisonGrid.add(planHeaderCell(summaries.get(i)), i + 1, 0);
        }

        // Current plan index — derive remaining months and financial context for new rows
        OptionalInt currentIdx = IntStream.range(0, n)
                .filter(i -> summaries.get(i).plan().isCurrent())
                .findFirst();
        double currentAnnual = currentIdx.isPresent()
                ? summaries.get(currentIdx.getAsInt()).annualCost() : Double.NaN;
        long remainingMonths = 0;
        double currentRemainingCost = Double.NaN;
        double exitFee = 0;
        if (currentIdx.isPresent()) {
            PlanSummary cp = summaries.get(currentIdx.getAsInt());
            if (cp.plan().getContractEndDate() != null) {
                remainingMonths = Math.max(0, ChronoUnit.MONTHS.between(
                        YearMonth.now(), YearMonth.from(cp.plan().getContractEndDate())));
            }
            currentRemainingCost = cp.remainingMonthsCost();
            exitFee = cp.terminationFee();
        }

        // Row labels (column 0, rows 1–9)
        String remainingLabel = remainingMonths > 0 ? "Cost (" + remainingMonths + " mo)" : "Cost (N mo)";
        String[] labels = {"Annual Cost", "vs. Current", "Exit Fee", remainingLabel, "Switch Savings",
                           "Avg ¢/kWh", "Highest Month", "Lowest Month", "Renewable"};
        for (int r = 0; r < labels.length; r++) {
            comparisonGrid.add(rowLabelCell(labels[r]), 0, r + 1);
        }

        // Find best (lowest cost) column per metric row
        int bestAnnual    = argMin(summaries, s -> s.annualCost());
        int bestRemaining = remainingMonths > 0 ? argMin(summaries, s -> s.remainingMonthsCost()) : -1;
        int bestRate      = argMin(summaries, s -> s.effectiveAvgPerKwh());
        int bestHighest   = argMin(summaries, s -> s.highestMonth().totalCost());
        int bestLowest    = argMin(summaries, s -> s.lowestMonth().totalCost());

        // Renewable: highest wins; only compete among plans that have a value
        boolean anyRenewable = summaries.stream()
                .anyMatch(s -> s.plan().getRenewablePercent() != null);
        int bestRenewable = anyRenewable
                ? argMax(summaries, s -> s.plan().getRenewablePercent() != null
                        ? s.plan().getRenewablePercent() : -1.0)
                : -1;

        final long rm = remainingMonths;
        final double crc = currentRemainingCost;
        final double ef = exitFee;

        // Data cells (rows 1–9)
        for (int i = 0; i < n; i++) {
            PlanSummary s = summaries.get(i);
            int col = i + 1;
            comparisonGrid.add(dataCell(formatDollars(s.annualCost()),          i == bestAnnual),    col, 1);
            comparisonGrid.add(deltaCell(s, currentAnnual),                                          col, 2);
            comparisonGrid.add(exitFeeCell(s),                                                       col, 3);
            String remText = rm > 0 ? formatDollars(s.remainingMonthsCost()) : "—";
            comparisonGrid.add(dataCell(remText, rm > 0 && i == bestRemaining),                      col, 4);
            comparisonGrid.add(switchSavingsCell(s, crc, ef, rm),                                    col, 5);
            comparisonGrid.add(dataCell(formatCents(s.effectiveAvgPerKwh()),    i == bestRate),      col, 6);
            comparisonGrid.add(dataCell(monthCostLabel(s.highestMonth()),       i == bestHighest),   col, 7);
            comparisonGrid.add(dataCell(monthCostLabel(s.lowestMonth()),        i == bestLowest),    col, 8);
            Double pct = s.plan().getRenewablePercent();
            boolean renewBest = i == bestRenewable && pct != null;
            comparisonGrid.add(dataCell(pct == null ? "—" : String.format("%.0f%%", pct), renewBest), col, 9);
        }
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

    private Node deltaCell(PlanSummary s, double currentAnnual) {
        if (s.plan().isCurrent() || Double.isNaN(currentAnnual)) {
            return dataCell("—", false);
        }
        double delta = s.annualCost() - currentAnnual;
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
        // Force row factory to re-evaluate against the new currentDetail
        detailTable.refresh();

        detailAnnualTotal.setText("Annual Total:   " + formatDollars(summary.annualCost()));
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

        colDetailTotal.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("$%.2f", c.getValue().totalCost())));

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
        XYChart.Series<String, Number> costSeries = new XYChart.Series<>();
        estimates.forEach(e -> costSeries.getData().add(new XYChart.Data<>(
                Month.of(e.month()).getDisplayName(TextStyle.SHORT, Locale.getDefault()), e.totalCost())));
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

    @FXML private void handleRefresh() { loadData(); }
    @FXML private void handleBack()    { showOverview(); }

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
        String abbr = Month.of(est.month()).getDisplayName(TextStyle.SHORT, Locale.getDefault());
        return abbr + "  " + String.format("$%,.2f", est.totalCost());
    }
}
