import { useState, useEffect } from 'react'
import { formatDay, formatTime } from '../formatting'
import SeriesPanel from './SeriesPanel'
import { API_URL } from '../api'

// The backend wants dates like 2026-10-15T09:00:00 (local time, no timezone).
function toLocalIso(date) {
    const pad = n => String(n).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
        + `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function SlotList({ court, user, bookingsVersion, onBookingsChanged }) {
    // null means "still loading", an empty array means "loaded, but no slots"
    const [slots, setSlots] = useState(null)
    // { text, version }: version is the bookingsVersion the message is about.
    // When bookings change somewhere else (e.g. a cancel in My Bookings), the
    // version moves on and the old message is no longer shown.
    const [message, setMessage] = useState(null)
    // The slot a request is in flight for, so its button can say so
    const [busySlotId, setBusySlotId] = useState(null)
    // The slot whose "book every week" panel is open, if any
    const [seriesSlotId, setSeriesSlotId] = useState(null)
    // Adding 1 to this makes the effect below run again, which re-fetches the slots
    const [refreshCount, setRefreshCount] = useState(0)

    useEffect(() => {
        const from = new Date()
        const to = new Date()
        to.setDate(to.getDate() + 7)

        // availableOnly=false: taken slots are shown too, so you can join their waitlist
        fetch(`${API_URL}/api/courts/${court.id}/slots`
            + `?from=${toLocalIso(from)}&to=${toLocalIso(to)}&availableOnly=false`)
            .then(res => res.json())
            .then(data => {
                setSlots([...data].sort((a, b) => a.startTime.localeCompare(b.startTime)))
            })
    }, [court.id, refreshCount, bookingsVersion])

    // Booking and joining a waitlist work the same way: POST the slot id, then
    // show what happened. successText gets the response body.
    function sendSlotRequest(slot, url, successText, fallbackError) {
        const showMessage = text => setMessage({ text, version: bookingsVersion })
        setMessage(null)
        setBusySlotId(slot.id)

        fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${user.token}`,
            },
            body: JSON.stringify({ timeSlotId: slot.id }),
        })
            .then(async res => {
                if (res.ok) {
                    const body = await res.json()
                    // + 1 because onBookingsChanged() below moves the version on
                    setMessage({ text: successText(body), version: bookingsVersion + 1 })
                    onBookingsChanged() // so My Bookings / My Waitlist show it
                    return
                }
                if (res.status === 401 || res.status === 403) {
                    showMessage('Your login has expired. Please log out and log in again.')
                    return
                }
                // e.g. 409 when someone else got there first: the backend explains why
                const body = await res.json().catch(() => ({}))
                showMessage(body.message || fallbackError)
            })
            .catch(() => showMessage('Could not reach the server.'))
            .finally(() => {
                setBusySlotId(null)
                setRefreshCount(count => count + 1) // reload, so the list is up to date
            })
    }

    function describe(slot) {
        return `${court.name}: ${formatDay(slot.startTime)}, `
            + `${formatTime(slot.startTime)} to ${formatTime(slot.endTime)}`
    }

    function handleBook(slot) {
        sendSlotRequest(slot, `${API_URL}/api/bookings`,
            () => `Booked ${describe(slot)}.`,
            'Could not book this slot.')
    }

    function handleJoinWaitlist(slot) {
        sendSlotRequest(slot, `${API_URL}/api/waitlist`,
            entry => `You're number ${entry.position} on the waitlist for ${describe(slot)}. `
                + `If it frees up, it will be booked for you automatically.`,
            'Could not join the waitlist.')
    }

    function renderStatus(slot) {
        if (slot.status === 'BLOCKED') {
            return <span className="badge badge-closed">Closed</span>
        }
        if (slot.status === 'AVAILABLE') {
            return <span className="badge badge-free">Free</span>
        }
        // BOOKED, or UNAVAILABLE because an overlapping space is booked
        return <span className="badge badge-taken">Taken</span>
    }

    function renderActions(slot) {
        if (!user || slot.status === 'BLOCKED') {
            return null
        }
        if (slot.status === 'AVAILABLE') {
            return (
                <>
                    <button
                        className="btn btn-primary"
                        onClick={() => handleBook(slot)}
                        disabled={busySlotId !== null}
                    >
                        {busySlotId === slot.id ? 'Booking...' : 'Book'}
                    </button>
                    <button
                        className="btn"
                        onClick={() => setSeriesSlotId(seriesSlotId === slot.id ? null : slot.id)}
                        aria-expanded={seriesSlotId === slot.id}
                    >
                        Book weekly
                    </button>
                </>
            )
        }
        return (
            <button className="btn" onClick={() => handleJoinWaitlist(slot)} disabled={busySlotId !== null}>
                {busySlotId === slot.id ? 'Joining...' : 'Join waitlist'}
            </button>
        )
    }

    if (slots === null) {
        return <p className="muted">Loading slots...</p>
    }

    // Slots arrive sorted by start time, so each day's slots are next to each other
    const days = []
    for (const slot of slots) {
        const day = formatDay(slot.startTime)
        if (days.length === 0 || days[days.length - 1].day !== day) {
            days.push({ day, slots: [] })
        }
        days[days.length - 1].slots.push(slot)
    }

    return (
        <div className="step">
            <span className="step-label">Slots: {court.name}</span>
            {!user && <p className="notice">Log in to book a slot or join a waitlist.</p>}
            {message?.version === bookingsVersion && <p className="notice">{message.text}</p>}
            {slots.length === 0 ? (
                <p className="empty">No slots in the next 7 days.</p>
            ) : (
                days.map(({ day, slots: daySlots }) => (
                    <div key={day} className="form-stack">
                        <h3>{day}</h3>
                        <ul className="list">
                            {daySlots.map(slot => (
                                <li className="row" key={slot.id}>
                                    <div className="row-main">
                                        <span className="row-title">
                                            {formatTime(slot.startTime)}–{formatTime(slot.endTime)}
                                        </span>
                                    </div>
                                    <div className="row-actions">
                                        {renderStatus(slot)}
                                        {renderActions(slot)}
                                    </div>
                                    {user && seriesSlotId === slot.id && (
                                        <div className="row-expanded">
                                            <SeriesPanel
                                                slot={slot}
                                                user={user}
                                                bookingsVersion={bookingsVersion}
                                                onBookingsChanged={onBookingsChanged}
                                                onClose={() => setSeriesSlotId(null)}
                                            />
                                        </div>
                                    )}
                                </li>
                            ))}
                        </ul>
                    </div>
                ))
            )}
        </div>
    )
}

export default SlotList
