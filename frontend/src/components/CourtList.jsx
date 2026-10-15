import { useState, useEffect } from 'react'
import SlotList from './SlotList'

function CourtList({ venue, user }) {
    const [courts, setCourts] = useState([])
    const [selectedCourt, setSelectedCourt] = useState(null)

    useEffect(() => {
        fetch(`http://localhost:8080/api/courts?venueId=${venue.id}`)
            .then(res => res.json())
            .then(data => setCourts(data))
    }, [venue.id])

    return (
        <div>
            <h4>Courts at {venue.name}</h4>
            <ul>
                {courts.map(court => (
                    <li key={court.id}>
                        <button onClick={() => setSelectedCourt(court)}>
                            {court.name} - {court.sportType}
                        </button>
                    </li>
                ))}
            </ul>

            {selectedCourt && (
                <SlotList key={selectedCourt.id} court={selectedCourt} user={user} />
            )}
        </div>
    )
}

export default CourtList