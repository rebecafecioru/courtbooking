import { useState, useEffect } from 'react'

function VenueList({ organisation }) {
    const [venues, setVenues] = useState([])

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
                        {venue.name} — {venue.address}
                    </li>
                ))}
            </ul>
        </div>
    )
}

export default VenueList