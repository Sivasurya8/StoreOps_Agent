package com.kiranapilot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bill_drafts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillDraft {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "draft_json", nullable = false, columnDefinition = "TEXT")
    private String draftJson;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
