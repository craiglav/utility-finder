package com.utilityfinder.service;

import com.utilityfinder.model.Workspace;
import com.utilityfinder.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock WorkspaceRepository repo;
    WorkspaceService service;

    @BeforeEach void setUp() {
        service = new WorkspaceService(repo);
        // lenient: validation tests throw before save is called
        lenient().when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test void create_succeeds_whenNameIsUnique() {
        when(repo.findAll()).thenReturn(List.of());
        assertDoesNotThrow(() -> service.create("Home"));
        verify(repo).save(any());
    }

    @Test void create_trimsWhitespace() {
        when(repo.findAll()).thenReturn(List.of());
        service.create("  Home  ");
        verify(repo).save(argThat(w -> "Home".equals(w.getName())));
    }

    @Test void create_blankName_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.create("   "));
        verify(repo, never()).save(any());
    }

    @Test void create_nullName_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.create(null));
    }

    @Test void create_duplicateName_throws() {
        when(repo.findAll()).thenReturn(List.of(workspace(1L, "Home")));
        assertThrows(IllegalArgumentException.class, () -> service.create("Home"));
    }

    @Test void create_duplicateNameCaseInsensitive_throws() {
        when(repo.findAll()).thenReturn(List.of(workspace(1L, "Home")));
        assertThrows(IllegalArgumentException.class, () -> service.create("HOME"));
    }

    @Test void create_differentName_succeeds() {
        when(repo.findAll()).thenReturn(List.of(workspace(1L, "Home")));
        assertDoesNotThrow(() -> service.create("Office"));
    }

    // ── rename ────────────────────────────────────────────────────────────────

    @Test void rename_succeeds_whenNameIsUnique() {
        Workspace ws = workspace(1L, "Home");
        when(repo.findById(1L)).thenReturn(Optional.of(ws));
        when(repo.findAll()).thenReturn(List.of(ws));

        assertDoesNotThrow(() -> service.rename(1L, "House"));
        verify(repo).update(ws);
    }

    @Test void rename_toSameName_allowed() {
        // Renaming to own name is not a duplicate
        Workspace ws = workspace(1L, "Home");
        when(repo.findById(1L)).thenReturn(Optional.of(ws));
        when(repo.findAll()).thenReturn(List.of(ws));

        assertDoesNotThrow(() -> service.rename(1L, "Home"));
    }

    @Test void rename_toSameNameDifferentCase_allowed() {
        Workspace ws = workspace(1L, "Home");
        when(repo.findById(1L)).thenReturn(Optional.of(ws));
        when(repo.findAll()).thenReturn(List.of(ws));

        assertDoesNotThrow(() -> service.rename(1L, "HOME"));
    }

    @Test void rename_toExistingOtherWorkspaceName_throws() {
        Workspace ws1 = workspace(1L, "Home");
        Workspace ws2 = workspace(2L, "Office");
        when(repo.findById(1L)).thenReturn(Optional.of(ws1));
        when(repo.findAll()).thenReturn(List.of(ws1, ws2));

        assertThrows(IllegalArgumentException.class, () -> service.rename(1L, "Office"));
    }

    @Test void rename_nonExistentWorkspace_throws() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.rename(99L, "Anything"));
    }

    @Test void rename_blankName_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.rename(1L, ""));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Workspace workspace(Long id, String name) {
        return new Workspace(id, name, LocalDate.now());
    }
}
