import { useState, useEffect } from 'react'
import CourtList from './CourtList'

function VenueList({ organisation, user, bookingsVersion, onBookingsChanged }) {
    const [venues, setVenues] = useState([])
    const [selectedVenue, setSelectedVenue] = useState(null)

    useEffect(() => {
        fetch(`http://localhost:8080/api/venues?organisationId=${organisation.id}`)
            .then(res => res.json())
            .then(data => setVenues(data))
    }, [organisation.id])

    return (
        <div>
            <h3>Venues at {organisation.name}</h3>
            <ul>
                {venues.map(venue => (
                    <li key={venue.id}>
                        <button onClick={() => setSelectedVenue(venue)}>
                            {venue.name} — {venue.address}
                        </button>
                    </li>
                ))}
            </ul>

            {selectedVenue && (
                <CourtList
                    key={selectedVenue.id}
                    venue={selectedVenue}
                    user={user}
                    bookingsVersion={bookingsVersion}
                    onBookingsChanged={onBookingsChanged}
                />
            )}
        </div>
    )
}

export default VenueList