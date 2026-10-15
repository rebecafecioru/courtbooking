import { useState, useEffect } from 'react'
import VenueList from './components/VenueList'
import LoginForm from './components/LoginForm'
import MyBookings from './components/MyBookings'

function App() {
    const [organisations, setOrganisations] = useState([])
    const [selectedOrganisation, setSelectedOrganisation] = useState(null)
    // null when logged out, otherwise { token, email, fullName, role }
    const [user, setUser] = useState(null)
    // Goes up by 1 whenever a booking is made or cancelled, so My Bookings
    // and the slot list both know to reload
    const [bookingsVersion, setBookingsVersion] = useState(0)
    const onBookingsChanged = () => setBookingsVersion(v => v + 1)

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

            {user && (
                <MyBookings
                    user={user}
                    bookingsVersion={bookingsVersion}
                    onBookingsChanged={onBookingsChanged}
                />
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
                <VenueList
                    organisation={selectedOrganisation}
                    user={user}
                    bookingsVersion={bookingsVersion}
                    onBookingsChanged={onBookingsChanged}
                />
            )}
        </div>
    )
}

export default App