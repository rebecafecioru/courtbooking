import { useState, useEffect } from 'react'
import { formatDay, formatTime } from '../formatting'

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
    // Adding 1 to this makes the effect below run again, which re-fetches the slots
    const [refreshCount, setRefreshCount] = useState(0)

    useEffect(() => {
        const from = new Date()
        const to = new Date()
        to.setDate(to.getDate() + 7)

        // availableOnly=false: taken slots are shown too, so you can join their waitlist
        fetch(`http://localhost:8080/api/courts/${court.id}/slots`
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
        sendSlotRequest(slot, 'http://localhost:8080/api/bookings',
            () => `Booked ${describe(slot)}.`,
            'Could not book this slot.')
    }

    function handleJoinWaitlist(slot) {
        sendSlotRequest(slot, 'http://localhost:8080/api/waitlist',
            entry => `You're number ${entry.position} on the waitlist for ${describe(slot)}. `
                + `If it frees up, it will be booked for you automatically.`,
            'Could not join the waitlist.')
    }

    function renderAction(slot) {
        if (slot.status === 'BLOCKED') {
            return <span>Closed</span>
        }
        if (slot.status === 'AVAILABLE') {
            return user && (
                <button onClick={() => handleBook(slot)} disabled={busySlotId !== null}>
                    {busySlotId === slot.id ? 'Booking...' : 'Book'}
                </button>
            )
        }
        // BOOKED, or UNAVAILABLE because an overlapping space is booked
        return (
            <>
                <span>Taken</span>{' '}
                {user && (
                    <button onClick={() => handleJoinWaitlist(slot)} disabled={busySlotId !== null}>
                        {busySlotId === slot.id ? 'Joining...' : 'Join waitlist'}
                    </button>
                )}
            </>
        )
    }

    if (slots === null) {
        return <p>Loading slots...</p>
    }

    return (
        <div>
            <h5>Slots: {court.name}</h5>
            {!user && <p>Log in to book a slot or join a waitlist.</p>}
            {message?.version === bookingsVersion && <p>{message.text}</p>}
            {slots.length === 0 ? (
                <p>No slots in the next 7 days.</p>
            ) : (
                <ul>
                    {slots.map(slot => (
                        <li key={slot.id}>
                            {formatDay(slot.startTime)}, {formatTime(slot.startTime)} to {formatTime(slot.endTime)}{' '}
                            {renderAction(slot)}
                        </li>
                    ))}
                </ul>
            )}
        </div>
    )
}

export default SlotList
