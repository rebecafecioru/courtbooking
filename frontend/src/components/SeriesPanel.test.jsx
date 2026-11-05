import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SeriesPanel from './SeriesPanel'
import { fakeBackend, requests, daysFromNow, loggedInUser } from '../test/fakeBackend'

const slot = { id: 10, startTime: daysFromNow(1, 19), endTime: daysFromNow(1, 20) }

function week(n, status) {
    return {
        week: n,
        timeSlotId: status === 'NO_SLOT' ? null : 100 + n,
        startTime: daysFromNow(1 + 7 * (n - 1), 19),
        endTime: daysFromNow(1 + 7 * (n - 1), 20),
        status,
    }
}

function renderPanel(onBookingsChanged = vi.fn()) {
    return render(<SeriesPanel slot={slot} user={loggedInUser} bookingsVersion={0}
                               onBookingsChanged={onBookingsChanged} onClose={vi.fn()} />)
}

describe('SeriesPanel (book every week)', () => {
    it('offers all the weeks when they are all free', async () => {
        fakeBackend(() => ({ body: [week(1, 'FREE'), week(2, 'FREE'), week(3, 'FREE')] }))
        renderPanel()

        expect(await screen.findByRole('button', { name: 'Book all 3 weeks' })).toBeInTheDocument()
    })

    it('offers only the free weeks, and books just those', async () => {
        const fetchMock = fakeBackend(method => method === 'POST'
            ? { status: 201, body: { seriesId: 1, bookings: [{}, {}] } }
            : { body: [week(1, 'FREE'), week(2, 'TAKEN'), week(3, 'CLOSED'), week(4, 'NO_SLOT'), week(5, 'FREE')] })
        const onBookingsChanged = vi.fn()
        renderPanel(onBookingsChanged)

        expect(await screen.findByText('Taken')).toBeInTheDocument()
        expect(screen.getByText('Closed')).toBeInTheDocument()
        expect(screen.getByText('No slot yet')).toBeInTheDocument()
        await userEvent.click(screen.getByRole('button', { name: 'Book the 2 free weeks' }))

        expect(await screen.findByText(/Booked 2 weeks/)).toBeInTheDocument()
        const post = fetchMock.mock.calls.find(([, options]) => options?.method === 'POST')
        expect(JSON.parse(post[1].body)).toEqual({ timeSlotIds: [101, 105] })
        expect(onBookingsChanged).toHaveBeenCalled()
    })

    it('says "week", not "weeks", when only one is free', async () => {
        fakeBackend(() => ({ body: [week(1, 'FREE'), week(2, 'TAKEN')] }))
        renderPanel()

        expect(await screen.findByRole('button', { name: 'Book the 1 free week' })).toBeInTheDocument()
    })

    it('has nothing to book when no week is free', async () => {
        fakeBackend(() => ({ body: [week(1, 'TAKEN'), week(2, 'CLOSED')] }))
        renderPanel()

        expect(await screen.findByText('None of these weeks are free.')).toBeInTheDocument()
        expect(screen.queryByRole('button', { name: /^Book/ })).toBeNull()
    })

    it('when a week is taken after the preview, says so and shows the weeks again', async () => {
        let previews = 0
        fakeBackend(method => {
            if (method === 'POST') {
                return { status: 409, body: { message: 'Nothing was booked, because these weeks are no longer free: week 2.' } }
            }
            previews++
            // Week 2 is taken by someone else between the first preview and the click
            return { body: [week(1, 'FREE'), week(2, previews === 1 ? 'FREE' : 'TAKEN'), week(3, 'FREE')] }
        })
        renderPanel()

        await userEvent.click(await screen.findByRole('button', { name: 'Book all 3 weeks' }))

        expect(await screen.findByText(/Nothing was booked/)).toBeInTheDocument()
        expect(await screen.findByRole('button', { name: 'Book the 2 free weeks' })).toBeInTheDocument()
        expect(previews).toBe(2)
    })

    it('checks again for a different number of weeks', async () => {
        const fetchMock = fakeBackend(() => ({ body: [week(1, 'FREE')] }))
        renderPanel()
        await screen.findByText('Free')

        await userEvent.selectOptions(screen.getByLabelText('Number of weeks'), '4')

        expect(requests(fetchMock).at(-1)[1]).toMatch(/firstSlotId=10&weeks=4$/)
    })
})
