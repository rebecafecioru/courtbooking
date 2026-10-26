import { useState } from 'react'
import { postJson, errorMessage } from '../api'

// Super admins only: add organisations, and the org admins who run them.
function PlatformPanel({ user, organisations, onOrganisationAdded }) {
    return (
        <section className="card">
            <h2>Platform</h2>
            <div className="grid-2">
                <AddOrganisationForm user={user} onAdded={onOrganisationAdded} />
                <AddOrgAdminForm user={user} organisations={organisations} />
            </div>
        </section>
    )
}

function AddOrganisationForm({ user, onAdded }) {
    const [name, setName] = useState('')
    const [description, setDescription] = useState('')
    const [contactEmail, setContactEmail] = useState('')
    const [message, setMessage] = useState(null)
    const [saving, setSaving] = useState(false)

    async function handleSubmit(event) {
        event.preventDefault()
        setMessage(null)
        setSaving(true)
        try {
            const result = await postJson('/api/organisations/admin',
                { name, description, contactEmail: contactEmail || null }, user.token)
            if (result.ok) {
                setMessage({ text: `Added ${result.body.name}. Now give it an org admin.`, error: false })
                setName('')
                setDescription('')
                setContactEmail('')
                onAdded()
                return
            }
            setMessage({ text: errorMessage(result, 'Could not add the organisation.'), error: true })
        } catch {
            setMessage({ text: 'Could not reach the server.', error: true })
        } finally {
            setSaving(false)
        }
    }

    return (
        <form className="inset" onSubmit={handleSubmit}>
            <h3>Add an organisation</h3>
            <input placeholder="Name, e.g. City Leisure Centre" value={name}
                   onChange={e => setName(e.target.value)} required />
            <input placeholder="Description (optional)" value={description}
                   onChange={e => setDescription(e.target.value)} />
            <input type="email" placeholder="Contact email (optional)" value={contactEmail}
                   onChange={e => setContactEmail(e.target.value)} />
            <div className="field-row">
                <button className="btn btn-primary" type="submit" disabled={saving}>
                    {saving ? 'Adding...' : 'Add organisation'}
                </button>
            </div>
            {message && <p className={message.error ? 'notice notice-error' : 'notice'}>{message.text}</p>}
        </form>
    )
}

function AddOrgAdminForm({ user, organisations }) {
    const [organisationId, setOrganisationId] = useState('')
    const [fullName, setFullName] = useState('')
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [message, setMessage] = useState(null)
    const [saving, setSaving] = useState(false)

    async function handleSubmit(event) {
        event.preventDefault()
        setMessage(null)
        setSaving(true)
        try {
            const result = await postJson('/api/admin/org-admins',
                { organisationId: Number(organisationId), fullName, email, password }, user.token)
            if (result.ok) {
                setMessage({
                    text: `${result.body.fullName} can now log in as the admin of ${result.body.organisationName}.`,
                    error: false,
                })
                setFullName('')
                setEmail('')
                setPassword('')
                return
            }
            setMessage({
                text: result.status === 409
                    ? 'An account with this email already exists.'
                    : errorMessage(result, 'Could not add the org admin.'),
                error: true,
            })
        } catch {
            setMessage({ text: 'Could not reach the server.', error: true })
        } finally {
            setSaving(false)
        }
    }

    return (
        <form className="inset" onSubmit={handleSubmit}>
            <h3>Add an org admin</h3>
            <select aria-label="Organisation" value={organisationId}
                    onChange={e => setOrganisationId(e.target.value)} required>
                <option value="">Choose an organisation</option>
                {organisations.map(org => (
                    <option key={org.id} value={org.id}>{org.name}</option>
                ))}
            </select>
            <input placeholder="Full name" value={fullName}
                   onChange={e => setFullName(e.target.value)} required />
            <input type="email" placeholder="Email" value={email}
                   onChange={e => setEmail(e.target.value)} required />
            <input type="password" placeholder="Password (8 to 72 characters)" value={password}
                   onChange={e => setPassword(e.target.value)} minLength={8} maxLength={72} required />
            <div className="field-row">
                <button className="btn btn-primary" type="submit" disabled={saving}>
                    {saving ? 'Adding...' : 'Add org admin'}
                </button>
            </div>
            {message && <p className={message.error ? 'notice notice-error' : 'notice'}>{message.text}</p>}
        </form>
    )
}

export default PlatformPanel
