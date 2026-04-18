# MiniBank - Digital Wallet System

A microservices-based digital wallet application built with **Spring Boot 3.x**, following **TDD methodology** and **Saga Pattern (Orchestration)** for distributed transactions.

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         API Gateway                              │
│                    (Spring Cloud Gateway)                        │
└─────────────────────────────────────────────────────────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        ▼                       ▼                       ▼
┌───────────────┐     ┌───────────────┐     ┌───────────────┐
│  User Service │     │Account Service│     │Transaction Svc│
│   Port: 8081  │     │   Port: 8082  │     │   Port: 8083  │
│   PostgreSQL  │     │   PostgreSQL  │     │   PostgreSQL  │
│     Redis     │     │     Redis     │     │   Outbox      │
└───────────────┘     └───────────────┘     └───────────────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              ▼
                    ┌───────────────┐
                    │ Apache Kafka  │
                    │   + Zookeeper │
                    └───────────────┘
                              │
                    ┌───────────────┐
                    │Saga Orchestr. │
                    │(Choreography) │
                    └───────────────┘
```

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| **Language** | Java 17 |
| **Framework** | Spring Boot 3.2.x |
| **Database** | PostgreSQL 15 |
| **Cache** | Redis 7 |
| **Message Broker** | Apache Kafka |
| **Distributed Tracing** | Micrometer Tracing + Zipkin |
| **Containerization** | Docker, Docker Compose |
| **Build Tool** | Maven |
| **Frontend** | React 18 (Upcoming) |

## 📦 Services

### User Service (Port: 8081)
- User registration & authentication
- JWT-based security
- Redis caching for sessions
- PostgreSQL for persistence

### Account Service (Port: 8082)
- Account management (create, activate, deactivate)
- Balance operations with optimistic locking
- Atomic balance updates
- Distributed idempotency support

### Transaction Service (Port: 8083) - *Planned*
- Money transfers between accounts
- Saga orchestration for distributed transactions
- Outbox pattern for reliable messaging

### Notification Service - *Planned*
- Email notifications
- SMS notifications
- Kafka consumer

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose

### Run Infrastructure

```bash
cd minibank
docker-compose up -d
```

This starts:
- PostgreSQL (ports 5432-5434 for each service)
- Redis (port 6379)
- Kafka (port 9092)
- Zipkin (port 9411)

### Build & Run Services

```bash
# Build all services
mvn clean install

# Run User Service
cd user-service
mvn spring-boot:run

# Run Account Service
cd account-service
mvn spring-boot:run
```

### Run Tests

```bash
# Run all tests
mvn test

# Run with coverage
mvn verify
```

## 📁 Project Structure

```
minibank/
├── docker-compose.yml          # Infrastructure containers
├── init-scripts/               # Database initialization scripts
├── user-service/               # User microservice
│   ├── src/main/java/
│   │   └── com/minibank/user/
│   │       ├── config/         # Security, Redis configs
│   │       ├── controller/     # REST controllers
│   │       ├── dto/            # Data Transfer Objects
│   │       ├── entity/         # JPA entities
│   │       ├── exception/      # Custom exceptions
│   │       ├── repository/     # Spring Data repositories
│   │       └── service/        # Business logic
│   └── src/test/               # Unit & Integration tests
├── account-service/            # Account microservice
│   └── ... (similar structure)
└── transaction-service/        # Transaction microservice (planned)
```

## 🧪 Testing Strategy

Following **TDD (Test-Driven Development)** approach:

1. **Unit Tests** - JUnit 5 + Mockito
2. **Integration Tests** - Testcontainers
3. **API Tests** - MockMvc

## 📋 Sprint Progress

| Sprint | Focus | Status |
|--------|-------|--------|
| 1 | Domain Analysis | ✅ Complete |
| 2 | Tech Stack Selection | ✅ Complete |
| 3 | Architecture Design (C4) | ✅ Complete |
| 4 | Database Design (ER) | ✅ Complete |
| 5 | Architecture Review | ✅ Complete |
| 6 | User Service Implementation | ✅ Complete |
| 7 | Account Service Implementation | 🚧 In Progress |
| 8 | Transaction Service + Saga | 📋 Planned |
| 9 | Notification Service + Kafka | 📋 Planned |
| 10 | API Gateway + Frontend | 📋 Planned |

## 🔐 Security Features

- JWT-based authentication
- Password hashing with BCrypt
- Role-based access control
- Distributed idempotency for duplicate request prevention
- Optimistic locking for concurrent balance updates

## 📝 Patterns Implemented

- **Saga Pattern (Orchestration)** - Distributed transactions
- **Outbox Pattern** - Reliable event publishing
- **Idempotency Key Pattern** - Duplicate request prevention
- **Database per Service** - Data isolation
- **Optimistic Locking** - Concurrency control

## 👨‍💻 Author

MiniBank Project - Java Developer Position Preparation

## 📄 License

This project is for educational and interview preparation purposes.
