package com.example.Internship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoteRequest {

    @NotBlank(message = "Title cannot be blank")
    @Size(max = 100, message = "Title must be under 100 characters")
    private String title;

    @NotBlank(message = "Content cannot be blank")
    private String content;
}
