# Court Booking Platform
A court booking system for sports facilities that have more than one venue. I built it because at a lot of universities they still use phoning reception which comes with challenges. You can be given the wrong time, or a slot that someone else already has.

Similar products already exist (Pitchbooking, Bookteq and OpenPlay), so this isn't trying to be new. I wanted to build the narrow version that would have fixed my problem, and use it to practise backend work: authentication, data modelling and, mainly, getting concurrent bookings right.

## The main problem: double booking

The system exists to stop two people getting the same space at the same time. That can happen in two ways:

1. Two people book the same slot at the same moment.
2. Two people book spaces that overlap. A sports hall can be booked whole or as two halves, so booking the full hall has to block both halves, and booking a half has to block the full hall but not the other half.

`BookingService` handles both with three layers:

1. **Pessimistic locks.** A booking locks its slot and the matching slot on every overlapping space, together and always in id order. A competing request waits for the first to finish, and the fixed order stops two overlapping bookings from deadlocking.
2. **An optimistic version check.** `TimeSlot` has a `@Version` column, which catches a slot being changed outside the locked path.
3. **A unique constraint** on `booking.time_slot_id`, so the database itself rejects a second booking for the same slot.

Overlaps use a parent link: Half A and Half B point at the full hall. A slot is `AVAILABLE`, `BOOKED`, `BLOCKED` (closed by an admin) or `UNAVAILABLE` (free, but covered by a booking on an overlapping space). Cancelling recalculates these, so the full hall stays unavailable while either half is still booked.

The tests start competing bookings on separate threads at the same moment and check that exactly one succeeds.

## Features
- Browse organisations, venues, courts and availability without an account
- Book a slot, and cancel your own bookings, once logged in
- Halls and pitches that can be booked whole or in halves
- Admin tools to add venues and courts, generate slots in bulk (for example every Monday, Wednesday and Friday, 9am to 9pm, in one-hour slots) and block slots for maintenance
- JWT authentication with three roles: `PUBLIC_USER`, `ORG_ADMIN` and `SUPER_ADMIN`
- Runs on in-memory H2 with seed data, so nothing needs setting up. A PostgreSQL profile is included
- A React frontend, partly built so far (organisations, venues and courts)

## Demo data
The seed data is based on the University of Leicester's sports facilities (Danielle Brown, Roger Bettles and the Charles Wilson Building), using what the university publishes. The project has no connection to the university. Names, addresses and facility types come from public pages. The time slots are invented, not real availability, and some figures, such as the number of tennis courts, are estimates.

## Tech stack
Java 21, Spring Boot 3.5, Spring Data JPA, Spring Security with JWT (jjwt), H2 and PostgreSQL, JUnit 5, and React with Vite.

## Running it
**Backend**

```bash
git clone https://github.com/rebecafecioru/courtbooking.git
cd courtbooking
./gradlew bootRun
```
The API starts on `http://localhost:8080` with an in-memory database already seeded with demo venues, courts, a week of slots and two accounts:

| Role        | Email                        | Password    |
|-------------|------------------------------|-------------|
| Org Admin   | admin@leicester-demo.ac.uk   | password123 |
| Public User | student@leicester-demo.ac.uk | password123 |

The H2 console is at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:courtbooking`).

**Frontend** (needs Node.js, and the backend must be running)

```bash
cd frontend
npm install
npm run dev
```

Then open `http://localhost:5173`.

**PostgreSQL instead of H2**

```bash
./gradlew bootRun --args='--spring.profiles.active=postgres'
```

Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and `JWT_SECRET` as environment variables first.

## Tests

```bash
./gradlew test
```
There are 13 tests, including:

- booking a slot, rejecting a double booking, and rebooking after a cancellation
- two people booking the same slot at the same moment
- a full hall and one of its halves booked at the same moment
- the half-hall rules: a half blocks the full hall but not the other half, and the full hall stays blocked until both halves are free

## API

| Method | Endpoint                                | Access          | Description                     |
|--------|-----------------------------------------|-----------------|---------------------------------|
| POST   | `/api/auth/register`                    | Public          | Create an account               |
| POST   | `/api/auth/login`                       | Public          | Get a JWT                       |
| GET    | `/api/organisations`                    | Public          | List organisations              |
| GET    | `/api/venues?organisationId=`           | Public          | List venues                     |
| GET    | `/api/courts?venueId=` or `?sportType=` | Public          | List courts                     |
| GET    | `/api/courts/{id}/slots?from=&to=`      | Public          | Check availability              |
| POST   | `/api/bookings`                         | Authenticated   | Book a slot                     |
| DELETE | `/api/bookings/{id}`                    | Authenticated   | Cancel your own booking         |
| POST   | `/api/venues/admin`                     | Org/Super Admin | Create a venue                  |
| POST   | `/api/courts/admin`                     | Org/Super Admin | Create a court                  |
| POST   | `/api/admin/slots/generate`             | Org/Super Admin | Generate time slots in bulk     |
| PATCH  | `/api/admin/slots/{id}/block`           | Org/Super Admin | Block a slot, for example for maintenance |

## Data model

```
Organisation 1---* Venue 1---* Court 1---* TimeSlot 1---0..1 Booking *---1 AppUser
                                  |
                                  +--- parent (a half points at its full space)
```

## Known limitations

- Cancelled bookings are deleted rather than kept as history, because the unique constraint allows only one booking row per slot.
- Halves are only set up in the seed data. The admin API can't create a court with a parent yet.
- A full space and its halves rely on having identical slot times, so they must be generated together.
- Blocking a half doesn't change the full hall's slot in the availability listing, although a booking attempt on it is still rejected.

## What's next

- React screens for court availability, login and booking
- Creating halves through the admin API
- Email or SMS booking confirmations
- Recurring bookings for regular club sessions
- A waitlist for full slots