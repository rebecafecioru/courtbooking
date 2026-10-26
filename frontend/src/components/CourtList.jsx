import { useState, useEffect } from 'react'
import SlotList from './SlotList'

function CourtList({ venue, user, bookingsVersion, onBookingsChanged }) {
    const [courts, setCourts] = useState([])
    const [selectedCourt, setSelectedCourt] = useState(null)

    useEffect(() => {
        fetch(`http://localhost:8080/api/courts?venueId=${venue.id}`)
            .then(res => res.json())
            .then(data => setCourts(data))
    }, [venue.id])

    return (
        <>
            <div className="step">
                <span className="step-label">Court</span>
                <ul className="chips">
                    {courts.map(court => (
                        <li key={court.id}>
                            <button
                                className="chip"
                                aria-pressed={selectedCourt?.id === court.id}
                                onClick={() => setSelectedCourt(court)}
                            >
                                {court.name}
                                <span className="chip-detail">{court.sportType}</span>
                            </button>
                        </li>
                    ))}
                </ul>
            </div>

            {selectedCourt && (
                <SlotList
                    key={selectedCourt.id}
                    court={selectedCourt}
                    user={user}
                    bookingsVersion={bookingsVersion}
                    onBookingsChanged={onBookingsChanged}
                />
            )}
        </>
    )
}

export default CourtList
