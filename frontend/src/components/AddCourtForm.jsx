import { useState } from 'react'
import { postJson, errorMessage } from '../api'

// courts: the venue's existing courts, to pick what a new one is part of
function AddCourtForm({ venue, courts, user, onAdded, onClose }) {
    const [name, setName] = useState('')
    const [sportType, setSportType] = useState('')
    const [capacity, setCapacity] = useState('')
    const [parentCourtId, setParentCourtId] = useState('')
    const [error, setError] = useState(null)
    const [saving, setSaving] = useState(false)

    // Halls split only one level deep, so only courts that aren't already
    // part of something can be a parent
    const possibleParents = courts.filter(c => c.parentCourtId === null)

    async function handleSubmit(event) {
        event.preventDefault()
        setError(null)
        setSaving(true)
        try {
            const result = await postJson('/api/courts/admin', {
                name,
                sportType,
                capacity: capacity === '' ? null : Number(capacity),
                venueId: venue.id,
                parentCourtId: parentCourtId === '' ? null : Number(parentCourtId),
            }, user.token)
            if (result.ok) {
                onAdded(result.body) // the new court
                return
            }
            setError(errorMessage(result, 'Could not add the court.'))
        } catch {
            setError('Could not reach the server.')
        } finally {
            setSaving(false)
        }
    }

    return (
        <form className="inset" onSubmit={handleSubmit}>
            <h3>Add a court to {venue.name}</h3>
            <div className="field-row">
                <input placeholder="Name, e.g. Sports Hall - Half A" value={name}
                       onChange={e => setName(e.target.value)} required />
                <input placeholder="Sports, e.g. Volleyball, Badminton" value={sportType}
                       onChange={e => setSportType(e.target.value)} required />
                <input type="number" min="1" placeholder="Players" aria-label="Capacity (optional)"
                       value={capacity} onChange={e => setCapacity(e.target.value)} />
            </div>
            {possibleParents.length > 0 && (
                <label className="field-row">
                    Part of
                    <select value={parentCourtId} onChange={e => setParentCourtId(e.target.value)}>
                        <option value="">Nothing: it's a space of its own</option>
                        {possibleParents.map(c => (
                            <option key={c.id} value={c.id}>{c.name}</option>
                        ))}
                    </select>
                </label>
            )}
            {parentCourtId !== '' && (
                <p className="muted">
                    Booking this court will block the court it's part of at the same time, and the other way round.
                </p>
            )}
            <div className="field-row">
                <button className="btn btn-primary" type="submit" disabled={saving}>
                    {saving ? 'Adding...' : 'Add court'}
                </button>
                <button className="btn" type="button" onClick={onClose}>Cancel</button>
            </div>
            {error && <p className="notice notice-error" role="alert">{error}</p>}
        </form>
    )
}

export default AddCourtForm
