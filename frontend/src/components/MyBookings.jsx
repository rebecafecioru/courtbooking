import { useState, useEffect } from 'react'
import { formatDay, formatTime } from '../formatting'

function MyBookings({ user, bookingsVersion, onBookingsChanged }) {
    // null means "still loading"
    const [bookings, setBookings] = useState(null)
    const [message, setMessage] = useState(null)
    const [cancellingId, setCancellingId] = useState(null)

    useEffect(() => {
        fetch('http://localhost:8080/api/bookings/me', {
            headers: { Authorization: `Bearer ${user.token}` },
        })
            .then(res => {
                if (!res.ok) {
                    throw new Error('Could not load bookings')
                }
                return res.json()
            })
            .then(data => setBookings(data))
            .catch(() => {
                setBookings([])
                setMessage('Could not load your bookings. Try logging out and in again.')
            })
    }, [user.token, bookingsVersion])

    function handleCancel(booking) {
        setMessage(null)
        setCancellingId(booking.id)

        fetch(`http://localhost:8080/api/bookings/${booking.id}`, {
            method: 'DELETE',
            headers: { Authorization: `Bearer ${user.token}` },
        })
            .then(async res => {
                if (res.ok) {
                    setMessage(
                        `Cancelled ${booking.courtName}: ${formatDay(booking.startTime)}, `
                        + `${formatTime(booking.startTime)}.`
                    )
                    return
                }
                const body = await res.json().catch(() => ({}))
                setMessage(body.message || 'Could not cancel this booking.')
            })
            .catch(() => setMessage('Could not reach the server.'))
            .finally(() => {
                setCancellingId(null)
                onBookingsChanged() // reloads this list and any open slot list
            })
    }

    if (bookings === null) {
        return <p>Loading your bookings...</p>
    }

    // The backend returns past bookings too; only upcoming ones can be cancelled.
    const now = new Date()
    const upcoming = bookings.filter(b => new Date(b.endTime) > now)

    return (
        <div>
            <h2>My bookings</h2>
            {message && <p>{message}</p>}
            {upcoming.length === 0 ? (
                <p>You have no upcoming bookings.</p>
            ) : (
                <ul>
                    {upcoming.map(booking => (
                        <li key={booking.id}>
                            {booking.courtName} at {booking.venueName}:{' '}
                            {formatDay(booking.startTime)}, {formatTime(booking.startTime)} to {formatTime(booking.endTime)}{' '}
                            <button
                                onClick={() => handleCancel(booking)}
                                disabled={cancellingId !== null}
                            >
                                {cancellingId === booking.id ? 'Cancelling...' : 'Cancel'}
                            </button>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    )
}

export default MyBookings
