# Updated Class Diagram – Design Patterns Applied

> CS3342 Group Assignment: Commercial Shop Rental Management System
> Updated design incorporating six GoF design patterns.

---

## 1. Core Domain Model (unchanged entities)

```
Admin            Building          ShopType
Merchant         Shop              LeaseContract
PaymentRecord
```

---

## 2. Design Patterns Added

### 2.1 Strategy – Rent Calculation

```
«interface»
RentCalculationStrategy
  + calculate(shop: Shop, shopType: ShopType): double
      ↑               ↑               ↑
StandardRentStrategy  PromotionalRentStrategy  PremiumRentStrategy
  (baseRent × ratio)  (standard × (1-discount)) (standard × (1+surcharge))
```

Used by:
- `LeaseContractFactory.create()` – injects strategy at contract creation time
- `ShopRentalService.adjustRent()` – Accounting adjusts rent by store status

---

### 2.2 State – Lease Contract Lifecycle

```
«interface»
ContractState
  + submit(contract)
  + approve(contract)
  + terminate(contract)
  + expire(contract)
  + getStateName(): String
      ↑         ↑           ↑             ↑         ↑
DraftState  PendingApprovalState  ActiveState  TerminatedState  ExpiredState
```

`LeaseContract.contractState` (String) is hydrated to a `ContractState` via `ContractStateFactory`.

Transitions:
```
DRAFT → submit() → PENDING_APPROVAL → approve() → ACTIVE → terminate() → TERMINATED
                                                          → expire()    → EXPIRED
PENDING_APPROVAL → terminate() → TERMINATED
```

---

### 2.3 Observer – Event Notification

```
«interface»
RentalEventListener
  + onEvent(event: RentalEvent)
            ↑
AuditLogListener   (extendable to: EmailNotifier, SMSNotifier)

RentalEventBus
  - listeners: List<RentalEventListener>
  + register(listener)
  + publish(event: RentalEvent)

RentalEvent
  - type: Type  {CONTRACT_SUBMITTED, CONTRACT_APPROVED, CONTRACT_TERMINATED,
                 PAYMENT_CONFIRMED, PAYMENT_REFUNDED, MERCHANT_APPROVED, MERCHANT_REJECTED}
  - entityId: String
  - message: String
```

---

### 2.4 Factory Method – Contract Creation

```
«abstract»
LeaseContractFactory
  + create(merchantId, shop, shopType, start, end, strategy): LeaseContract  ← template method
  # createContract(): LeaseContract   ← factory method (abstract)
  # depositMonths(): int
  # configure(contract, shop, shopType)   ← hook
        ↑                        ↑
StandardLeaseFactory     ShortTermLeaseFactory
  (2-month deposit)        (1-month deposit)
```

---

### 2.5 Singleton – Data Repository

```
DataRepository   «Spring @Repository – effective singleton»
  - buildings    : Map<String, Building>
  - shopTypes    : Map<String, ShopType>
  - shops        : Map<String, Shop>
  - merchants    : Map<String, Merchant>
  - admins       : Map<String, Admin>
  - contracts    : Map<String, LeaseContract>
  - payments     : Map<String, PaymentRecord>
  + load(file)
  + save()
  + seedDemoData()
```

---

### 2.6 Facade – Simplified API

```
ShopRentalFacade
  - service: ShopRentalService
  + adminLogin / merchantLogin
  + getFloorPlan(buildingId): List<Shop>
  + updateShopFromGrid(shop): Shop
  + applyForLease / submitLease / approveLease / terminateLease
  + approveMerchant
  + adjustRent(contractId, strategy)
  + makePayment / confirmPayment / refundPayment
  + getReport
```

`ShopRentalFacade` hides `ShopRentalService` + factory/strategy/state logic from REST controllers.

---

## 3. Full Class Relationship Overview

```
Admin ──manages──► Building ──has──► Shop ──classifiedBy──► ShopType
  │                                    │
  │                              LeaseContractFactory
  │                                    │ creates
  │                                    ▼
  └─approves──► LeaseContract ◄──────── Merchant
                     │   uses State pattern (DraftState etc.)
                     │   uses RentCalculationStrategy
                     │
                     ▼
               PaymentRecord
                     │
                     └─ published via RentalEventBus → AuditLogListener
```

---

## 4. Grid / Floor Plan Feature

`Shop` carries:
- `gridRow` / `gridCol` – position on the floor plan grid
- `imageUrl` – optional photo

`Building` carries:
- `gridRows` / `gridCols` – grid dimensions

Manager edits shop properties (including grid position) via the `/shops/{id}` PUT endpoint, reflected live in the browser floor-plan grid.

---

## 5. REST API Summary

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/admin` | Admin login |
| POST | `/api/auth/merchant` | Merchant login |
| GET/POST | `/api/buildings` | List / create buildings |
| GET | `/api/buildings/{id}/floor-plan` | Floor plan grid data |
| GET/PUT/POST | `/api/shops` | List / create / update shops |
| POST | `/api/merchants/register` | Merchant self-registration |
| PUT | `/api/merchants/{id}/approve` | Admin approve/reject |
| POST | `/api/contracts` | Create lease contract |
| PUT | `/api/contracts/{id}/submit` | Tenant submits contract |
| PUT | `/api/contracts/{id}/approve` | Admin approves |
| PUT | `/api/contracts/{id}/terminate` | Terminate contract |
| PUT | `/api/contracts/{id}/adjust-rent` | Accounting adjusts rent |
| POST/GET | `/api/payments` | Create / list payments |
| PUT | `/api/payments/{id}/confirm` | Confirm payment |
| PUT | `/api/payments/{id}/refund` | Refund payment |
| GET | `/api/reports` | Occupancy & revenue report |
| GET | `/api/audit-log` | Observer audit trail |
