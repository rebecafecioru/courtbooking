export function formatDay(isoString) {
    return new Date(isoString).toLocaleDateString('en-GB', {
        weekday: 'short', day: 'numeric', month: 'short',
    })
}

export function formatTime(isoString) {
    return new Date(isoString).toLocaleTimeString('en-GB', {
        hour: '2-digit', minute: '2-digit',
    })
}
