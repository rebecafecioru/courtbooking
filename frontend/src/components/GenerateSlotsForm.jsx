import { useState } from 'react'

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

// <input type="date"> wants YYYY-MM-DD in local time
function toDateInput(date) {
    const pad = n => String(n).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function inDays(n) {
    const date = new Date()
    date.setDate(date.getDate() + n)
    return date
}

function GenerateSlotsForm({ court, user, onGenerated }) {
    const [fromDate, setFromDate] = useState(toDateInput(new Date()))
    const [toDate, setToDate] = useState(toDateInput(inDays(6)))
    const [days, setDays] = useState(new Set(DAYS.slice(0, 5))) // Monday to Friday
    const [startTime, setStartTime] = useState('09:00')
    const [endTime, setEndTime] = useState('21:00')
    const [minutes, setMinutes] = useState(60)
    const [message, setMessage] = useState(null)
    const [saving, setSaving] = useState(false)

    function toggleDay(day) {
        const next = new Set(days)
        if (next.has(day)) {
            next.delete(day)
        } else {
            next.add(day)
        }
        setDays(next)
    }

    function handleSubmit(event) {
        event.preventDefault()
        setMessage(null)
        setSaving(true)

        fetch('http://localhost:8080/api/admin/slots/generate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${user.token}`,
            },
            body: JSON.stringify({
                courtId: court.id,
                fromDate,
                toDate,
                daysOfWeek: [...days],
                dailyStartTime: startTime,
                dailyEndTime: endTime,
                slotDurationMinutes: Number(minutes),
            }),
        })
            .then(async res => {
                if (res.ok) {
                    const created = await res.json()
                    setMessage(created.length === 0
                        ? 'No new slots: those times already have slots.'
                        : `Created ${created.length} new slots. Times that already had a slot were skipped.`)
                    onGenerated() // so the slot lists reload
                    return
                }
                if (res.status === 403) {
                    setMessage('You can only manage your own organisation\'s courts.')
                    return
                }
                const body = await res.json().catch(() => ({}))
                setMessage(body.message || 'Could not create the slots. Check the details and try again.')
            })
            .catch(() => setMessage('Could not reach the server.'))
            .finally(() => setSaving(false))
    }

    return (
        <form onSubmit={handleSubmit}>
            <h3>Add slots to {court.name}</h3>
            <p>
                From <input type="date" value={fromDate} onChange={e => setFromDate(e.target.value)} required />{' '}
                to <input type="date" value={toDate} onChange={e => setToDate(e.target.value)} required />
            </p>
            <p>
                {DAYS.map(day => (
                    <label key={day}>
                        <input type="checkbox" checked={days.has(day)} onChange={() => toggleDay(day)} />
                        {day.slice(0, 3)}{' '}
                    </label>
                ))}
            </p>
            <p>
                Between <input type="time" value={startTime} onChange={e => setStartTime(e.target.value)} required />{' '}
                and <input type="time" value={endTime} onChange={e => setEndTime(e.target.value)} required />,{' '}
                <input
                    type="number"
                    min="15"
                    step="15"
                    value={minutes}
                    onChange={e => setMinutes(e.target.value)}
                    required
                />{' '}
                minutes each
            </p>
            <button type="submit" disabled={saving || days.size === 0}>
                {saving ? 'Adding...' : 'Add slots'}
            </button>
            {message && <p>{message}</p>}
        </form>
    )
}

export default GenerateSlotsForm
