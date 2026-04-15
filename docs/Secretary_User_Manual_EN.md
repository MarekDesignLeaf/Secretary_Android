# Secretary - Complete User Manual

## 1. What Secretary does
Secretary is a mobile CRM and operations assistant for a small business. It combines clients, jobs, tasks, communication, quotes, invoices, work reports, calendar planning, voice control, and plant analysis in one app.

Core principle:
- shared business data lives on the server
- each signed-in person works through a backend user account and role
- system language is local per signed-in user
- customer language is a company setting and only an administrator can change it

## 2. First launch

### Sign-in
- Sign in with email and password.
- If credentials are already saved and the device supports biometrics, fingerprint sign-in is available.
- After the first successful login, credentials can be reused for biometric login on that device.

### Company onboarding
The first-run setup collects:
1. company name and legal form
2. industry group and specialization
3. internal language mode and customer language mode
4. default internal and customer language
5. workspace mode

The result is stored on the server as company configuration.

## 3. Main navigation
The app has 5 primary sections:
- `Home`
- `CRM`
- `Tasks`
- `Calendar`
- `Settings`

## 4. Home
The Home screen is the operational dashboard. It typically shows:
- urgent tasks
- active tasks
- active jobs
- new leads
- CRM client counts
- items waiting for client, material, or payment

Use it as the starting point for the day, then move into CRM, Tasks, or Calendar for execution.

## 5. CRM - module overview
CRM contains these tabs:
- `Today`
- `Clients`
- `Jobs`
- `Tasks`
- `Leads`
- `Quotes`
- `Invoices`
- `Work Reports`
- `Contacts`
- `Plants`
- `Communications`

### How the modules connect
- a phone contact can be marked as a client and synced into shared CRM
- a client can have jobs, notes, communication, properties, and individual service rates
- a lead can be converted into a client or directly into a job
- an approved quote can create a job
- a work report can create an invoice
- planned jobs and tasks feed the shared calendar
- communication, notes, photos, and audit logs are linked to business entities

## 6. Clients

### Client search
Client search works by:
- name
- client code
- email
- phone

### Manual client creation
Open CRM -> `Clients` and use `+`.

Available fields include:
- contact name
- company
- phone and secondary phone
- email and secondary email
- registration number and VAT number
- website
- billing address
- city, postcode, country
- client type
- preferred contact channel

### Set clients from phone contacts
The `Set clients` mode loads contacts from the phone:
- checked contacts become CRM clients on the server
- unchecked contacts are not kept as synced CRM clients
- the server tries to merge duplicates by phone and email
- if multiple users sync overlapping contacts, data is merged into one shared client

This is the recommended way to build a shared client database from several devices.

### Individual client service rates
Each client can have their own service rates. These override global company service rates from Settings and should be used when a specific customer has a special hourly price list.

## 7. Jobs

### Creating a job
A job can be created manually in CRM or generated from another flow:
- lead conversion
- approved quote

A job supports:
- linked client
- dates
- status
- planning note
- handover note
- assignment to people

### Planning and handover
Jobs can be planned with:
- planned start
- planned end
- handover note

This is used for internal delegation and calendar visibility.

### Job stages
Each job keeps structured operational records:
- `Before start`
- `Progress`
- `Completion`
- `Complications`
- `Audit log`

Each stage can contain:
- notes
- photos

The photo action opens camera or gallery and stores the images on the server under the specific job and stage.

### Audit log
The audit log records important actions such as:
- status changes
- note additions
- operational updates

## 8. Tasks
Tasks exist as standalone work items or can be linked to a client or job.

A task can contain:
- title
- type
- description
- priority
- deadline
- planned start and end
- assigned person
- result
- completion state

Typical workflow:
1. create task
2. assign it
3. set priority and deadline
4. record result and optional photo
5. complete it

## 9. Leads
A lead is a pre-sales record.

It includes:
- contact name
- source
- email
- phone
- inquiry description

It can be converted:
- to a client
- to a job

## 10. Quotes
Quotes bridge inquiry and delivery.

They support:
- creating a quote for a client
- adding quote items
- calculating totals
- approval

Approving a quote can automatically create a job.

## 11. Invoices
Invoices can be:
- created manually
- created from a single work report
- created in batch from multiple work reports

Invoice sending is prepared through:
- SMS
- WhatsApp
- email

## 12. Work reports
Work reports capture completed work and provide billing input.

A report contains:
- client
- date
- hours worked
- price
- notes

