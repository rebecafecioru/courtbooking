import { useState } from 'react'

function LoginForm({ onLogin }) {
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState(null)

    function handleSubmit(event) {
        // A form normally reloads the whole page when submitted. We want React
        // to handle it instead.
        event.preventDefault()
        setError(null)

        fetch('http://localhost:8080/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password }),
        })
            .then(res => {
                if (!res.ok) {
                    throw new Error('Login failed')
                }
                return res.json()
            })
            .then(data => onLogin(data))
            .catch(() => setError('Could not log in. Check your email and password.'))
    }

    return (
        <form onSubmit={handleSubmit}>
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
            <button type="submit">Log in</button>
            {error && <p>{error}</p>}
        </form>
    )
}

export default LoginForm