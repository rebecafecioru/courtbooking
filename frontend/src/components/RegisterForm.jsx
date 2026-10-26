import { useState } from 'react'
import { API_URL } from '../api'

function RegisterForm({ onLogin }) {
    const [fullName, setFullName] = useState('')
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState(null)

    function handleSubmit(event) {
        event.preventDefault()
        setError(null)

        fetch(`${API_URL}/api/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ fullName, email, password }),
        })
            .then(async res => {
                if (res.status === 409) {
                    throw new Error('An account with this email already exists.')
                }
                if (res.status === 400) {
                    // The backend says which fields are wrong, e.g. a password that's too short
                    const body = await res.json().catch(() => ({}))
                    const problems = Object.values(body.fieldErrors ?? {})
                    throw new Error(problems.length > 0
                        ? problems.join('. ') + '.'
                        : 'Could not create your account. Check the details and try again.')
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
        <form className="form-stack" onSubmit={handleSubmit}>
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
                placeholder="Password (8 to 72 characters)"
                value={password}
                onChange={e => setPassword(e.target.value)}
                // Same rule as the backend, so most mistakes are caught before sending
                minLength={8}
                maxLength={72}
                required
            />
            <button className="btn btn-primary" type="submit">Create account</button>
            {error && <p className="notice notice-error" role="alert">{error}</p>}
        </form>
    )
}

export default RegisterForm
