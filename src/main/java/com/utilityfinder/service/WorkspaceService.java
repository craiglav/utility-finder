package com.utilityfinder.service;

import com.utilityfinder.model.Workspace;
import com.utilityfinder.repository.WorkspaceRepository;

import java.util.List;
import java.util.Optional;

public class WorkspaceService {

    private final WorkspaceRepository repo;

    public WorkspaceService(WorkspaceRepository repo) {
        this.repo = repo;
    }

    public List<Workspace> findAll() {
        throw new UnsupportedOperationException("TODO");
    }

    public Workspace create(String name) {
        throw new UnsupportedOperationException("TODO");
    }

    public void rename(long id, String newName) {
        throw new UnsupportedOperationException("TODO");
    }

    public void delete(long id) {
        throw new UnsupportedOperationException("TODO");
    }

    public Optional<Workspace> findById(long id) {
        throw new UnsupportedOperationException("TODO");
    }
}
