# PropCheck Backend API Documentation

This document provides a detailed overview of all the REST API endpoints currently available in the `propcheck-be` Spring Boot application.

**Base URL:** 
- Local: `http://localhost:8080`
- Production: `https://propcheck-be.onrender.com`

---

## 1. Authentication APIs
**Base Path:** `/api/auth`

### 1.1 Login
- **Method:** `POST /api/auth/login`
- **Description:** Authenticates a user and returns their profile summary. Supports both plain-text (legacy) and BCrypt hashed passwords.
- **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "password123"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "id": 1,
    "name": "John Doe",
    "email": "user@example.com",
    "role": "SALES",
    "city": null
  }
  ```

### 1.2 Change Password
- **Method:** `POST /api/auth/change-password`
- **Description:** Allows an authenticated user to change their password. The new password will be BCrypt hashed.
- **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "oldPassword": "oldPassword123",
    "newPassword": "newPassword123"
  }
  ```

---

## 2. User Management APIs (Admin Only)
**Base Path:** `/api/users`

### 2.1 List Users
- **Method:** `GET /api/users`
- **Description:** Fetch a list of all internal users. 
- **Query Params:** `role` (Optional) Filter by role (e.g., `?role=SALES` or `?role=CITY_HEAD`).

### 2.2 Create Sales User
- **Method:** `POST /api/users/sales`
- **Description:** Creates a new user with the `SALES` role.
- **Request Body:**
  ```json
  {
    "name": "Jane Sales",
    "email": "jane@example.com",
    "password": "password123"
  }
  ```

### 2.3 Create City Head User
- **Method:** `POST /api/users/cityhead`
- **Description:** Creates a new user with the `CITY_HEAD` role, assigned to a specific city.
- **Request Body:**
  ```json
  {
    "name": "Bob City",
    "email": "bob@example.com",
    "password": "password123",
    "city": "Bangalore"
  }
  ```

### 2.4 Update User
- **Method:** `PUT /api/users/{id}`
- **Description:** Update an existing user's details. If `password` is provided, it will be securely hashed with BCrypt.
- **Request Body (All fields optional):**
  ```json
  {
    "name": "Updated Name",
    "email": "updated@example.com",
    "city": "Pune",
    "password": "newpassword123"
  }
  ```

### 2.5 Delete User
- **Method:** `DELETE /api/users/{id}`
- **Description:** Permanently deletes a user from the system.

---

## 3. Leads Management APIs
**Base Path:** `/api/leads`

### 3.1 Search & List Leads
- **Method:** `GET /api/leads`
- **Description:** Fetches leads from the database. Computes the dynamic `currentBookingStatus` for the UI based on active/inactive bookings.
- **Query Params:**
  - `city`: (Optional) Filter by city. Passing empty string returns all cities.
  - `search`: (Optional) Search by customer name or email.
  - `owner`: (Optional) Filter by Zoho lead owner.
  - `bookingFilter`: (Optional, Default="ALL"). Options: `UNBOOKED`, `REPORT_SENT`, `POSTPONED_CANCELLED`, `ALL`.

### 3.2 Get Single Lead
- **Method:** `GET /api/leads/{id}`
- **Description:** Fetch complete details of a single lead by its primary key.

### 3.3 Get Customer Lead Data (Customer Login)
- **Method:** `GET /api/leads/me`
- **Description:** Used by the customer portal to fetch their specific lead data.
- **Query Params:** `email` (e.g., `?email=customer@example.com`)

### 3.4 Sync Leads Manually from Zoho
- **Method:** `POST /api/leads/sync`
- **Description:** Triggers a manual sync of "Lead Won" records from Zoho CRM.
- **Query Params:** `force` (Optional boolean). If `true`, pulls all historical leads. If false, only pulls records modified in the last 2 hours.

---

## 4. Booking & Assignment APIs
**Base Path:** `/api/bookings`

