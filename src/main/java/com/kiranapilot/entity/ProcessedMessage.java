package com.kiranapilot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "processed_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedMessage {

    @Id
    @Column(name = "update_id")
    private Long updateId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @CreationTimestamp
    @Column(name = "processed_at", updatable = false)
    private OffsetDateTime processedAt;
}
