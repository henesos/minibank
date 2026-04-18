# MiniBank Contribution Guide

## Development Setup

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose
- Git

### Getting Started

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd minibank
   ```

2. **Start infrastructure**
   ```bash
   docker-compose up -d
   ```
   
   This starts:
   - PostgreSQL (user-db: port 5432, account-db: port 5433)
   - Redis (port 6379)
   - Kafka (ports 9092, 9093)
   - Zipkin (port 9411)

3. **Build and run services**
   ```bash
   # Build all services
   mvn clean install
   
   # Run User Service
   cd user-service
   mvn spring-boot:run
   
   # Run Account Service (in another terminal)
   cd account-service
   mvn spring-boot:run
   ```

## Project Structure

```
minibank/
├── user-service/          # User management microservice
├── account-service/       # Account & balance microservice
├── transaction-service/   # Transaction service (planned)
├── notification-service/  # Notification service (planned)
├── api-gateway/           # API Gateway (planned)
├── docker-compose.yml     # Infrastructure containers
├── init-scripts/          # Database initialization scripts
└── README.md
```

## Coding Standards

### Java
- Follow Google Java Style Guide
- Use Lombok for boilerplate reduction
- Use MapStruct for DTO mapping
- All public methods must have Javadoc

### Testing
- **TDD Approach:** Write tests first, then implementation
- Unit tests with JUnit 5 + Mockito
- Integration tests with Testcontainers
- Minimum 80% code coverage

### Commits
- Use conventional commits format:
  - `feat:` New feature
  - `fix:` Bug fix
  - `docs:` Documentation changes
  - `test:` Test additions/changes
  - `refactor:` Code refactoring
  - `chore:` Maintenance tasks

Example:
```
feat(account): add atomic balance update methods

- Add deductBalance() with balance check
- Add addBalance() for deposits
- Add comprehensive unit tests
```

## Branch Strategy

- `main` - Production-ready code
- `develop` - Development branch
- `feature/*` - New features
- `bugfix/*` - Bug fixes
- `release/*` - Release preparation

## Key Patterns

### Balance Operations (CRITICAL)
```java
// NEVER do this:
Account account = repository.findById(id);
account.setBalance(account.getBalance() - amount);
repository.save(account);

// ALWAYS do this (atomic update):
int rows = repository.deductBalance(id, amount);
if (rows == 0) {
    throw new InsufficientBalanceException();
}
```

### Cache Strategy
- User profile: Cacheable (5 min TTL)
- Balance: NEVER cached
- Session: Redis (30 min TTL)

## API Documentation

Each service exposes OpenAPI documentation:
- User Service: http://localhost:8081/swagger-ui.html
- Account Service: http://localhost:8082/swagger-ui.html

## Monitoring

- Health checks: `/actuator/health`
- Metrics: `/actuator/metrics`
- Distributed tracing: Zipkin at http://localhost:9411

## Troubleshooting

See `TROUBLESHOOTING.md` for known issues and solutions.
