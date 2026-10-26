package com.courtbooking.service;

import com.courtbooking.exception.BadRequestException;
import com.courtbooking.exception.ResourceNotFoundException;
import com.courtbooking.exception.SlotNotAvailableException;
import com.courtbooking.exception.WaitlistException;
import com.courtbooking.model.AppUser;
import com.courtbooking.model.Booking;
import com.courtbooking.model.BookingSeries;
import com.courtbooking.model.Court;
import com.courtbooking.model.SlotStatus;
import com.courtbooking.model.TimeSlot;
import com.courtbooking.model.WaitlistEntry;
import com.courtbooking.repository.AppUserRepository;
import com.courtbooking.repository.BookingRepository;
import com.courtbooking.repository.BookingSeriesRepository;
import com.courtbooking.repository.CourtRepository;
import com.courtbooking.repository.TimeSlotRepository;
import com.courtbooking.repository.WaitlistEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles booking and cancellation.
 * <p>
 * The point of this service is that two people can never end up with the same
 * physical space at the same time. It defends against that in layers:
 * <p>
 * 1. Pessimistic locks - first the hall's top-level court row (the full
 *    hall, or the court itself if it has no parent), then every slot that
 *    could clash (the slot itself plus every slot on an overlapping space
 *    whose time overlaps it) together, in id order, so a concurrent request
 *    has to wait for the first one to finish. The court lock is what covers
 *    slots that don't exist yet: slot generation takes it too, so a request
 *    never works out which slots to lock while new ones are being added.
 *    Always court first, then slots, so nothing can deadlock.
 * 2. Optimistic version check (@Version on TimeSlot) - a backstop if a slot is
 *    ever changed outside this locked path.
 * 3. A unique constraint on booking.time_slot_id - the database itself refuses
 *    two bookings for the same slot.
 * <p>
 * Overlapping spaces: a court can have a parent (Half A and Half B point at the
 * full hall). Booking the full hall marks both halves UNAVAILABLE while it
 * runs, and booking a half marks the full hall UNAVAILABLE. Cancelling frees
 * them again, unless another booking still covers them. An admin-BLOCKED slot
 * (e.g. maintenance) covers its overlapping spaces in the same way.
 * <p>
 * Slots clash when their times overlap (each starts before the other ends),
 * not only when they match exactly: the full hall and its halves can have
 * different timetables (say 60-minute slots on the hall, 90-minute ones on a
 * half), so a full-hall 09:00-10:00 and a Half A 09:30-11:00 share the floor
 * from 09:30 to 10:00. Back-to-back slots (09:00-10:00 and 10:00-11:00) don't
 * clash. Because of this a change can ripple one step further than the slots
 * it touches: cancelling the full hall 09:00-10:00 can hand Half A 09:30-11:00
 * to someone waiting, which in turn covers the full hall 10:00-11:00. So the
 * cancel/block/unblock paths lock the slots overlapping the slot and the slots
 * overlapping those, all in one id-ordered lock.
 * <p>
 * Blocking: an admin can't block a slot while it, or a space overlapping it,
 * is booked; the booking would be left pointing at a closed space. Unblocking
 * frees the slot and gives it to the first person on its waitlist.
 * <p>
 * Time: a slot that has already started can't be booked, cancelled,
 * waitlisted or handed out from its waitlist. Cancelling after the session
 * would otherwise free a slot nobody can use, and give it to the next person
 * in line after the fact.
 * <p>
 * Waitlist: users can queue for a taken slot. When a cancel frees a slot, the
 * first person in line is booked into it automatically, inside the cancel's
 * transaction while the slots are still locked, so nobody can grab it in
 * between. Joining and leaving a waitlist take the same locks, so they can't
 * race with a cancel either.
 * <p>
 * Weekly series: a user can book the same slot for several weeks in one go.
 * It's all or nothing: every week is locked and checked first, and if any
 * isn't free, nothing is booked. That's safe to do in one transaction because
 * every path here locks the hall's court row first, so nothing else can
 * change the hall while the series is being checked and booked.
 */
@Service
public class BookingService {

    private final TimeSlotRepository timeSlotRepository;
    private final BookingRepository bookingRepository;
    private final AppUserRepository appUserRepository;
    private final CourtRepository courtRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final BookingSeriesRepository bookingSeriesRepository;

