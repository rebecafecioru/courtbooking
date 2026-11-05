import { useState } from 'react'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SlotList from './SlotList'
import { fakeBackend, daysFromNow, loggedInUser } from '../test/fakeBackend'

const court = { id: 3, name: 'Court 1' }

// One slot of each kind, at different hours so each row is easy to find
const slots = [
    { id: 1, startTime: daysFromNow(1, 9), endTime: daysFromNow(1, 10), status: 'AVAILABLE', yours: false, onWaitlist: false },
    { id: 2, startTime: daysFromNow(1, 10), endTime: daysFromNow(1, 11), status: 'BOOKED', yours: false, onWaitlist: false },
    { id: 3, startTime: daysFromNow(1, 11), endTime: daysFromNow(1, 12), status: 'BLOCKED', yours: false, onWaitlist: false },
    { id: 4, startTime: daysFromNow(2, 12), endTime: daysFromNow(2, 13), status: 'BOOKED', yours: true, onWaitlist: false },
    { id: 5, startTime: daysFromNow(2, 13), endTime: daysFromNow(2, 14), status: 'UNAVAILABLE', yours: false, onWaitlist: true },
]

// The row for the slot starting at this hour
async function row(hour) {
    const time = await screen.findByText(new RegExp(`^${String(hour).padStart(2, '0')}:00–`))
    return time.closest('li')
}

// Like App: onBookingsChanged bumps bookingsVersion, which the list reacts to
// (it re-fetches, and shows its "Booked ..." message for the new version)
function Harness({ user, onBookingsChanged }) {
    const [version, setVersion] = useState(0)
    return (
        <SlotList
            court={court}
            user={user}
            bookingsVersion={version}
            onBookingsChanged={() => {
                onBookingsChanged()
                setVersion(v => v + 1)
            }}
        />
    )
}

function renderSlots(user, onBookingsChanged = vi.fn()) {
    return render(<Harness user={user} onBookingsChanged={onBookingsChanged} />)
}

describe('SlotList', () => {
    it('shows every slot with its status, grouped by day', async () => {
        fakeBackend(() => ({ body: slots }))
        renderSlots(loggedInUser)

        expect(within(await row(9)).getByText('Free')).toBeInTheDocument()
        expect(within(await row(10)).getByText('Taken')).toBeInTheDocument()
        expect(within(await row(11)).getByText('Closed')).toBeInTheDocument()
        expect(within(await row(12)).getByText('Yours')).toBeInTheDocument()
        expect(within(await row(13)).getByText('On your waitlist')).toBeInTheDocument()
        expect(screen.getAllByRole('heading', { level: 3 })).toHaveLength(2) // two days
    })

    it('offers the right action for each slot', async () => {
        fakeBackend(() => ({ body: slots }))
        renderSlots(loggedInUser)

        const free = await row(9)
        expect(within(free).getByRole('button', { name: 'Book' })).toBeInTheDocument()
        expect(within(free).getByRole('button', { name: 'Book weekly' })).toBeInTheDocument()
        expect(within(await row(10)).getByRole('button', { name: 'Join waitlist' })).toBeInTheDocument()
        // Closed, your own booking, and a slot you're already waiting for: nothing to do here
        expect(within(await row(11)).queryByRole('button')).toBeNull()
        expect(within(await row(12)).queryByRole('button')).toBeNull()
        expect(within(await row(13)).queryByRole('button')).toBeNull()
    })

    it('shows no buttons when logged out, and asks you to log in', async () => {
        fakeBackend(() => ({ body: slots }))
        renderSlots(null)

        await row(9)
        expect(screen.queryAllByRole('button')).toHaveLength(0)
        expect(screen.getByText(/Log in to book/)).toBeInTheDocument()
    })

    it('sends the login token only when logged in', async () => {
        const fetchMock = fakeBackend(() => ({ body: slots }))
        renderSlots(loggedInUser)
        await row(9)
        expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer test-token')

        fetchMock.mockClear()
        renderSlots(null)
        await screen.findByText(/Log in to book/)
        expect(fetchMock.mock.calls[0][1]).toBeUndefined()
    })

    it('books a free slot and says so', async () => {
        const fetchMock = fakeBackend(method =>
            method === 'POST' ? { status: 201, body: { id: 99 } } : { body: slots })
        const onBookingsChanged = vi.fn()
        renderSlots(loggedInUser, onBookingsChanged)

        await userEvent.click(within(await row(9)).getByRole('button', { name: 'Book' }))

        expect(await screen.findByText(/^Booked Court 1/)).toBeInTheDocument()
        const post = fetchMock.mock.calls.find(([, options]) => options?.method === 'POST')
        expect(post[0]).toMatch(/\/api\/bookings$/)
        expect(JSON.parse(post[1].body)).toEqual({ timeSlotId: 1 })
        expect(onBookingsChanged).toHaveBeenCalled()
    })

    it("shows the backend's reason when someone else got there first", async () => {
        fakeBackend(method => method === 'POST'
            ? { status: 409, body: { message: 'This slot is no longer available (status: BOOKED).' } }
            : { body: slots })
        renderSlots(loggedInUser)

        await userEvent.click(within(await row(9)).getByRole('button', { name: 'Book' }))

        expect(await screen.findByText('This slot is no longer available (status: BOOKED).')).toBeInTheDocument()
    })
})
