import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import LoginForm from './LoginForm'
import { fakeBackend } from '../test/fakeBackend'

async function logIn(password) {
    await userEvent.type(screen.getByPlaceholderText('Email'), 'me@example.test')
    await userEvent.type(screen.getByPlaceholderText('Password'), password)
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('LoginForm', () => {
    it('logs in with the right password', async () => {
        const account = { token: 't', fullName: 'Me', role: 'PUBLIC_USER' }
        fakeBackend(() => ({ body: account }))
        const onLogin = vi.fn()
        render(<LoginForm onLogin={onLogin} />)

        await logIn('right-password')

        await vi.waitFor(() => expect(onLogin).toHaveBeenCalledWith(account))
    })

    it('says to check the details after a wrong password', async () => {
        fakeBackend(() => ({ status: 401, body: { message: 'Email or password is incorrect.' } }))
        render(<LoginForm onLogin={vi.fn()} />)

        await logIn('wrong')

        expect(await screen.findByRole('alert')).toHaveTextContent('Check your email and password.')
    })

    it('says how long to wait after too many tries', async () => {
        fakeBackend(() => ({
            status: 429,
            body: { message: 'Too many failed login attempts. Please try again in 15 minutes.' },
        }))
        const onLogin = vi.fn()
        render(<LoginForm onLogin={onLogin} />)

        await logIn('wrong')

        expect(await screen.findByRole('alert')).toHaveTextContent('Please try again in 15 minutes.')
        expect(onLogin).not.toHaveBeenCalled()
    })
})
