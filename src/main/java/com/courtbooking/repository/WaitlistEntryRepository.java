package com.courtbooking.repository;

import com.courtbooking.model.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    boolean existsByTimeSlotIdAndUserId(Long timeSlotId, Long userId);

    // Everyone queueing for any of these slots, first in line first.
    List<WaitlistEntry> findByTimeSlotIdInOrderByIdAsc(Collection<Long> timeSlotIds);

    // How many people are ahead of (or are) this entry: its place in line.
    long countByTimeSlotIdAndIdLessThanEqual(Long timeSlotId, Long id);

    // Just the slot id, and only if the entry belongs to this user, without
    // loading the entry itself (so nothing stale is cached before the locks).
    @Query("select w.timeSlot.id from WaitlistEntry w where w.id = :id and w.user.id = :userId")
    Optional<Long> findSlotIdByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // Returns how many rows were deleted: 0 means it was already gone.
    @Modifying
    @Query("delete from WaitlistEntry w where w.id = :id")
    int deleteEntry(@Param("id") Long id);

    @Query("select w from WaitlistEntry w "
            + "join fetch w.timeSlot t join fetch t.court c join fetch c.venue "
            + "where w.user.id = :userId order by t.startTime")
    List<WaitlistEntry> findByUserWithDetails(@Param("userId") Long userId);
}
