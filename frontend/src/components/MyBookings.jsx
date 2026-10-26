import { useState, useEffect } from 'react'
import { formatDay, formatTime } from '../formatting'
import { API_URL } from '../api'

function MyBookings({ user, bookingsVersion, onBookingsChanged }) {
    // null means "still loading"
    const [bookings, setBookings] = useState(null)
    const [message, setMessage] = useState(null)
    // The booking id, or 'series-<id>' while a whole series is being cancelled
    const [cancellingId, setCancellingId] = useState(null)

    useEffect(() => {
        fetch(`${API_URL}/api/bookings/me`, {
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

        fetch(`${API_URL}/api/bookings/series/${series.id}`, {
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

        fetch(`${API_URL}/api/bookings/${booking.id}`, {
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
        <section className="card">
            <h2>My bookings</h2>
            {message && <p className="notice">{message}</p>}
            {upcoming.length === 0 ? (
                <p className="empty">You have no upcoming bookings.</p>
            ) : (
                <ul className="list">
                    {upcoming.map(booking => (
                        <li className="row" key={booking.id}>
                            <div className="row-main">
                                <span className="row-title">
                                    {formatDay(booking.startTime)}, {formatTime(booking.startTime)}–{formatTime(booking.endTime)}
                                </span>
                                <span className="row-meta">{booking.courtName} · {booking.venueName}</span>
                            </div>
                            <div className="row-actions">
                                {booking.seriesId !== null && <span className="badge badge-yours">Weekly</span>}
                                <button
                                    className="btn btn-danger"
                                    onClick={() => handleCancel(booking)}
                                    disabled={cancellingId !== null}
                                >
                                    {cancellingId === booking.id ? 'Cancelling...' : 'Cancel'}
                                </button>
                            </div>
                        </li>
                    ))}
                </ul>
            )}
            {seriesList.length > 0 && (
                <div className="step">
                    <span className="step-label">Weekly bookings</span>
                    <ul className="list">
                        {seriesList.map(series => (
                            <li className="row" key={series.id}>
                                <div className="row-main">
                                    <span className="row-title">{series.weekday} at {series.time}</span>
                                    <span className="row-meta">
                                        {series.courtName} · {series.venueName} ·{' '}
                                        {series.weeksLeft} {series.weeksLeft === 1 ? 'week' : 'weeks'} left
                                    </span>
                                </div>
                                <div className="row-actions">
                                    <button
                                        className="btn btn-danger"
                                        onClick={() => handleCancelSeries(series)}
                                        disabled={cancellingId !== null}
                                    >
                                        {cancellingId === `series-${series.id}` ? 'Cancelling...' : 'Cancel all remaining'}
                                    </button>
                                </div>
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </section>
    )
}

export default MyBookings
