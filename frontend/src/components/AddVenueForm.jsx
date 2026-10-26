import { useState } from 'react'
import { postJson, errorMessage } from '../api'

function AddVenueForm({ organisation, user, onAdded, onClose }) {
    const [name, setName] = useState('')
    const [address, setAddress] = useState('')
    const [error, setError] = useState(null)
    const [saving, setSaving] = useState(false)

    async function handleSubmit(event) {
        event.preventDefault()
        setError(null)
        setSaving(true)
        try {
            const result = await postJson('/api/venues/admin',
                { name, address, organisationId: organisation.id }, user.token)
            if (result.ok) {
                onAdded(result.body) // the new venue
                return
            }
            setError(errorMessage(result, 'Could not add the venue.'))
        } catch {
            setError('Could not reach the server.')
        } finally {
            setSaving(false)
        }
    }

    return (
        <form className="inset" onSubmit={handleSubmit}>
            <h3>Add a venue to {organisation.name}</h3>
            <div className="field-row">
                <input placeholder="Name, e.g. Main Sports Centre" value={name}
                       onChange={e => setName(e.target.value)} required />
                <input placeholder="Address" value={address}
                       onChange={e => setAddress(e.target.value)} required />
            </div>
            <div className="field-row">
                <button className="btn btn-primary" type="submit" disabled={saving}>
                    {saving ? 'Adding...' : 'Add venue'}
                </button>
                <button className="btn" type="button" onClick={onClose}>Cancel</button>
            </div>
            {error && <p className="notice notice-error" role="alert">{error}</p>}
        </form>
    )
}

export default AddVenueForm
