package com.utilityfinder.ui.controller;

import com.utilityfinder.app.Services;
import com.utilityfinder.model.RatePlan;
import com.utilityfinder.model.TierDiscount;
import com.utilityfinder.model.TouWindow;
import com.utilityfinder.model.Workspace;
import com.utilityfinder.service.RatePlanService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class RatePlanController implements WorkspaceAware {

    // ── FXML ──────────────────────────────────────────────────────────────────

    @FXML private TableView<RatePlan>           plansTable;
    @FXML private TableColumn<RatePlan, String> colProvider;
    @FXML private TableColumn<RatePlan, String> colPlanName;
    @FXML private TableColumn<RatePlan, String> colTerm;
    @FXML private TableColumn<RatePlan, String> colBase;
    @FXML private TableColumn<RatePlan, String> colRate;
    @FXML private TableColumn<RatePlan, String> colRenewable;
    @FXML private TableColumn<RatePlan, Void>   colActions;

    @FXML private Label     formTitle;
    @FXML private TextField fieldProvider;
    @FXML private TextField fieldPlanName;
    @FXML private TextField fieldTerm;
    @FXML private TextField fieldBase;
    @FXML private TextField fieldRate;
    @FXML private CheckBox  checkCurrent;
    @FXML private TextField  fieldRenewable;
    @FXML private DatePicker fieldContractEnd;
    @FXML private TextField  fieldTermFeeFlat;
    @FXML private TextField  fieldTermFeePerMonth;
    @FXML private VBox       discountRows;
    @FXML private VBox       touWindowRows;
    @FXML private TextArea   fieldNotes;

    // ── State ─────────────────────────────────────────────────────────────────

    private static final List<String> HOUR_LABELS = IntStream.range(0, 24)
            .mapToObj(h -> h == 0 ? "12am" : h < 12 ? h + "am" : h == 12 ? "12pm" : (h - 12) + "pm")
            .toList();

    private final ObservableList<RatePlan> plans = FXCollections.observableArrayList();
    private final List<DiscountRow>   discountRowList   = new ArrayList<>();
    private final List<TouWindowRow>  touWindowRowList  = new ArrayList<>();
    private RatePlan editing = null;
    private Workspace workspace;
    private final RatePlanService service = Services.get().ratePlans;

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupTable();
        setupFormInputGuards();
    }

    @Override
    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
        loadPlans();
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    private void setupTable() {
        colProvider.setCellValueFactory(c -> {
            RatePlan p = c.getValue();
            String name = p.isCurrent() ? "★ " + p.getProviderName() : p.getProviderName();
            return new SimpleStringProperty(name);
        });
        colPlanName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getPlanName()));
        colTerm.setCellValueFactory(c -> {
            Integer t = c.getValue().getContractTermMonths();
            return new SimpleStringProperty(t == null ? "—" : t + " mo");
        });
        colBase.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("$%.2f", c.getValue().getBaseCharge())));
        colRate.setCellValueFactory(c -> {
            RatePlan p = c.getValue();
            String rate = formatRate(p.getRatePerKwh());
            return new SimpleStringProperty(p.hasTouWindows() ? rate + "  (TOU)" : rate);
        });
        colRenewable.setCellValueFactory(c -> {
            Double pct = c.getValue().getRenewablePercent();
            return new SimpleStringProperty(pct == null ? "—" : String.format("%.0f%%", pct));
        });

        colActions.setCellFactory(tc -> new TableCell<>() {
            private final Button editBtn   = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final Button currentBtn = new Button("Mark ★");
            private final HBox box = new HBox(6, editBtn, deleteBtn, currentBtn);

            {
                editBtn.getStyleClass().add("cell-button");
                deleteBtn.getStyleClass().add("cell-button");
                currentBtn.getStyleClass().add("cell-button");
                box.setAlignment(Pos.CENTER_LEFT);
                editBtn.setOnAction(e    -> handleEditPlan(getTableRow().getItem()));
                deleteBtn.setOnAction(e  -> handleDeletePlan(getTableRow().getItem()));
                currentBtn.setOnAction(e -> handleMarkCurrent(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    currentBtn.setDisable(getTableRow().getItem().isCurrent());
                    setGraphic(box);
                }
            }
        });

        plansTable.setOnKeyPressed(e -> {
            RatePlan sel = plansTable.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (e.getCode() == KeyCode.F2)    { handleEditPlan(sel);   e.consume(); }
            if (e.getCode() == KeyCode.DELETE) { handleDeletePlan(sel); e.consume(); }
        });

        plansTable.setItems(plans);
    }

    private void loadPlans() {
        if (workspace == null) return;
        plans.setAll(service.findByWorkspace(workspace.getId()));
    }

    // ── Form ──────────────────────────────────────────────────────────────────

    private void setupFormInputGuards() {
        // Term: digits only, max 3
        fieldTerm.textProperty().addListener((obs, old, nv) -> {
            String digits = nv.replaceAll("[^\\d]", "");
            if (digits.length() > 3) digits = digits.substring(0, 3);
            if (!digits.equals(nv)) fieldTerm.setText(digits);
        });

        // Base and Rate: digits + single decimal point
        for (TextField f : new TextField[]{fieldBase, fieldRate}) {
            f.textProperty().addListener((obs, old, nv) -> {
                if (!nv.matches("\\d*\\.?\\d*")) f.setText(old);
            });
        }

        // Renewable: 0–100 with optional decimal
        fieldRenewable.textProperty().addListener((obs, old, nv) -> {
            if (!nv.matches("\\d*\\.?\\d*")) { fieldRenewable.setText(old); return; }
            try {
                if (!nv.isEmpty() && Double.parseDouble(nv) > 100) fieldRenewable.setText(old);
            } catch (NumberFormatException ignored) {}
        });

        // Termination fee fields: non-negative decimal
        for (TextField f : new TextField[]{fieldTermFeeFlat, fieldTermFeePerMonth}) {
            f.textProperty().addListener((obs, old, nv) -> {
                if (!nv.matches("\\d*\\.?\\d*")) f.setText(old);
            });
        }
    }

    @FXML
    private void handleAddPlan() {
        resetForm();
        fieldProvider.requestFocus();
    }

    @FXML
    private void handleSave() {
        // Collect and validate basic fields
        String provider          = fieldProvider.getText().strip();
        String planName          = fieldPlanName.getText().strip();
        String termText          = fieldTerm.getText().strip();
        String baseText          = fieldBase.getText().strip();
        String rateText          = fieldRate.getText().strip();
        String renewableText     = fieldRenewable.getText().strip();
        String termFeeFlatText   = fieldTermFeeFlat.getText().strip();
        String termFeeMonthText  = fieldTermFeePerMonth.getText().strip();

        if (provider.isEmpty()) { showError("Provider name is required."); fieldProvider.requestFocus(); return; }
        if (planName.isEmpty()) { showError("Plan name is required.");     fieldPlanName.requestFocus(); return; }
        if (rateText.isEmpty()) { showError("Rate (¢/kWh) is required.");  fieldRate.requestFocus();     return; }

        double baseCharge;
        double rateCents;
        try {
            baseCharge = baseText.isEmpty() ? 0.0 : Double.parseDouble(baseText);
            rateCents  = Double.parseDouble(rateText);
        } catch (NumberFormatException ex) {
            showError("Invalid number in Base Charge or Rate field.");
            return;
        }

        Double renewablePercent = null;
        if (!renewableText.isEmpty()) {
            try {
                renewablePercent = Double.parseDouble(renewableText);
            } catch (NumberFormatException ex) {
                showError("Invalid number in Renewable (%) field.");
                return;
            }
        }

        Double terminationFeeFlat = null;
        if (!termFeeFlatText.isEmpty()) {
            try {
                terminationFeeFlat = Double.parseDouble(termFeeFlatText);
            } catch (NumberFormatException ex) {
                showError("Invalid number in Flat Exit Fee field.");
                return;
            }
        }

        Double terminationFeePerMonth = null;
        if (!termFeeMonthText.isEmpty()) {
            try {
                terminationFeePerMonth = Double.parseDouble(termFeeMonthText);
            } catch (NumberFormatException ex) {
                showError("Invalid number in Exit Fee/Month field.");
                return;
            }
        }

        LocalDate contractEndDate = fieldContractEnd.getValue();

        Integer termMonths = null;
        if (!termText.isEmpty()) {
            termMonths = Integer.parseInt(termText);
        }

        // Collect discount rows
        List<TierDiscount> discounts = new ArrayList<>();
        for (int i = 0; i < discountRowList.size(); i++) {
            DiscountRow row = discountRowList.get(i);
            String threshText  = row.thresholdField.getText().strip();
            String amountText  = row.amountField.getText().strip();
            if (threshText.isEmpty() && amountText.isEmpty()) continue; // skip blank rows
            try {
                double thresh = Double.parseDouble(threshText);
                double amount = Double.parseDouble(amountText);
                discounts.add(new TierDiscount(thresh, amount, i));
            } catch (NumberFormatException ex) {
                showError("Invalid number in discount row " + (i + 1) + ".");
                return;
            }
        }

        // Collect TOU windows
        List<TouWindow> touWindows = new ArrayList<>();
        for (int i = 0; i < touWindowRowList.size(); i++) {
            TouWindowRow row = touWindowRowList.get(i);
            int startHour = row.startBox.getSelectionModel().getSelectedIndex();
            int endHour   = row.endBox.getSelectionModel().getSelectedIndex();
            String touRateText = row.rateField.getText().strip();
            if (touRateText.isEmpty()) { showError("TOU window " + (i + 1) + " is missing a rate."); return; }
            double rateCentsWindow;
            try {
                rateCentsWindow = Double.parseDouble(touRateText);
            } catch (NumberFormatException ex) {
                showError("Invalid rate in TOU window " + (i + 1) + ".");
                return;
            }
            touWindows.add(new TouWindow(startHour, endHour, rateCentsWindow / 100.0, i));
        }

        // Build the plan
        RatePlan plan = editing != null ? editing : new RatePlan();
        plan.setWorkspaceId(workspace.getId());
        plan.setProviderName(provider);
        plan.setPlanName(planName);
        plan.setContractTermMonths(termMonths);
        plan.setBaseCharge(baseCharge);
        plan.setRatePerKwh(rateCents / 100.0);
        plan.setNotes(fieldNotes.getText().strip().isEmpty() ? null : fieldNotes.getText().strip());
        plan.setCurrent(checkCurrent.isSelected());
        plan.setRenewablePercent(renewablePercent);
        plan.setTerminationFeeFlat(terminationFeeFlat);
        plan.setTerminationFeePerMonth(terminationFeePerMonth);
        plan.setContractEndDate(contractEndDate);
        plan.setDiscounts(discounts);
        plan.setTouWindows(touWindows);

        try {
            if (editing == null) {
                service.save(plan);
            } else {
                service.update(plan);
            }
            loadPlans();
            resetForm();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        resetForm();
    }

    @FXML
    private void handleAddDiscount() {
        addDiscountRow(0, 0);
        if (!discountRowList.isEmpty()) {
            discountRowList.get(discountRowList.size() - 1).thresholdField.requestFocus();
        }
    }

    @FXML
    private void handleAddTouWindow() {
        addTouWindowRow(21, 7, 0.0); // default: 9 pm – 7 am, free
        if (!touWindowRowList.isEmpty()) {
            touWindowRowList.get(touWindowRowList.size() - 1).rateField.requestFocus();
        }
    }

    private void handleEditPlan(RatePlan plan) {
        if (plan == null) return;
        editing = plan;
        formTitle.setText("Edit Plan");
        fieldProvider.setText(plan.getProviderName());
        fieldPlanName.setText(plan.getPlanName());
        fieldTerm.setText(plan.getContractTermMonths() == null ? ""
                : String.valueOf(plan.getContractTermMonths()));
        fieldBase.setText(String.format("%.2f", plan.getBaseCharge()));
        fieldRate.setText(String.format("%.4f", plan.getRatePerKwh() * 100)
                .replaceAll("0+$", "").replaceAll("\\.$", ""));
        checkCurrent.setSelected(plan.isCurrent());
        fieldRenewable.setText(plan.getRenewablePercent() == null ? ""
                : String.format("%.4f", plan.getRenewablePercent())
                        .replaceAll("0+$", "").replaceAll("\\.$", ""));
        fieldContractEnd.setValue(plan.getContractEndDate());
        fieldTermFeeFlat.setText(plan.getTerminationFeeFlat() == null ? ""
                : String.format("%.2f", plan.getTerminationFeeFlat())
                        .replaceAll("0+$", "").replaceAll("\\.$", ""));
        fieldTermFeePerMonth.setText(plan.getTerminationFeePerMonth() == null ? ""
                : String.format("%.2f", plan.getTerminationFeePerMonth())
                        .replaceAll("0+$", "").replaceAll("\\.$", ""));
        fieldNotes.setText(plan.getNotes() == null ? "" : plan.getNotes());

        discountRowList.clear();
        discountRows.getChildren().clear();
        for (TierDiscount d : plan.getDiscounts()) {
            addDiscountRow(d.getThresholdKwh(), d.getDiscountAmt());
        }

        touWindowRowList.clear();
        touWindowRows.getChildren().clear();
        for (TouWindow w : plan.getTouWindows()) {
            addTouWindowRow(w.getStartHour(), w.getEndHour(), w.getRatePerKwh() * 100.0);
        }
        fieldProvider.requestFocus();
        fieldProvider.selectAll();
    }

    private void handleDeletePlan(RatePlan plan) {
        if (plan == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + plan.getProviderName() + " — " + plan.getPlanName() + "\"?",
                ButtonType.YES, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.initOwner(plansTable.getScene().getWindow());
        if (confirm.showAndWait().filter(b -> b == ButtonType.YES).isPresent()) {
            service.delete(plan.getId());
            loadPlans();
            if (editing != null && editing.getId().equals(plan.getId())) resetForm();
        }
    }

    private void handleMarkCurrent(RatePlan plan) {
        if (plan == null) return;
        service.markAsCurrent(plan.getId(), workspace.getId());
        loadPlans();
    }

    // ── Discount row management ───────────────────────────────────────────────

    private void addDiscountRow(double threshold, double amount) {
        TextField thresholdField = new TextField(threshold > 0 ? formatDecimal(threshold) : "");
        TextField amountField    = new TextField(amount > 0    ? formatDecimal(amount)    : "");
        thresholdField.setPrefWidth(100);
        amountField.setPrefWidth(100);

        // Numeric guards
        for (TextField f : new TextField[]{thresholdField, amountField}) {
            f.textProperty().addListener((obs, old, nv) -> {
                if (!nv.matches("\\d*\\.?\\d*")) f.setText(old);
            });
        }

        Button removeBtn = new Button("✕");
        removeBtn.getStyleClass().add("cell-button");
        removeBtn.setStyle("-fx-text-fill: #e74c3c;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.NEVER);

        HBox hbox = new HBox(8,
                new Label("If usage ≥"), thresholdField,
                new Label("kWh, subtract $"), amountField,
                removeBtn);
        hbox.setAlignment(Pos.CENTER_LEFT);

        DiscountRow row = new DiscountRow(thresholdField, amountField, hbox);
        discountRowList.add(row);
        discountRows.getChildren().add(hbox);

        removeBtn.setOnAction(e -> {
            discountRowList.remove(row);
            discountRows.getChildren().remove(hbox);
        });
    }

    // ── TOU window row management ─────────────────────────────────────────────

    private void addTouWindowRow(int startHour, int endHour, double rateCents) {
        ComboBox<String> startBox = new ComboBox<>(FXCollections.observableArrayList(HOUR_LABELS));
        ComboBox<String> endBox   = new ComboBox<>(FXCollections.observableArrayList(HOUR_LABELS));
        startBox.getSelectionModel().select(startHour);
        endBox.getSelectionModel().select(endHour);
        startBox.setPrefWidth(90);
        endBox.setPrefWidth(90);

        TextField rateField = new TextField(rateCents > 0 ? formatDecimal(rateCents) : "0");
        rateField.setPrefWidth(80);
        rateField.textProperty().addListener((obs, old, nv) -> {
            if (!nv.matches("\\d*\\.?\\d*")) rateField.setText(old);
        });

        Button removeBtn = new Button("✕");
        removeBtn.getStyleClass().add("cell-button");
        removeBtn.setStyle("-fx-text-fill: #e74c3c;");

        HBox hbox = new HBox(8,
                new Label("From"), startBox,
                new Label("to"), endBox,
                new Label("at ¢/kWh"), rateField,
                removeBtn);
        hbox.setAlignment(Pos.CENTER_LEFT);

        TouWindowRow row = new TouWindowRow(startBox, endBox, rateField, hbox);
        touWindowRowList.add(row);
        touWindowRows.getChildren().add(hbox);

        removeBtn.setOnAction(e -> {
            touWindowRowList.remove(row);
            touWindowRows.getChildren().remove(hbox);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void resetForm() {
        editing = null;
        formTitle.setText("Add Plan");
        fieldProvider.clear();
        fieldPlanName.clear();
        fieldTerm.clear();
        fieldBase.clear();
        fieldRate.clear();
        checkCurrent.setSelected(false);
        fieldRenewable.clear();
        fieldContractEnd.setValue(null);
        fieldTermFeeFlat.clear();
        fieldTermFeePerMonth.clear();
        fieldNotes.clear();
        discountRowList.clear();
        discountRows.getChildren().clear();
        touWindowRowList.clear();
        touWindowRows.getChildren().clear();
    }

    private static String formatRate(double ratePerKwh) {
        return String.format("%.4f", ratePerKwh * 100)
                .replaceAll("0+$", "").replaceAll("\\.$", "") + "¢/kWh";
    }

    private static String formatDecimal(double value) {
        return String.format("%.4f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.initOwner(plansTable.getScene().getWindow());
        alert.showAndWait();
    }

    // ── Inner type ────────────────────────────────────────────────────────────

    private record DiscountRow(TextField thresholdField, TextField amountField, HBox hbox) {}

    private record TouWindowRow(ComboBox<String> startBox, ComboBox<String> endBox,
                                TextField rateField, HBox hbox) {}
}
