import { useState, useEffect } from 'react'
import VenueList from './components/VenueList'
import LoginForm from './components/LoginForm'
import RegisterForm from './components/RegisterForm'
import MyBookings from './components/MyBookings'
import MyWaitlist from './components/MyWaitlist'
import AdminPanel from './components/AdminPanel'
import { loadSavedUser, saveUser, clearSavedUser } from './savedUser'
import { API_URL } from './api'

function App() {
    const [organisations, setOrganisations] = useState([])
    const [selectedOrganisation, setSelectedOrganisation] = useState(null)
    // null when logged out, otherwise { token, email, fullName, role, organisationId }.
    // Starts from localStorage, so a refresh keeps you logged in.
    const [user, setUser] = useState(loadSavedUser)
    // Logged out: show the log in form, or the register form instead
    const [showRegister, setShowRegister] = useState(false)
    // Goes up by 1 whenever bookings, waitlists or slots change, so My Bookings,
    // My Waitlist and the slot lists all know to reload. (A cancel or an admin
    // reopening a slot can turn someone's waitlist place into a booking.)
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

    // Which organisation the admin panel manages: an org admin's own one, or
    // for a super admin, whichever organisation is selected in the list below
    const adminOrganisation = user?.role === 'ORG_ADMIN'
        ? organisations.find(org => org.id === user.organisationId)
        : user?.role === 'SUPER_ADMIN' ? selectedOrganisation : null

    useEffect(() => {
        fetch(`${API_URL}/api/organisations`)
            .then(res => res.json())
            .then(data => setOrganisations(data))
    }, [])

    const roleLabel = { ORG_ADMIN: 'Org admin', SUPER_ADMIN: 'Super admin' }[user?.role]

    return (
        <>
            <header className="app-header">
                <div className="app-header-inner">
                    <h1 className="app-title">
                        <span className="app-title-mark" aria-hidden="true" />
                        Court Booking
                    </h1>
                    {user && (
                        <div className="user-bar">
                            <span>{user.fullName}</span>
                            {roleLabel && <span className="badge badge-yours">{roleLabel}</span>}
                            <button className="btn" onClick={handleLogout}>Log out</button>
                        </div>
                    )}
                </div>
            </header>

            <main className="page">
                {!user && (
                    <section className="card auth-card">
                        {showRegister ? (
                            <>
                                <RegisterForm onLogin={handleLogin} />
                                <p className="auth-switch">
                                    Already have an account?{' '}
                                    <button className="btn-link" onClick={() => setShowRegister(false)}>Log in</button>
                                </p>
                            </>
                        ) : (
                            <>
                                <LoginForm onLogin={handleLogin} />
                                <p className="auth-switch">
                                    No account yet?{' '}
                                    <button className="btn-link" onClick={() => setShowRegister(true)}>Create one</button>
                                </p>
                            </>
                        )}
                    </section>
                )}

                {user && (
                    <div className="grid-2">
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
                    </div>
                )}

                {user?.role === 'SUPER_ADMIN' && !adminOrganisation && (
                    <p className="notice">Choose an organisation below to manage its courts.</p>
                )}
                {adminOrganisation && (
                    <AdminPanel
                        key={adminOrganisation.id}
                        user={user}
                        organisation={adminOrganisation}
                        bookingsVersion={bookingsVersion}
                        onBookingsChanged={onBookingsChanged}
                    />
                )}

                <section className="card">
                    <h2>Book a court</h2>
                    <div className="step">
                        <span className="step-label">Organisation</span>
                        <ul className="chips">
                            {organisations.map(org => (
                                <li key={org.id}>
                                    <button
                                        className="chip"
                                        aria-pressed={selectedOrganisation?.id === org.id}
                                        onClick={() => setSelectedOrganisation(org)}
                                    >
                                        {org.name}
                                    </button>
                                </li>
                            ))}
                        </ul>
                    </div>

                    {selectedOrganisation && (
                        <VenueList
                            key={selectedOrganisation.id}
                            organisation={selectedOrganisation}
                            user={user}
                            bookingsVersion={bookingsVersion}
                            onBookingsChanged={onBookingsChanged}
                        />
                    )}
                </section>
            </main>
        </>
    )
}

export default App
