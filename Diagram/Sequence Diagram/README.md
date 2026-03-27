# Sequence Diagram Source (V1)

This folder contains sequence diagram source derived from:
- `Code/V1/src/main/java/com/shoprentals/v1/ShopRentalApplication.java`
- related classes in `model`, `service`, and `pattern` packages.

## Directory Structure

We have 6 separate use-case scenarios broken down to match the project requirements:

- **v2_fixed/SD01_Login_RoleRouting_Fixed.puml**: Login and character/role menu routing.
- **v2_fixed/SD02_Tenant_RequestLease_Fixed.puml**: Tenant requesting a new lease.
- **v2_fixed/SD03_ContractManager_ApproveContract_Fixed.puml**: Manager contract approval logic.
- **v2_fixed/SD04_Tenant_UploadMonthlySales_Fixed.puml**: Monthly sales upload and payment calculation.
- **v2_fixed/SD05_Accounting_ConfirmPayment_Fixed.puml**: Accounting completing a payment.
- **v2_fixed/SD06_Admin_ChangeUserPermission_Fixed.puml**: Admin configuring user permissions.

> Note: The files in the root of this folder are older drafts. Please refer to `v2_fixed` for the most accurate MVC stereotyped diagrams with properly scoped lifelines.

## Visual Paradigm note
Visual Paradigm `.vpp` is a proprietary project database format (SQLite-based). It is not safe to hand-author directly.

