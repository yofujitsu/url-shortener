package ru.yofujitsu.url_shortener.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Entity
@Table(name = "links", indexes = {
        @Index(columnList = "code"),
        @Index(columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID userId;
    @Column(nullable = false, unique = true)
    private String code;
    @Column(nullable = false)
    private String originalUrl;
    private Integer maxClicks; // 0 = unlimited
    @Builder.Default
    private AtomicInteger clicks = new AtomicInteger(0);
    @CreationTimestamp
    private Instant createdAt;
    private Instant expiresAt;
    @Builder.Default
    private boolean active = true;

}
