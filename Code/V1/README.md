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

You can also type `register` at login prompt to create a new tenant account.

- Tenant: `freshMart / pass`
- ContractManager: `cm / pass`
- Accounting: `acct / pass`
- SystemAdmin: `admin / admin123`

Each role has its own menu:

- Tenant: view open shops, create lease request, select active contract, upload monthly sales, view all state
- ContractManager: list contracts, select active contract, approve contract, update shop status
- Accounting: list payments, select active payment, confirm payment, inspect shops
- SystemAdmin: user create/update/delete/list, change/view permission, shop create/update/delete

Suggested full flow:

1. Login as Tenant -> view open shops -> request lease
2. Login as ContractManager -> approve contract
3. Login as Tenant -> upload monthly sales record
4. Login as Accounting -> confirm payment

## Data Persistence

- Application state is automatically saved to `Code/V1/data/app-state.properties`.
- Saved data includes: shop status, all contracts, all payments, active contract/payment selection, admin permissions, and next ID counters.
- Saved data also includes all user accounts (username, role, password) and user id counter.
- To reset to a fresh state, delete `Code/V1/data/app-state.properties` and run again.

## HTML UI (V1 Folder)

An HTML frontend is available at `Code/V1/frontend/index.html`.

This UI is based on the V1 workflow and includes role-based screens for:

- Tenant
- ContractManager
- Accounting
- SystemAdmin

### Open the UI

1) Start the Java API server from `Code/V1` first:

	PowerShell:
	.\mvnw.cmd --% -DskipTests compile exec:java -Dexec.mainClass=com.shoprentals.v1.api.V1ApiServerApplication

	Command Prompt (cmd.exe):
	mvnw.cmd -DskipTests compile exec:java -Dexec.mainClass=com.shoprentals.v1.api.V1ApiServerApplication

2) Open the UI in your browser:

	http://localhost:8080

You can still open `Code/V1/frontend/index.html` directly if needed.

### Demo Accounts

- `admin / admin123`
- `freshMart / pass`
- `cm / pass`
- `acct / pass`

### Notes

- The frontend now calls the V1 Java API at `http://localhost:8080/api`.
- Use the **Reset Demo Data** button in the UI header to reset backend in-memory demo state.
- API mode now auto-loads `Code/V1/data/app-state.properties` on startup and auto-saves on each data-changing API operation.