From a report you can create an invoice individually or in batch.

## 13. Communications
Communication records are stored manually in CRM and linked to a client or job.

Supported types:
- phone
- email
- SMS
- WhatsApp
- Checkatrade
- in person

Each record has a subject, message, and direction:
- incoming
- outgoing

## 14. Shared contacts
The `Contacts` tab is a shared business directory outside the client list.

Built-in sections:
- employees
- subcontractors
- material suppliers
- tool and vehicle rentals

You can also create custom sections. Contacts can be:
- created manually
- imported from phone contacts
- saved to the server
- shared and edited by other users

## 15. Plants
The `Plants` tab has two modes:
- `Identify plant`
- `Check disease`

### Plant identification
Use at least 1 photo. Recommended shots:
- whole plant
- leaf detail
- flower, fruit, or bark

Output:
- best match
- confidence
- alternative matches
- short description and care requirements

### Plant disease assessment
Use at least 1 clear photo of the damaged area. Recommended shots:
- damaged part
- leaf close-up
- whole plant or stem context

Output:
- most likely issue
- confidence
- short diagnosis
- recommended treatment
- prevention
- alternative possible issues

## 16. Calendar
The Calendar combines shared planning from the server with local device calendar sync.

It shows:
- planned jobs
- planned tasks
- assigned work items

Visibility logic:
- assigned people see items as actionable reminders
- other users see them as planned entries with assignment visibility

## 17. Voice assistant

### Basics
Voice control uses the wake word configured in Settings.

Available options:
- wake word detection on or off
- custom wake word
- speech rate
- pitch
- silence length
- optional restriction to working hours only

### Types of voice flows
1. direct command
2. guided multi-step voice session
3. voice-guided photo capture

### Example direct commands
- `log out`
- `what plant is this`
- `what disease is this`
- `how do i treat this plant`

### What voice is used for
- CRM operations
- guided work report creation
- weather queries
- communication support
- plant recognition and plant disease capture

## 18. Settings
Settings contains these sections:
- Company profile
- Service rates
- Language
- App theme
- Voice control
- Server and connection
- CRM
- Notifications
- Work profile
- Users and permissions
- Data
- About
- Version history

### Company profile
Shows:
- internal language
- customer language
- language modes
- company limits

### Service rates
Contains:
- hourly service rates by work type
- other rates such as waste removal or minimum job price

### Language
- `System language` is stored locally for the signed-in user
- `Customer language` is a company setting and can be changed only by an administrator

### Users and permissions
This section manages backend users, not local device-only profiles.

It allows:
- creating users
- changing role
- changing account status
- deleting users
- applying individual permission overrides

Built-in roles:
- admin
- manager
- worker
- assistant
- viewer

Each role provides default permissions and each user can have custom overrides.

### Data
Used for:
- CRM export to CSV
- importing phone contacts
- importing a database file
- clearing history
- restoring defaults

## 19. Typical business workflows

### Phone contact -> client -> job -> invoice
1. open `Set clients`
2. mark a phone contact as a client
3. save selection
4. create a job
5. complete the work and create a work report
6. create an invoice from the report

### Lead -> quote -> job
1. create a lead
2. convert or prepare the client
3. create a quote
4. add quote items
5. approve the quote
6. let the system create the job

### Job -> photo record -> audit
1. open job detail
2. add notes to the correct stage
3. attach photos before start, during progress, at completion, or under complications
4. review the audit log

### Plant -> diagnosis -> treatment
1. open `Plants`
2. switch to `Check disease`
3. take at least 1 clear photo
4. review diagnosis and treatment guidance

## 20. Recommended role usage
- `admin`: company setup, users, permissions, customer language, critical settings
- `manager`: planning, delegation, CRM, sales, oversight
- `worker`: own tasks, own calendar, work reports, photos
- `assistant`: communication support, CRM administration, records
- `viewer`: read-only access

## 21. Common issues
- Cannot change customer language: the user is not an administrator.
- Voice does not react: check wake word, microphone permission, and hotword setting.
- Plant disease service says it is not configured: the server is missing a plant health API key.
- A contact is missing in CRM: it was not saved as a client in `Set clients`.
- Wrong pricing for a client: check individual client rates because they override company defaults.

## 22. Short usage examples
- `Add client Novak, email novak@example.com, phone 07000 000000`
- `Plan the job for Friday 9 AM`
- `Create a work report for Green Garden`
- `What plant is this`
- `How do i treat this plant`
- `Log out`
