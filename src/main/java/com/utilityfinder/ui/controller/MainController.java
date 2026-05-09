package com.utilityfinder.ui.controller;

import com.utilityfinder.app.Services;
import com.utilityfinder.model.Workspace;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class MainController {

    @FXML private ComboBox<Workspace> workspaceSelector;
    @FXML private StackPane contentArea;
    @FXML private Button btnUsage;
    @FXML private Button btnPlans;
    @FXML private Button btnCompare;

    @FXML
    public void initialize() {
        refreshWorkspaces();
        workspaceSelector.setOnAction(e -> onWorkspaceSelected());
        // Defer first-run check until after the scene is shown
        Platform.runLater(this::checkFirstRun);
    }

    @FXML
    private void handleManageWorkspaces() {
        openWorkspaceManager();
    }

    @FXML
    private void handleNavUsage() {
        setActiveNav(btnUsage);
        // TODO: load usage view into contentArea
    }

    @FXML
    private void handleNavPlans() {
        setActiveNav(btnPlans);
        // TODO: load plans view into contentArea
    }

    @FXML
    private void handleNavCompare() {
        setActiveNav(btnCompare);
        // TODO: load comparison view into contentArea
    }

    // ── Workspace management ──────────────────────────────────────────────────

    private void checkFirstRun() {
        if (workspaceSelector.getItems().isEmpty()) {
            openWorkspaceManager();
        } else {
            workspaceSelector.getSelectionModel().selectFirst();
        }
    }

    private void openWorkspaceManager() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/utilityfinder/ui/view/workspace-manager.fxml"));
            VBox root = loader.load();

            WorkspaceManagerController ctrl = loader.getController();
            ctrl.init(Services.get().workspaces, workspaceSelector.getValue());
            ctrl.load();

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(workspaceSelector.getScene().getWindow());
            dialog.setTitle("Manage Workspaces");
            dialog.setResizable(false);
            dialog.setScene(new Scene(root));
            dialog.showAndWait();

            refreshWorkspaces();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void refreshWorkspaces() {
        Workspace current = workspaceSelector.getValue();
        List<Workspace> all = Services.get().workspaces.findAll();
        workspaceSelector.getItems().setAll(all);

        if (current != null) {
            all.stream()
                    .filter(w -> w.getId().equals(current.getId()))
                    .findFirst()
                    .ifPresentOrElse(
                            workspaceSelector.getSelectionModel()::select,
                            () -> workspaceSelector.getSelectionModel().selectFirst());
        } else if (!all.isEmpty()) {
            workspaceSelector.getSelectionModel().selectFirst();
        }
    }

    private void onWorkspaceSelected() {
        // TODO: notify active views to reload for the new workspace
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void setActiveNav(Button active) {
        for (Button btn : new Button[]{btnUsage, btnPlans, btnCompare}) {
            btn.getStyleClass().remove("nav-button-active");
        }
        active.getStyleClass().add("nav-button-active");
    }
}
