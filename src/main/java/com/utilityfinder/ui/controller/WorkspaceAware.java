package com.utilityfinder.ui.controller;

import com.utilityfinder.model.Workspace;

/** Implemented by view controllers that are scoped to a single workspace. */
public interface WorkspaceAware {
    void setWorkspace(Workspace workspace);
}
