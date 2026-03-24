# CS3342 Group Assignment: Commercial Shop Rental Management System

## Project Overview
This project is a **commercial shop rental management system** designed for the Hong Kong real estate context. It streamlines the entire lifecycle of shop leasing, from building and shop management to merchant registration, contract signing, and payment tracking.

---

## Key Features

### 1. Building Management
- Store and update building information (ID, name, address, total floors, status).
- Define allowed shop types (e.g., "Studio", "F&B", "Retail") for each building.
- Validate shop types against building restrictions before adding new shops.

### 2. Shop Management
- Manage shop details (ID, number, area, floor, rent, location, compliance status).
- Track utility limits and facility requirements for each shop.
- Check if a shop's facilities match its assigned shop type requirements.
- Verify utility compliance to avoid regulatory violations.

### 3. Shop Type Management
- Configure shop types with rent ratios, restrictions (e.g., "no open flame"), and facility requirements (e.g., "ventilation system").
- Update rent ratios and restrictions dynamically.
- Check if a shop meets the restrictions of its assigned type.

### 4. Merchant Management
- Allow merchants to register and verify their information.
- Enable merchants to apply for shop leases and view their lease records.
- Admin approval for merchant registration.

### 5. Lease Contract Management
- Create, sign, renew, and terminate lease contracts.
- Calculate rent based on shop area and type rent ratio.
- Admin approval for lease contracts.

### 6. Payment Management
- Track payment records (amount, time, method, status) linked to contracts.
- Support payment creation, confirmation, and refund operations.

### 7. Admin Dashboard
- Approve merchants and lease contracts.
- Manage buildings, shops, and shop types.
- Generate reports for operational insights.

---

## System Architecture

### Core Entities & Relationships
The system is built around the following core entities, as defined in our UML class diagram:

| Entity          | Description                                                  |
| --------------- | ------------------------------------------------------------ |
| `Admin`         | Manages the system, approves requests, and generates reports. |
| `Building`      | Represents a commercial building with allowed shop types and restrictions. |
| `Shop`          | Represents a shop within a building, with specific utility and compliance details. |
| `ShopType`      | Defines the type of a shop, including rent ratio and restrictions. |
| `Merchant`      | Represents a business entity applying for shop leases.       |
| `LeaseContract` | Represents a lease agreement between a merchant and a shop.  |
| `PaymentRecord` | Tracks financial transactions related to a lease contract.   |

---

## Technology Stack
- **Backend**: [Java]
- **UML Design**: [Visual Paradigm]

---

## Google Document
https://docs.google.com/document/d/1oIB0DIR1F6UC53HkZp9HEkb2BmRGBoxC9GXrg91q5r4/edit?usp=sharing

## License
This project is for educational purposes only as part of the CS3342 course..

