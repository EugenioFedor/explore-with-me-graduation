package ru.practicum.ewm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.model.Comment;
import ru.practicum.ewm.model.CommentStatus;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByEventIdAndStatus(Long eventId, CommentStatus status, Pageable pageable);

    Page<Comment> findByAuthorId(Long authorId, Pageable pageable);

    Optional<Comment> findByIdAndEventId(Long id, Long eventId);

    Optional<Comment> findByIdAndAuthorId(Long id, Long authorId);

    Page<Comment> findByStatus(CommentStatus status, Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE (:status IS NULL OR c.status = :status)")
    Page<Comment> findAllByStatus(@Param("status") CommentStatus status, Pageable pageable);

    long countByEventIdAndStatus(Long eventId, CommentStatus status);

    @Query("SELECT c.event.id, COUNT(c.id) FROM Comment c " +
            "WHERE c.event.id IN :eventIds AND c.status = :status " +
            "GROUP BY c.event.id")
    Map<Long, Long> countByEventIdsAndStatus(@Param("eventIds") Collection<Long> eventIds,
                                             @Param("status") CommentStatus status);
}