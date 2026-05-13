Project1-UserAccountSystem-CS1B

Final project for **Computer Programming 2** and **Information Management**: a small web app where people can register, log in, manage their profile and password, and delete their account. Regular users live in the `users` table; admins use `admins`. Stack is plain **HTML/CSS/JS** in the browser, **PHP** on the server, **MySQL** through XAMPP.

## Group

- Cerico, Charles Lorenz Chu  
- Olivares, Dirk Dwaynne Edrick  
- Pulgan, France Reeno Ape  

## What it does

Sign up creates a row in `users` (the signup form posts JSON to `api/users.php`). Login hits `api/login.php` with a `user` or `admin` role; after that, PHP keeps you in a session. The dashboard and profile pages load the current account with `api/me.php`. Editing profile goes through `api/users.php` or `api/admins.php` (PUT/PATCH + JSON). Password changes use `api/change-password.php`; deleting your own account uses `api/delete-me.php` with your password. The admin dashboard pulls lists from `api/users.php` and `api/admins.php`.

Passwords are hashed with PHP’s `password_hash` and checked with `password_verify`. Queries go through `mysqli` with prepared statements.

## What you need installed

XAMPP (Apache + MySQL) is enough. Put this project under `htdocs`, e.g. `C:\xampp\htdocs\Project1-UserAccountSystem-CS1B`.

## Database

1. Turn on MySQL in XAMPP.  
2. In phpMyAdmin, create a database called **`accsystem_db`** (or pick another name and update `config/db.php` to match).  
3. Run or import **`database/schema_users.sql`** and **`database/schema_admins.sql`**.  
4. Add at least one admin row manually in phpMyAdmin if you don’t have another way to create admins yet.

Connection defaults live in **`config/db.php`**: host `127.0.0.1`, port `3306`, user `root`, empty password — typical for a fresh XAMPP install. Change only if yours is different.

## Running it locally

1. Apache and MySQL both running in XAMPP.  
2. Database steps above done.  
3. In the browser (adjust the path if your folder name isn’t the same):

   - Login: `http://localhost/Project1-UserAccountSystem-CS1B/login/index.html`  
   - Sign up: `http://localhost/Project1-UserAccountSystem-CS1B/signup/index.html`  

Use **`http://localhost/...`** links, not `file://`, and ideally don’t split the HTML and `api/` across different hosts/ports — the app relies on cookies/session with `fetch(..., { credentials: 'same-origin' })`. Live Server on another port can break that unless you add extra setup.

## Repo layout (quick)

- `api/` — `login.php`, `logout.php`, `me.php`, `users.php`, `admins.php`, `change-password.php`, `delete-me.php`, plus shared `bootstrap.php`  
- `classes/` — `UserRepository.php`, `AdminRepository.php`  
- `config/db.php` — MySQL connection  
- `database/` — SQL to create tables  
- `login/`, `signup/` — main entry UIs; other `.html` pages at the project root  

## API cheat sheet

Send JSON with `Content-Type: application/json` unless a specific script says otherwise.

| Endpoint | Method | Rough purpose |
|----------|--------|----------------|
| `api/login.php` | POST | `login` / `username` / `email`, `password`, `role` (`user` or `admin`). Starts session. |
| `api/logout.php` | GET | Clears session, returns `{ "ok": true }`. |
| `api/me.php` | GET | Who am I? Uses session. |
| `api/users.php` | GET, POST, PUT, PATCH, DELETE | CRUD on users; signup uses POST. |
| `api/admins.php` | GET, POST, PUT, PATCH, DELETE | CRUD on admins. |
| `api/change-password.php` | POST | `current_password`, `new_password`. |
| `api/delete-me.php` | POST | `password` — deletes the logged-in account after check. |

For field names and exact responses, the comments at the top of each file under `api/` are the source of truth.

## Before you turn it in

Export your database as `.sql` from phpMyAdmin for the zip. Grab screenshots and finish the write-up your instructor asked for. Make sure the GitHub repo is public (or whatever they required) and the link you submit matches this project.

## If something breaks

- **Database connection error** — MySQL not started, or wrong database name / user / password in `config/db.php`.  
- **Logged in but `me.php` says not authenticated** — Usually opening the site from the wrong URL (see above) or cookies blocked.  
- **404 on API** — The path after `localhost/` has to match your actual folder name under `htdocs`.

One leftover: **`admin-dashboard.html`** still has a fallback that points at `http://localhost:8080` for a profile-style request. If anything still hits that old port, switch it to the PHP side (same pattern as `me.php`) or remove it so everything goes through Apache.
