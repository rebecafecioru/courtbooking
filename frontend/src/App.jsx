import { useState, useEffect } from 'react'
import VenueList from './components/VenueList'
import LoginForm from './components/LoginForm'

function App() {
    const [organisations, setOrganisations] = useState([])
    const [selectedOrganisation, setSelectedOrganisation] = useState(null)
    // null when logged out, otherwise { token, email, fullName, role }
    const [user, setUser] = useState(null)

    useEffect(() => {
        fetch('http://localhost:8080/api/organisations')
            .then(res => res.json())
            .then(data => setOrganisations(data))
    }, [])

    return (
        <div>
            <h1>Court Booking</h1>

            {user ? (
                <p>
                    Logged in as {user.fullName}{' '}
                    <button onClick={() => setUser(null)}>Log out</button>
                </p>
            ) : (
                <LoginForm onLogin={setUser} />
            )}

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

            {selectedOrganisation && (
                <VenueList organisation={selectedOrganisation} user={user} />
            )}
        </div>
    )
}

export default App