import { vi } from 'vitest'

// Replaces fetch with a fake backend. handler(method, url, body) returns
// { status, body } (status defaults to 200), or nothing for an empty 200.
// Returns the mock, so tests can check what was requested.
export function fakeBackend(handler) {
    const fetchMock = vi.fn(async (url, options = {}) => {
        const method = options.method ?? 'GET'
        const requestBody = options.body ? JSON.parse(options.body) : undefined
        const { status = 200, body } = (await handler(method, String(url), requestBody)) ?? {}
        const noContent = status === 204 || body === undefined
        return new Response(noContent ? null : JSON.stringify(body), {
            status,
            headers: { 'Content-Type': 'application/json' },
        })
    })
    vi.stubGlobal('fetch', fetchMock)
    return fetchMock
}

// The requests the app made, as [method, url] pairs, e.g. to check what was sent
export function requests(fetchMock) {
    return fetchMock.mock.calls.map(([url, options = {}]) => [options.method ?? 'GET', String(url)])
}

// A local date-time like the backend sends ("2026-11-06T19:00:00"), some days from now
export function daysFromNow(days, hour = 19) {
    const d = new Date()
    d.setDate(d.getDate() + days)
    d.setHours(hour, 0, 0, 0)
    const pad = n => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(hour)}:00:00`
}

export const loggedInUser = { token: 'test-token', fullName: 'Test Player', role: 'PUBLIC_USER' }
