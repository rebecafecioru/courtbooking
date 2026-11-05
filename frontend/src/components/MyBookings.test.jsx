import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import MyBookings from './MyBookings'
import { fakeBackend, requests, daysFromNow, loggedInUser } from '../test/fakeBackend'

function booking(id, days, seriesId = null) {
    return {
        id, seriesId, courtName: 'Half A', venueName: 'Sports Centre',
        startTime: daysFromNow(days, 19), endTime: daysFromNow(days, 20),
    }
}

describe('MyBookings', () => {
    it('lists upcoming bookings, marking weekly ones, and sums up each series', async () => {
        fakeBackend(() => ({
            body: [booking(1, 1, 7), booking(2, 8, 7), booking(3, 15, 7), booking(4, 2), booking(5, -3)],
        }))
        render(<MyBookings user={loggedInUser} bookingsVersion={0} onBookingsChanged={vi.fn()} />)

        expect(await screen.findAllByText('Weekly')).toHaveLength(3)
        // The one in the past isn't listed
        expect(screen.getAllByRole('button', { name: 'Cancel' })).toHaveLength(4)
        expect(screen.getByText(/3 weeks left/)).toBeInTheDocument()
    })

    it('cancels all the remaining weeks of a series in one go', async () => {
        const fetchMock = fakeBackend(method => method === 'DELETE'
            ? { body: { cancelledWeeks: 2 } }
            : { body: [booking(1, 1, 7), booking(2, 8, 7)] })
        const onBookingsChanged = vi.fn()
        render(<MyBookings user={loggedInUser} bookingsVersion={0} onBookingsChanged={onBookingsChanged} />)

        const series = (await screen.findByText(/2 weeks left/)).closest('li')
        await userEvent.click(within(series).getByRole('button', { name: 'Cancel all remaining' }))

        expect(await screen.findByText(/Cancelled 2 weeks of Half A/)).toBeInTheDocument()
        expect(requests(fetchMock)).toContainEqual(['DELETE', '/api/bookings/series/7'])
        expect(onBookingsChanged).toHaveBeenCalled()
    })
})
