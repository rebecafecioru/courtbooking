import { useState, useEffect } from 'react'
import { formatDay, formatTime } from '../formatting'

function MyWaitlist({ user, bookingsVersion, onBookingsChanged }) {
    // null means "still loading"
    const [entries, setEntries] = useState(null)
    const [message, setMessage] = useState(null)
    const [leavingId, setLeavingId] = useState(null)

    useEffect(() => {
        fetch('http://localhost:8080/api/waitlist/me', {
            headers: { Authorization: `Bearer ${user.token}` },
        })
            .then(res => {
                if (!res.ok) {
                    throw new Error('Could not load waitlist')
                }
                return res.json()
            })
            .then(data => setEntries(data))
            .catch(() => {
                setEntries([])
                setMessage('Could not load your waitlist. Try logging out and in again.')
            })
    }, [user.token, bookingsVersion])

    function handleLeave(entry) {
        setMessage(null)
        setLeavingId(entry.id)

        fetch(`http://localhost:8080/api/waitlist/${entry.id}`, {
            method: 'DELETE',
            headers: { Authorization: `Bearer ${user.token}` },
        })
            .then(async res => {
                if (res.ok) {
                    setMessage(`Left the waitlist for ${entry.courtName}: ${formatDay(entry.startTime)}, `
                        + `${formatTime(entry.startTime)}.`)
                    return
                }
                // e.g. 404 if you were given the slot just before clicking Leave
                const body = await res.json().catch(() => ({}))
                setMessage(body.message || 'Could not leave this waitlist.')
            })
            .catch(() => setMessage('Could not reach the server.'))
            .finally(() => {
                setLeavingId(null)
                onBookingsChanged() // reloads this list, My Bookings and any open slot list
            })
    }

    if (entries === null) {
        return <p>Loading your waitlist...</p>
    }

    const now = new Date()
    const upcoming = entries.filter(e => new Date(e.endTime) > now)

    // Nothing to show: keep the page tidy rather than an empty heading
    if (upcoming.length === 0 && !message) {
        return null
    }

    return (
        <div>
            <h2>My waitlist</h2>
            {message && <p>{message}</p>}
            {upcoming.length === 0 ? (
                <p>You're not on any waitlists.</p>
            ) : (
                <ul>
                    {upcoming.map(entry => (
                        <li key={entry.id}>
                            {entry.position === 1 ? 'Next in line' : `Number ${entry.position} in line`} for{' '}
                            {entry.courtName} at {entry.venueName}:{' '}
                            {formatDay(entry.startTime)}, {formatTime(entry.startTime)} to {formatTime(entry.endTime)}{' '}
                            <button
                                onClick={() => handleLeave(entry)}
                                disabled={leavingId !== null}
                            >
                                {leavingId === entry.id ? 'Leaving...' : 'Leave'}
                            </button>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    )
}

export default MyWaitlist
