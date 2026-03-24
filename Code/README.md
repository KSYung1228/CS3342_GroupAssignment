# Code – Setup & Run Guide

## Project Structure

```
Code/
├── backend/          ← Spring Boot (Java 17, Maven)
│   ├── pom.xml
│   └── src/main/java/com/shoprentals/
│       ├── ShopRentalApplication.java
│       ├── model/                  Domain entities
│       │   └── state/              State pattern classes
│       ├── pattern/
│       │   ├── strategy/           Rent calculation strategies
│       │   ├── observer/           Event bus + listeners
│       │   └── factory/            Contract factories
│       ├── repository/             JSON data store (Singleton)
│       ├── service/                Business logic
│       ├── facade/                 Facade pattern
│       └── controller/             REST controllers
└── frontend/         ← Vanilla HTML/CSS/JS
    ├── index.html
    ├── css/style.css
    └── js/app.js
```

## Running the Backend

### Prerequisites
- Java 17+
- Maven 3.8+

```bash
cd Code/backend
mvn spring-boot:run
```

Server starts on **http://localhost:8080**

On first run, a `data/shoprentals.json` file is created in the working directory with demo data.

## Running the Frontend

Open `Code/frontend/index.html` in a browser **after** the backend is running.

> If you get CORS errors, start the backend first or serve the frontend via a simple HTTP server:
> ```bash
> cd Code/frontend
> npx serve .
> ```

## Demo Login Credentials

| Role | Username / Email | Password |
|------|-----------------|----------|
| System Admin | `admin` | `admin123` |
| Accounting | `accounting` | `acc123` |
| Contract Manager | `contract` | `con123` |
| Merchant | `chan@trading.hk` | `mer123` |

## Design Patterns Used

| Pattern | Where | Purpose |
|---------|-------|---------|
| **Strategy** | `pattern/strategy/` | Pluggable rent calculation (Standard / Promotional / Premium) |
| **State** | `model/state/` | Contract lifecycle (Draft → Pending → Active → Terminated/Expired) |
| **Observer** | `pattern/observer/` | Audit log notified on every key event |
| **Factory Method** | `pattern/factory/` | Standard vs Short-Term lease creation |
| **Singleton** | `repository/DataRepository` | Single JSON data store bean |
| **Facade** | `facade/ShopRentalFacade` | Simplified controller interface |

## Floor Plan Grid

- Shops have `gridRow` / `gridCol` coordinates inside their building's grid.
- Buildings have `gridRows` × `gridCols` dimensions.
- Tenants see a colour-coded floor plan (green = available, yellow = occupied, red = maintenance).
- Admins can click any cell to edit the shop's details or add a new shop to an empty cell.
- Setting `imageUrl` on a shop shows its photo as the cell background.
