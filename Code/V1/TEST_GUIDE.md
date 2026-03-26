# Shop Rental System - Test Cases Guide

This document provides instructions for running the comprehensive test suite for the Shop Rental System V1.

## Test Coverage

The test suite includes 8 test classes covering all major functionalities:

### 1. **UserTest** (`src/test/java/com/shoprentals/v1/model/UserTest.java`)
- Tests user authentication (login/logout)
- Tests password management
- Tests all user types: Tenant, ContractManager, Accounting, SystemAdmin
- 10 test cases

### 2. **ShopTest** (`src/test/java/com/shoprentals/v1/model/ShopTest.java`)
- Tests shop creation and initialization
- Tests shop status management (OPEN, RENTED, CLOSED)
- Tests tenant assignment to shops
- 9 test cases

### 3. **PaymentTest** (`src/test/java/com/shoprentals/v1/model/PaymentTest.java`)
- Tests payment creation and status management
- Tests sales record creation
- Tests commission calculation on sales records
- 8 test cases

### 4. **LeaseContractTest** (`src/test/java/com/shoprentals/v1/model/LeaseContractTest.java`)
- Tests lease contract creation and lifecycle
- Tests contract state transitions (DRAFT → PENDING_APPROVAL → ACTIVE → TERMINATED)
- Tests rent and commission calculations
- 8 test cases

### 5. **ShopRentalServiceTest** (`src/test/java/com/shoprentals/v1/service/ShopRentalServiceTest.java`)
- Tests core business logic for shop rental operations
- Tests lease request creation
- Tests contract approval workflow
- Tests monthly sales submission and payment generation
- 11 test cases

### 6. **FactoryPatternTest** (`src/test/java/com/shoprentals/v1/pattern/FactoryPatternTest.java`)
- Tests Factory Pattern for creating lease contracts
- Tests StandardLeaseFactory and ShortTermLeaseFactory
- Tests contract creation with different parameters
- 8 test cases

### 7. **StrategyPatternTest** (`src/test/java/com/shoprentals/v1/pattern/StrategyPatternTest.java`)
- Tests Strategy Pattern for commission calculations
- Tests StandardCommissionStrategy and ProgressiveCommissionStrategy
- Tests different sales scenarios
- 8 test cases

### 8. **StatePatternTest** (`src/test/java/com/shoprentals/v1/pattern/StatePatternTest.java`)
- Tests State Pattern for contract lifecycle management
- Tests all state transitions
- Tests invalid state transition handling
- 9 test cases

### 9. **ShopRentalSystemIntegrationTest** (`src/test/java/com/shoprentals/v1/ShopRentalSystemIntegrationTest.java`)
- Integration tests for complete workflows
- Tests user authentication across all roles
- Tests payment lifecycle and status transitions
- Tests shop status management
- 9 test cases

**Total: 80+ test cases**

---

## Prerequisites

Ensure Maven is installed on your system. Check with:
```
mvn --version
```

---

## Method 1: Running Tests from Command Line (Windows PowerShell)

### Run All Tests
```powershell
cd Code\V1
mvn clean test
```

### Run Specific Test Class
```powershell
mvn test -Dtest=UserTest
mvn test -Dtest=ShopTest
mvn test -Dtest=LeaseContractTest
mvn test -Dtest=ShopRentalServiceTest
mvn test -Dtest=FactoryPatternTest
mvn test -Dtest=StrategyPatternTest
mvn test -Dtest=StatePatternTest
mvn test -Dtest=ShopRentalSystemIntegrationTest
```

### Run Specific Test Method
```powershell
mvn test -Dtest=UserTest#testTenantLoginSuccess
mvn test -Dtest=ShopTest#testShopCreation
mvn test -Dtest=PaymentTest#testPaymentConfirmation
```

### Run with Detailed Output
```powershell
mvn test -X
```

### View Test Report
After running tests, view the report at:
```
target/surefire-reports/index.html
```

---

## Method 2: Running Tests in Eclipse IDE

### Step 1: Import Project
1. Open Eclipse
2. File → Import → Maven → Existing Maven Projects
3. Select the `Code\V1` folder
4. Click Finish

