package ru.yofujitsu.url_shortener.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import ru.yofujitsu.url_shortener.model.entity.Link;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByCode(String code);
    boolean existsByCodeAndUserId(String code, UUID userId);
    List<Link> findByExpiresAtBefore(Instant time);
}
