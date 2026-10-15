import { useState, useEffect } from 'react'
import VenueList from './components/VenueList'

function App() {
    const [organisations, setOrganisations] = useState([])
    const [selectedOrganisation, setSelectedOrganisation] = useState(null)

    useEffect(() => {
        fetch('http://localhost:8080/api/organisations')
            .then(res => res.json())
            .then(data => setOrganisations(data))
    }, [])

    return (
        <div>
            <h1>Court Booking</h1>
            <h2>Organisations</h2>
            <ul>
                {organisations.map(org => (
                    <li key={org.id}>
                        <button onClick={() => setSelectedOrganisation(org)}>
                            {org.name}
                        </button>
                    </li>
                ))}
            </ul>

            {selectedOrganisation && <VenueList organisation={selectedOrganisation} />}
        </div>
    )
}

export default App