import { useState, useEffect } from 'react'
import { formatDay, formatTime } from '../formatting'

// The backend wants dates like 2026-10-15T09:00:00 (local time, no timezone).
function toLocalIso(date) {
    const pad = n => String(n).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
        + `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

// Label, badge colour and hover explanation for each status
const STATUSES = {
    AVAILABLE: ['Free', 'badge-free', 'Nobody has booked this'],
    BOOKED: ['Booked', 'badge-yours', 'Someone has booked this. Cancel the booking before closing it'],
    UNAVAILABLE: ['Covered', 'badge-taken', 'An overlapping space (the full hall or a half) is booked or closed'],
    BLOCKED: ['Closed', 'badge-closed', 'Closed by an admin'],
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
        <div className="form-stack">
            <h3>Next two weeks: {court.name}</h3>
            {message && <p className="notice notice-error">{message}</p>}
            {slots.length === 0 ? (
                <p className="empty">No slots in the next two weeks. Add some above.</p>
            ) : (
                days.map(({ day, slots: daySlots }) => (
                    <div key={day} className="form-stack">
                        <span className="step-label">{day}</span>
                        <ul className="list">
                            {daySlots.map(slot => {
                                const [label, badgeClass, explanation] =
                                    STATUSES[slot.status] ?? [slot.status, 'badge-closed', '']
                                return (
                                    <li className="row" key={slot.id}>
                                        <span className="row-title">
                                            {formatTime(slot.startTime)}–{formatTime(slot.endTime)}
                                        </span>
                                        <div className="row-actions">
                                            <span className={`badge ${badgeClass}`} title={explanation}>{label}</span>
                                            {slot.status === 'BLOCKED' ? (
                                                <button
                                                    className="btn"
                                                    onClick={() => changeSlot(slot, 'unblock')}
                                                    disabled={busySlotId !== null}
                                                >
                                                    {busySlotId === slot.id ? 'Reopening...' : 'Reopen'}
                                                </button>
                                            ) : slot.status !== 'BOOKED' && (
                                                // Booked slots can't be closed: cancel the booking first
                                                <button
                                                    className="btn btn-danger"
                                                    onClick={() => changeSlot(slot, 'block')}
                                                    disabled={busySlotId !== null}
                                                >
                                                    {busySlotId === slot.id ? 'Closing...' : 'Close'}
                                                </button>
                                            )}
                                        </div>
                                    </li>
                                )
                            })}
                        </ul>
                    </div>
                ))
            )}
        </div>
    )
}

export default AdminSlotList
