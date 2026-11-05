import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import NotificationBell from './NotificationBell'
import { fakeBackend, requests, loggedInUser } from '../test/fakeBackend'

const older = { id: 1, message: 'Older news', createdAt: '2026-11-01T10:00:00', read: true }
const newer = { id: 2, message: "A place opened up: you're now booked", createdAt: '2026-11-02T10:00:00', read: false }

describe('NotificationBell', () => {
    it('shows how many are unread', async () => {
        fakeBackend(() => ({ body: { unreadCount: 1, notifications: [newer, older] } }))
        render(<NotificationBell user={loggedInUser} bookingsVersion={0} onNews={vi.fn()} />)

        expect(await screen.findByRole('button', { name: 'Notifications, 1 unread' })).toBeInTheDocument()
    })

    it('opening it lists them and marks them read', async () => {
        const fetchMock = fakeBackend(method => method === 'POST'
            ? { status: 204 }
            : { body: { unreadCount: 1, notifications: [newer, older] } })
        render(<NotificationBell user={loggedInUser} bookingsVersion={0} onNews={vi.fn()} />)

        await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))

        expect(screen.getByText(/A place opened up/)).toBeInTheDocument()
        expect(screen.getByText('Older news')).toBeInTheDocument()
        expect(await screen.findByRole('button', { name: 'Notifications, 0 unread' })).toBeInTheDocument()
        expect(requests(fetchMock)).toContainEqual(['POST', '/api/notifications/me/read'])
    })

    it('tells the app about news that arrives later, but not what was already there', async () => {
        let current = [older]
        fakeBackend(() => ({ body: { unreadCount: 0, notifications: current } }))
        const onNews = vi.fn()
        const { rerender } = render(<NotificationBell user={loggedInUser} bookingsVersion={0} onNews={onNews} />)
        await screen.findByRole('button', { name: 'Notifications, 0 unread' })
        expect(onNews).not.toHaveBeenCalled()

        // The waitlist gives this user a slot; the next check picks it up
        current = [newer, older]
        rerender(<NotificationBell user={loggedInUser} bookingsVersion={1} onNews={onNews} />)

        await vi.waitFor(() => expect(onNews).toHaveBeenCalledTimes(1))
    })
})
