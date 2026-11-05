import { describe, it, expect } from 'vitest'
import { loadSavedUser, saveUser, clearSavedUser } from './savedUser'

// A JWT-shaped token whose payload is base64url JSON, the way the backend
// makes them: '-' and '_' instead of '+' and '/', and no '=' padding
function tokenWith(payload) {
    const base64url = btoa(JSON.stringify(payload)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
    return `header.${base64url}.signature`
}

const inSeconds = s => Math.floor(Date.now() / 1000) + s

describe('saved login', () => {
    it('comes back after a refresh while the token is valid', () => {
        const user = { fullName: 'Test Player', token: tokenWith({ sub: 'p@example.test', exp: inSeconds(3600) }) }
        saveUser(user)

        expect(loadSavedUser()).toEqual(user)
    })

    it('is dropped once the token has expired', () => {
        saveUser({ fullName: 'Test Player', token: tokenWith({ exp: inSeconds(-60) }) })

        expect(loadSavedUser()).toBeNull()
        expect(localStorage.length).toBe(0) // and cleared, not just ignored
    })

    it('copes with base64url payloads that need padding or use - and _', () => {
        // Payloads of different lengths exercise every amount of missing padding
        for (const sub of ['a', 'ab', 'abc', '???>>>~~~']) {
            const user = { token: tokenWith({ sub, exp: inSeconds(3600) }) }
            saveUser(user)
            expect(loadSavedUser(), `sub=${sub}`).toEqual(user)
        }
    })

    it('treats a token it cannot read as logged out', () => {
        saveUser({ token: 'not-a-jwt' })

        expect(loadSavedUser()).toBeNull()
    })

    it('treats corrupted storage as logged out', () => {
        localStorage.setItem('courtbooking.user', '{not json')

        expect(loadSavedUser()).toBeNull()
    })

    it('is gone after logging out', () => {
        saveUser({ token: tokenWith({ exp: inSeconds(3600) }) })
        clearSavedUser()

        expect(loadSavedUser()).toBeNull()
    })
})
