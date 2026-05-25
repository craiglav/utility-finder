package com.utilityfinder.ui.controller;

import com.utilityfinder.app.Services;
import com.utilityfinder.model.Tdsp;
import com.utilityfinder.model.TdspRates;
import com.utilityfinder.model.Workspace;
import com.utilityfinder.service.TdspService;
import com.utilityfinder.service.WorkspaceService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class WorkspaceManagerController {

    // ── Workspace list FXML ───────────────────────────────────────────────────
    @FXML private ListView<Workspace> workspaceList;
    @FXML private Button              btnRename;
    @FXML private Button              btnDelete;

    // ── TDSP section FXML ─────────────────────────────────────────────────────
    @FXML private VBox    tdspPane;
    @FXML private Label   tdspSectionTitle;
    @FXML private TextField zipField;
    @FXML private ComboBox<Tdsp> tdspCombo;
    @FXML private Label   detectionLabel;
    @FXML private Label   baseChargeLabel;
    @FXML private Label   perKwhLabel;
    @FXML private Label   lastVerifiedLabel;
    @FXML private Label   fetchStatusLabel;
    @FXML private Button  btnCheckUpdates;

    // ── State ─────────────────────────────────────────────────────────────────
    private WorkspaceService service;
    private TdspService      tdspService;
    private Workspace        activeWorkspace;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");

    public void init(WorkspaceService service, Workspace activeWorkspace) {
        this.service         = service;
        this.tdspService     = Services.get().tdsp;
        this.activeWorkspace = activeWorkspace;
    }

    @FXML
    public void initialize() {
        workspaceList.setCellFactory(lv -> new WorkspaceCell());
        workspaceList.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> {
                    updateButtonState(sel);
                    refreshTdspPanel(sel);
                });

        workspaceList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F2)          handleRename();
            else if (e.getCode() == KeyCode.DELETE) handleDelete();
        });

        updateButtonState(null);
    }

    public void load() {
        tdspCombo.getItems().setAll(tdspService.findAll());
        refresh();
        workspaceList.getSelectionModel().selectFirst();
    }

    // ── Workspace CRUD ────────────────────────────────────────────────────────

    @FXML
    private void handleNew() {
        promptName("New Workspace", "Workspace name:", "").ifPresent(name -> {
            try {
                service.create(name);
                refresh();
                workspaceList.getItems().stream()
                        .filter(w -> w.getName().equals(name))
                        .findFirst()
                        .ifPresent(workspaceList.getSelectionModel()::select);
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        });
    }

    @FXML
    private void handleRename() {
        Workspace selected = workspaceList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        promptName("Rename Workspace", "New name:", selected.getName()).ifPresent(name -> {
            try {
                service.rename(selected.getId(), name);
                if (activeWorkspace != null && activeWorkspace.getId().equals(selected.getId())) {
                    activeWorkspace.setName(name);
                }
                refresh();
                workspaceList.getItems().stream()
                        .filter(w -> w.getId().equals(selected.getId()))
                        .findFirst()
                        .ifPresent(workspaceList.getSelectionModel()::select);
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        });
    }

    @FXML
    private void handleDelete() {
        Workspace selected = workspaceList.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String msg = "Delete \"" + selected.getName() + "\"?\n\n"
                + "All usage data and rate plans for this workspace will be permanently removed.";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.CANCEL);
        confirm.setTitle("Delete Workspace");
        confirm.setHeaderText(null);
        confirm.initOwner(workspaceList.getScene().getWindow());

        if (confirm.showAndWait().filter(b -> b == ButtonType.YES).isPresent()) {
            service.delete(selected.getId());
            refresh();
            workspaceList.getSelectionModel().selectFirst();
        }
    }

    @FXML
    private void handleClose() {
        ((Stage) workspaceList.getScene().getWindow()).close();
    }

    // ── TDSP panel ────────────────────────────────────────────────────────────

    @FXML
    private void handleZipLookup() {
        String zip = zipField.getText().strip();
        if (zip.length() < 3) return;

        tdspService.lookupByZip(zip).ifPresentOrElse(found -> {
            tdspCombo.getSelectionModel().select(found);
            showDetection("ZIP matched: " + found.getName() + " — confirm or choose another.");
            saveCarrierSelection(found);
        }, () -> {
            showDetection("ZIP not recognized — please select your carrier below.");
        });
    }

    @FXML
    private void handleTdspSelected() {
        Tdsp sel = tdspCombo.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Workspace ws = workspaceList.getSelectionModel().getSelectedItem();
        if (ws == null) return;
        saveCarrierSelection(sel);
    }

    private void saveCarrierSelection(Tdsp tdsp) {
        Workspace ws = workspaceList.getSelectionModel().getSelectedItem();
        if (ws == null) return;
        tdspService.setForWorkspace(ws.getId(), tdsp.getId());
        refreshRatesDisplay(tdsp, false);
    }

    @FXML
    private void handleEditRates() {
        Tdsp tdsp = tdspCombo.getSelectionModel().getSelectedItem();
        if (tdsp == null) return;

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Edit Carrier Rates — " + tdsp.getName());
        dlg.setHeaderText(null);
        dlg.initOwner(workspaceList.getScene().getWindow());
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField baseField = new TextField(String.format("%.2f", tdsp.getBaseCharge()));
        TextField kwhField  = new TextField(String.format("%.5f", tdsp.getPerKwhCharge()));

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10); grid.setVgap(8);
        grid.setPadding(new javafx.geometry.Insets(12));
        grid.add(new Label("Base charge ($/month):"), 0, 0);
        grid.add(baseField, 1, 0);
        grid.add(new Label("Distribution ($/kWh):"),  0, 1);
        grid.add(kwhField,  1, 1);
        dlg.getDialogPane().setContent(grid);

        dlg.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            try {
                double base = Double.parseDouble(baseField.getText().strip());
                double kwh  = Double.parseDouble(kwhField.getText().strip());
                TdspRates rates = new TdspRates(base, kwh, java.time.LocalDate.now());
                tdspService.applyRates(tdsp.getId(), rates);
                // Reload the updated TDSP object and refresh display
                tdspService.findAll().stream()
                        .filter(t -> t.getId().equals(tdsp.getId()))
                        .findFirst()
                        .ifPresent(updated -> {
                            int idx = tdspCombo.getItems().indexOf(tdsp);
                            if (idx >= 0) tdspCombo.getItems().set(idx, updated);
                            tdspCombo.getSelectionModel().select(updated);
                            refreshRatesDisplay(updated, false);
                        });
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
            TdspRates fetched = task.getValue();
            showUpdateConfirmation(tdsp, fetched);
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
        confirm.initOwner(workspaceList.getScene().getWindow());
        confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> {
            tdspService.applyRates(tdsp.getId(), fetched);
            // Reload so displayed rates reflect the save
            tdspService.findAll().stream()
                    .filter(t -> t.getId().equals(tdsp.getId()))
                    .findFirst()
                    .ifPresent(updated -> {
                        int idx = tdspCombo.getItems().indexOf(tdsp);
                        if (idx >= 0) tdspCombo.getItems().set(idx, updated);
                        tdspCombo.getSelectionModel().select(updated);
                        refreshRatesDisplay(updated, false);
                    });
        });
    }

    // ── TDSP panel helpers ────────────────────────────────────────────────────

    private void refreshTdspPanel(Workspace ws) {
        if (ws == null) {
            setVisible(tdspPane, false);
            return;
        }
        setVisible(tdspPane, true);
        tdspSectionTitle.setText("Delivery Carrier — " + ws.getName());
        zipField.clear();
        setVisible(detectionLabel, false);

        Optional<Tdsp> current = tdspService.findForWorkspace(ws.getId());
        current.ifPresentOrElse(
                tdsp -> {
                    tdspCombo.getSelectionModel().select(tdsp);
                    refreshRatesDisplay(tdsp, false);
                },
                () -> {
                    tdspCombo.getSelectionModel().clearSelection();
                    clearRatesDisplay();
                });
    }

    private void refreshRatesDisplay(Tdsp tdsp, boolean autoDetected) {
        baseChargeLabel.setText(String.format("$%.2f / month", tdsp.getBaseCharge()));
        perKwhLabel.setText(String.format("%.4f ¢/kWh",
                tdsp.getPerKwhCharge() * 100));
        String verifiedText = tdsp.getLastVerified() != null
                ? "Last verified: " + tdsp.getLastVerified().format(DATE_FMT)
                : "Rates not yet verified";
        lastVerifiedLabel.setText(verifiedText);
        btnCheckUpdates.setDisable(tdsp.getPdfFilename() == null);

        if (autoDetected) {
            showDetection("✓ Auto-detected from meter ESIID");
        }
    }

    private void clearRatesDisplay() {
        baseChargeLabel.setText("—");
        perKwhLabel.setText("—");
        lastVerifiedLabel.setText("");
        btnCheckUpdates.setDisable(true);
    }

    private void showDetection(String msg) {
        detectionLabel.setText(msg);
        setVisible(detectionLabel, true);
    }

    private void setFetchStatus(String msg) {
        boolean show = msg != null && !msg.isBlank();
        fetchStatusLabel.setText(msg);
        setVisible(fetchStatusLabel, show);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private void refresh() {
        workspaceList.getItems().setAll(service.findAll());
    }

    private void updateButtonState(Workspace selected) {
        btnRename.setDisable(selected == null);
        btnDelete.setDisable(selected == null);
    }

    private Optional<String> promptName(String title, String label, String initial) {
        TextInputDialog dlg = new TextInputDialog(initial);
        dlg.setTitle(title);
        dlg.setHeaderText(null);
        dlg.setContentText(label);
        dlg.initOwner(workspaceList.getScene().getWindow());
        dlg.getEditor().selectAll();
        return dlg.showAndWait()
                .map(String::strip)
                .filter(s -> !s.isBlank());
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.initOwner(workspaceList.getScene().getWindow());
        alert.showAndWait();
    }

    private static void setVisible(Region node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    // ── List cell ─────────────────────────────────────────────────────────────

    private class WorkspaceCell extends ListCell<Workspace> {

        private final HBox   box       = new HBox(8);
        private final Label  bullet    = new Label("●");
        private final Label  nameLabel = new Label();
        private final Region spacer    = new Region();
        private final Button renameBtn = new Button("Rename");
        private final Button deleteBtn = new Button("Delete");

        WorkspaceCell() {
            HBox.setHgrow(spacer, Priority.ALWAYS);
            bullet.setStyle("-fx-text-fill: #2980b9;");
            bullet.setMinWidth(12);
            renameBtn.getStyleClass().add("cell-button");
            deleteBtn.getStyleClass().add("cell-button");
            box.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().addAll(bullet, nameLabel, spacer, renameBtn, deleteBtn);

            renameBtn.setOnAction(e -> {
                getListView().getSelectionModel().select(getItem());
                handleRename();
            });
            deleteBtn.setOnAction(e -> {
                getListView().getSelectionModel().select(getItem());
                handleDelete();
            });
        }

        @Override
        protected void updateItem(Workspace ws, boolean empty) {
            super.updateItem(ws, empty);
            if (empty || ws == null) {
                setGraphic(null);
            } else {
                bullet.setVisible(activeWorkspace != null
                        && activeWorkspace.getId().equals(ws.getId()));
                nameLabel.setText(ws.getName());
                setGraphic(box);
            }
        }
    }
}
