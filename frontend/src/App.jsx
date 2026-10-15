import { useState, useEffect } from 'react'
import VenueList from './components/VenueList'
import LoginForm from './components/LoginForm'
import RegisterForm from './components/RegisterForm'
import MyBookings from './components/MyBookings'
import MyWaitlist from './components/MyWaitlist'
import { loadSavedUser, saveUser, clearSavedUser } from './savedUser'

function App() {
    const [organisations, setOrganisations] = useState([])
    const [selectedOrganisation, setSelectedOrganisation] = useState(null)
    // null when logged out, otherwise { token, email, fullName, role }.
    // Starts from localStorage, so a refresh keeps you logged in.
    const [user, setUser] = useState(loadSavedUser)
    // Logged out: show the log in form, or the register form instead
    const [showRegister, setShowRegister] = useState(false)
    // Goes up by 1 whenever a booking or waitlist place is added or removed, so
    // My Bookings, My Waitlist and the slot list all know to reload. (A cancel
    // can turn someone's waitlist place into a booking.)
    const [bookingsVersion, setBookingsVersion] = useState(0)
    const onBookingsChanged = () => setBookingsVersion(v => v + 1)

    function handleLogin(newUser) {
        saveUser(newUser)
        setUser(newUser)
    }

    function handleLogout() {
        clearSavedUser()
        setUser(null)
        setShowRegister(false) // back to the log in form, not whichever was last open
    }

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
                    <button onClick={handleLogout}>Log out</button>
                </p>
            ) : showRegister ? (
                <>
                    <RegisterForm onLogin={handleLogin} />
                    <p>
                        Already have an account?{' '}
                        <button onClick={() => setShowRegister(false)}>Log in</button>
                    </p>
                </>
            ) : (
                <>
                    <LoginForm onLogin={handleLogin} />
                    <p>
                        No account yet?{' '}
                        <button onClick={() => setShowRegister(true)}>Create one</button>
                    </p>
                </>
            )}

            {user && (
                <>
                    <MyBookings
                        user={user}
                        bookingsVersion={bookingsVersion}
                        onBookingsChanged={onBookingsChanged}
                    />
                    <MyWaitlist
                        user={user}
                        bookingsVersion={bookingsVersion}
                        onBookingsChanged={onBookingsChanged}
                    />
                </>
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