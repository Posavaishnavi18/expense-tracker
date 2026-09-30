# Expense Tracker (Servlets + JDBC + MySQL + HTML/CSS/JS)

Steps 1-3: database + connection pool, register/login/logout with sessions, expense and category CRUD per user.

## Run it in Eclipse
1. Run `sql/schema.sql` in MySQL Workbench (creates the `expense_tracker_web` database).
2. Edit `src/main/resources/db.properties` and put your MySQL password in `db.password`.
3. File > Import > Maven > Existing Maven Projects > pick this folder.
   (Needs "Eclipse IDE for Enterprise Java and Web Developers" and a Tomcat 10.1 or 11 server, Java 17+.)
4. Right-click the project > Run As > Run on Server > choose Tomcat.
5. Open the URL Eclipse shows, e.g. http://localhost:8080/expense-tracker/ , and sign up.

## API (all JSON, need the session cookie; non-GET calls need header X-Requested-With)
POST /api/auth/register | /login | /logout   GET /api/auth/me
GET/POST /api/expenses  (GET accepts ?categoryId=)   PUT/DELETE /api/expenses/{id}
GET/POST /api/categories   PUT/DELETE /api/categories/{id}
