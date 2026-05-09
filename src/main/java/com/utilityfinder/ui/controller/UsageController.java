package com.utilityfinder.ui.controller;

import com.utilityfinder.app.Services;
import com.utilityfinder.model.UsageRecord;
import com.utilityfinder.model.Workspace;
import com.utilityfinder.service.UsageService;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
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

    @FXML private TableView<UsageRecord> usageTable;
    @FXML private TableColumn<UsageRecord, Integer> colYear;
    @FXML private TableColumn<UsageRecord, String>  colMonth;
    @FXML private TableColumn<UsageRecord, Double>  colKwh;
    @FXML private TableColumn<UsageRecord, Void>    colActions;
    @FXML private ComboBox<String> yearFilter;
    @FXML private VBox profileContainer;
    @FXML private Label formTitle;
    @FXML private TextField fieldYear;
    @FXML private ComboBox<String> fieldMonth;
    @FXML private TextField fieldKwh;

    private final ObservableList<UsageRecord> records = FXCollections.observableArrayList();
    private FilteredList<UsageRecord> filteredRecords;
    private UsageRecord editing = null;
    private Workspace workspace;
    private final UsageService service = Services.get().usage;

    @FXML
    public void initialize() {
        setupTable();
        setupForm();

        filteredRecords = new FilteredList<>(records, r -> true);
        SortedList<UsageRecord> sorted = new SortedList<>(filteredRecords);
        sorted.comparatorProperty().bind(usageTable.comparatorProperty());
        usageTable.setItems(sorted);

        colYear.setSortType(TableColumn.SortType.DESCENDING);
        colMonth.setSortType(TableColumn.SortType.ASCENDING);
        usageTable.getSortOrder().setAll(colYear, colMonth);

        yearFilter.getItems().add("All Years");
        yearFilter.getSelectionModel().selectFirst();
    }

    @Override
    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
        loadRecords();
    }

    // ── Table setup ───────────────────────────────────────────────────────────

    private void setupTable() {
        colYear.setCellValueFactory(c ->
                new SimpleIntegerProperty(c.getValue().getYear()).asObject());

        colMonth.setCellValueFactory(c ->
                new SimpleStringProperty(MONTH_NAMES.get(c.getValue().getMonth() - 1)));

        colKwh.setCellValueFactory(c ->
                new SimpleDoubleProperty(c.getValue().getKwhUsed()).asObject());
        colKwh.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("%,.1f", v));
            }
        });

        colActions.setCellFactory(tc -> new TableCell<>() {
            private final Button editBtn   = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox box = new HBox(6, editBtn, deleteBtn);

            {
                editBtn.getStyleClass().add("cell-button");
                deleteBtn.getStyleClass().add("cell-button");
                box.setAlignment(Pos.CENTER_LEFT);
                editBtn.setOnAction(e   -> handleEditRecord(getTableRow().getItem()));
                deleteBtn.setOnAction(e -> handleDeleteRecord(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        usageTable.setOnKeyPressed(e -> {
            UsageRecord sel = usageTable.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (e.getCode() == KeyCode.F2)     { handleEditRecord(sel);   e.consume(); }
            if (e.getCode() == KeyCode.DELETE)  { handleDeleteRecord(sel); e.consume(); }
        });
    }

    // ── Form setup ────────────────────────────────────────────────────────────

    private void setupForm() {
        fieldMonth.getItems().addAll(MONTH_NAMES);

        // Year: digits only, max 4
        fieldYear.textProperty().addListener((obs, old, nv) -> {
            String digits = nv.replaceAll("[^\\d]", "");
            if (digits.length() > 4) digits = digits.substring(0, 4);
            if (!digits.equals(nv)) fieldYear.setText(digits);
        });

        // kWh: digits + at most one decimal point
        fieldKwh.textProperty().addListener((obs, old, nv) -> {
            if (!nv.matches("\\d*\\.?\\d*")) fieldKwh.setText(old);
        });

        // Enter on kWh triggers save
        fieldKwh.setOnAction(e -> handleSave());

        fieldYear.setText(String.valueOf(LocalDate.now().getYear()));
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadRecords() {
        if (workspace == null) return;
        records.setAll(service.findByWorkspace(workspace.getId()));
        refreshYearFilter();
        refreshProfile();
    }

    private void refreshYearFilter() {
        String selected = yearFilter.getValue();
        yearFilter.getItems().setAll("All Years");
        records.stream()
                .mapToInt(UsageRecord::getYear)
                .distinct()
                .boxed()
                .sorted(Comparator.reverseOrder())
                .map(String::valueOf)
                .forEach(yearFilter.getItems()::add);

        if (selected != null && yearFilter.getItems().contains(selected)) {
            yearFilter.setValue(selected);
        } else {
            yearFilter.getSelectionModel().selectFirst();
        }
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

    // ── Event handlers ────────────────────────────────────────────────────────

    @FXML
    private void handleYearFilter() {
        String selected = yearFilter.getValue();
        if (selected == null || "All Years".equals(selected)) {
            filteredRecords.setPredicate(r -> true);
        } else {
            int year = Integer.parseInt(selected);
            filteredRecords.setPredicate(r -> r.getYear() == year);
        }
    }

    @FXML
    private void handleAddEntry() {
        resetForm();
        fieldYear.requestFocus();
        fieldYear.selectAll();
    }

    @FXML
    private void handleSave() {
        // Validate year
        String yearText = fieldYear.getText().strip();
        if (yearText.length() != 4) {
            showError("Please enter a 4-digit year.");
            fieldYear.requestFocus();
            return;
        }
        int year = Integer.parseInt(yearText);

        // Validate month
        int monthIndex = fieldMonth.getSelectionModel().getSelectedIndex();
        if (monthIndex < 0) {
            showError("Please select a month.");
            fieldMonth.requestFocus();
            return;
        }
        int month = monthIndex + 1;

        // Validate kWh
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
                // Keep year — user likely entering multiple months for the same year
                fieldMonth.getSelectionModel().clearSelection();
                fieldKwh.clear();
                fieldMonth.requestFocus();
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
}
