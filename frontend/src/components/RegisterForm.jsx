import { useState } from 'react'

function RegisterForm({ onLogin }) {
    const [fullName, setFullName] = useState('')
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState(null)

    function handleSubmit(event) {
        event.preventDefault()
        setError(null)

        fetch('http://localhost:8080/api/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ fullName, email, password }),
        })
            .then(res => {
                if (res.status === 409) {
                    throw new Error('An account with this email already exists.')
                }
                if (!res.ok) {
                    throw new Error('Could not create your account. Check the details and try again.')
                }
                return res.json()
            })
            // Register returns a token too, so the new user is logged in straight away
            .then(data => onLogin(data))
            .catch(err => setError(err.message))
    }

    return (
        <form onSubmit={handleSubmit}>
            <h2>Create an account</h2>
            <input
                placeholder="Full name"
                value={fullName}
                onChange={e => setFullName(e.target.value)}
                required
            />
            <input
                type="email"
                placeholder="Email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
            />
            <input
                type="password"
                placeholder="Password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
            />
            <button type="submit">Create account</button>
            {error && <p>{error}</p>}
        </form>
    )
}

export default RegisterForm
