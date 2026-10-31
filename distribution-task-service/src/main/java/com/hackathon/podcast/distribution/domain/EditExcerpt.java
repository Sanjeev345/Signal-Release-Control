package com.hackathon.podcast.distribution.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "edit_excerpt")
@Getter
@Setter
@NoArgsConstructor
public class EditExcerpt {

    @EmbeddedId
    private EditExcerptId id;

    public static EditExcerpt link(java.util.UUID editId, java.util.UUID excerptId) {
        EditExcerpt link = new EditExcerpt();
        link.id = new EditExcerptId(editId, excerptId);
        return link;
    }
}
