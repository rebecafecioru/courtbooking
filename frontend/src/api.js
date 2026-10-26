// Where the backend is. Empty means "the same site as this page": in
// development Vite forwards /api to the backend (see vite.config.js), and in
// the Docker setup nginx does. Set VITE_API_URL when building only if the
// backend is hosted somewhere else, e.g. VITE_API_URL=https://api.example.com
export const API_URL = import.meta.env.VITE_API_URL ?? ''

// POSTs JSON as the logged-in user. Returns { ok, status, body }, where body
// is the parsed response (or {} if there isn't one).
export async function postJson(path, data, token) {
    const res = await fetch(`${API_URL}${path}`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(data),
    })
    const body = await res.json().catch(() => ({}))
    return { ok: res.ok, status: res.status, body }
}

// A readable message from a failed request: the backend's field errors
// (e.g. "Password must be 8 to 72 characters long"), its message, or the fallback.
export function errorMessage({ status, body }, fallback) {
    if (status === 401 || status === 403) {
        return 'You aren\'t allowed to do that. Your login may have expired: try logging out and in again.'
    }
    const problems = Object.values(body.fieldErrors ?? {})
    if (problems.length > 0) {
        return problems.join('. ') + '.'
    }
    return body.message || fallback
}
