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
        return repo.findAll();
    }

    public Optional<Workspace> findById(long id) {
        return repo.findById(id);
    }

    public Workspace create(String name) {
        String trimmed = validate(name);
        boolean duplicate = repo.findAll().stream()
                .anyMatch(w -> w.getName().equalsIgnoreCase(trimmed));
        if (duplicate) throw new IllegalArgumentException(
                "A workspace named \"" + trimmed + "\" already exists.");
        return repo.save(new Workspace(trimmed));
    }

    public void rename(long id, String newName) {
        String trimmed = validate(newName);
        Workspace existing = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + id));
        boolean duplicate = repo.findAll().stream()
                .anyMatch(w -> !w.getId().equals(id) && w.getName().equalsIgnoreCase(trimmed));
        if (duplicate) throw new IllegalArgumentException(
                "A workspace named \"" + trimmed + "\" already exists.");
        existing.setName(trimmed);
        repo.update(existing);
    }

    public void delete(long id) {
        repo.delete(id);
    }

    private String validate(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isBlank())
            throw new IllegalArgumentException("Workspace name cannot be blank.");
        return trimmed;
    }
}
