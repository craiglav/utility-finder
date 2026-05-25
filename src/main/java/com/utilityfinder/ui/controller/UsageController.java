package com.utilityfinder.ui.controller;

import com.utilityfinder.app.Services;
import com.utilityfinder.model.ImportResult;
import com.utilityfinder.model.MonthSummary;
import com.utilityfinder.model.Workspace;
import com.utilityfinder.service.IntervalImportService;
import com.utilityfinder.service.IntervalService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class UsageController implements WorkspaceAware {

    // ── FXML ──────────────────────────────────────────────────────────────────

    @FXML private Button                      importBtn;
    @FXML private HBox                        statusBanner;
    @FXML private Label                       esiidLabel;
    @FXML private Label                       coverageLabel;
    @FXML private Label                       importedLabel;
    @FXML private Button                      clearBtn;
    @FXML private VBox                        emptyPane;
    @FXML private VBox                        dataPane;
    @FXML private BarChart<String, Number>    usageChart;
    @FXML private CategoryAxis                chartXAxis;
    @FXML private NumberAxis                  chartYAxis;
    @FXML private TableView<UsageRow>         monthTable;
    @FXML private TableColumn<UsageRow, String> colMonth;
    @FXML private TableColumn<UsageRow, String> colTotalKwh;
    @FXML private TableColumn<UsageRow, String> colDailyAvg;
    @FXML private TableColumn<UsageRow, String> colDays;
    @FXML private TableColumn<UsageRow, String> colPeak;

    // ── Row types for the grouped table ──────────────────────────────────────

    sealed interface UsageRow {
        record YearHeader(int year) implements UsageRow {}
        record MonthData(MonthSummary summary) implements UsageRow {}
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private Workspace workspace;
    private final IntervalService       intervalService = Services.get().intervals;
    private final IntervalImportService importService   = Services.get().intervalImport;

    private static final DateTimeFormatter IMPORT_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd  h:mm a");
    private static final DateTimeFormatter PEAK_FMT =
            DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter DISPLAY_DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy");

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupTable();
    }

    @Override
    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
        loadData();
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    private void loadData() {
        if (workspace == null) return;

        long wid = workspace.getId();
        boolean hasData = intervalService.hasData(wid);

        setVisible(statusBanner, hasData);
        setVisible(emptyPane,    !hasData);
        setVisible(dataPane,     hasData);

        if (!hasData) return;

        // Status banner
        intervalService.getEsiid(wid)
                .ifPresent(e -> esiidLabel.setText("ESIID: " + e));

        Optional<LocalDate> from = intervalService.getMinDate(wid);
        Optional<LocalDate> to   = intervalService.getMaxDate(wid);
        long count = intervalService.getTotalCount(wid);
        if (from.isPresent() && to.isPresent()) {
            coverageLabel.setText(
                    "Coverage: " + formatDate(from.get()) + " – " + formatDate(to.get())
                    + "  ·  " + String.format("%,d", count) + " intervals");
        }

        intervalService.getLastImportTime(wid)
                .ifPresent(t -> importedLabel.setText(
                        "Last imported: " + t.format(IMPORT_DATE_FMT)));

        // Chart + table
        List<MonthSummary> summaries = intervalService.getMonthlySummaries(wid);
        buildChart(summaries);
        buildTable(summaries);
    }

    // ── Chart ─────────────────────────────────────────────────────────────────

    private void buildChart(List<MonthSummary> summaries) {
        usageChart.getData().clear();
        chartXAxis.getCategories().clear();

        // summaries are newest-first; reverse to chronological for the chart
        List<MonthSummary> chrono = new ArrayList<>(summaries.reversed());

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        List<String> labels = new ArrayList<>();

        for (MonthSummary s : chrono) {
            String label = Month.of(s.month())
                    .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    + " '" + String.valueOf(s.year()).substring(2);
            labels.add(label);
            series.getData().add(new XYChart.Data<>(label, s.totalKwh()));
        }

        chartXAxis.setCategories(FXCollections.observableArrayList(labels));
        usageChart.getData().add(series);
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    private void setupTable() {
        colMonth.setCellValueFactory(c -> {
            UsageRow row = c.getValue();
            if (row instanceof UsageRow.YearHeader h) {
                return new SimpleStringProperty(String.valueOf(h.year()));
            }
            MonthSummary s = ((UsageRow.MonthData) row).summary();
            return new SimpleStringProperty(
                    Month.of(s.month()).getDisplayName(TextStyle.FULL, Locale.getDefault())
                    + " " + s.year());
        });

        colTotalKwh.setCellValueFactory(c -> {
            if (c.getValue() instanceof UsageRow.YearHeader) return new SimpleStringProperty("");
            MonthSummary s = ((UsageRow.MonthData) c.getValue()).summary();
            return new SimpleStringProperty(String.format("%,.1f", s.totalKwh()));
        });

        colDailyAvg.setCellValueFactory(c -> {
            if (c.getValue() instanceof UsageRow.YearHeader) return new SimpleStringProperty("");
            MonthSummary s = ((UsageRow.MonthData) c.getValue()).summary();
            return new SimpleStringProperty(String.format("%,.1f", s.dailyAvgKwh()));
        });

        colDays.setCellValueFactory(c -> {
            if (c.getValue() instanceof UsageRow.YearHeader) return new SimpleStringProperty("");
            MonthSummary s = ((UsageRow.MonthData) c.getValue()).summary();
            return new SimpleStringProperty(String.valueOf(s.daysWithData()));
        });

        colPeak.setCellValueFactory(c -> {
            if (c.getValue() instanceof UsageRow.YearHeader) return new SimpleStringProperty("");
            MonthSummary s = ((UsageRow.MonthData) c.getValue()).summary();
            if (s.peakDate() == null) return new SimpleStringProperty("—");
            return new SimpleStringProperty(
                    PEAK_FMT.format(s.peakDate())
                    + "  —  " + String.format("%,.1f kWh", s.peakKwh()));
        });

        // Year-header rows get a dark header style; normal rows get default styling
        monthTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(UsageRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setStyle("");
                    setPadding(Insets.EMPTY);
                } else if (row instanceof UsageRow.YearHeader) {
                    setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; "
                            + "-fx-font-weight: bold; -fx-font-size: 12;");
                    setPadding(new Insets(0, 0, 0, 8));
                } else {
                    setStyle("");
                    setPadding(Insets.EMPTY);
                }
            }
        });
    }

    private void buildTable(List<MonthSummary> summaries) {
        ObservableList<UsageRow> rows = FXCollections.observableArrayList();
        int currentYear = -1;
        for (MonthSummary s : summaries) {  // already sorted year desc, month desc
            if (s.year() != currentYear) {
                rows.add(new UsageRow.YearHeader(s.year()));
                currentYear = s.year();
            }
            rows.add(new UsageRow.MonthData(s));
        }
        monthTable.setItems(rows);
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    @FXML
    private void handleImport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Smart Meter Texas Interval Data CSV");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV files", "*.csv", "*.CSV"));

        File file = chooser.showOpenDialog(importBtn.getScene().getWindow());
        if (file == null) return;

        importBtn.setDisable(true);
        clearBtn.setDisable(true);

        Task<ImportResult> task = new Task<>() {
            @Override
            protected ImportResult call() throws IOException {
                return importService.importCsv(workspace.getId(), file);
            }
        };

        task.setOnSucceeded(e -> {
            importBtn.setDisable(false);
            clearBtn.setDisable(false);
            ImportResult r = task.getValue();
            Services.get().tdsp.autoDetectIfNeeded(workspace.getId(), r.esiid());
            loadData();
            showImportResult(r);
        });

        task.setOnFailed(e -> {
            importBtn.setDisable(false);
            clearBtn.setDisable(false);
            showError("Import failed: " + task.getException().getMessage());
        });

        Thread thread = new Thread(task, "interval-import");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handleClear() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete all imported interval data for this workspace?\nThis cannot be undone.",
                ButtonType.YES, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.initOwner(importBtn.getScene().getWindow());
        if (confirm.showAndWait().filter(b -> b == ButtonType.YES).isPresent()) {
            intervalService.deleteByWorkspace(workspace.getId());
            loadData();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showImportResult(ImportResult r) {
        String body = String.format(
                "ESIID: %s%nDate range: %s – %s%nNew intervals: %,d  ·  Updated: %,d",
                r.esiid() != null ? r.esiid() : "—",
                r.from() != null ? formatDate(r.from()) : "—",
                r.to()   != null ? formatDate(r.to())   : "—",
                r.inserted(), r.overwritten());

        Alert info = new Alert(Alert.AlertType.INFORMATION, body, ButtonType.OK);
        info.setHeaderText("Import complete");
        info.initOwner(importBtn.getScene().getWindow());
        info.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.initOwner(importBtn.getScene().getWindow());
        alert.showAndWait();
    }

    private static void setVisible(Region node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static String formatDate(LocalDate d) {
        return d.format(DISPLAY_DATE_FMT);
    }
}
