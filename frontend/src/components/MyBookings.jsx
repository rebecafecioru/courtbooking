import { useState, useEffect } from 'react'
import { formatDay, formatTime } from '../formatting'

function MyBookings({ user, bookingsVersion, onBookingsChanged }) {
    // null means "still loading"
    const [bookings, setBookings] = useState(null)
    const [message, setMessage] = useState(null)
    // The booking id, or 'series-<id>' while a whole series is being cancelled
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

    function handleCancelSeries(series) {
        setMessage(null)
        setCancellingId(`series-${series.id}`)

        fetch(`http://localhost:8080/api/bookings/series/${series.id}`, {
            method: 'DELETE',
            headers: { Authorization: `Bearer ${user.token}` },
        })
            .then(async res => {
                if (res.ok) {
                    const body = await res.json()
                    setMessage(`Cancelled ${body.cancelledWeeks} weeks of ${series.courtName} on ${series.weekday}.`)
                    return
                }
                const body = await res.json().catch(() => ({}))
                setMessage(body.message || 'Could not cancel this series.')
            })
            .catch(() => setMessage('Could not reach the server.'))
            .finally(() => {
                setCancellingId(null)
                onBookingsChanged() // reloads this list and any open slot list
            })
    }

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

    // The backend returns past bookings too; only ones that haven't started yet
    // can be cancelled.
    const now = new Date()
    const upcoming = bookings.filter(b => new Date(b.startTime) > now)

    // One entry per weekly series, from its upcoming weeks (earliest first)
    const seriesList = []
    for (const b of upcoming) {
        if (b.seriesId === null) {
            continue
        }
        const existing = seriesList.find(s => s.id === b.seriesId)
        if (existing) {
            existing.weeksLeft++
        } else {
            seriesList.push({
                id: b.seriesId,
                courtName: b.courtName,
                venueName: b.venueName,
                weekday: new Date(b.startTime).toLocaleDateString('en-GB', { weekday: 'long' }) + 's',
                time: formatTime(b.startTime),
                weeksLeft: 1,
            })
        }
    }

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
                            {booking.seriesId !== null && '(weekly) '}
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
            {seriesList.length > 0 && (
                <>
                    <h3>Weekly bookings</h3>
                    <ul>
                        {seriesList.map(series => (
                            <li key={series.id}>
                                {series.courtName} at {series.venueName}, {series.weekday} at {series.time}:{' '}
                                {series.weeksLeft} {series.weeksLeft === 1 ? 'week' : 'weeks'} left{' '}
                                <button
                                    onClick={() => handleCancelSeries(series)}
                                    disabled={cancellingId !== null}
                                >
                                    {cancellingId === `series-${series.id}` ? 'Cancelling...' : 'Cancel all remaining weeks'}
                                </button>
                            </li>
                        ))}
                    </ul>
                </>
            )}
        </div>
    )
}

export default MyBookings
