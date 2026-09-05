package com.kiranapilot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "owner_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerPreference {

    @Id
    @Column(name = "pref_key", length = 128)
    private String key;

    @Column(name = "pref_value", nullable = false, columnDefinition = "TEXT")
    private String value;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
