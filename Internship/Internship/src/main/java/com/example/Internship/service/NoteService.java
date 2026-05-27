package com.example.Internship.service;


import com.example.Internship.dto.NoteRequest;
import com.example.Internship.model.Note;
import com.example.Internship.repository.NoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class NoteService {

    private static final Logger logger = LoggerFactory.getLogger(NoteService.class);
    private static final String NOTES_COUNT_KEY = "notes:total_count";

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;


    public Note createNote(NoteRequest request) {
        logger.info("Creating new note with title: {}", request.getTitle());

        Note note = Note.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .build();
        Note saved = noteRepository.save(note);

        redisTemplate.delete(NOTES_COUNT_KEY);

        logger.info("Note created with ID: {}", saved.getId());
        return saved;
    }


    @Cacheable(value = "notes", key = "'all'")
    public List<Note> getAllNotes() {
        logger.info("Fetching all notes from DATABASE (not cache)");
        return noteRepository.findAll();
    }

    @Cacheable(value = "notes", key = "#id")
    public Optional<Note> getNoteById(Long id) {
        logger.info("Fetching note ID: {} from DATABASE", id);
        return noteRepository.findById(id);
    }

    @CachePut(value = "notes", key = "#id")
    @CacheEvict(value = "notes", key = "'all'")
    public Optional<Note> updateNote(Long id, NoteRequest request) {
        logger.info("Updating note ID: {}", id);

        return noteRepository.findById(id).map(existing -> {
            existing.setTitle(request.getTitle());
            existing.setContent(request.getContent());
            Note updated = noteRepository.save(existing);
            logger.info("Note ID: {} updated successfully", id);
            return updated;
        });
    }

    @CacheEvict(value = "notes", allEntries = true)
    public boolean deleteNote(Long id) {
        logger.info("Deleting note ID: {}", id);

        if (noteRepository.existsById(id)) {
            noteRepository.deleteById(id);
            redisTemplate.delete(NOTES_COUNT_KEY);
            logger.info("Note ID: {} deleted", id);
            return true;
        }
        return false;
    }


    public List<Note> searchNotes(String keyword) {
        logger.info("Searching notes with keyword: {}", keyword);
        return noteRepository.findByTitleContainingIgnoreCase(keyword);
    }


    public long getTotalNotesCount() {
        Object cached = redisTemplate.opsForValue().get(NOTES_COUNT_KEY);
        if (cached != null) {
            logger.info("Notes count served from Redis cache");
            return Long.parseLong(cached.toString());
        }

        long count = noteRepository.countAllNotes();
        redisTemplate.opsForValue().set(NOTES_COUNT_KEY, count);
        logger.info("Notes count fetched from DB and cached in Redis: {}", count);
        return count;
    }
}
