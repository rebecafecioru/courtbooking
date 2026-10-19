import { useState, useEffect } from 'react'
import GenerateSlotsForm from './GenerateSlotsForm'
import AdminSlotList from './AdminSlotList'

// Lets an admin pick one of the organisation's courts, then generate,
// block and unblock its slots.
function AdminPanel({ user, organisation, bookingsVersion, onBookingsChanged }) {
    const [venues, setVenues] = useState([])
    const [venueId, setVenueId] = useState('')
    const [courts, setCourts] = useState([])
    const [courtId, setCourtId] = useState('')

    useEffect(() => {
        fetch(`http://localhost:8080/api/venues?organisationId=${organisation.id}`)
            .then(res => res.json())
            .then(data => setVenues(data))
    }, [organisation.id])

    useEffect(() => {
        if (!venueId) {
            return
        }
        fetch(`http://localhost:8080/api/courts?venueId=${venueId}`)
            .then(res => res.json())
            .then(data => setCourts(data))
    }, [venueId])

    function handleVenueChange(event) {
        setVenueId(event.target.value)
        setCourts([])
        setCourtId('') // the old court belongs to the old venue
    }

    const court = courts.find(c => String(c.id) === courtId)

    return (
        <div>
            <h2>Manage courts: {organisation.name}</h2>
            <select value={venueId} onChange={handleVenueChange}>
                <option value="">Choose a venue</option>
                {venues.map(venue => (
                    <option key={venue.id} value={venue.id}>{venue.name}</option>
                ))}
            </select>{' '}
            {venueId && (
                <select value={courtId} onChange={e => setCourtId(e.target.value)}>
                    <option value="">Choose a court</option>
                    {courts.map(c => (
                        <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                </select>
            )}

            {court && (
                // key: start fresh (messages, form) when the court changes
                <div key={court.id}>
                    <GenerateSlotsForm court={court} user={user} onGenerated={onBookingsChanged} />
                    <AdminSlotList
                        court={court}
                        user={user}
                        bookingsVersion={bookingsVersion}
                        onBookingsChanged={onBookingsChanged}
                    />
                </div>
            )}
        </div>
    )
}

export default AdminPanel
