package com.utilityfinder.ui.controller;

import com.utilityfinder.app.Services;
import com.utilityfinder.model.Workspace;
import com.utilityfinder.service.InsightsService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

public class InsightsController implements WorkspaceAware {

    // ── FXML ──────────────────────────────────────────────────────────────────

    @FXML private VBox             emptyPane;
    @FXML private VBox             dataPane;
    @FXML private ComboBox<String> seasonFilter;
    @FXML private HBox             dailyPatternPane;
    @FXML private HBox             statsRow;
    @FXML private VBox             seasonalPane;
    @FXML private VBox             yoyPane;
    @FXML private VBox             trendPane;
    @FXML private VBox             heatmapPane;

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String[] SEASON_NAMES  = {
        "All Seasons", "Winter (Dec–Feb)", "Spring (Mar–May)", "Summer (Jun–Aug)", "Fall (Sep–Nov)"
    };
    private static final int[][] SEASON_MONTHS  = {
        null, {12, 1, 2}, {3, 4, 5}, {6, 7, 8}, {9, 10, 11}
    };
    private static final String[] SEASON_LABELS = {"Winter", "Spring", "Summer", "Fall"};
    private static final String[] SEASON_COLORS = {"#3498db", "#27ae60", "#e74c3c", "#e67e22"};

    // Mon–Sun display order; index 0 = Mon maps to DAYOFWEEK=2, index 6 = Sun = DOW 1
    private static final int[]    DOW_DB_INDEX  = {2, 3, 4, 5, 6, 7, 1};
    private static final String[] DOW_NAMES     = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final String[] DOW_COLORS    =
        {"#3498db", "#9b59b6", "#27ae60", "#f39c12", "#e74c3c", "#1abc9c", "#e67e22"};

    private static final String[] YOY_COLORS    =
        {"#2980b9", "#e74c3c", "#27ae60", "#e67e22", "#9b59b6"};

    private static final int HEATMAP_MAX_YEARS  = 5;

    // ── State ─────────────────────────────────────────────────────────────────

    private Workspace workspace;
    private final InsightsService insights = Services.get().insights;

