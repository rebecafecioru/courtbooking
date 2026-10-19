import { useState, useEffect } from 'react'
import { formatDay, formatTime } from '../formatting'

// The backend wants dates like 2026-10-15T09:00:00 (local time, no timezone).
function toLocalIso(date) {
    const pad = n => String(n).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
        + `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const STATUS_LABELS = {
    AVAILABLE: 'Free',
    BOOKED: 'Booked',
    UNAVAILABLE: 'Covered by an overlapping booking or closure',
    BLOCKED: 'Closed',
}

function AdminSlotList({ court, user, bookingsVersion, onBookingsChanged }) {
    // null means "still loading"
    const [slots, setSlots] = useState(null)
    const [message, setMessage] = useState(null)
    const [busySlotId, setBusySlotId] = useState(null)

    useEffect(() => {
        const from = new Date()
        const to = new Date()
        to.setDate(to.getDate() + 14)

        fetch(`http://localhost:8080/api/courts/${court.id}/slots`
            + `?from=${toLocalIso(from)}&to=${toLocalIso(to)}&availableOnly=false`)
            .then(res => res.json())
            .then(data => {
                setSlots([...data].sort((a, b) => a.startTime.localeCompare(b.startTime)))
            })
    }, [court.id, bookingsVersion])

    // action is 'block' or 'unblock'
    function changeSlot(slot, action) {
        setMessage(null)
        setBusySlotId(slot.id)

        fetch(`http://localhost:8080/api/admin/slots/${slot.id}/${action}`, {
            method: 'PATCH',
            headers: { Authorization: `Bearer ${user.token}` },
        })
            .then(async res => {
                if (res.ok) {
                    return
                }
                if (res.status === 403) {
                    setMessage('You can only manage your own organisation\'s courts.')
                    return
                }
                // e.g. 409 when trying to close a booked slot: the backend explains why
                const body = await res.json().catch(() => ({}))
                setMessage(body.message || `Could not ${action} this slot.`)
            })
            .catch(() => setMessage('Could not reach the server.'))
            .finally(() => {
                setBusySlotId(null)
                // Unblocking can give the slot to someone on the waitlist, so
                // everything that shows bookings needs to reload, not just this list
                onBookingsChanged()
            })
    }

    if (slots === null) {
        return <p>Loading slots...</p>
    }

    return (
        <div>
            <h3>Next two weeks: {court.name}</h3>
            {message && <p>{message}</p>}
            {slots.length === 0 ? (
                <p>No slots in the next two weeks. Add some above.</p>
            ) : (
                <ul>
                    {slots.map(slot => (
                        <li key={slot.id}>
                            {formatDay(slot.startTime)}, {formatTime(slot.startTime)} to {formatTime(slot.endTime)}:{' '}
                            {STATUS_LABELS[slot.status] ?? slot.status}{' '}
                            {slot.status === 'BLOCKED' ? (
                                <button onClick={() => changeSlot(slot, 'unblock')} disabled={busySlotId !== null}>
                                    {busySlotId === slot.id ? 'Reopening...' : 'Reopen'}
                                </button>
                            ) : slot.status !== 'BOOKED' && (
                                // Booked slots can't be closed: cancel the booking first
                                <button onClick={() => changeSlot(slot, 'block')} disabled={busySlotId !== null}>
                                    {busySlotId === slot.id ? 'Closing...' : 'Close'}
                                </button>
                            )}
                        </li>
                    ))}
                </ul>
            )}
        </div>
    )
}

export default AdminSlotList
