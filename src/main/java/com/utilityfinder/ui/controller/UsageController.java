package com.utilityfinder.ui.controller;

import com.utilityfinder.app.Services;
import com.utilityfinder.model.UsageRecord;
import com.utilityfinder.model.Workspace;
import com.utilityfinder.service.UsageService;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class UsageController implements WorkspaceAware {

    private static final List<String> MONTH_NAMES = List.of(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December");

    private static final String[] MONTH_ABBR = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    @FXML private TreeTableView<UsageRow>           usageTable;
    @FXML private TreeTableColumn<UsageRow, String> colGroup;
    @FXML private TreeTableColumn<UsageRow, String> colKwh;
    @FXML private TreeTableColumn<UsageRow, Void>   colActions;
    @FXML private VBox             profileContainer;
    @FXML private Label            formTitle;
    @FXML private TextField        fieldYear;
    @FXML private ComboBox<String> fieldMonth;
    @FXML private TextField        fieldKwh;

    private UsageRecord editing = null;
    private Workspace workspace;
    private final UsageService service = Services.get().usage;

    @FXML
    public void initialize() {
        setupTreeTable();
        setupForm();
    }

    @Override
    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
        loadRecords();
    }

    // ── Tree table ────────────────────────────────────────────────────────────

    private void setupTreeTable() {
        colGroup.setCellValueFactory(c -> {
            TreeItem<UsageRow> item = c.getValue();
            if (item == null) return new SimpleStringProperty("");
            UsageRow row = item.getValue();
            if (row == null) return new SimpleStringProperty("");
            return row.isYearGroup()
                    ? new SimpleStringProperty(String.valueOf(row.year()))
                    : new SimpleStringProperty(MONTH_NAMES.get(row.record().getMonth() - 1));
        });

        colKwh.setCellValueFactory(c -> {
            TreeItem<UsageRow> item = c.getValue();
            if (item == null) return new SimpleStringProperty("");
            UsageRow row = item.getValue();
            if (row == null || row.isYearGroup()) return new SimpleStringProperty("");
            return new SimpleStringProperty(String.format("%,.1f", row.record().getKwhUsed()));
        });

        colActions.setCellFactory(tc -> new TreeTableCell<>() {
            private final Button editBtn   = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox   box       = new HBox(6, editBtn, deleteBtn);

            {
                editBtn.getStyleClass().add("cell-button");
                deleteBtn.getStyleClass().add("cell-button");
                box.setAlignment(Pos.CENTER_LEFT);
                editBtn.setOnAction(e -> {
                    UsageRow row = rowItem();
                    if (row != null && !row.isYearGroup()) handleEditRecord(row.record());
                });
                deleteBtn.setOnAction(e -> {
                    UsageRow row = rowItem();
                    if (row != null && !row.isYearGroup()) handleDeleteRecord(row.record());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                UsageRow row = rowItem();
                setGraphic(!empty && row != null && !row.isYearGroup() ? box : null);
            }

            private UsageRow rowItem() {
                TreeTableRow<UsageRow> r = getTreeTableRow();
                return r == null ? null : r.getItem();
            }
        });

        // Year group rows: bold, tinted background
        usageTable.setRowFactory(tv -> new TreeTableRow<>() {
            @Override
            protected void updateItem(UsageRow item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(!empty && item != null && item.isYearGroup()
                        ? "-fx-font-weight: bold; -fx-background-color: #eaf3fb;"
                        : "");
            }
        });

        usageTable.setOnKeyPressed(e -> {
            TreeItem<UsageRow> sel = usageTable.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getValue() == null || sel.getValue().isYearGroup()) return;
            UsageRecord record = sel.getValue().record();
            if (e.getCode() == KeyCode.F2)     { handleEditRecord(record);   e.consume(); }
            if (e.getCode() == KeyCode.DELETE)  { handleDeleteRecord(record); e.consume(); }
        });
    }

    // ── Form setup ────────────────────────────────────────────────────────────

    private void setupForm() {
        fieldMonth.getItems().addAll(MONTH_NAMES);

        fieldYear.textProperty().addListener((obs, old, nv) -> {
            String digits = nv.replaceAll("[^\\d]", "");
            if (digits.length() > 4) digits = digits.substring(0, 4);
            if (!digits.equals(nv)) fieldYear.setText(digits);
        });

        fieldKwh.textProperty().addListener((obs, old, nv) -> {
            if (!nv.matches("\\d*\\.?\\d*")) fieldKwh.setText(old);
        });

        fieldKwh.setOnAction(e -> handleSave());

        fieldYear.setText(String.valueOf(LocalDate.now().getYear()));
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadRecords() {
        if (workspace == null) return;
        List<UsageRecord> all = service.findByWorkspace(workspace.getId());

        TreeItem<UsageRow> root = new TreeItem<>();
        all.stream()
                .mapToInt(UsageRecord::getYear)
                .distinct()
                .boxed()
                .sorted(Comparator.reverseOrder())
                .forEach(year -> {
                    TreeItem<UsageRow> yearItem = new TreeItem<>(new UsageRow(year, null));
                    yearItem.setExpanded(true);
                    all.stream()
                            .filter(r -> r.getYear() == year)
                            .sorted(Comparator.comparingInt(UsageRecord::getMonth))
                            .map(r -> new TreeItem<>(new UsageRow(null, r)))
                            .forEach(yearItem.getChildren()::add);
                    root.getChildren().add(yearItem);
                });

        usageTable.setRoot(root);
        refreshProfile();
    }

    private void refreshProfile() {
        profileContainer.getChildren().clear();
        if (workspace == null) return;
        double[] profile = service.getAveragedProfile(workspace.getId());
        profileContainer.getChildren().addAll(
                buildProfileRow(profile, 0, 6),
                buildProfileRow(profile, 6, 12));
    }

    private HBox buildProfileRow(double[] profile, int start, int end) {
        HBox row = new HBox(6);
        for (int i = start; i < end; i++) {
            VBox cell = new VBox(2);
            cell.setAlignment(Pos.CENTER);
            cell.setStyle("-fx-min-width: 72; -fx-background-color: #eaf3fb; " +
                          "-fx-padding: 4 8; -fx-background-radius: 4;");
            Label monthLbl = new Label(MONTH_ABBR[i]);
            monthLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");
            String kwhText = Double.isNaN(profile[i]) ? "—"
                    : String.format("%,.1f", profile[i]);
            Label kwhLbl = new Label(kwhText);
            cell.getChildren().addAll(monthLbl, kwhLbl);
            row.getChildren().add(cell);
        }
        return row;
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    @FXML
    private void handleAddEntry() {
        resetForm();
        fieldYear.requestFocus();
        fieldYear.selectAll();
    }

    @FXML
    private void handleSave() {
        String yearText = fieldYear.getText().strip();
        if (yearText.length() != 4) {
            showError("Please enter a 4-digit year.");
            fieldYear.requestFocus();
            return;
        }
        int year = Integer.parseInt(yearText);

        int monthIndex = fieldMonth.getSelectionModel().getSelectedIndex();
        if (monthIndex < 0) {
            showError("Please select a month.");
            fieldMonth.requestFocus();
            return;
        }
        int month = monthIndex + 1;

        String kwhText = fieldKwh.getText().strip();
        if (kwhText.isEmpty()) {
            showError("Please enter the kWh value.");
            fieldKwh.requestFocus();
            return;
        }
        double kwh;
        try {
            kwh = Double.parseDouble(kwhText);
        } catch (NumberFormatException ex) {
            showError("Invalid kWh value.");
            fieldKwh.requestFocus();
            return;
        }

        try {
            if (editing == null) {
                service.save(new UsageRecord(workspace.getId(), year, month, kwh));
                loadRecords();
                // Auto-advance: move to next month (wrapping Dec → Jan of next year)
                int nextIndex = monthIndex + 1;
                if (nextIndex >= 12) {
                    nextIndex = 0;
                    fieldYear.setText(String.valueOf(year + 1));
                }
                fieldMonth.getSelectionModel().select(nextIndex);
                fieldKwh.clear();
                fieldKwh.requestFocus();
            } else {
                editing.setKwhUsed(kwh);
                service.update(editing);
                loadRecords();
                resetForm();
            }
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        resetForm();
    }

    private void handleEditRecord(UsageRecord record) {
        if (record == null) return;
        editing = record;
        formTitle.setText("Edit  —  "
                + Month.of(record.getMonth()).getDisplayName(TextStyle.FULL, Locale.getDefault())
                + " " + record.getYear());
        fieldYear.setText(String.valueOf(record.getYear()));
        fieldMonth.getSelectionModel().select(record.getMonth() - 1);
        fieldKwh.setText(String.format("%.1f", record.getKwhUsed()));
        fieldKwh.requestFocus();
        fieldKwh.selectAll();
    }

    private void handleDeleteRecord(UsageRecord record) {
        if (record == null) return;
        String label = MONTH_NAMES.get(record.getMonth() - 1) + " " + record.getYear();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete the " + label + " record (" + String.format("%,.1f", record.getKwhUsed()) + " kWh)?",
                ButtonType.YES, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.initOwner(usageTable.getScene().getWindow());
        if (confirm.showAndWait().filter(b -> b == ButtonType.YES).isPresent()) {
            service.delete(record.getId());
            loadRecords();
            if (editing != null && editing.getId().equals(record.getId())) resetForm();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void resetForm() {
        editing = null;
        formTitle.setText("Add Entry");
        fieldYear.setText(String.valueOf(LocalDate.now().getYear()));
        fieldMonth.getSelectionModel().clearSelection();
        fieldKwh.clear();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.initOwner(usageTable.getScene().getWindow());
        alert.showAndWait();
    }

    // ── Inner type ────────────────────────────────────────────────────────────

    private record UsageRow(Integer year, UsageRecord record) {
        boolean isYearGroup() { return record == null; }
    }
}
