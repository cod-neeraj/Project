package com.example.Internship.repository;

import com.example.Internship.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByTitleContainingIgnoreCase(String keyword);

    @Query("SELECT COUNT(n) FROM Note n")
    long countAllNotes();
}
