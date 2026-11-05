import { useState } from 'react'
import { API_URL } from '../api'

function LoginForm({ onLogin }) {
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState(null)

    function handleSubmit(event) {
        // A form normally reloads the whole page when submitted. We want React
        // to handle it instead.
        event.preventDefault()
        setError(null)

        fetch(`${API_URL}/api/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password }),
        })
            .then(async res => {
                if (res.status === 429) {
                    // Too many failed tries: the backend says how long to wait
                    const body = await res.json().catch(() => ({}))
                    throw new Error(body.message || 'Too many failed attempts. Please try again later.')
                }
                if (!res.ok) {
                    throw new Error('Could not log in. Check your email and password.')
                }
                return res.json()
            })
            .then(data => onLogin(data))
            .catch(err => setError(err.message))
    }

    return (
        <form className="form-stack" onSubmit={handleSubmit}>
            <h2>Log in</h2>
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
            <button className="btn btn-primary" type="submit">Log in</button>
            {error && <p className="notice notice-error" role="alert">{error}</p>}
        </form>
    )
}

export default LoginForm