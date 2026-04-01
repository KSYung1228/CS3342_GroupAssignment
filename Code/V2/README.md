# Shop Rental System V2

V2 extends V1 with an **interactive mall floor plan** — tenants click a shop cell to request a lease, and contract managers can approve or terminate directly from the map.

---

## What's New in V2

| Feature | V1 | V2 |
|---------|----|----|
| Interactive floor plan | No | Yes — click to select shops |
| Shop grid coordinates | No | Yes — posX/posY/width/height |
| Request lease from map | No | Yes — tenant clicks available shop |
| Approve/terminate from map | No | Yes — manager acts on map |
| Terminate contract API | No | Yes — `/manager/terminate-contract` |
| Language | English | English |

---

## Design Patterns

- **Factory** — `StandardLeaseFactory` (8% commission), `ShortTermLeaseFactory` (12%, 90% rent)
- **State** — Contract lifecycle: DRAFT → PENDING_APPROVAL → ACTIVE → (TERMINATED | EXPIRED)
- **Strategy** — `StandardCommissionStrategy`, `ProgressiveCommissionStrategy` (tiered rates)
- **Observer** — `RentalEventBus` + `AuditLogListener` for audit logging

---

## Default Accounts

| Role | Username | Password |
|------|----------|----------|
| System Admin | admin | admin123 |
| Tenant | freshMart | pass |
| Contract Manager | cm | pass |
| Accounting | acct | pass |

---

## Running the System

### Web UI (Recommended)

**Step 1 — Start the Java API server** from `Code\V2`:

PowerShell:
```powershell
.\mvnw.cmd --% -DskipTests compile exec:java -Dexec.mainClass=com.shoprentals.v2.api.V2ApiServerApplication
```

CMD:
```cmd
mvnw.cmd -DskipTests compile exec:java -Dexec.mainClass=com.shoprentals.v2.api.V2ApiServerApplication
```

**Step 2 — Open browser:**
```
http://localhost:8080
```

### CLI Mode

PowerShell:
```powershell
.\mvnw.cmd --% -DskipTests compile exec:java -Dexec.mainClass=com.shoprentals.v2.ShopRentalApplication
```

CMD:
```cmd
mvnw.cmd -DskipTests compile exec:java -Dexec.mainClass=com.shoprentals.v2.ShopRentalApplication
```

### Run Tests

```powershell
.\mvnw.cmd test
```

---

## Web UI Workflow

### Tenant
1. Login → **Floor Plan** tab
2. Click a green (available) shop cell
3. Side panel shows shop details — click **Request Lease**
4. Enter base rent and submit
5. Go to **My Contracts** → click **Set Active** on the approved contract
6. Go to **Upload Sales** → submit monthly sales record

### Contract Manager
1. Login → **Floor Plan** tab
2. Click a shop with a pending contract
3. Side panel shows **Approve Contract** button
4. Or use **Contracts** tab for bulk actions

### Accounting
1. **Payments** tab — click **Confirm** on unverified payments
2. **Inspection** tab — view all shop statuses and tenants

### System Admin
1. **Users** tab — create/delete users, change passwords, set permissions
2. **Shops** tab — view all shops and floor plan positions

---

## CLI Workflow

1. Login as Tenant (freshMart/pass) → view open shops → request lease
2. Login as Contract Manager (cm/pass) → select contract → approve
3. Login as Tenant → select active contract → upload monthly sales
4. Login as Accounting (acct/pass) → select payment → confirm

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/auth/login | Login |
| POST | /api/auth/register | Register tenant |
| GET  | /api/state | Full application state |
| POST | /api/tenant/request-lease | Request lease |
| POST | /api/tenant/active-contract | Set active contract |
| POST | /api/tenant/upload-sales | Upload monthly sales |
| POST | /api/manager/approve-contract | Approve contract |
| POST | /api/manager/terminate-contract | Terminate contract |
| POST | /api/accounting/confirm-payment | Confirm payment |
| GET/POST | /api/shops | List / create shops |
| PUT/DELETE | /api/shops/{id} | Update / delete shop |
| GET/POST | /api/admin/users | List / create users |
| PUT | /api/admin/users/{username}/password | Change password |
| DELETE | /api/admin/users/{username} | Delete user |
| PUT | /api/admin/permissions/{userId} | Set permission |
| POST | /api/reset | Reset demo data |

---

## Data Persistence

State is auto-saved to `Code/V2/data/app-state.properties` after every change.

To reset: delete that file and restart, or click **Reset Demo Data** in the Web UI.

---

## Test Coverage

40 test cases across:
- User authentication (8)
- Shop management with floor plan coordinates (4)
- Contract lifecycle / state transitions (9)
- Commission strategies (4)
- Factory pattern (2)
- Observer pattern (2)
- Service layer (4)
- Payment processing (3)
- Accounting business logic (2)
- Contract manager logic (1)
- Integration test (1)
