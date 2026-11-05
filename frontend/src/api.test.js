import { describe, it, expect } from 'vitest'
import { postJson, errorMessage } from './api'
import { fakeBackend } from './test/fakeBackend'

describe('errorMessage', () => {
    it('lists the field errors from a 400', () => {
        const result = {
            status: 400,
            body: { message: 'Validation failed', fieldErrors: { password: 'Password must be 8 to 72 characters long' } },
        }
        expect(errorMessage(result, 'fallback')).toBe('Password must be 8 to 72 characters long.')
    })

    it("uses the backend's message when there are no field errors", () => {
        expect(errorMessage({ status: 409, body: { message: 'Slot taken' } }, 'fallback')).toBe('Slot taken')
    })

    it('falls back when the backend says nothing useful', () => {
        expect(errorMessage({ status: 500, body: {} }, 'Could not add the venue.')).toBe('Could not add the venue.')
    })

    it('explains a 401 or 403 as not allowed / logged out', () => {
        expect(errorMessage({ status: 403, body: {} }, 'fallback')).toMatch(/aren't allowed/)
        expect(errorMessage({ status: 401, body: {} }, 'fallback')).toMatch(/logging out and in/)
    })
})

describe('postJson', () => {
    it('sends JSON with the login token and returns the parsed reply', async () => {
        const fetchMock = fakeBackend(() => ({ status: 201, body: { id: 7 } }))

        const result = await postJson('/api/venues/admin', { name: 'Hall' }, 'tok')

        expect(result).toEqual({ ok: true, status: 201, body: { id: 7 } })
        const [, options] = fetchMock.mock.calls[0]
        expect(options.headers.Authorization).toBe('Bearer tok')
        expect(JSON.parse(options.body)).toEqual({ name: 'Hall' })
    })

    it('copes with an empty reply', async () => {
        fakeBackend(() => ({ status: 409 }))

        expect(await postJson('/api/x', {}, 'tok')).toEqual({ ok: false, status: 409, body: {} })
    })
})
