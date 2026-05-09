package com.utilityfinder.ui.controller;

import com.utilityfinder.model.Workspace;
import com.utilityfinder.service.WorkspaceService;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.util.Optional;

public class WorkspaceManagerController {

    @FXML private ListView<Workspace> workspaceList;
    @FXML private Button btnRename;
    @FXML private Button btnDelete;

    private WorkspaceService service;
    private Workspace activeWorkspace;

    public void init(WorkspaceService service, Workspace activeWorkspace) {
        this.service = service;
        this.activeWorkspace = activeWorkspace;
    }

    @FXML
    public void initialize() {
        workspaceList.setCellFactory(lv -> new WorkspaceCell());
        workspaceList.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> updateButtonState(sel));

        workspaceList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F2)            handleRename();
            else if (e.getCode() == KeyCode.DELETE)   handleDelete();
        });

        updateButtonState(null);
    }

    /** Called after init() to populate the list. */
    public void load() {
        refresh();
        workspaceList.getSelectionModel().selectFirst();
    }

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

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    // ── List cell ─────────────────────────────────────────────────────────────

    private class WorkspaceCell extends ListCell<Workspace> {

        private final HBox box = new HBox(8);
        private final Label bullet = new Label("●");
        private final Label nameLabel = new Label();
        private final Region spacer = new Region();
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
