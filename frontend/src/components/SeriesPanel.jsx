import { useState, useEffect } from 'react'
import { formatDay, formatTime } from '../formatting'

const STATUS_LABELS = {
    FREE: 'Free',
    TAKEN: 'Taken',
    CLOSED: 'Closed',
    NO_SLOT: 'No slot that week yet',
    STARTED: 'Too late',
}

// "Book this slot every week": previews the coming weeks, then books the
// free ones as one series. All or nothing on the backend, so if a week is
// taken between the preview and the click, nothing is booked and the
// preview reloads.
function SeriesPanel({ slot, user, bookingsVersion, onBookingsChanged, onClose }) {
    const [weeks, setWeeks] = useState(8)
    // null means "still loading"
    const [preview, setPreview] = useState(null)
    const [message, setMessage] = useState(null)
    const [booking, setBooking] = useState(false)
    // Adding 1 to this re-fetches the preview
    const [refreshCount, setRefreshCount] = useState(0)

    useEffect(() => {
        fetch(`http://localhost:8080/api/bookings/series/preview?firstSlotId=${slot.id}&weeks=${weeks}`, {
            headers: { Authorization: `Bearer ${user.token}` },
        })
            .then(res => {
                if (!res.ok) {
                    throw new Error('Could not load the weeks')
                }
                return res.json()
            })
            .then(data => setPreview(data))
            .catch(() => {
                setPreview([])
                setMessage('Could not check the coming weeks. Try logging out and in again.')
            })
    }, [slot.id, weeks, user.token, bookingsVersion, refreshCount])

    function handleBook(timeSlotIds) {
        setMessage(null)
        setBooking(true)

        fetch('http://localhost:8080/api/bookings/series', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${user.token}`,
            },
            body: JSON.stringify({ timeSlotIds }),
        })
            .then(async res => {
                if (res.ok) {
                    const body = await res.json()
                    setMessage(`Booked ${body.bookings.length} weeks. They're in My bookings.`)
                    onBookingsChanged()
                    return
                }
                // e.g. 409 when a week was taken after the preview: the backend says which
                const body = await res.json().catch(() => ({}))
                setMessage(body.message || 'Could not book these weeks.')
                setRefreshCount(count => count + 1) // show what changed
            })
            .catch(() => setMessage('Could not reach the server.'))
            .finally(() => setBooking(false))
    }

    const freeIds = (preview ?? []).filter(w => w.status === 'FREE').map(w => w.timeSlotId)
    const allFree = preview !== null && preview.length > 0 && freeIds.length === preview.length

    return (
        <div>
            <p>
                Book {formatTime(slot.startTime)} to {formatTime(slot.endTime)} every week for{' '}
                <select value={weeks} onChange={e => setWeeks(Number(e.target.value))}>
                    {[2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map(n => (
                        <option key={n} value={n}>{n} weeks</option>
                    ))}
                </select>{' '}
                <button onClick={onClose}>Close</button>
            </p>
            {message && <p>{message}</p>}
            {preview === null ? (
                <p>Checking the coming weeks...</p>
            ) : (
                <>
                    <ul>
                        {preview.map(w => (
                            <li key={w.week}>
                                Week {w.week}, {formatDay(w.startTime)}: {STATUS_LABELS[w.status] ?? w.status}
                            </li>
                        ))}
                    </ul>
                    {allFree ? (
                        <button onClick={() => handleBook(freeIds)} disabled={booking}>
                            {booking ? 'Booking...' : `Book all ${freeIds.length} weeks`}
                        </button>
                    ) : freeIds.length > 0 ? (
                        <button onClick={() => handleBook(freeIds)} disabled={booking}>
                            {booking ? 'Booking...' : `Book the ${freeIds.length} free weeks`}
                        </button>
                    ) : (
                        <p>None of these weeks are free.</p>
                    )}
                </>
            )}
        </div>
    )
}

export default SeriesPanel
