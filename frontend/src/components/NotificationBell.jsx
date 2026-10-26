import { useState, useEffect, useRef } from 'react'
import { API_URL } from '../api'
import { formatDay, formatTime } from '../formatting'

// How often to check for new notifications while the page is open
const POLL_MS = 30_000

// Header button with an unread count; opens a list of recent notifications
// and marks them all read. onNews is called when a new notification arrives,
// since it usually means bookings changed (e.g. the waitlist gave you a slot).
function NotificationBell({ user, bookingsVersion, onNews }) {
    const [data, setData] = useState({ unreadCount: 0, notifications: [] })
    const [open, setOpen] = useState(false)
    // Adding 1 to this re-fetches the list
    const [refreshCount, setRefreshCount] = useState(0)
    // Id of the newest notification seen so far (undefined before the first fetch)
    const newestSeenId = useRef(undefined)

    useEffect(() => {
        fetch(`${API_URL}/api/notifications/me`, {
            headers: { Authorization: `Bearer ${user.token}` },
        })
            .then(res => (res.ok ? res.json() : null))
            .then(body => {
                if (!body) {
                    return
                }
                setData(body)
                const newestId = body.notifications[0]?.id ?? null
                // Not on the first fetch: what's already there isn't news
                if (newestSeenId.current !== undefined && newestId !== newestSeenId.current) {
                    onNews()
                }
                newestSeenId.current = newestId
            })
            .catch(() => {}) // a missed check is fine: the next one will catch up
    // onNews isn't a dependency: a new function each render shouldn't re-fetch
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [user.token, bookingsVersion, refreshCount])

    // Something like a cancel by someone else can give you a slot at any
    // time, so check now and then rather than only after your own actions
    useEffect(() => {
        const timer = setInterval(() => setRefreshCount(c => c + 1), POLL_MS)
        return () => clearInterval(timer)
    }, [])

    function handleToggle() {
        const opening = !open
        setOpen(opening)
        if (opening && data.unreadCount > 0) {
            // Shown now, so mark them read. The list keeps its "new" marks
            // until it's next fetched, so you can see which ones were new.
            fetch(`${API_URL}/api/notifications/me/read`, {
                method: 'POST',
                headers: { Authorization: `Bearer ${user.token}` },
            })
                .then(() => setData(d => ({ ...d, unreadCount: 0 })))
                .catch(() => {})
        }
    }

    return (
        <div className="notif-wrap">
            <button
                className="btn"
                onClick={handleToggle}
                aria-expanded={open}
                aria-label={`Notifications, ${data.unreadCount} unread`}
            >
                Notifications
                {data.unreadCount > 0 && <span className="count-badge">{data.unreadCount}</span>}
            </button>
            {open && (
                <div className="notif-panel">
                    {data.notifications.length === 0 ? (
                        <p className="empty">Nothing yet. If the waitlist gives you a slot, you'll hear about it here.</p>
                    ) : (
                        <ul className="list">
                            {data.notifications.map(n => (
                                <li key={n.id} className={n.read ? 'notif' : 'notif notif-unread'}>
                                    <span>{n.message}</span>
                                    <span className="row-meta">
                                        {formatDay(n.createdAt)}, {formatTime(n.createdAt)}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            )}
        </div>
    )
}

export default NotificationBell
