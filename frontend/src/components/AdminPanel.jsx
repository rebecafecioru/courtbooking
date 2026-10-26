import { useState, useEffect } from 'react'
import GenerateSlotsForm from './GenerateSlotsForm'
import AdminSlotList from './AdminSlotList'
import AddVenueForm from './AddVenueForm'
import AddCourtForm from './AddCourtForm'
import { API_URL } from '../api'

// Lets an admin add venues and courts, pick one of the organisation's courts,
// then generate, block and unblock its slots.
function AdminPanel({ user, organisation, bookingsVersion, onBookingsChanged }) {
    const [venues, setVenues] = useState([])
    const [venueId, setVenueId] = useState('')
    const [courts, setCourts] = useState([])
    const [courtId, setCourtId] = useState('')
    // Which add form is open: 'venue', 'court' or null
    const [adding, setAdding] = useState(null)
    // Adding 1 to these re-fetches the venues or courts, after adding one
    const [venuesVersion, setVenuesVersion] = useState(0)
    const [courtsVersion, setCourtsVersion] = useState(0)

    useEffect(() => {
        fetch(`${API_URL}/api/venues?organisationId=${organisation.id}`)
            .then(res => res.json())
            .then(data => setVenues(data))
    }, [organisation.id, venuesVersion])

    useEffect(() => {
        if (!venueId) {
            return
        }
        fetch(`${API_URL}/api/courts?venueId=${venueId}`)
            .then(res => res.json())
            .then(data => setCourts(data))
    }, [venueId, courtsVersion])

    function handleVenueChange(event) {
        selectVenue(event.target.value)
    }

    function selectVenue(id) {
        setVenueId(id)
        setCourts([])
        setCourtId('') // the old court belongs to the old venue
        setAdding(null)
    }

    // Select what was just added, so the admin can carry straight on
    function handleVenueAdded(venue) {
        setVenuesVersion(v => v + 1)
        selectVenue(String(venue.id))
    }

    function handleCourtAdded(newCourt) {
        setCourtsVersion(v => v + 1)
        setCourtId(String(newCourt.id))
        setAdding(null)
    }

    const venue = venues.find(v => String(v.id) === venueId)
    const court = courts.find(c => String(c.id) === courtId)

    return (
        <section className="card">
            <div className="card-header">
                <h2>Manage courts</h2>
                <span className="muted">{organisation.name}</span>
            </div>
            <div className="field-row">
                <select aria-label="Venue" value={venueId} onChange={handleVenueChange}>
                    <option value="">Choose a venue</option>
                    {venues.map(venue => (
                        <option key={venue.id} value={venue.id}>{venue.name}</option>
                    ))}
                </select>
                {venueId && (
                    <select aria-label="Court" value={courtId} onChange={e => setCourtId(e.target.value)}>
                        <option value="">Choose a court</option>
                        {courts.map(c => (
                            <option key={c.id} value={c.id}>{c.name}</option>
                        ))}
                    </select>
                )}
                <button className="btn" onClick={() => setAdding(adding === 'venue' ? null : 'venue')}>
                    Add venue
                </button>
                {venue && (
                    <button className="btn" onClick={() => setAdding(adding === 'court' ? null : 'court')}>
                        Add court
                    </button>
                )}
            </div>

            {adding === 'venue' && (
                <AddVenueForm
                    organisation={organisation}
                    user={user}
                    onAdded={handleVenueAdded}
                    onClose={() => setAdding(null)}
                />
            )}
            {adding === 'court' && venue && (
                <AddCourtForm
                    key={venue.id}
                    venue={venue}
                    courts={courts}
                    user={user}
                    onAdded={handleCourtAdded}
                    onClose={() => setAdding(null)}
                />
            )}
            {venues.length === 0 && adding !== 'venue' && (
                <p className="empty">No venues yet. Add one to get started.</p>
            )}

            {court && (
                // key: start fresh (messages, form) when the court changes
                <div key={court.id} className="form-stack">
                    <GenerateSlotsForm court={court} user={user} onGenerated={onBookingsChanged} />
                    <AdminSlotList
                        court={court}
                        user={user}
                        bookingsVersion={bookingsVersion}
                        onBookingsChanged={onBookingsChanged}
                    />
                </div>
            )}
        </section>
    )
}

export default AdminPanel
