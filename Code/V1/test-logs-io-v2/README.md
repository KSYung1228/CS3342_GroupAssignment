# Test Logs with Input and Output (V2)

This folder contains newly generated CLI test logs for `ShopRentalApplication`.

## How these logs were generated

1. Run from `Code/V1`.
2. Execute `ShopRentalApplication` with scripted stdin inputs.
3. Capture full stdout output.
4. Save both user inputs and program output in each case file.

Command used:

```powershell
$inputText | .\mvnw.cmd --% -q -DskipTests compile exec:java -Dexec.mainClass=com.shoprentals.v1.ShopRentalApplication
```

## Cases

- `case1-tenant-request-lease-with-io.txt`
- `case2-contractmanager-select-then-approve-with-io.txt`
- `case3-tenant-select-contract-then-upload-sales-with-io.txt`
- `case4-accounting-select-payment-then-confirm-with-io.txt`
- `case5-admin-change-and-view-permission-with-io.txt`
- `case6-permission-not-enforced-with-io.txt`

## Error Cases

- `error1-invalid-login-with-io.txt`
- `error2-tenant-upload-without-active-contract-with-io.txt`
- `error3-accounting-confirm-without-active-payment-with-io.txt`

## Contract operation rule coverage

For contract-related operations, these cases explicitly perform selection first:

- ContractManager: option `2` (Select active contract) before option `3` (Approve active contract).
- Tenant: option `3` (Select active contract) before option `4` (Upload monthly sales record).
- Accounting payment flow: option `2` (Select active payment) before option `3` (Confirm active payment).

## Note on state

These cases are sequential and rely on persisted state in `data/app-state.properties`.
If you want to regenerate from scratch, delete that file first.

For the `error*.txt` files, each case was generated independently from a clean state
(`data/app-state.properties` removed before execution).