    private List<Map.Entry<LocalDate, Double>> dailyTotals      = new ArrayList<>();
    private Map<LocalDate, Double>             dailyMap         = new HashMap<>();
    private double[]                           allSeasonHourlyAvg; // avg kWh per hour, all seasons
    private Map<Integer, double[]>             yoyData;
    private Map<Integer, double[]>             seasonalData;
    private double[]                           estimatedVsActual;

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        seasonFilter.setItems(FXCollections.observableArrayList(SEASON_NAMES));
        seasonFilter.getSelectionModel().selectFirst();
        seasonFilter.setOnAction(e -> rebuildDowHourlySection());
    }

    @Override
    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
        loadData();
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadData() {
        if (workspace == null) return;
        long wid = workspace.getId();

        boolean hasData = Services.get().intervals.hasData(wid);
        setVisible(emptyPane, !hasData);
        setVisible(dataPane,  hasData);
        if (!hasData) return;

        dailyTotals         = insights.getDailyTotals(wid);
        dailyMap            = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, Double> e : dailyTotals) dailyMap.put(e.getKey(), e.getValue());

        allSeasonHourlyAvg  = insights.getHourlyProfile(wid, null);
        yoyData             = insights.getYearlyMonthlyTotals(wid);
        seasonalData        = insights.getSeasonalHourlyProfiles(wid);
        estimatedVsActual   = insights.getEstimatedVsActual(wid);

        rebuildDowHourlySection();
        buildStatsRow();
        buildSeasonalChart();
        buildYoyChart();
        buildTrendChart();
        buildAllYearsHeatmap();
    }

    // ── (1) DOW hourly profiles + DOW average (season-filterable) ─────────────

    private void rebuildDowHourlySection() {
        if (workspace == null || !dataPane.isVisible()) return;
        int sel = Math.max(0, seasonFilter.getSelectionModel().getSelectedIndex());
        int[] months = sel < SEASON_MONTHS.length ? SEASON_MONTHS[sel] : null;
        long wid = workspace.getId();

        Map<Integer, double[]> dowHourly = insights.getDayOfWeekHourlyProfiles(wid, months);
        double[]               dowAvg    = insights.getDayOfWeekProfile(wid, months);

        VBox                     hourlyBox = buildDowHourlyChart(dowHourly);
        BarChart<String, Number> avgChart  = buildDowAvgChart(dowAvg);
        dailyPatternPane.getChildren().setAll(hourlyBox, avgChart);
    }

    private VBox buildDowHourlyChart(Map<Integer, double[]> profiles) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Hour of Day");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Avg kWh");

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Hourly Profile by Day of Week");
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        chart.setPrefWidth(560);
        chart.setPrefHeight(255);
        chart.getStyleClass().add("dow-chart");
        HBox.setHgrow(chart, Priority.ALWAYS);

        List<String> cats = new ArrayList<>();
        for (int h = 0; h < 24; h++) cats.add(hourLabel(h));
        xAxis.setCategories(FXCollections.observableArrayList(cats));

        for (int i = 0; i < 7; i++) {
            double[] profile = profiles.getOrDefault(DOW_DB_INDEX[i], new double[24]);
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(DOW_NAMES[i]);
            for (int h = 0; h < 24; h++) {
                series.getData().add(new XYChart.Data<>(hourLabel(h), profile[h]));
            }
            chart.getData().add(series);
        }

        FlowPane legend = buildSeriesLegend(chart, DOW_NAMES, DOW_COLORS);
        VBox wrapper = new VBox(4, chart, legend);
        HBox.setHgrow(wrapper, Priority.ALWAYS);
        return wrapper;
    }

    private BarChart<String, Number> buildDowAvgChart(double[] profile) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        yAxis.setLabel("Avg kWh");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Day-of-Week Average");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCategoryGap(4);
        chart.setBarGap(0);
        chart.setPrefWidth(310);
        chart.setPrefHeight(270);

        // profile[0]=Sun, [1]=Mon, ..., [6]=Sat  (DAYOFWEEK 1-based, 0-indexed here)
        List<String> cats = new ArrayList<>();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < 7; i++) {
            cats.add(DOW_NAMES[i]);
            series.getData().add(new XYChart.Data<>(DOW_NAMES[i], profile[DOW_DB_INDEX[i] - 1]));
        }
        xAxis.setCategories(FXCollections.observableArrayList(cats));
        chart.getData().add(series);
        return chart;
    }

    // ── (2) Stats row: sliding peak window + data quality ─────────────────────

    private void buildStatsRow() {
        statsRow.getChildren().setAll(buildPeakWindowPanel(), buildDataQualityPanel());
    }

    private VBox buildPeakWindowPanel() {
        VBox box = card();
        HBox.setHgrow(box, Priority.ALWAYS);

        double[] hourlyAvg = allSeasonHourlyAvg;
        double totalAvg = Arrays.stream(hourlyAvg).sum();

        Label title = bold("Peak Window Analysis");

        // Window time-range display
        Label windowLabel = new Label();
        windowLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        // Start-hour slider (0–23, snaps to integers)
        Slider startSlider = new Slider(0, 23, 15);
        startSlider.setMajorTickUnit(6);
        startSlider.setMinorTickCount(5);
        startSlider.setSnapToTicks(true);
        startSlider.setShowTickMarks(true);
        startSlider.setShowTickLabels(true);
        startSlider.setLabelFormatter(new StringConverter<>() {
            @Override public String toString(Double v)   { return hourLabel(v.intValue()); }
            @Override public Double  fromString(String s){ return 0.0; }
        });
        HBox.setHgrow(startSlider, Priority.ALWAYS);

        // Duration slider (1–12 hours)
        Slider lengthSlider = new Slider(1, 12, 4);
        lengthSlider.setMajorTickUnit(3);
        lengthSlider.setMinorTickCount(2);
        lengthSlider.setSnapToTicks(true);
        lengthSlider.setShowTickMarks(true);
        lengthSlider.setShowTickLabels(true);
        lengthSlider.setLabelFormatter(new StringConverter<>() {
            @Override public String toString(Double v)   { return v.intValue() + "h"; }
            @Override public Double  fromString(String s){ return 0.0; }
        });
        HBox.setHgrow(lengthSlider, Priority.ALWAYS);

        // Stacked bar
        HBox bar = new HBox(0);
        bar.setPrefHeight(22);
        bar.setMaxWidth(Double.MAX_VALUE);
        Region peakBarR = new Region();
        peakBarR.setPrefHeight(22);
        peakBarR.setStyle("-fx-background-color: #e74c3c; -fx-background-radius: 4 0 0 4;");
        Region offBarR = new Region();
        offBarR.setPrefHeight(22);
        offBarR.setStyle("-fx-background-color: #2980b9; -fx-background-radius: 0 4 4 0;");
        bar.getChildren().addAll(peakBarR, offBarR);

        Label peakPctLabel    = new Label();
        Label offPeakPctLabel = new Label();
        peakPctLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11;");
        offPeakPctLabel.setStyle("-fx-text-fill: #2980b9; -fx-font-size: 11;");

        // Mutable state shared between slider listener and width listener
        double[] peakPctRef = {0};

        Runnable update = () -> {
            int startH = (int) Math.round(startSlider.getValue());
            int length = (int) Math.round(lengthSlider.getValue());
            int endH   = (startH + length) % 24;

            double windowSum = 0;
            for (int i = 0; i < length; i++) windowSum += hourlyAvg[(startH + i) % 24];
            double peakPct = totalAvg > 0 ? windowSum / totalAvg * 100 : 0;
            peakPctRef[0] = peakPct;

            String endLabel = hourLabel(endH);
            windowLabel.setText(hourLabel(startH) + " – " + endLabel
                    + (startH + length >= 24 ? " (spans midnight)" : ""));
            peakPctLabel.setText(String.format("Peak: %.1f%%", peakPct));
            offPeakPctLabel.setText(String.format("Off-peak: %.1f%%", 100 - peakPct));

            double w = bar.getWidth();
            if (w > 0) {
                peakBarR.setPrefWidth(w * peakPct / 100);
                offBarR.setPrefWidth(w * (100 - peakPct) / 100);
            }
        };

        bar.widthProperty().addListener((obs, o, n) -> {
            double w = n.doubleValue();
            peakBarR.setPrefWidth(w * peakPctRef[0] / 100);
            offBarR.setPrefWidth(w * (100 - peakPctRef[0]) / 100);
        });
        startSlider.valueProperty().addListener((obs, o, n) -> update.run());
        lengthSlider.valueProperty().addListener((obs, o, n) -> update.run());
        update.run(); // initial render

        HBox startRow  = new HBox(8, muted("Start:"), startSlider);
        startRow.setAlignment(Pos.CENTER_LEFT);
        HBox lengthRow = new HBox(8, muted("Length:"), lengthSlider);
        lengthRow.setAlignment(Pos.CENTER_LEFT);

        HBox statsLegend = new HBox(16,
                swatchLabel("#e74c3c", "Peak"), peakPctLabel,
                swatchLabel("#2980b9", "Off-peak"), offPeakPctLabel);
        statsLegend.setAlignment(Pos.CENTER_LEFT);

        Label note = muted("Drag sliders to explore. Works across midnight.");
        note.setStyle(note.getStyle() + " -fx-font-size: 11;");

        box.getChildren().addAll(title, startRow, lengthRow, windowLabel, bar, statsLegend, note);
        return box;
    }

    private VBox buildDataQualityPanel() {
        double estimated = estimatedVsActual[0];
        double actual    = estimatedVsActual[1];
        double total     = estimated + actual;
        double estPct    = total > 0 ? estimated / total * 100 : 0;
        double actPct    = 100 - estPct;

        VBox box = card();
        HBox.setHgrow(box, Priority.ALWAYS);

        Label title      = bold("Data Quality");
        Label totalLabel = new Label(String.format("Total: %,.0f kWh", total));
        totalLabel.setStyle("-fx-text-fill: #2c3e50;");
        Label actLabel   = new Label(String.format("Metered:   %,.0f kWh  (%.1f%%)", actual,    actPct));
        Label estLabel   = new Label(String.format("Estimated: %,.0f kWh  (%.1f%%)", estimated, estPct));
        actLabel.setStyle("-fx-text-fill: #27ae60;");
        estLabel.setStyle("-fx-text-fill: #e67e22;");

        ProgressBar pb = new ProgressBar(actPct / 100);
        pb.setMaxWidth(Double.MAX_VALUE);
        pb.setStyle("-fx-accent: #27ae60;");

        Label pbLabel = muted(String.format("%.1f%% actual meter data", actPct));

        box.getChildren().addAll(title, totalLabel, actLabel, estLabel, pb, pbLabel);
        return box;
    }

    // ── (3) Seasonal load curves (unchanged) ─────────────────────────────────

    private void buildSeasonalChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Hour of Day");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Avg kWh");

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Average Daily Load by Season");
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(290);
        chart.setMaxWidth(Double.MAX_VALUE);
        chart.getStyleClass().add("seasonal-chart");

        List<String> cats = new ArrayList<>();
        for (int h = 0; h < 24; h++) cats.add(hourLabel(h));
        xAxis.setCategories(FXCollections.observableArrayList(cats));

        for (int s = 0; s < 4; s++) {
            double[] profile = seasonalData.getOrDefault(s, new double[24]);
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(SEASON_LABELS[s]);
            for (int h = 0; h < 24; h++) {
                series.getData().add(new XYChart.Data<>(hourLabel(h), profile[h]));
            }
            chart.getData().add(series);
        }

        FlowPane legend = buildSeriesLegend(chart, SEASON_LABELS, SEASON_COLORS);
        seasonalPane.getChildren().setAll(chart, legend);
    }

    // ── (3) Year-over-year: line chart, partial years handled naturally ────────

    private void buildYoyChart() {
        if (yoyData == null || yoyData.isEmpty()) return;

        List<String> monthNames = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            monthNames.add(Month.of(m).getDisplayName(TextStyle.SHORT, Locale.getDefault()));
        }

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Month");
        xAxis.setCategories(FXCollections.observableArrayList(monthNames));
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("kWh");
        yAxis.setForceZeroInRange(true);

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Year-over-Year Monthly Usage");
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setPrefHeight(290);
        chart.setMaxWidth(Double.MAX_VALUE);
        chart.getStyleClass().add("yoy-chart");

        int colorIdx = 0;
        for (Map.Entry<Integer, double[]> entry : yoyData.entrySet()) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(String.valueOf(entry.getKey()));
            double[] vals = entry.getValue();
            for (int m = 0; m < 12; m++) {
                if (vals[m] > 0) {
                    series.getData().add(new XYChart.Data<>(monthNames.get(m), vals[m]));
                }
            }
            chart.getData().add(series);

            // Apply color inline after the series node exists
            final String color = YOY_COLORS[Math.min(colorIdx, YOY_COLORS.length - 1)];
            if (series.getNode() != null) {
                series.getNode().setStyle("-fx-stroke: " + color + "; -fx-stroke-width: 2;");
            }
            colorIdx++;
        }
        yoyPane.getChildren().setAll(chart);
    }

    // ── (4) 30-day rolling average (unchanged) ────────────────────────────────

    private void buildTrendChart() {
        if (dailyTotals.isEmpty()) return;

        long minEpoch = dailyTotals.get(0).getKey().toEpochDay();
        long maxEpoch = dailyTotals.get(dailyTotals.size() - 1).getKey().toEpochDay();

        NumberAxis xAxis = new NumberAxis(minEpoch, maxEpoch, 30);
        xAxis.setLabel("Date");
        xAxis.setTickLabelFormatter(new StringConverter<>() {
            private final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM yy");
            @Override public String toString(Number n) {
                return LocalDate.ofEpochDay(n.longValue()).format(FMT);
            }
            @Override public Number fromString(String s) { return 0; }
        });
        xAxis.setMinorTickCount(0);
        xAxis.setTickLabelRotation(30);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("kWh");
        yAxis.setForceZeroInRange(true);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("30-Day Rolling Average");
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setPrefHeight(260);
        chart.setMaxWidth(Double.MAX_VALUE);
        chart.getStyleClass().add("trend-chart");

        XYChart.Series<Number, Number> dailySeries = new XYChart.Series<>();
        dailySeries.setName("Daily");
        for (Map.Entry<LocalDate, Double> e : dailyTotals) {
            dailySeries.getData().add(new XYChart.Data<>(e.getKey().toEpochDay(), e.getValue()));
        }

        List<double[]> rolling = insights.getRollingAverage(dailyTotals, 30);
        XYChart.Series<Number, Number> rollingSeries = new XYChart.Series<>();
        rollingSeries.setName("30-day avg");
        for (double[] pt : rolling) {
            rollingSeries.getData().add(new XYChart.Data<>(pt[0], pt[1]));
        }

        chart.getData().addAll(dailySeries, rollingSeries);
        trendPane.getChildren().setAll(chart);
    }

    // ── (4) Calendar heatmap: all years, global scale ─────────────────────────

    private void buildAllYearsHeatmap() {
        if (dailyMap.isEmpty()) return;

        // Collect and limit years
        List<Integer> years = new ArrayList<>(new TreeSet<>(
                dailyMap.keySet().stream().map(LocalDate::getYear).toList()));
        if (years.size() > HEATMAP_MAX_YEARS) {
            years = years.subList(years.size() - HEATMAP_MAX_YEARS, years.size());
        }

        // Single global max for consistent color scale across all shown years
        final List<Integer> finalYears = years;
        double globalMax = dailyMap.entrySet().stream()
                .filter(e -> finalYears.contains(e.getKey().getYear()))
                .mapToDouble(Map.Entry::getValue)
                .max().orElse(1.0);

        VBox container = new VBox(12);
        container.setStyle("-fx-background-color: white; -fx-background-radius: 6; "
                + "-fx-border-color: #d5d8dc; -fx-border-radius: 6; -fx-padding: 14;");

        for (int year : years) {
            Label yearLabel = new Label(String.valueOf(year));
            yearLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12; -fx-text-fill: #2c3e50;");
            container.getChildren().addAll(yearLabel, buildHeatmapGrid(year, globalMax));
        }

        container.getChildren().add(buildHeatmapLegend(globalMax));
        heatmapPane.getChildren().setAll(container);
    }

    private GridPane buildHeatmapGrid(int year, double globalMax) {
        final int CELL = 14;
        final int GAP  = 2;

        LocalDate jan1  = LocalDate.of(year, 1, 1);
        LocalDate dec31 = LocalDate.of(year, 12, 31);

        // Sunday = 0 offset for the starting column
        int startOffset = jan1.getDayOfWeek().getValue() % 7;

        GridPane grid = new GridPane();
        grid.setHgap(GAP);
        grid.setVgap(GAP);
        grid.setPadding(new Insets(0, 0, 4, 0));

        // Day-of-week labels (column 0, rows 1–7)
        String[] dayLabels = {"S", "M", "T", "W", "T", "F", "S"};
        for (int d = 0; d < 7; d++) {
            Label lbl = new Label(dayLabels[d]);
            lbl.setPrefWidth(18);
            lbl.setPrefHeight(CELL);
            lbl.setStyle("-fx-font-size: 10; -fx-text-fill: #95a5a6;");
            grid.add(lbl, 0, d + 1);
        }

        // Month labels (row 0)
        for (int month = 1; month <= 12; month++) {
            LocalDate firstOfMonth = LocalDate.of(year, month, 1);
            int col = (firstOfMonth.getDayOfYear() - 1 + startOffset) / 7 + 1;
            Label lbl = new Label(Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault()));
            lbl.setStyle("-fx-font-size: 10; -fx-text-fill: #95a5a6;");
            grid.add(lbl, col, 0);
        }

        // Day cells
        DateTimeFormatter tipFmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
        for (LocalDate date = jan1; !date.isAfter(dec31); date = date.plusDays(1)) {
            int col = (date.getDayOfYear() - 1 + startOffset) / 7 + 1;
            int row = date.getDayOfWeek().getValue() % 7 + 1;

            Double kwh = dailyMap.get(date);

            Rectangle rect = new Rectangle(CELL, CELL);
            rect.setArcWidth(3);
            rect.setArcHeight(3);
            rect.setFill(kwh != null ? heatColor(kwh, globalMax) : Color.web("#ecf0f1"));

            Tooltip.install(rect, new Tooltip(date.format(tipFmt)
                    + (kwh != null ? String.format("  —  %.1f kWh", kwh) : "  —  no data")));
            grid.add(rect, col, row);
        }
        return grid;
    }

    private HBox buildHeatmapLegend(double maxKwh) {
        HBox legend = new HBox(4);
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.getChildren().add(muted("Less  "));
        for (int i = 0; i <= 6; i++) {
            double t = (double) i / 6;
            Rectangle swatch = new Rectangle(14, 14);
            swatch.setArcWidth(3);
            swatch.setArcHeight(3);
            swatch.setFill(i == 0 ? Color.web("#ecf0f1") : heatColor(t * maxKwh, maxKwh));
            legend.getChildren().add(swatch);
        }
        legend.getChildren().add(muted(String.format("  More  (max %.0f kWh/day)", maxKwh)));
        return legend;
    }

    // ── Legend hover: highlight one series, dim the rest ─────────────────────

    /**
     * Builds a custom FlowPane legend with hover highlighting.
     * Uses direct series references so there is no timing dependency on chart layout.
     */
    private static FlowPane buildSeriesLegend(XYChart<?, ?> chart, String[] names, String[] colors) {
        FlowPane legend = new FlowPane(8, 4);
        legend.setPadding(new Insets(4, 0, 0, 8));

        for (int i = 0; i < names.length && i < chart.getData().size(); i++) {
            final int idx = i;

            Rectangle swatch = new Rectangle(18, 3);
            swatch.setFill(Color.web(colors[i % colors.length]));

            Label lbl = new Label(names[i]);
            lbl.setStyle("-fx-font-size: 11; -fx-text-fill: #2c3e50;");

            HBox item = new HBox(5, swatch, lbl);
            item.setAlignment(Pos.CENTER_LEFT);
            item.setCursor(Cursor.HAND);
            item.setOnMouseEntered(e -> highlightSeries(chart, idx));
            item.setOnMouseExited(e  -> restoreAllSeries(chart));

            legend.getChildren().add(item);
        }
        return legend;
    }

    private static void highlightSeries(XYChart<?, ?> chart, int idx) {
        for (int i = 0; i < chart.getData().size(); i++) {
            Node node = chart.getData().get(i).getNode();
            if (node != null) node.setOpacity(i == idx ? 1.0 : 0.15);
        }
    }

    private static void restoreAllSeries(XYChart<?, ?> chart) {
        for (XYChart.Series<?, ?> s : chart.getData()) {
            Node node = s.getNode();
            if (node != null) node.setOpacity(1.0);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String hourLabel(int h) {
        int n = ((h % 24) + 24) % 24;
        if (n == 0)  return "12am";
        if (n < 12)  return n + "am";
        if (n == 12) return "12pm";
        return (n - 12) + "pm";
    }

    private static Color heatColor(double value, double max) {
        if (max <= 0) return Color.web("#d6eaf8");
        double t = Math.min(1.0, value / max);
        int r = (int) (0xd6 + t * (0x15 - 0xd6));
        int g = (int) (0xea + t * (0x43 - 0xea));
        int b = (int) (0xf8 + t * (0x60 - 0xf8));
        return Color.rgb(r, g, b);
    }

    private static VBox card() {
        VBox box = new VBox(8);
        box.setStyle("-fx-background-color: white; -fx-background-radius: 6; "
                + "-fx-border-color: #d5d8dc; -fx-border-radius: 6; -fx-padding: 14;");
        return box;
    }

    private static Label bold(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 13; -fx-text-fill: #2c3e50;");
        return l;
    }

    private static Label muted(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #7f8c8d;");
        return l;
    }

    private static HBox swatchLabel(String color, String text) {
        Rectangle swatch = new Rectangle(12, 12);
        swatch.setFill(Color.web(color));
        swatch.setArcWidth(3);
        swatch.setArcHeight(3);
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 11; -fx-text-fill: #2c3e50;");
        HBox item = new HBox(6, swatch, lbl);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private static void setVisible(Region node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
