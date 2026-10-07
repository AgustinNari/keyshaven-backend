# KeysHaven — Backend

Backend API for KeysHaven, a digital-key marketplace developed as a team project.

The application manages authentication, users, products, digital-key inventory, discounts and coupons, orders, and reviews.

The frontend is available in [keyshaven-frontend](https://github.com/AgustinNari/keyshaven-frontend).

## Tech Stack

- Java 17
- Spring Boot 3.5
- Spring Security
- JWT
- JPA / Hibernate
- MySQL
- Maven

## Local Setup

Requirements:

- JDK 17+
- MySQL
- Internet access for Maven Wrapper dependency downloads

Create a local database and a user with permissions over it.

Use a separate database for integration tests.

The `.env.example` file documents the required variables. Spring Boot does **not** automatically load that file, so the variables must be exported before starting the application.

PowerShell example:

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/marketplace?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'
$env:DB_USERNAME='keyshaven_local'
$env:DB_PASSWORD='replace_with_local_password'
$env:JWT_SECRET=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
$env:CORS_ALLOWED_ORIGINS='http://localhost:5173'

cd marketplace
.\mvnw.cmd spring-boot:run
```

On Linux/macOS:

```bash
export DB_URL='jdbc:mysql://localhost:3306/marketplace?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'
export DB_USERNAME='keyshaven_local'
export DB_PASSWORD='<local-password>'
export JWT_SECRET='<jwt-secret>'

cd marketplace
./mvnw spring-boot:run
```

The default backend port is:

```text
4002
```

Optional configuration includes:

- `SERVER_PORT`
- `JWT_EXPIRATION_MS`
- `DDL_AUTO`
- `CORS_ALLOWED_ORIGINS`

`DDL_AUTO=update` is intended for local development and does not replace a database migration strategy.

## Testing

```bash
./mvnw clean verify
```

Integration tests require the same database variables configured against an isolated test database.

## Security Notes

Public registration supports the `BUYER` and `SELLER` roles.

`ADMIN` accounts are not publicly registered and must be provisioned separately by an authorized user.

Real credentials should never be committed to the repository.

## Payment Flow

The payment flow is simulated and does not process real money or transactions.
