# Court Booking Platform

[![CI](https://github.com/rebecafecioru/courtbooking/actions/workflows/ci.yml/badge.svg)](https://github.com/rebecafecioru/courtbooking/actions/workflows/ci.yml)

A multi-venue court booking system, built to solve a genuinely annoying real
problem: at many schools and universities, some sports can be booked online
and others (like volleyball, in my experience) still require phoning up
reception — where you can end up with the wrong time, or a slot that's
already taken.

This is a focused reimplementation of that idea for learning and
demonstration purposes. Commercial products in this space already exist
(Pitchbooking, Bookteq, OpenPlay, and others), so the goal here isn't
novelty — it's building the specific, narrow tool that would have actually
solved the problem I ran into, and using it as a vehicle to practise proper
backend engineering: authentication, correct data modelling, and above all,
**correctness under concurrency**.

## The interesting engineering problem

The whole reason this project exists is to avoid one bug: **two people
booking the same court at the same time.** That's a real race condition, not
a hypothetical one — it's exactly the kind of bug that causes the "we were
told this time was free" problem in the first place.

It's harder than it sounds because courts can overlap. A sports hall can be
booked whole, or as Half A and Half B, so booking the full hall has to rule
out both halves at that time (and the other way round). The hall and its
halves can also have different slot lengths, say 60 minutes on the hall and
90 on a half, so slots clash whenever their times overlap, not just when they
match exactly.

`BookingService` defends against it with three independent layers:

1. **Pessimistic locks** — every request first locks the hall's top-level
   court row, then every slot that could clash with the one it's touching
   (the slot itself plus every overlapping slot on the hall or its halves),
   all with `SELECT ... FOR UPDATE` and always in id order so two requests
   can't deadlock. A concurrent request for any of those slots has to wait
   for the first transaction to finish. The court lock is there for slots
   that don't exist yet: slot generation takes it too, so a booking never
   decides what to lock while new slots are being added to the hall.
2. **Optimistic version check** — `TimeSlot` carries a `@Version` column as
   a backup. If the row were ever updated outside the locked path, the
   stale write would fail with an `ObjectOptimisticLockingFailureException`
   instead of silently succeeding.
3. **Database-level unique constraint** — `Booking.time_slot_id` is unique.
   Even if both of the above were somehow bypassed, Postgres itself
   physically cannot store two `Booking` rows for the same slot.

This is proven with real concurrency tests. `BookingServiceConcurrencyTest`
fires two booking requests at the same slot from two threads at the same
instant and asserts that exactly one succeeds and one is rejected as a
conflict — not "usually one," every time. The others race the full hall
against a half (`FullHallVersusHalfConcurrencyTest`,
`MisalignedSlotsConcurrencyTest`), cancels against the waitlist
(`WaitlistConcurrencyTest`), blocking against booking
(`SlotBlockingConcurrencyTest`) and slot generation against booking
(`SlotGenerationConcurrencyTest`).

## Features

- **Public browsing** — anyone can view organisations, venues, courts, and
  live availability without an account
- **Booking & cancellation** — logged-in users book a slot and can cancel
  their own bookings, up until the slot starts
- **Overlapping spaces** — a hall can be split into halves; booking or
  closing one makes the overlapping slots on the others unavailable
- **Weekly bookings** — book the same slot for up to 12 weeks in one go
  (e.g. a club's Tuesday session). A preview shows which weeks are free
  first, and booking is all or nothing: if any chosen week is taken in the
  meantime, nothing is booked (see `SeriesBookingConcurrencyTest`). The
  whole series, or single weeks, can be cancelled
- **Waitlist** — queue for a taken slot; when it's cancelled, the first
  person in line is booked into it automatically, inside the cancel's own
  locked transaction, so nobody can grab it in between (see
  `WaitlistConcurrencyTest`)
- **Org admin tools** — venue/court creation, bulk time-slot generation
  (e.g. "every Mon/Wed/Fri, 9am–9pm, 1-hour slots"), and blocking slots for
  maintenance (refused while the space is booked; unblocking hands the slot
  to the first person on its waitlist). Slot generation and closing/reopening
  slots are in the React frontend's "Manage courts" section; generating never
  duplicates a slot the court already has
- **JWT authentication** with role-based access (`PUBLIC_USER`,
  `ORG_ADMIN`, `SUPER_ADMIN`)
- **Zero-setup local dev** — runs on an in-memory H2 database with seed
  data out of the box; a separate Postgres profile is provided for real
  deployment

## Tech stack

- Java 21, Spring Boot 3.5
- Spring Data JPA / Hibernate
- Spring Security + JWT (jjwt)
- H2 (dev) / PostgreSQL (prod profile)
- JUnit 5, Spring Boot Test

## Running it

```bash
git clone <this-repo>
cd courtbooking
./gradlew bootRun
```

and in a second terminal, the frontend:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The frontend calls `/api` on its own address
and Vite forwards it to the backend (see `frontend/vite.config.js`).

The backend starts on `http://localhost:8080` with an in-memory database
already seeded with a demo organisation, two courts, a week of time slots,
and three accounts:

| Role       | Email                          | Password    |
|------------|---------------------------------|-------------|
| Org Admin  | admin@leicester-demo.ac.uk      | password123 |
| Public User| student@leicester-demo.ac.uk    | password123 |
| Super Admin| superadmin@courtbooking-demo.dev | password123 |

Log in as the Super Admin to add organisations and their org admins (the
"Platform" section). An org admin can then add venues and courts under
"Manage courts", including halves of a hall ("Part of"), and give them slots.

The H2 console is available at `http://localhost:8080/h2-console`
(JDBC URL: `jdbc:h2:mem:courtbooking`).

To run against a real Postgres database instead:

```bash
./gradlew bootRun --args='--spring.profiles.active=postgres'
```

with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` set as
environment variables.

The demo data and accounts above are **not** created on the `postgres`
profile unless `SEED_DEMO_DATA=true`, because their passwords are public.
Without it a new database has no admin. To make the first one, sign up through the app as normal, then
promote that account directly in the database:

```sql
UPDATE app_user SET role = 'SUPER_ADMIN' WHERE email = 'you@example.com';
```

From then on, that Super Admin can set everything else up in the app.

### With Docker

`docker-compose.yml` runs the whole thing: Postgres, the backend and the
frontend (nginx serving the built React app and forwarding `/api` to the
backend, so the browser only ever talks to one site).

```bash
cp .env.example .env   # then set POSTGRES_PASSWORD and JWT_SECRET
docker compose up --build
```

Then open `http://localhost:3000`. For a throwaway public demo, set
`SEED_DEMO_DATA=true` in `.env` to start with the demo venues, courts and
accounts above.

Other settings, all optional:

| Variable | What it's for |
|----------|----------------|
| `PORT` | Port the site is served on (default 3000) |
| `CORS_ALLOWED_ORIGINS` | Only if the frontend is hosted on a different address from the backend, e.g. `https://courts.example.com` |
| `VITE_API_URL` | Build argument for the frontend image, for the same case: the backend's address |

The backend trusts `X-Forwarded-*` headers from the proxy in front of it, so
it also works behind a hosting platform's HTTPS proxy.

## Running the tests

```bash
./gradlew test
```

This includes the concurrency test described above — worth reading even if
you don't run it, it's the part of this project I'm most proud of.

## API overview

| Method | Endpoint                              | Access          | Description                          |
|--------|----------------------------------------|-----------------|---------------------------------------|
| POST   | `/api/auth/register`                   | Public          | Create an account                     |
| POST   | `/api/auth/login`                      | Public          | Get a JWT                             |
| GET    | `/api/organisations`                   | Public          | List organisations                    |
| GET    | `/api/venues?organisationId=`          | Public          | List venues                           |
| GET    | `/api/courts?venueId=`                 | Public          | List courts                           |
| GET    | `/api/courts/{id}/slots?from=&to=`     | Public          | Check availability (up to 31 days)    |
| POST   | `/api/bookings`                        | Authenticated   | Book a slot                           |
| GET    | `/api/bookings/me`                     | Authenticated   | Your bookings                         |
| GET    | `/api/bookings/series/preview?firstSlotId=&weeks=` | Authenticated | Which weeks of a weekly booking are free |
| POST   | `/api/bookings/series`                 | Authenticated   | Book several weeks, all or nothing    |
| DELETE | `/api/bookings/series/{id}`            | Authenticated   | Cancel a weekly booking's remaining weeks |
| DELETE | `/api/bookings/{id}`                   | Authenticated   | Cancel your own booking               |
| POST   | `/api/waitlist`                        | Authenticated   | Join the waitlist for a taken slot    |
| GET    | `/api/waitlist/me`                     | Authenticated   | Your waitlist places                  |
| DELETE | `/api/waitlist/{id}`                   | Authenticated   | Leave a waitlist                      |
| POST   | `/api/organisations/admin`             | Super Admin     | Create an organisation                |
| POST   | `/api/venues/admin`                    | Own-org admin / Super Admin | Create a venue                        |
| POST   | `/api/courts/admin`                    | Own-org admin / Super Admin | Create a court                        |
| POST   | `/api/admin/slots/generate`            | Own-org admin / Super Admin | Bulk-generate time slots              |
| PATCH  | `/api/admin/slots/{id}/block`          | Own-org admin / Super Admin | Block a slot (e.g. maintenance)       |
| PATCH  | `/api/admin/slots/{id}/unblock`        | Own-org admin / Super Admin | Reopen a blocked slot                 |
| POST   | `/api/admin/org-admins`                | Super Admin     | Create an org admin for an organisation |
| PATCH  | `/api/admin/users/{id}/organisation`   | Super Admin     | Make an existing user the org admin of an organisation |

"Own-org admin" means an `ORG_ADMIN` can only act on venues, courts and slots
that belong to their own organisation; anything else returns `403`. A
`SUPER_ADMIN` can act on every organisation.

Only a `SUPER_ADMIN` can create organisations, create org admins or assign a
user to an organisation; anyone else gets `403`. An unknown user or organisation returns
`404`, a taken email `409`, and a `SUPER_ADMIN` can't be turned into an org
admin (`409`).

## What's next

- Email/SMS booking confirmations (including "you got a slot from the waitlist")

## Data model

```
Organisation 1---* Venue 1---* Court 1---* TimeSlot 1---0..1 Booking *---1 AppUser
                                               TimeSlot 1---* WaitlistEntry *---1 AppUser
                                                    Booking *---0..1 BookingSeries *---1 AppUser
```
