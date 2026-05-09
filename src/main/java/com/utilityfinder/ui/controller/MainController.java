package com.utilityfinder.ui.controller;

import com.utilityfinder.model.Workspace;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.StackPane;

public class MainController {

    @FXML private ComboBox<Workspace> workspaceSelector;
    @FXML private StackPane contentArea;
    @FXML private Button btnUsage;
    @FXML private Button btnPlans;
    @FXML private Button btnCompare;

    @FXML
    public void initialize() {
        // TODO: inject services, load workspaces into selector,
        //       prompt to create first workspace if none exist,
        //       default to Usage view
    }

    @FXML
    private void handleManageWorkspaces() {
        // TODO: open workspace manager dialog
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

    private void setActiveNav(Button active) {
        for (Button btn : new Button[]{btnUsage, btnPlans, btnCompare}) {
            btn.getStyleClass().remove("nav-button-active");
        }
        active.getStyleClass().add("nav-button-active");
    }
}
