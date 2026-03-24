# Shop Rental System V1 (Java)

This folder contains a clean Java implementation based on the provided use-case and class diagrams.

## Implemented classes from diagram

- User hierarchy: `User`, `SystemAdmin`, `Accounting`, `Floor`, `Tenant`, `ContractManager`
- Domain entities: `Shop`, `LeaseContract`, `SalesRecord`, `Payment`
- Supporting enums: `ShopStatus`, `ContractStatus`, `PaymentStatus`

## Design patterns included

- Factory: `LeaseContractFactory`, `StandardLeaseFactory`, `ShortTermLeaseFactory`
- State: `ContractState` + concrete state classes for contract lifecycle
- Strategy: `CommissionStrategy` with `StandardCommissionStrategy`, `ProgressiveCommissionStrategy`
- Observer: `RentalEventBus`, `RentalEventListener`, `AuditLogListener`

## Run

Open PowerShell and go to Code/V1 first.

Option 1: Run with Maven (if Maven is installed)

	mvn compile
	mvn exec:java

Option 2: Run directly with Java compiler (works on your current machine)

	if (-not (Test-Path out)) { New-Item -ItemType Directory -Path out | Out-Null }
	$sources = Get-ChildItem -Recurse -Path src\main\java -Filter *.java | ForEach-Object { $_.FullName }
	javac -d out $sources
	java -cp out com.shoprentals.v1.ShopRentalApplication

Expected output includes:

- open shops list
- audit logs for lease request/approval/monthly upload
- payment confirmation result

## Interactive Mode

After launching the program, enter username/password directly.
The system automatically opens the corresponding role menu.

- Tenant: `freshMart / pass`
- ContractManager: `cm / pass`
- Accounting: `acct / pass`
- SystemAdmin: `admin / admin123`

Each role has its own menu:

- Tenant: view open shops, create lease request, select active contract, upload monthly sales, view all state
- ContractManager: list contracts, select active contract, approve contract, update shop status
- Accounting: list payments, select active payment, confirm payment, inspect shops
- SystemAdmin: change/view user permission

Suggested full flow:

1. Login as Tenant -> view open shops -> request lease
2. Login as ContractManager -> approve contract
3. Login as Tenant -> upload monthly sales record
4. Login as Accounting -> confirm payment

## Data Persistence

- Application state is automatically saved to `Code/V1/data/app-state.properties`.
- Saved data includes: shop status, all contracts, all payments, active contract/payment selection, admin permissions, and next ID counters.
- To reset to a fresh state, delete `Code/V1/data/app-state.properties` and run again.
