# 🥐 Bakery Management System

Hệ thống quản lý tiệm bánh — Multi-module Spring Boot project.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 25 (Temurin LTS) |
| Framework | Spring Boot 3.4.5 |
| Batch | Spring Batch 5.x |
| Security | Spring Security 6.x + JWT |
| ORM | JPA / Hibernate 6.x |
| Database | PostgreSQL 16 |
| Migration | Flyway 10.x |
| Build | Maven (Multi-module) |
| Container | Docker + Docker Compose |

## Project Structure

```
bakery-management/
├── bakery-common/          # Shared: entities, repositories, DTOs, utils
├── bakery-batch/           # Spring Batch: đọc file Excel, reconcile, báo cáo
├── bakery-api/             # REST API: CRUD, Spring Security, JWT
└── docker/
    ├── docker-compose.yml  # PostgreSQL + pgAdmin
    └── postgres/
        └── init/           # Init scripts (nếu cần)
```

## Quick Start

### 1. Start Database

```bash
cd docker
docker compose up -d
```

- PostgreSQL: `localhost:5432`
- pgAdmin:    `http://localhost:5050` (admin@bakery.com / admin123)

### 2. Build

```bash
mvn clean install -DskipTests
```

### 3. Run API

```bash
cd bakery-api
mvn spring-boot:run
```

API: `http://localhost:8080/api`  
Swagger UI: `http://localhost:8080/api/swagger-ui.html`

### 4. Run Batch

```bash
cd bakery-batch
mvn spring-boot:run
```

Batch API: `http://localhost:8081/batch`

## Database Migration

Flyway tự chạy khi application start:

| Version | File | Mô tả |
|---------|------|-------|
| V1 | `V1__init_schema.sql` | Tạo toàn bộ schema (17 bảng) |
| V2 | `V2__dummy_data.sql` | Dữ liệu mẫu để phát triển |

Migration files: `bakery-common/src/main/resources/db/migration/`

## Environment Variables

| Variable | Default | Mô tả |
|----------|---------|-------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `bakery_db` | Database name |
| `DB_USER` | `bakery` | Database user |
| `DB_PASS` | `bakery123` | Database password |
| `JWT_SECRET` | (see yml) | JWT secret key |
| `BATCH_INPUT_DIR` | `./batch-input` | Thư mục chứa file Excel đầu vào |

## Batch Input Directory Structure

```
batch-input/
├── production_request/    # File yêu cầu sản xuất (chủ tiệm)
├── production_actual/     # File thực tế sản xuất (bếp)
├── daily_inventory/       # File kiểm kê cuối ngày
└── pos_export/            # File export từ máy POS
```