### 4.1 Create Booking
- **Method:** `POST /api/bookings`
- **Description:** Schedules an inspection. Enforces guards: blocks Refunded leads, blocks Unpaid customers, checks engineer capacity, and prevents duplicate active inspections.
- **Request Body:**
  ```json
  {
    "leadId": 1234,
    "createdByUserId": 1,
    "bookingType": "INSPECTION",
    "city": "Bangalore",
    "date": "2026-10-15",
    "slot": "MORNING",
    "slotTime": "10:30"
  }
  ```

### 4.2 Assign / Re-assign Engineer
- **Method:** `PUT /api/bookings/{id}/assign` or `PUT /api/bookings/for-lead/{leadId}/assign`
- **Description:** Allots a specific engineer to a pending booking. Changes status to `CONFIRMED`.
- **Request Body:**
  ```json
  {
    "engineerEmployeeNumber": "EMP-001"
  }
  ```

### 4.3 Reschedule Booking
- **Method:** `PUT /api/bookings/{id}/reschedule` or `PUT /api/bookings/for-lead/{leadId}/reschedule`
- **Description:** Changes the date/time of an active booking. Clears the assigned engineer and resets status to `PENDING_ENGINEER_ASSIGNMENT`.

### 4.4 Cancel Booking
- **Method:** `PUT /api/bookings/{id}/cancel` or `PUT /api/bookings/for-lead/{leadId}/cancel`
- **Description:** Cancels an active booking.

### 4.5 Lifecycle Status Updates
- **Mark Inspection Done:** `PUT /api/bookings/for-lead/{leadId}/mark-done` -> Status becomes `INSPECTION_DONE`.
- **Mark Validation Done:** `PUT /api/bookings/for-lead/{leadId}/mark-validation-done` -> Status becomes `VALIDATION_DONE`.
- **Mark Report Sent:** `PUT /api/bookings/for-lead/{leadId}/report-sent` -> Status becomes `REPORT_SENT`.

### 4.6 Get Available Calendar (Monthly View)
- **Method:** `GET /api/bookings/calendar-availability`
- **Description:** Bulk fetches available capacity for a date range (used by the Date Picker UI to gray out full days).
- **Query Params:** `city`, `startDate`, `days`, `bookingType`.

### 4.7 Get Available Engineers for a Slot
- **Method:** `GET /api/bookings/for-lead/{leadId}/available-engineers`
- **Description:** Returns a list of engineers in the city who are NOT on approved leave and DO NOT have a conflicting booking on that exact date and slot.

---

## 5. Schedule & Matrix APIs
**Base Path:** `/api/schedule`

### 5.1 Get Engineer Schedule Matrix
- **Method:** `GET /api/schedule/matrix`
- **Description:** Returns a structured grid of all engineers in a city, their leave status, and their assigned bookings for a specific date (used by City Heads to manage daily operations).
- **Query Params:** `city`, `date`

---

## 6. Keka Integrations (HR & Leaves)
**Base Path:** `/api/keka`

### 6.1 Manual Sync All
- **Method:** `POST /api/keka/sync`
- **Description:** Triggers a full sync of the employee directory and approved leaves from the Keka HR API. Automatically flags any `CONFIRMED` bookings that now conflict with a newly approved leave.

---

## 7. Webhooks
**Base Path:** `/api/webhooks`

### 7.1 Zoho CRM Lead Update
- **Method:** `POST /api/webhooks/zoho/lead-update`
- **Description:** The target URL for Zoho CRM's outbound webhook workflow. When a lead is edited in Zoho, Zoho hits this endpoint with a generic JSON payload. The backend extracts the ID, fetches the fresh record directly from Zoho CRM, and upserts it locally.

---

## 8. Health Checks
**Base Path:** `/api`

### 8.1 Basic Health Check
- **Method:** `GET /api/health`
- **Description:** A lightweight, unauthenticated endpoint that returns `{"status": "ok"}`. Used for Render uptime monitoring (e.g., BetterStack) to prevent the free tier from sleeping.
