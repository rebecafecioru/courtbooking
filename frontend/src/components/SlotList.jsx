import { useState, useEffect } from 'react'
import { formatDay, formatTime } from '../formatting'

// The backend wants dates like 2026-10-15T09:00:00 (local time, no timezone).
function toLocalIso(date) {
    const pad = n => String(n).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
        + `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function SlotList({ court, user, bookingsVersion, onBookingsChanged }) {
    // null means "still loading", an empty array means "loaded, but nothing free"
    const [slots, setSlots] = useState(null)
    // { text, version }: version is the bookingsVersion the message is about.
    // When bookings change somewhere else (e.g. a cancel in My Bookings), the
    // version moves on and the old message is no longer shown.
    const [message, setMessage] = useState(null)
    const [bookingSlotId, setBookingSlotId] = useState(null)
    // Adding 1 to this makes the effect below run again, which re-fetches the slots
    const [refreshCount, setRefreshCount] = useState(0)

    useEffect(() => {
        const from = new Date()
        const to = new Date()
        to.setDate(to.getDate() + 7)

        fetch(`http://localhost:8080/api/courts/${court.id}/slots?from=${toLocalIso(from)}&to=${toLocalIso(to)}`)
            .then(res => res.json())
            .then(data => {
                setSlots([...data].sort((a, b) => a.startTime.localeCompare(b.startTime)))
            })
    }, [court.id, refreshCount, bookingsVersion])

    function handleBook(slot) {
        const showMessage = text => setMessage({ text, version: bookingsVersion })
        setMessage(null)
        setBookingSlotId(slot.id)

        fetch('http://localhost:8080/api/bookings', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${user.token}`,
            },
            body: JSON.stringify({ timeSlotId: slot.id }),
        })
            .then(async res => {
                if (res.ok) {
                    // + 1 because onBookingsChanged() below moves the version on
                    setMessage({
                        text: `Booked ${court.name}: ${formatDay(slot.startTime)}, `
                            + `${formatTime(slot.startTime)} to ${formatTime(slot.endTime)}.`,
                        version: bookingsVersion + 1,
                    })
                    onBookingsChanged() // so My Bookings shows it
                    return
                }
                if (res.status === 401 || res.status === 403) {
                    showMessage('Your login has expired. Please log out and log in again.')
                    return
                }
                // e.g. 409 when someone else got there first: the backend explains why
                const body = await res.json().catch(() => ({}))
                showMessage(body.message || 'Could not book this slot.')
            })
            .catch(() => showMessage('Could not reach the server.'))
            .finally(() => {
                setBookingSlotId(null)
                setRefreshCount(count => count + 1) // reload, so the list is up to date
            })
    }

    if (slots === null) {
        return <p>Loading slots...</p>
    }

    return (
        <div>
            <h5>Available slots: {court.name}</h5>
            {!user && <p>Log in to book a slot.</p>}
            {message?.version === bookingsVersion && <p>{message.text}</p>}
            {slots.length === 0 ? (
                <p>No available slots in the next 7 days.</p>
            ) : (
                <ul>
                    {slots.map(slot => (
                        <li key={slot.id}>
                            {formatDay(slot.startTime)}, {formatTime(slot.startTime)} to {formatTime(slot.endTime)}{' '}
                            {user && (
                                <button
                                    onClick={() => handleBook(slot)}
                                    disabled={bookingSlotId !== null}
                                >
                                    {bookingSlotId === slot.id ? 'Booking...' : 'Book'}
                                </button>
                            )}
                        </li>
                    ))}
                </ul>
            )}
        </div>
    )
}

export default SlotList