### Step 2: Run Tests
Option A - Run All Tests:
1. Right-click on `src/test/java` folder
2. Run As → Maven Test

Option B - Run Single Test Class:
1. Right-click on the test class (e.g., `UserTest.java`)
2. Run As → JUnit Test

Option C - Run via Maven Eclipse Plugin:
1. Right-click on project
2. Run As → Maven Test

### Step 3: View Test Results
- View results in the JUnit panel at the bottom of the Eclipse window
- Green bar = all tests passed
- Red bar = some tests failed
- Click on individual tests to see details

---

## Method 3: Running Tests in IntelliJ IDEA

### Step 1: Open Project
1. Open IntelliJ IDEA
2. File → Open
3. Select `Code\V1` folder
4. Click OK

### Step 2: Run Tests
Option A - Run All Tests:
1. Right-click on `src/test/java` folder in Project Explorer
2. Select "Run 'Tests in 'test''" or press Ctrl+Shift+F10

Option B - Run Single Test Class:
1. Open the test file (e.g., `UserTest.java`)
2. Click the green play button next to the class name or press Ctrl+Shift+F10
3. Or right-click on class name → Run 'ClassName'

Option C - Run Specific Test Method:
1. Click the green play button next to a test method name
2. Or right-click on method → Run 'methodName'

Option D - Run via Maven:
1. View → Tool Windows → Maven
2. Expand your project → Lifecycle
3. Double-click "test" to run all tests

### Step 3: View Test Results
- View results in the Run tool window (bottom of IDE)
- Green checkmarks = tests passed
- Red X marks = tests failed
- Click on failed test to see error details

---

## Running Specific Test Categories

### Test User Authentication
```powershell
mvn test -Dtest=UserTest
```

### Test Model Entities
```powershell
mvn test -Dtest=ShopTest,PaymentTest,LeaseContractTest
```

### Test Business Services
```powershell
mvn test -Dtest=ShopRentalServiceTest
```

### Test Design Patterns
```powershell
mvn test -Dtest=FactoryPatternTest,StrategyPatternTest,StatePatternTest
```

### Test System Integration
```powershell
mvn test -Dtest=ShopRentalSystemIntegrationTest
```

---

## Test Output Examples

### Successful Test Run
```
[INFO] Tests run: 80, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Failed Test Run
```
[INFO] Tests run: 80, Failures: 2, Errors: 1, Skipped: 0
[INFO] BUILD FAILURE
```

The detailed failure report will show which tests failed and why.

---

## Troubleshooting

### Issue: Tests fail with "Cannot find class"
**Solution:** Rebuild the project
```powershell
mvn clean compile
mvn test
```

### Issue: "No tests found"
**Solution:** Ensure test files are in `src/test/java` directory and follow naming convention (ends with `Test.java`)

### Issue: JUnit not found in IDE
**Solution:** 
- Eclipse: Project → Properties → Java Build Path → Add JUnit library
- IntelliJ: File → Project Structure → Libraries → Add JUnit 5 library

### Issue: Maven not recognized
**Solution:** Add Maven to PATH or specify full Maven path:
```powershell
C:\path\to\maven\bin\mvn test
```

---

## Test Naming Convention

All test classes follow the pattern: `[ClassName]Test.java`
All test methods start with `test`: `testUserCreation()`, `testShopStatus()`, etc.

Each test is annotated with `@DisplayName` for readable test names in IDE output.

---

## Continuous Integration

To run tests automatically during Maven build:
```powershell
mvn clean install
```

This will:
1. Clean previous builds
2. Compile source code
3. Run all tests
4. Skip deployment if tests fail

---

## Test Statistics

- Total Test Classes: 9
- Total Test Methods: 80+
- Coverage Areas:
  - User Management: 10 tests
  - Shop Management: 9 tests
  - Payment Processing: 8 tests
  - Lease Contracts: 8 tests
  - Business Logic: 11 tests
  - Design Patterns: 25 tests
  - System Integration: 9 tests

---

Generated: March 26, 2026
Version: 1.0
