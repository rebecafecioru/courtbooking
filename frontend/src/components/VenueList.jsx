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
        <>
            <div className="step">
                <span className="step-label">Venue</span>
                <ul className="chips">
                    {venues.map(venue => (
                        <li key={venue.id}>
                            <button
                                className="chip"
                                aria-pressed={selectedVenue?.id === venue.id}
                                onClick={() => setSelectedVenue(venue)}
                            >
                                {venue.name}
                                <span className="chip-detail">{venue.address}</span>
                            </button>
                        </li>
                    ))}
                </ul>
            </div>

            {selectedVenue && (
                <CourtList
                    key={selectedVenue.id}
                    venue={selectedVenue}
                    user={user}
                    bookingsVersion={bookingsVersion}
                    onBookingsChanged={onBookingsChanged}
                />
            )}
        </>
    )
}

export default VenueList
