// Keeps the logged-in user in localStorage, so a page refresh doesn't log you out.
//
// Trade-off worth knowing: any JavaScript on the page can read localStorage, so
// if the site ever had an XSS bug the token could be stolen. An httpOnly cookie
// avoids that, but needs backend changes; localStorage is fine for now.

const KEY = 'courtbooking.user'

// The JWT's middle part is base64url-encoded JSON; "exp" is when it expires,
// in seconds since 1970.
function isExpired(token) {
    try {
        const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
        const { exp } = JSON.parse(atob(payload))
        return exp * 1000 < Date.now()
    } catch {
        return true // can't read it, so don't trust it
    }
}

export function loadSavedUser() {
    try {
        const user = JSON.parse(localStorage.getItem(KEY))
        if (user && !isExpired(user.token)) {
            return user
        }
    } catch {
        // missing or corrupted: treat as logged out
    }
    clearSavedUser()
    return null
}

export function saveUser(user) {
    try {
        localStorage.setItem(KEY, JSON.stringify(user))
    } catch {
        // storage blocked (e.g. private browsing): login still works, just won't survive a refresh
    }
}

export function clearSavedUser() {
    try {
        localStorage.removeItem(KEY)
    } catch {
        // nothing to clear
    }
}