    // A term's worth of weekly sessions
    public static final int MAX_SERIES_WEEKS = 12;

    private static final DateTimeFormatter WEEK_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK);

    public BookingService(TimeSlotRepository timeSlotRepository,
                          BookingRepository bookingRepository,
                          AppUserRepository appUserRepository,
                          CourtRepository courtRepository,
                          WaitlistEntryRepository waitlistEntryRepository,
                          BookingSeriesRepository bookingSeriesRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.bookingRepository = bookingRepository;
        this.appUserRepository = appUserRepository;
        this.courtRepository = courtRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.bookingSeriesRepository = bookingSeriesRepository;
    }

    @Transactional
    public Booking bookSlot(Long timeSlotId, Long userId) {
        List<TimeSlot> group = lockSlotGroup(timeSlotId);
        TimeSlot slot = findInGroup(group, timeSlotId);

        if (hasStarted(slot)) {
            throw new SlotNotAvailableException("This slot has already started.");
        }
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new SlotNotAvailableException(
                    "This slot is no longer available (status: " + slot.getStatus() + ").");
        }

        // Another slot in the group that is booked, or closed by an admin
        // (e.g. maintenance on one half), also rules this slot out.
        boolean overlapTaken = group.stream()
                .anyMatch(t -> !t.getId().equals(timeSlotId)
                        && (t.getStatus() == SlotStatus.BOOKED || t.getStatus() == SlotStatus.BLOCKED));
        if (overlapTaken) {
            throw new SlotNotAvailableException("Part of this space is already booked or closed at that time.");
        }

        return confirmBooking(group, slot, findUser(userId), null);
    }

    @Transactional
    public void cancelBooking(Long bookingId, Long requestingUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " not found"));

        if (!booking.getBookedBy().getId().equals(requestingUserId)) {
            throw new SlotNotAvailableException("You can only cancel your own bookings.");
        }

        if (!cancelIfNotStarted(booking)) {
            throw new SlotNotAvailableException("This booking has already started, so it can't be cancelled.");
        }
    }

    // What booking this slot every week would look like. Takes no locks and
    // books nothing: it's a snapshot, and a week shown as free can be taken
    // before the user books (bookSeries checks again under the locks).
    @Transactional(readOnly = true)
    public List<SeriesWeek> previewSeries(Long firstSlotId, int weeks) {
        if (weeks < 1 || weeks > MAX_SERIES_WEEKS) {
            throw new BadRequestException("A series can be 1 to " + MAX_SERIES_WEEKS + " weeks long.");
        }
        TimeSlot first = timeSlotRepository.findById(firstSlotId)
                .orElseThrow(() -> new ResourceNotFoundException("Time slot " + firstSlotId + " not found"));

        List<SeriesWeek> result = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            LocalDateTime start = first.getStartTime().plusWeeks(i);
            LocalDateTime end = first.getEndTime().plusWeeks(i);
            TimeSlot slot = timeSlotRepository
                    .findFirstByCourtIdAndStartTimeAndEndTime(first.getCourt().getId(), start, end)
                    .orElse(null);
            result.add(new SeriesWeek(i + 1, slot, start, end, weekStatus(slot)));
        }
        return result;
    }

    // Books these slots (the same court and time, on different weeks) as one
    // series. All or nothing: if any isn't free, nothing is booked and the
    // error names the weeks that aren't.
    @Transactional
    public List<Booking> bookSeries(List<Long> timeSlotIds, Long userId) {
        List<Long> ids = timeSlotIds.stream().distinct().toList();
        if (ids.isEmpty() || ids.size() > MAX_SERIES_WEEKS) {
            throw new BadRequestException("A series can be 1 to " + MAX_SERIES_WEEKS + " weeks long.");
        }

        List<TimeSlot> group = lockSeriesGroup(ids);
        List<TimeSlot> slots = ids.stream()
                .map(id -> findInGroup(group, id))
                .sorted(Comparator.comparing(TimeSlot::getStartTime))
                .toList();
        checkSameTimeEachWeek(slots);

        // Check every week before booking any, so a clash in week 5 doesn't
        // leave weeks 1-4 booked
        List<TimeSlot> notFree = slots.stream()
                .filter(s -> hasStarted(s) || !isFreeToBook(s))
                .toList();
        if (!notFree.isEmpty()) {
            throw new SlotNotAvailableException("Nothing was booked, because these weeks are no longer free: "
                    + notFree.stream().map(s -> s.getStartTime().format(WEEK_FORMAT))
                    .collect(Collectors.joining(", "))
                    + ". Check the weeks again and book the free ones.");
        }

        AppUser user = findUser(userId);
        BookingSeries series = bookingSeriesRepository.save(new BookingSeries(user));
        List<Booking> bookings = new ArrayList<>();
        for (TimeSlot slot : slots) {
            bookings.add(confirmBooking(group, slot, user, series));
        }
        return bookings;
    }

    // Cancels every week of the series that hasn't started yet; each freed slot
    // goes to its waitlist as usual. Returns how many weeks were cancelled.
    @Transactional
    public int cancelSeries(Long seriesId, Long requestingUserId) {
        BookingSeries series = bookingSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking series " + seriesId + " not found"));
        if (!series.getBookedBy().getId().equals(requestingUserId)) {
            throw new SlotNotAvailableException("You can only cancel your own bookings.");
        }

        int cancelled = 0;
        for (Booking booking : bookingRepository.findBySeriesIdOrderById(seriesId)) {
            if (cancelIfNotStarted(booking)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    // Cancels the booking and hands the slot to its waitlist, unless the slot
    // has already started (then it does nothing and returns false).
    private boolean cancelIfNotStarted(Booking booking) {
        Long slotId = booking.getTimeSlot().getId(); // just the id: the slot itself is read under the lock
        // The whole hall, not just this slot's group: a promotion can hand out
        // a different slot in the family (e.g. the full hall after a half is
        // cancelled), and every slot that changes needs to be locked and refreshed.
        List<TimeSlot> group = lockFamilyGroup(slotId);
        TimeSlot slot = findInGroup(group, slotId);

        if (hasStarted(slot)) {
            return false;
        }

        // Deleted rather than soft-marked CANCELLED, because the unique
        // constraint allows only one booking row per slot. flush() makes the
        // DELETE run now, before any later INSERT in the same transaction.
        bookingRepository.delete(booking);
        bookingRepository.flush();

        slot.setStatus(SlotStatus.AVAILABLE);
        refreshOverlaps(group);
        promoteFromWaitlist(group, slot);
        return true;
    }

    private SeriesWeek.Status weekStatus(TimeSlot slot) {
        if (slot == null) {
            return SeriesWeek.Status.NO_SLOT;
        }
        if (hasStarted(slot)) {
            return SeriesWeek.Status.STARTED;
        }
        if (slot.getStatus() == SlotStatus.BLOCKED) {
            return SeriesWeek.Status.CLOSED;
        }
        return isFreeToBook(slot) ? SeriesWeek.Status.FREE : SeriesWeek.Status.TAKEN;
    }

    // A series is one court at the same time of day, on the same weekday,
    // for the same length: slots must be whole weeks apart.
    private void checkSameTimeEachWeek(List<TimeSlot> slotsByStart) {
        TimeSlot first = slotsByStart.get(0);
        Duration length = Duration.between(first.getStartTime(), first.getEndTime());
        for (TimeSlot s : slotsByStart) {
            long days = ChronoUnit.DAYS.between(first.getStartTime(), s.getStartTime());
            boolean sameTime = s.getStartTime().equals(first.getStartTime().plusDays(days));
            if (days % 7 != 0 || !sameTime
                    || !Duration.between(s.getStartTime(), s.getEndTime()).equals(length)) {
                throw new BadRequestException("A series has to be the same time on the same day each week.");
            }
        }
    }

    // Org admin: close a slot, e.g. for maintenance.
    @Transactional
    public void blockSlot(Long timeSlotId) {
        List<TimeSlot> group = lockFamilyGroup(timeSlotId);
        TimeSlot slot = findInGroup(group, timeSlotId);

        if (slot.getStatus() == SlotStatus.BLOCKED) {
            return; // already blocked: nothing to do
        }
        if (slot.getStatus() == SlotStatus.BOOKED || overlapHasStatus(slot, SlotStatus.BOOKED)) {
            throw new SlotNotAvailableException(
                    "This space, or part of it, is booked at that time. Cancel the booking before blocking it.");
        }

        slot.setStatus(SlotStatus.BLOCKED);
        refreshOverlaps(group); // e.g. a blocked half makes the full hall UNAVAILABLE
    }

    // Org admin: reopen a blocked slot. Anyone waiting for it, or for a space it
    // was covering, gets it straight away.
    @Transactional
    public void unblockSlot(Long timeSlotId) {
        List<TimeSlot> group = lockFamilyGroup(timeSlotId);
        TimeSlot slot = findInGroup(group, timeSlotId);

        if (slot.getStatus() != SlotStatus.BLOCKED) {
            return; // not blocked: nothing to do
        }

        slot.setStatus(SlotStatus.AVAILABLE);
        refreshOverlaps(group); // works out AVAILABLE vs UNAVAILABLE for the whole hall
        promoteFromWaitlist(group, slot);
    }

    @Transactional
    public WaitlistEntry joinWaitlist(Long timeSlotId, Long userId) {
        // Locked like a cancel, so a cancel can't free the slot halfway through
        // this check and leave the user queueing for a slot that's now free.
        List<TimeSlot> group = lockFamilyGroup(timeSlotId);
        TimeSlot slot = findInGroup(group, timeSlotId);

        if (hasStarted(slot)) {
            throw new WaitlistException("This slot has already started.");
        }
        if (slot.getStatus() == SlotStatus.BLOCKED) {
            throw new WaitlistException("This slot is closed, so there is no waitlist for it.");
        }
        if (slot.getStatus() == SlotStatus.AVAILABLE) {
            throw new WaitlistException("This slot isn't booked, so there's no need to wait for it.");
        }
        boolean ownBooking = bookingRepository.findByTimeSlotId(timeSlotId)
                .map(b -> b.getBookedBy().getId().equals(userId))
                .orElse(false);
        if (ownBooking) {
            throw new WaitlistException("You have already booked this slot.");
        }
        if (waitlistEntryRepository.existsByTimeSlotIdAndUserId(timeSlotId, userId)) {
            throw new WaitlistException("You are already on the waitlist for this slot.");
        }

        return waitlistEntryRepository.save(new WaitlistEntry(slot, findUser(userId)));
    }

    @Transactional
    public void leaveWaitlist(Long entryId, Long userId) {
        Long slotId = waitlistEntryRepository.findSlotIdByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Waitlist place " + entryId + " not found"));

        // Locked so this can't run halfway through a cancel that is promoting
        // this very entry. Once we hold the lock, the entry may already be gone
        // (promoted to a booking), which the delete count tells us.
        lockFamilyGroup(slotId);
        if (waitlistEntryRepository.deleteEntry(entryId) == 0) {
            throw new ResourceNotFoundException(
                    "Waitlist place " + entryId + " not found. You may have just been given the slot.");
        }
    }

    // The user's waitlist places, earliest slot first.
    @Transactional(readOnly = true)
    public List<WaitlistEntry> getWaitlistForUser(Long userId) {
        return waitlistEntryRepository.findByUserWithDetails(userId);
    }

    // 1 means next in line.
    @Transactional(readOnly = true)
    public long positionOf(WaitlistEntry entry) {
        return waitlistEntryRepository.countByTimeSlotIdAndIdLessThanEqual(
                entry.getTimeSlot().getId(), entry.getId());
    }

    // The user's bookings, earliest first.
    @Transactional(readOnly = true)
    public List<Booking> getBookingsForUser(Long userId) {
        return bookingRepository.findByUserWithDetails(userId);
    }

    // Marks the slot BOOKED and saves the booking (series is null for a
    // one-off). The caller must already hold the group's locks and have
    // checked the slot is free.
    private Booking confirmBooking(List<TimeSlot> group, TimeSlot slot, AppUser user, BookingSeries series) {
        slot.setStatus(SlotStatus.BOOKED);
        timeSlotRepository.save(slot); // optimistic @Version check happens on commit
        refreshOverlaps(group);

        return bookingRepository.save(new Booking(slot, user, series)); // unique constraint is the final backstop
    }

    // Gives the freed slot, and any slot it was covering, to the people waiting
    // for them, first in line first. Called from cancelBooking and unblockSlot
    // while the family group is still locked. Only those slots can have just
    // become free, and the group holds every slot that overlaps them, so each
    // promotion's own overlaps are locked too.
    private void promoteFromWaitlist(List<TimeSlot> group, TimeSlot freed) {
        List<Long> slotIds = group.stream()
                .filter(t -> t.getId().equals(freed.getId()) || sharesSpace(t, freed))
                .map(TimeSlot::getId)
                .toList();

        for (WaitlistEntry entry : waitlistEntryRepository.findByTimeSlotIdInOrderByIdAsc(slotIds)) {
            TimeSlot slot = findInGroup(group, entry.getTimeSlot().getId());
            // Checked again for every entry: promoting an earlier one (e.g. to
            // the full hall) may have made this slot UNAVAILABLE. A slot that
            // has started (e.g. an admin reopening one mid-session) isn't
            // handed out.
            if (!hasStarted(slot) && isFreeToBook(slot)) {
                waitlistEntryRepository.delete(entry);
                confirmBooking(group, slot, entry.getUser(), null);
            }
        }
    }

    // Free means AVAILABLE and no overlapping space is booked or closed at any
    // point during the slot. Only *overlapping* spaces count: Half B being booked doesn't stop
    // Half A, so we can't just look at every other slot in the family group.
    private boolean isFreeToBook(TimeSlot slot) {
        return slot.getStatus() == SlotStatus.AVAILABLE
                && !overlapHasStatus(slot, SlotStatus.BOOKED)
                && !overlapHasStatus(slot, SlotStatus.BLOCKED);
    }

    private boolean hasStarted(TimeSlot slot) {
        return !slot.getStartTime().isAfter(LocalDateTime.now());
    }

    // Whether any space overlapping this slot has a slot with this status whose
    // time overlaps it.
    private boolean overlapHasStatus(TimeSlot slot, SlotStatus status) {
        List<Long> related = relatedCourtIds(slot.getCourt().getId());
        return !related.isEmpty()
                && timeSlotRepository.existsOverlapping(
                related, slot.getStartTime(), slot.getEndTime(), status);
    }

    // Whether two slots are on overlapping spaces (one court is the other's
    // parent) and their times overlap.
    private boolean sharesSpace(TimeSlot a, TimeSlot b) {
        Court ca = a.getCourt();
        Court cb = b.getCourt();
        boolean relatedCourts = (ca.getParent() != null && ca.getParent().getId().equals(cb.getId()))
                || (cb.getParent() != null && cb.getParent().getId().equals(ca.getId()));
        return relatedCourts
                && a.getStartTime().isBefore(b.getEndTime())
                && a.getEndTime().isAfter(b.getStartTime());
    }

    private AppUser findUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found"));
    }

    // Locks the hall, then the slot plus every slot on an overlapping court whose time overlaps it.
    private List<TimeSlot> lockSlotGroup(Long timeSlotId) {
        TimeSlotRepository.SlotWindow window = timeSlotRepository.findWindowById(timeSlotId)
                .orElseThrow(() -> new ResourceNotFoundException("Time slot " + timeSlotId + " not found"));
        lockHall(window.getCourtId());

        Set<Long> ids = new HashSet<>();
        ids.add(timeSlotId);

        List<Long> relatedCourtIds = relatedCourtIds(window.getCourtId());
        if (!relatedCourtIds.isEmpty()) {
            ids.addAll(timeSlotRepository.findIdsByCourtsOverlapping(
                    relatedCourtIds, window.getStartTime(), window.getEndTime()));
        }
        return timeSlotRepository.findAllByIdForUpdate(ids);
    }

    // lockSlotGroup for several slots at once: the hall, then every slot and
    // everything overlapping any of them, in one id-ordered lock. The slots
    // must all be on the same court (so there's only one hall to lock).
    private List<TimeSlot> lockSeriesGroup(List<Long> timeSlotIds) {
        List<TimeSlotRepository.SlotWindow> windows = timeSlotIds.stream()
                .map(id -> timeSlotRepository.findWindowById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Time slot " + id + " not found")))
                .toList();
        Long courtId = windows.get(0).getCourtId();
        if (windows.stream().anyMatch(w -> !w.getCourtId().equals(courtId))) {
            throw new BadRequestException("All the weeks of a series have to be on the same court.");
        }
        lockHall(courtId);

        Set<Long> ids = new HashSet<>(timeSlotIds);
        List<Long> relatedCourtIds = relatedCourtIds(courtId);
        if (!relatedCourtIds.isEmpty()) {
            for (TimeSlotRepository.SlotWindow w : windows) {
                ids.addAll(timeSlotRepository.findIdsByCourtsOverlapping(
                        relatedCourtIds, w.getStartTime(), w.getEndTime()));
            }
        }
        return timeSlotRepository.findAllByIdForUpdate(ids);
    }

    // Locks the slots across the whole hall (the top-level court and all of its
    // sub-courts, e.g. the full hall and both halves) that overlap this slot,
    // and also the ones overlapping *those*: a promotion can book a slot that
    // overlaps this one, and every slot that booking covers must be locked
    // and refreshed too. To get them, the window is widened to cover every
    // family slot overlapping this one, and everything in the family
    // overlapping that wider window is locked (a few more than strictly
    // needed, which is harmless). Courts are only ever one level deep, so the
    // family is the parent plus its children. Same id ordering as
    // lockSlotGroup, so the two can't deadlock each other.
    private List<TimeSlot> lockFamilyGroup(Long timeSlotId) {
        TimeSlotRepository.SlotWindow window = timeSlotRepository.findWindowById(timeSlotId)
                .orElseThrow(() -> new ResourceNotFoundException("Time slot " + timeSlotId + " not found"));

        Court top = lockHall(window.getCourtId());

        List<Long> familyCourtIds = new ArrayList<>();
        familyCourtIds.add(top.getId());
        courtRepository.findByParentId(top.getId()).forEach(c -> familyCourtIds.add(c.getId()));

        // The slot itself is in the family, so the span is only ever empty for
        // a zero-length slot; fall back to the slot's own window then.
        TimeSlotRepository.TimeSpan span = timeSlotRepository.findSpanOfOverlapping(
                familyCourtIds, window.getStartTime(), window.getEndTime());
        boolean hasSpan = span != null && span.getSpanStart() != null;

        Set<Long> ids = new HashSet<>(timeSlotRepository.findIdsByCourtsOverlapping(familyCourtIds,
                hasSpan ? span.getSpanStart() : window.getStartTime(),
                hasSpan ? span.getSpanEnd() : window.getEndTime()));
        ids.add(timeSlotId);
        return timeSlotRepository.findAllByIdForUpdate(ids);
    }

    // Locks the top-level court of this court's hall (its parent, or itself if
    // it has none) and returns it. Taken before any slot locks; see the class comment.
    private Court lockHall(Long courtId) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new ResourceNotFoundException("Court " + courtId + " not found"));
        Long topId = court.getParent() != null ? court.getParent().getId() : court.getId();
        return courtRepository.findByIdForUpdate(topId)
                .orElseThrow(() -> new ResourceNotFoundException("Court " + topId + " not found"));
    }

    // Re-works out UNAVAILABLE for every slot in the group. A slot is
    // UNAVAILABLE if a slot on an overlapping space whose time overlaps it is
    // BOOKED or BLOCKED.
    // BOOKED and admin-BLOCKED slots are left alone.
    private void refreshOverlaps(List<TimeSlot> group) {
        for (TimeSlot t : group) {
            if (t.getStatus() == SlotStatus.BOOKED || t.getStatus() == SlotStatus.BLOCKED) {
                continue;
            }
            boolean covered = overlapHasStatus(t, SlotStatus.BOOKED) || overlapHasStatus(t, SlotStatus.BLOCKED);
            t.setStatus(covered ? SlotStatus.UNAVAILABLE : SlotStatus.AVAILABLE);
        }
    }

    // The courts that share physical space with this one: its parent (the full
    // hall, if this is a half) and its children (the halves, if this is the full hall).
    private List<Long> relatedCourtIds(Long courtId) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new ResourceNotFoundException("Court " + courtId + " not found"));

        List<Long> ids = new ArrayList<>();
        if (court.getParent() != null) {
            ids.add(court.getParent().getId());
        }
        courtRepository.findByParentId(courtId).forEach(c -> ids.add(c.getId()));
        return ids;
    }

    private TimeSlot findInGroup(List<TimeSlot> group, Long id) {
        return group.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Time slot " + id + " not found"));
    }
}