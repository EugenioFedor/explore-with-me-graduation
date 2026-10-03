package ru.practicum.ewm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.model.Request;
import ru.practicum.ewm.model.RequestStatus;

import java.util.Collection;
import java.util.List;

public interface RequestRepository extends JpaRepository<Request, Long> {

    List<Request> findByRequesterId(Long requesterId);

    List<Request> findByEventId(Long eventId);

    List<Request> findByEventIdAndIdIn(Long eventId, List<Long> ids);

    List<Request> findByEventIdAndStatus(Long eventId, RequestStatus status);

    boolean existsByEventIdAndRequesterId(Long eventId, Long requesterId);

    long countByEventIdAndStatus(Long eventId, RequestStatus status);

    @Query("""
        select r.eventId, count(r.id)
        from Request r
        where r.eventId in :eventIds
          and r.status = :status
        group by r.eventId
        """)
    List<Object[]> countByEventIdsAndStatus(@Param("eventIds") Collection<Long> eventIds,
                                             @Param("status") RequestStatus status);
}