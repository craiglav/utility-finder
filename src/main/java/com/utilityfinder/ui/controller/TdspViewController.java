package com.utilityfinder.ui.controller;

import com.utilityfinder.app.Services;
import com.utilityfinder.model.Tdsp;
import com.utilityfinder.model.TdspRates;
import com.utilityfinder.model.Workspace;
import com.utilityfinder.service.TdspService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class TdspViewController implements WorkspaceAware {

    // ── Carrier selector ──────────────────────────────────────────────────────
    @FXML private ComboBox<Tdsp> tdspCombo;
    @FXML private TextField      zipField;
    @FXML private Label          detectionLabel;

    // ── Rate display ──────────────────────────────────────────────────────────
    @FXML private VBox   ratesBox;
    @FXML private Label  baseChargeLabel;
    @FXML private Label  perKwhLabel;
    @FXML private Label  lastVerifiedLabel;
    @FXML private Label  fetchStatusLabel;
    @FXML private Button btnCheckUpdates;

    // ── Monthly table ─────────────────────────────────────────────────────────
    @FXML private Label                          tableSubtitle;
    @FXML private Label                          noCarrierLabel;
    @FXML private Label                          noUsageLabel;
    @FXML private TableView<MonthRow>            monthTable;
    @FXML private TableColumn<MonthRow, String>  colMonth;
    @FXML private TableColumn<MonthRow, String>  colKwh;
    @FXML private TableColumn<MonthRow, String>  colBase;
    @FXML private TableColumn<MonthRow, String>  colDistribution;
    @FXML private TableColumn<MonthRow, String>  colTotal;
    @FXML private HBox                           annualRow;
    @FXML private Label                          annualTotalLabel;

    // ── State ─────────────────────────────────────────────────────────────────
    private Workspace   workspace;
    private TdspService tdspService;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private record MonthRow(int monthNum, double avgKwh, boolean estimated,
                            double base, double distribution, double total) {}

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        tdspService = Services.get().tdsp;
        tdspCombo.getItems().setAll(tdspService.findAll());
        setupTable();
    }

    @Override
    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
        refreshCarrier();
    }

    // ── Carrier ───────────────────────────────────────────────────────────────

    private void refreshCarrier() {
        zipField.clear();
        setVisible(detectionLabel, false);

        Optional<Tdsp> current = tdspService.findForWorkspace(workspace.getId());
        current.ifPresentOrElse(
                tdsp -> {
                    tdspCombo.getSelectionModel().select(tdsp);
                    showRates(tdsp);
                    buildTable(tdsp);
                },
                () -> {
                    tdspCombo.getSelectionModel().clearSelection();
                    clearRates();
                    buildTable(null);
                });
    }

    @FXML
    private void handleZipLookup() {
        String zip = zipField.getText().strip();
        if (zip.length() < 3) return;
        tdspService.lookupByZip(zip).ifPresentOrElse(found -> {
            tdspCombo.getSelectionModel().select(found);
            showDetection("ZIP matched: " + found.getName() + " — confirm or choose another.");
            saveCarrierSelection(found);
        }, () -> showDetection("ZIP not recognized — please select your carrier below."));
    }

    @FXML
    private void handleTdspSelected() {
        Tdsp sel = tdspCombo.getSelectionModel().getSelectedItem();
        if (sel == null || workspace == null) return;
        saveCarrierSelection(sel);
    }

    private void saveCarrierSelection(Tdsp tdsp) {
        if (workspace == null) return;
        tdspService.setForWorkspace(workspace.getId(), tdsp.getId());
        showRates(tdsp);
        buildTable(tdsp);
    }

    // ── Rate display ──────────────────────────────────────────────────────────

    private void showRates(Tdsp tdsp) {
        baseChargeLabel.setText(String.format("$%.2f / month", tdsp.getBaseCharge()));
        perKwhLabel.setText(String.format("%.4f ¢/kWh", tdsp.getPerKwhCharge() * 100));
        lastVerifiedLabel.setText(tdsp.getLastVerified() != null
                ? "Last verified: " + tdsp.getLastVerified().format(DATE_FMT)
                : "Rates not yet verified");
        btnCheckUpdates.setDisable(tdsp.getPdfFilename() == null);
        setVisible(ratesBox, true);
    }

    private void clearRates() {
        setVisible(ratesBox, false);
        btnCheckUpdates.setDisable(true);
    }

    @FXML
    private void handleEditRates() {
        Tdsp tdsp = tdspCombo.getSelectionModel().getSelectedItem();
        if (tdsp == null) return;

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Edit Carrier Rates — " + tdsp.getName());
        dlg.setHeaderText(null);
        dlg.initOwner(tdspCombo.getScene().getWindow());
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField baseField = new TextField(String.format("%.2f", tdsp.getBaseCharge()));
        TextField kwhField  = new TextField(String.format("%.5f", tdsp.getPerKwhCharge()));

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.add(new Label("Base charge ($/month):"), 0, 0);
        grid.add(baseField, 1, 0);
        grid.add(new Label("Distribution ($/kWh):"),  0, 1);
        grid.add(kwhField,  1, 1);
        dlg.getDialogPane().setContent(grid);

        dlg.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            try {
                double base = Double.parseDouble(baseField.getText().strip());
                double kwh  = Double.parseDouble(kwhField.getText().strip());
                tdspService.applyRates(tdsp.getId(), new TdspRates(base, kwh, LocalDate.now()));
                reloadTdspInCombo(tdsp.getId());
            } catch (NumberFormatException ex) {
                showError("Invalid number format. Enter decimal values like 6.49 or 0.04616.");
            }
        });
    }

    @FXML
    private void handleCheckForUpdates() {
        Tdsp tdsp = tdspCombo.getSelectionModel().getSelectedItem();
        if (tdsp == null) return;

        btnCheckUpdates.setDisable(true);
        setFetchStatus("Fetching rates from PUCT…");

        Task<TdspRates> task = new Task<>() {
            @Override
            protected TdspRates call() throws IOException, InterruptedException {
                return tdspService.fetchUpdatedRates(tdsp);
            }
        };
        task.setOnSucceeded(e -> {
            btnCheckUpdates.setDisable(false);
            setFetchStatus("");
            showUpdateConfirmation(tdsp, task.getValue());
        });
        task.setOnFailed(e -> {
            btnCheckUpdates.setDisable(false);
            setFetchStatus("");
            showError("Could not fetch rates:\n" + task.getException().getMessage());
        });

        Thread t = new Thread(task, "tdsp-rate-fetch");
        t.setDaemon(true);
        t.start();
    }

    private void showUpdateConfirmation(Tdsp tdsp, TdspRates fetched) {
        String msg = String.format(
                "Updated rates found for %s:%n%n" +
                "  Base charge:    $%.2f/mo  (was $%.2f)%n" +
                "  Distribution:   %.5f $/kWh  (was %.5f)%n%n" +
                "Apply these rates?",
                tdsp.getName(),
                fetched.baseCharge(), tdsp.getBaseCharge(),
                fetched.perKwhCharge(), tdsp.getPerKwhCharge());

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Rate Update — " + tdsp.getName());
        confirm.setHeaderText(null);
        confirm.initOwner(tdspCombo.getScene().getWindow());
        confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> {
            tdspService.applyRates(tdsp.getId(), fetched);
            reloadTdspInCombo(tdsp.getId());
        });
    }

    private void reloadTdspInCombo(long tdspId) {
        tdspService.findAll().stream()
                .filter(t -> t.getId() == tdspId)
                .findFirst()
                .ifPresent(updated -> {
                    int idx = tdspCombo.getItems().stream()
                            .filter(t -> t.getId() == tdspId)
                            .findFirst()
                            .map(t -> tdspCombo.getItems().indexOf(t))
                            .orElse(-1);
                    if (idx >= 0) tdspCombo.getItems().set(idx, updated);
                    tdspCombo.getSelectionModel().select(updated);
                    showRates(updated);
                    buildTable(updated);
                });
    }

    // ── Monthly table ─────────────────────────────────────────────────────────

    private void setupTable() {
        colMonth.setCellValueFactory(c -> new SimpleStringProperty(
                Month.of(c.getValue().monthNum())
                     .getDisplayName(TextStyle.FULL, Locale.getDefault())));

        colKwh.setCellValueFactory(c -> {
            MonthRow r = c.getValue();
            return new SimpleStringProperty(
                    (r.estimated() ? "~" : "") + String.format("%,.1f", r.avgKwh()));
        });

        colBase.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("$%.2f", c.getValue().base())));

        colDistribution.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("$%.2f", c.getValue().distribution())));

        colTotal.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("$%.2f", c.getValue().total())));
    }

    private void buildTable(Tdsp tdsp) {
        if (tdsp == null) {
            setVisible(noCarrierLabel, true);
            setVisible(noUsageLabel, false);
            setVisible(monthTable, false);
            setVisible(annualRow, false);
            tableSubtitle.setText("Monthly Delivery Costs");
            return;
        }

        setVisible(noCarrierLabel, false);

        boolean hasUsage = Services.get().intervals.hasData(workspace.getId());
        if (!hasUsage) {
            setVisible(noUsageLabel, true);
            setVisible(monthTable, false);
            setVisible(annualRow, false);
            tableSubtitle.setText("Monthly Delivery Costs");
            return;
        }

        setVisible(noUsageLabel, false);
        setVisible(monthTable, true);
        setVisible(annualRow, true);

        List<Integer> years = Services.get().intervals.getDistinctYears(workspace.getId());
        tableSubtitle.setText("Monthly Delivery Costs  ·  Based on "
                + years.size() + " year" + (years.size() == 1 ? "" : "s") + " of usage data"
                + (years.isEmpty() ? "" : "  (" + years.get(0) + " – " + years.get(years.size() - 1) + ")"));

        double[] raw     = Services.get().intervals.getAveragedProfile(workspace.getId());
        double   avg     = Arrays.stream(raw).filter(v -> !Double.isNaN(v)).average().orElse(0.0);
        double[] profile = Arrays.copyOf(raw, raw.length);
        for (int i = 0; i < profile.length; i++) {
            if (Double.isNaN(profile[i])) profile[i] = avg;
        }

        List<MonthRow> rows = new ArrayList<>();
        double annualTotal = 0;
        for (int i = 0; i < 12; i++) {
            double kwh  = profile[i];
            double base = tdsp.getBaseCharge();
            double dist = tdsp.getPerKwhCharge() * kwh;
            double tot  = base + dist;
            annualTotal += tot;
            rows.add(new MonthRow(i + 1, kwh, Double.isNaN(raw[i]), base, dist, tot));
        }

        monthTable.setItems(FXCollections.observableArrayList(rows));
        annualTotalLabel.setText(String.format("$%,.2f", annualTotal));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showDetection(String msg) {
        detectionLabel.setText(msg);
        setVisible(detectionLabel, true);
    }

    private void setFetchStatus(String msg) {
        fetchStatusLabel.setText(msg);
        setVisible(fetchStatusLabel, msg != null && !msg.isBlank());
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.initOwner(tdspCombo.getScene().getWindow());
        a.showAndWait();
    }

    private static void setVisible(Region node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
