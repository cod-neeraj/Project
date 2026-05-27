package com.example.Internship.controller;

import com.example.Internship.dto.ApiResponse;
import com.example.Internship.dto.NoteRequest;
import com.example.Internship.model.Note;
import com.example.Internship.service.NoteService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/notes")
@CrossOrigin(origins = "*")  // Allow all origins (restrict in production)
public class NoteController {

    private static final Logger logger = LoggerFactory.getLogger(NoteController.class);

    @Autowired
    private NoteService noteService;

    // ─── CREATE NOTE ──────────────────────────────────────────────────────────
    // POST /api/notes
    @PostMapping
    public ResponseEntity<ApiResponse<Note>> createNote(@Valid @RequestBody NoteRequest request) {
        logger.info("POST /api/notes - Creating note: {}", request.getTitle());
        Note note = noteService.createNote(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Note created successfully", note));
    }

    // ─── GET ALL NOTES ────────────────────────────────────────────────────────
    // GET /api/notes
    @GetMapping
    public ResponseEntity<ApiResponse<List<Note>>> getAllNotes() {
        logger.info("GET /api/notes - Fetching all notes");
        List<Note> notes = noteService.getAllNotes();
        return ResponseEntity.ok(ApiResponse.success("Notes fetched successfully", notes));
    }

    // ─── GET NOTE BY ID ───────────────────────────────────────────────────────
    // GET /api/notes/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Note>> getNoteById(@PathVariable Long id) {
        logger.info("GET /api/notes/{} - Fetching note by ID", id);
        Optional<Note> note = noteService.getNoteById(id);

        return note.map(n -> ResponseEntity.ok(ApiResponse.success(n)))
                   .orElseGet(() -> ResponseEntity
                           .status(HttpStatus.NOT_FOUND)
                           .body(ApiResponse.error("Note not found with ID: " + id)));
    }

    // ─── UPDATE NOTE ──────────────────────────────────────────────────────────
    // PUT /api/notes/{id}
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Note>> updateNote(
            @PathVariable Long id,
            @Valid @RequestBody NoteRequest request) {

        logger.info("PUT /api/notes/{} - Updating note", id);
        Optional<Note> updated = noteService.updateNote(id, request);

        return updated.map(n -> ResponseEntity.ok(ApiResponse.success("Note updated successfully", n)))
                      .orElseGet(() -> ResponseEntity
                              .status(HttpStatus.NOT_FOUND)
                              .body(ApiResponse.error("Note not found with ID: " + id)));
    }

    // ─── DELETE NOTE ──────────────────────────────────────────────────────────
    // DELETE /api/notes/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNote(@PathVariable Long id) {
        logger.info("DELETE /api/notes/{} - Deleting note", id);
        boolean deleted = noteService.deleteNote(id);

        if (deleted) {
            return ResponseEntity.ok(ApiResponse.success("Note deleted successfully", null));
        }
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Note not found with ID: " + id));
    }

    // ─── SEARCH NOTES ─────────────────────────────────────────────────────────
    // GET /api/notes/search?keyword=java
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Note>>> searchNotes(@RequestParam String keyword) {
        logger.info("GET /api/notes/search?keyword={}", keyword);
        List<Note> results = noteService.searchNotes(keyword);
        return ResponseEntity.ok(ApiResponse.success("Search results", results));
    }

    // ─── STATS ────────────────────────────────────────────────────────────────
    // GET /api/notes/stats
    // This endpoint shows Redis working — count is cached
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getStats() {
        logger.info("GET /api/notes/stats");
        long count = noteService.getTotalNotesCount();

        var stats = new java.util.HashMap<String, Object>();
        stats.put("totalNotes", count);
        stats.put("cacheInfo", "Count is cached in Redis");

        return ResponseEntity.ok(ApiResponse.success("Stats fetched", stats));
    }
}
