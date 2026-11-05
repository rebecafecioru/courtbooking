import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import RegisterForm from './RegisterForm'
import { fakeBackend } from '../test/fakeBackend'

async function fillIn() {
    await userEvent.type(screen.getByPlaceholderText('Full name'), 'Test Player')
    await userEvent.type(screen.getByPlaceholderText('Email'), 'player@example.test')
    await userEvent.type(screen.getByPlaceholderText(/^Password/), 'long-enough-pw')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
}

describe('RegisterForm', () => {
    it('logs you straight in when the account is created', async () => {
        const account = { token: 't', fullName: 'Test Player', role: 'PUBLIC_USER' }
        fakeBackend(() => ({ body: account }))
        const onLogin = vi.fn()
        render(<RegisterForm onLogin={onLogin} />)

        await fillIn()

        await vi.waitFor(() => expect(onLogin).toHaveBeenCalledWith(account))
    })

    it("shows the backend's field errors", async () => {
        fakeBackend(() => ({
            status: 400,
            body: { message: 'Validation failed', fieldErrors: { email: 'must be a well-formed email address' } },
        }))
        render(<RegisterForm onLogin={vi.fn()} />)

        await fillIn()

        expect(await screen.findByRole('alert')).toHaveTextContent('must be a well-formed email address.')
    })

    it('says when the email is already registered', async () => {
        fakeBackend(() => ({ status: 409 }))
        const onLogin = vi.fn()
        render(<RegisterForm onLogin={onLogin} />)

        await fillIn()

        expect(await screen.findByRole('alert')).toHaveTextContent('An account with this email already exists.')
        expect(onLogin).not.toHaveBeenCalled()
    })

    it('asks for the same password length the backend requires', () => {
        render(<RegisterForm onLogin={vi.fn()} />)

        const password = screen.getByPlaceholderText(/^Password/)
        expect(password).toHaveAttribute('minLength', '8')
        expect(password).toHaveAttribute('maxLength', '72')
    })
})
