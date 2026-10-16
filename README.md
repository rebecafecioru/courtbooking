# Court Booking Platform

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

`BookingService` defends against it with three independent layers:

1. **Pessimistic row lock** — when a booking request comes in, the service
   reads the target `TimeSlot` with `SELECT ... FOR UPDATE`. A second,
   concurrent request for the same slot has to wait for the first
   transaction to finish before it can even read the row.
2. **Optimistic version check** — `TimeSlot` carries a `@Version` column as
   a backup. If the row were ever updated outside the locked path, the
   stale write would fail with an `ObjectOptimisticLockingFailureException`
   instead of silently succeeding.
3. **Database-level unique constraint** — `Booking.time_slot_id` is unique.
   Even if both of the above were somehow bypassed, Postgres itself
   physically cannot store two `Booking` rows for the same slot.

This is proven with a real concurrency test
(`BookingServiceConcurrencyTest`) that fires two booking requests at the
same slot from two threads at the same instant and asserts that exactly one
succeeds and one is rejected as a conflict — not "usually one," every time.

## Features

- **Public browsing** — anyone can view organisations, venues, courts, and
  live availability without an account
- **Booking & cancellation** — logged-in users book a slot and can cancel
  their own bookings
- **Waitlist** — queue for a taken slot; when it's cancelled, the first
  person in line is booked into it automatically, inside the cancel's own
  locked transaction, so nobody can grab it in between (see
  `WaitlistConcurrencyTest`)
- **Org admin tools** — venue/court creation, bulk time-slot generation
  (e.g. "every Mon/Wed/Fri, 9am–9pm, 1-hour slots"), and blocking slots for
  maintenance (refused while the space is booked; unblocking hands the slot
  to the first person on its waitlist)
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

The app starts on `http://localhost:8080` with an in-memory database
already seeded with a demo organisation, two courts, a week of time slots,
and three accounts:

| Role       | Email                          | Password    |
|------------|---------------------------------|-------------|
| Org Admin  | admin@leicester-demo.ac.uk      | password123 |
| Public User| student@leicester-demo.ac.uk    | password123 |
| Super Admin| superadmin@courtbooking-demo.dev | password123 |

The Super Admin account is only created in local dev (not on the `postgres`
profile). Use it to create org admins with `POST /api/admin/org-admins`.

The H2 console is available at `http://localhost:8080/h2-console`
(JDBC URL: `jdbc:h2:mem:courtbooking`).

To run against a real Postgres database instead:

```bash
./gradlew bootRun --args='--spring.profiles.active=postgres'
```

with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` set as
environment variables.

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
| GET    | `/api/courts/{id}/slots?from=&to=`     | Public          | Check availability                    |
| POST   | `/api/bookings`                        | Authenticated   | Book a slot                           |
| GET    | `/api/bookings/me`                     | Authenticated   | Your bookings                         |
| DELETE | `/api/bookings/{id}`                   | Authenticated   | Cancel your own booking               |
| POST   | `/api/waitlist`                        | Authenticated   | Join the waitlist for a taken slot    |
| GET    | `/api/waitlist/me`                     | Authenticated   | Your waitlist places                  |
| DELETE | `/api/waitlist/{id}`                   | Authenticated   | Leave a waitlist                      |
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

Only a `SUPER_ADMIN` can create org admins or assign a user to an
organisation; anyone else gets `403`. An unknown user or organisation returns
`404`, a taken email `409`, and a `SUPER_ADMIN` can't be turned into an org
admin (`409`).

## What's next

- Admin screens in the React frontend
- Email/SMS booking confirmations (including "you got a slot from the waitlist")
- Recurring bookings for regular club sessions

## Data model

```
Organisation 1---* Venue 1---* Court 1---* TimeSlot 1---0..1 Booking *---1 AppUser
                                               TimeSlot 1---* WaitlistEntry *---1 AppUser
```
