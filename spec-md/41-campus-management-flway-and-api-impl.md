# 42 — Campus Management Backend: Flyway Migration and Complete REST API Implementation

**Project:** Smart AI University Student Assistant  
**Module:** Campus Management  
**Backend:** Java 21, Spring Boot 4.x, Maven  
**Database:** PostgreSQL (`smart_university_db`)  
**Migration:** Flyway  
**Security:** Spring Security, RS256 JWT  
**Admin:** React Web Admin  
**Student:** Flutter Android/iOS  
**Specification path:** `spec-md/42-campus-management-backend-implementation.md`

---

# 1. Objective

Implement the complete Campus Management backend, from normalized PostgreSQL migrations to Admin and Student REST APIs.

The Admin Web controls official campus information.

The Student Mobile app consumes the published campus information for discovering places, viewing facilities and navigating around the university.

Final architecture:

```text
React Admin Web
      |
      | Admin JWT
      v
Spring Boot Admin Campus APIs
      |
      | Create / Update / Manage
      v
PostgreSQL Campus Management
      ^
      | Read / Search / Navigate
      |
Spring Boot Student Campus APIs
      ^
      | Student JWT
      |
Flutter Android / iOS
```

Use the existing PostgreSQL database. Do not create another database.

Campus Management is separate from the LLM provider and does not need Gemini API calls.

---

# 2. Inspected Figma References

## Student Mobile

File:

`https://www.figma.com/design/RUhIF3MsPa9jTWfT0Dou02/Smart-Ai-University-Mobile`

Screens inspected:

```text
52:30    Campus Screen
52:1188  Campus Screen - 2
52:2351  Campus Screen - 3 (Navigation)
52:2643  Campus Place Details
52:3027  Campus Place Details variant
```

## Admin Web

File:

`https://www.figma.com/design/FQhq9uVri66RJpADxECMtm/Smart-Ai-Admin-Web`

Screens inspected:

```text
121:2     Buildings
121:2466  Campus Map
121:3054  Rooms
121:3861  Campus Places
121:5185  Navigation Routes
```

The Figma examples are design data, not verified real locations.

Do not seed Figma demonstration data into production migrations.

The linked designs show lists and controls, but do not establish every form validation rule or internal data relationship. The normalized schema and API contracts below are implementation decisions derived from those screen requirements.

---

# 3. Required Features

## Admin Web

```text
Campus Management
├── Campus Map
│   ├── View map
│   ├── View building markers
│   ├── View place markers
│   ├── View navigation nodes
│   ├── View walking routes
│   ├── Search and filter
│   └── Location summary
│
├── Buildings
│   ├── Building list
│   ├── Create building
│   ├── Update building
│   ├── Building status
│   ├── Building details
│   ├── Opening hours
│   ├── Total floors
│   ├── Total rooms
│   └── Soft delete
│
├── Rooms
│   ├── Room list
│   ├── Create room
│   ├── Update room
│   ├── Room type
│   ├── Room capacity
│   ├── Floor
│   ├── Room status
│   ├── Facilities
│   └── Soft delete
│
├── Campus Places
│   ├── List and cards
│   ├── Create place
│   ├── Update place
│   ├── Category
│   ├── Description
│   ├── Building / Floor
│   ├── Opening hours
│   ├── Contact information
│   ├── Facilities
│   ├── Popular flag
│   ├── Visibility
│   ├── Status
│   └── Soft delete
│
└── Navigation Routes
    ├── Navigation node list
    ├── Add node
    ├── Connect nodes
    ├── Route list
    ├── Distance
    ├── Walk time
    ├── Surface
    ├── Accessibility
    ├── Route status
    ├── Route geometry
    └── Route validation
```

## Student Mobile

```text
Campus Navigation
├── Campus map
├── Search buildings and facilities
├── Category filters
│   ├── All
│   ├── Buildings
│   ├── Food
│   ├── Library
│   ├── Student Services
│   ├── Health
│   └── Parking
│
├── Nearby locations
├── Location details
│   ├── Name
│   ├── Category
│   ├── Code
│   ├── Description
│   ├── Distance
│   ├── Estimated walking time
│   ├── Opening hours
│   ├── Facilities
│   ├── View rooms
│   └── Start navigation
│
└── Navigation
    ├── Origin
    ├── Destination
    ├── Walking route
    ├── Distance remaining
    ├── Estimated time
    ├── Estimated arrival
    ├── Next instruction
    ├── Accessible route option
    └── End navigation
```

---

# 4. Mandatory Agent Instructions

Before coding:

1. Read `AGENTS.md`.
2. Read `spec-md/`.
3. Inspect current feature package conventions.
4. Inspect all Flyway migrations and `flyway_schema_history`.
5. Inspect existing `course_sections`, timetable and room fields.
6. Inspect Student JWT and Admin JWT security filters.
7. Inspect current Admin API response wrapper and exception handling.
8. Search for existing campus, building, room, place or navigation entities.
9. Reuse equivalent entities where they already exist.
10. Determine actual next Flyway versions.
11. Do not edit previously applied migrations.
12. Implement Flyway migrations before dependent Java entities.
13. Run PostgreSQL and Hibernate validation.
14. Implement Admin APIs.
15. Implement Student read and navigation APIs.
16. Create automated tests.
17. Verify with Postman.
18. Preserve all existing academic and assistant endpoints.

Do not assume the repository is empty.

Use actual project names if they differ from the names proposed here.

---

# 5. Database Design

Recommended normalized entities:

```text
campus_sites
campus_categories

campus_buildings
campus_rooms

campus_places
campus_facilities
campus_room_facilities
campus_place_facilities

campus_opening_hours
campus_opening_exceptions

campus_nav_nodes
campus_nav_edges
campus_nav_edge_points
campus_nav_destinations
```

This design supports one campus today and additional campuses later.

Relationship overview:

```text
campus_sites
    |
    +-- campus_buildings
    |       |
    |       +-- campus_rooms
    |       |       |
    |       |       +-- campus_room_facilities
    |       |
    |       +-- campus_places (optional)
    |
    +-- campus_places
    |       |
    |       +-- campus_categories
    |       +-- campus_place_facilities
    |
    +-- campus_nav_nodes
            |
            +-- campus_nav_edges
            |       |
            |       +-- campus_nav_edge_points
            |
            +-- campus_nav_destinations
                    |
                    +-- Building OR Place
```

Opening-hour records reference either a Building or a Campus Place.

Do not duplicate building names, room information, categories or navigation coordinates unnecessarily.

---

# PART A — FLYWAY MIGRATION

# 6. Database Standards

Use:

```text
BIGINT GENERATED BY DEFAULT AS IDENTITY
BOOLEAN
VARCHAR
TEXT
INTEGER
SMALLINT
NUMERIC
DATE
TIME
TIMESTAMP
```

Use `TIMESTAMPTZ` when an absolute instant is required.

Never use MySQL `TINYINT`, `DATETIME`, or `AUTO_INCREMENT`.

Use foreign keys, checks and appropriate indexes.

Use `is_deleted BOOLEAN NOT NULL DEFAULT FALSE` for soft-deletable business records.

Audit fields:

```text
created_by
created_at
updated_by
updated_at
```

The `created_by` and `updated_by` fields must reference the existing `app_users` table where appropriate.

Do not use `ON DELETE CASCADE` on core academic or campus business records.

---

# 7. Migration File Plan

Create migration files in dependency order:

```text
V{next}__create_campus_sites_and_categories.sql

V{next+1}__create_campus_buildings_and_rooms.sql

V{next+2}__create_campus_places_and_facilities.sql

V{next+3}__create_campus_opening_hours.sql

V{next+4}__create_campus_navigation_nodes.sql

V{next+5}__create_campus_navigation_edges.sql

V{next+6}__create_campus_navigation_destinations.sql
```

These versions are placeholders.

If one migration is too large, split it into additional consecutively numbered migrations without changing the dependency order.

**Complete and verify this migration phase before starting API implementation.**

---

# 8. Campus Sites

Purpose: define the campus in which Buildings, Places and Navigation Nodes exist.

```sql
CREATE TABLE campus_sites (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    site_code VARCHAR(50) NOT NULL UNIQUE,
    site_name VARCHAR(200) NOT NULL,
    description TEXT,
    timezone VARCHAR(60) NOT NULL DEFAULT 'Asia/Bangkok',
    center_latitude NUMERIC(10,7),
    center_longitude NUMERIC(10,7),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT REFERENCES app_users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES app_users(id),
    updated_at TIMESTAMP,

    CONSTRAINT chk_campus_site_latitude
        CHECK (
            center_latitude IS NULL
            OR center_latitude BETWEEN -90 AND 90
        ),
    CONSTRAINT chk_campus_site_longitude
        CHECK (
            center_longitude IS NULL
            OR center_longitude BETWEEN -180 AND 180
        )
);
```

The university's actual campus name and coordinates must be entered through verified Admin data.

Do not invent campus center coordinates.

---

# 9. Campus Categories

The Admin design uses detailed categories such as:

```text
Academic Building
Library
Cafeteria
Registrar
Student Affairs
Health Center
ATM
Parking
Sports Center
Main Gate
```

The Mobile design groups these into broader filters.

Create:

```sql
CREATE TABLE campus_categories (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    category_code VARCHAR(60) NOT NULL UNIQUE,
    category_name VARCHAR(150) NOT NULL,
    mobile_group VARCHAR(40) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_campus_category_group
        CHECK (
            mobile_group IN (
                'BUILDINGS',
                'FOOD',
                'LIBRARY',
                'STUDENT_SERVICES',
                'HEALTH',
                'PARKING',
                'OTHER'
            )
        )
);
```

Example category mapping:

| Admin Category | Mobile Group |
|---|---|
| Academic Building | BUILDINGS |
| Library | LIBRARY |
| Cafeteria | FOOD |
| Registrar | STUDENT_SERVICES |
| Student Affairs | STUDENT_SERVICES |
| Health Center | HEALTH |
| Parking | PARKING |
| ATM | OTHER |
| Sports Center | OTHER |
| Main Gate | OTHER |

The category table stores the mapping. The Flutter application should not hard-code every individual category-to-group relationship.

`ALL` is a request filter, not a stored mobile group.

---

# 10. Campus Buildings

Fields required by Admin Figma:

```text
Building Code
Building Name
Area
Floors
Room Count
Opening Hours
Status
Actions
```

Room Count is derived from Rooms.

Opening Hours are stored in a separate schedule table.

```sql
CREATE TABLE campus_buildings (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    site_id BIGINT NOT NULL REFERENCES campus_sites(id),

    building_code VARCHAR(50) NOT NULL,
    building_name VARCHAR(200) NOT NULL,
    building_name_th VARCHAR(200),
    description TEXT,

    total_floors SMALLINT NOT NULL,
    area_square_meters NUMERIC(12,2),

    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    created_by BIGINT REFERENCES app_users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES app_users(id),
    updated_at TIMESTAMP,

    CONSTRAINT uk_campus_buildings_code
        UNIQUE (site_id, building_code),

    CONSTRAINT chk_campus_building_floors
        CHECK (total_floors > 0),

    CONSTRAINT chk_campus_building_area
        CHECK (
            area_square_meters IS NULL
            OR area_square_meters > 0
        ),

    CONSTRAINT chk_campus_building_status
        CHECK (
            status IN (
                'ACTIVE',
                'UNDER_RENOVATION',
                'INACTIVE'
            )
        )
);

CREATE INDEX idx_campus_buildings_site_status
ON campus_buildings(site_id, status)
WHERE is_deleted = FALSE;
```

Rules:

- Building Code must be unique inside a Campus.
- Building has one Campus.
- One Building has many Rooms.
- Floor count must be positive.
- Area is optional.
- Room count must be computed.
- A Building under renovation should not automatically be presented as an accessible navigation destination.
- Deleting a Building must not delete its Rooms, Places or history.

---

# 11. Campus Rooms

Fields from Admin Figma:

```text
Room Number
Room Name
Building
Floor
Type
Capacity
Status
Facilities
```

Room types shown include:

```text
Classroom
Computer Lab
Laboratory
Lecture Hall
Meeting Room
Study Room
```

Create:

```sql
CREATE TABLE campus_rooms (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    building_id BIGINT NOT NULL
        REFERENCES campus_buildings(id),

    room_number VARCHAR(50) NOT NULL,
    room_name VARCHAR(200),

    floor_number SMALLINT NOT NULL,

    room_type VARCHAR(40) NOT NULL,

    capacity INTEGER,

    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    created_by BIGINT REFERENCES app_users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES app_users(id),
    updated_at TIMESTAMP,

    CONSTRAINT uk_campus_rooms_building_number
        UNIQUE (building_id, room_number),

    CONSTRAINT chk_campus_room_capacity
        CHECK (
            capacity IS NULL
            OR capacity > 0
        ),

    CONSTRAINT chk_campus_room_type
        CHECK (
            room_type IN (
                'CLASSROOM',
                'COMPUTER_LAB',
                'LABORATORY',
                'LECTURE_HALL',
                'MEETING_ROOM',
                'STUDY_ROOM',
                'OFFICE',
                'OTHER'
            )
        ),

    CONSTRAINT chk_campus_room_status
        CHECK (
            status IN (
                'AVAILABLE',
                'OCCUPIED',
                'RESERVED',
                'MAINTENANCE',
                'INACTIVE'
            )
        )
);

CREATE INDEX idx_campus_rooms_building
ON campus_rooms(building_id)
WHERE is_deleted = FALSE;

CREATE INDEX idx_campus_rooms_type_status
ON campus_rooms(room_type, status)
WHERE is_deleted = FALSE;
```

Rules:

- Room Number is unique inside its Building.
- Floor Number can be zero or negative for ground/basement levels, according to Admin-defined policy.
- Validate that the floor is permitted by the Building's floor configuration.
- The declared room status is not automatically real-time occupancy.
- Real-time occupancy requires verified bookings or timetable data.
- Do not claim the room is currently empty simply because Admin set `AVAILABLE`.

---

# 12. Campus Places

Campus Places represent student-facing points of interest and services.

Examples from the design:

```text
Library
Cafeteria
Registrar Office
Student Affairs Office
Health Center
ATM
Parking
Sports Center
Gate
```

Some Places may be inside a Building; others are standalone.

```sql
CREATE TABLE campus_places (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    site_id BIGINT NOT NULL
        REFERENCES campus_sites(id),

    category_id BIGINT NOT NULL
        REFERENCES campus_categories(id),

    building_id BIGINT
        REFERENCES campus_buildings(id),

    place_code VARCHAR(50) NOT NULL,
    place_name VARCHAR(200) NOT NULL,
    place_name_th VARCHAR(200),

    description TEXT,
    floor_label VARCHAR(60),
    phone_number VARCHAR(50),

    is_popular BOOLEAN NOT NULL DEFAULT FALSE,
    is_visible_on_mobile BOOLEAN NOT NULL DEFAULT TRUE,

    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    created_by BIGINT REFERENCES app_users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES app_users(id),
    updated_at TIMESTAMP,

    CONSTRAINT uk_campus_places_code
        UNIQUE (site_id, place_code),

    CONSTRAINT chk_campus_place_status
        CHECK (
            status IN (
                'ACTIVE',
                'TEMPORARILY_CLOSED',
                'INACTIVE'
            )
        )
);

CREATE INDEX idx_campus_places_category
ON campus_places(category_id)
WHERE is_deleted = FALSE;

CREATE INDEX idx_campus_places_building
ON campus_places(building_id)
WHERE is_deleted = FALSE;

CREATE INDEX idx_campus_places_mobile
ON campus_places(site_id, status, is_popular)
WHERE is_deleted = FALSE
  AND is_visible_on_mobile = TRUE;
```

Rules:

- Place Code is unique within the Campus.
- Place Category is required.
- Building reference is optional.
- If Building is supplied, it must belong to the same Campus.
- Floor Label is nullable because some Places are outside buildings.
- The popularity flag is managed by Admin.
- Mobile visibility is controlled by Admin.
- Soft-deleted Places are never returned to Students.
- Do not treat `ACTIVE` as proof the Place is open at the current time.

The mobile's `View Rooms` action is available when the Place has an associated Building with eligible Rooms.

---

# 13. Campus Facilities

The mobile details screen shows facilities such as:

```text
Study Rooms
Printing
Wi-Fi
Café Corner
e-Library
```

Normalize these as a reusable catalog.

```sql
CREATE TABLE campus_facilities (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    facility_code VARCHAR(60) NOT NULL UNIQUE,
    facility_name VARCHAR(150) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE campus_place_facilities (
    place_id BIGINT NOT NULL
        REFERENCES campus_places(id),
    facility_id BIGINT NOT NULL
        REFERENCES campus_facilities(id),

    PRIMARY KEY (place_id, facility_id)
);

CREATE TABLE campus_room_facilities (
    room_id BIGINT NOT NULL
        REFERENCES campus_rooms(id),
    facility_id BIGINT NOT NULL
        REFERENCES campus_facilities(id),

    PRIMARY KEY (room_id, facility_id)
);
```

Do not store facilities as:

```text
"Wi-Fi, Printing, Study Room"
```

in a single comma-separated database column.

Facility counts and names should be derived through these relationships.

---

# 14. Building and Place Opening Hours

Opening hours are required in both Admin and Mobile designs.

Support different schedules for:

```text
Monday
Tuesday
Wednesday
Thursday
Friday
Saturday
Sunday
```

A Place can have its own hours even if it belongs to a Building.

```sql
CREATE TABLE campus_opening_hours (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    building_id BIGINT REFERENCES campus_buildings(id),
    place_id BIGINT REFERENCES campus_places(id),

    day_of_week SMALLINT NOT NULL,

    opens_at TIME,
    closes_at TIME,

    is_closed BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_campus_opening_owner
        CHECK (
            (building_id IS NOT NULL AND place_id IS NULL)
            OR
            (building_id IS NULL AND place_id IS NOT NULL)
        ),

    CONSTRAINT chk_campus_opening_day
        CHECK (day_of_week BETWEEN 1 AND 7),

    CONSTRAINT chk_campus_opening_time
        CHECK (
            (is_closed = TRUE
             AND opens_at IS NULL
             AND closes_at IS NULL)
            OR
            (is_closed = FALSE
             AND opens_at IS NOT NULL
             AND closes_at IS NOT NULL
             AND closes_at > opens_at)
        )
);

CREATE UNIQUE INDEX uk_campus_building_opening_day
ON campus_opening_hours(building_id, day_of_week)
WHERE building_id IS NOT NULL;

CREATE UNIQUE INDEX uk_campus_place_opening_day
ON campus_opening_hours(place_id, day_of_week)
WHERE place_id IS NOT NULL;
```

Use:

```text
1 = Monday
...
7 = Sunday
```

One schedule interval per day is supported in this initial design.

For businesses with multiple daily intervals, extend the model later rather than inserting duplicate unsupported rows.

For overnight hours, add an explicitly supported cross-day policy before accepting such records.

---

# 15. Opening Exceptions

University holidays, special closures and exceptional opening hours must not be ignored.

```sql
CREATE TABLE campus_opening_exceptions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    building_id BIGINT REFERENCES campus_buildings(id),
    place_id BIGINT REFERENCES campus_places(id),

    exception_date DATE NOT NULL,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,

    opens_at TIME,
    closes_at TIME,

    reason VARCHAR(255),

    CONSTRAINT chk_campus_exception_owner
        CHECK (
            (building_id IS NOT NULL AND place_id IS NULL)
            OR
            (building_id IS NULL AND place_id IS NOT NULL)
        ),

    CONSTRAINT chk_campus_exception_time
        CHECK (
            (is_closed = TRUE
             AND opens_at IS NULL
             AND closes_at IS NULL)
            OR
            (is_closed = FALSE
             AND opens_at IS NOT NULL
             AND closes_at IS NOT NULL
             AND closes_at > opens_at)
        )
);

CREATE UNIQUE INDEX uk_campus_building_exception
ON campus_opening_exceptions(building_id, exception_date)
WHERE building_id IS NOT NULL;

CREATE UNIQUE INDEX uk_campus_place_exception
ON campus_opening_exceptions(place_id, exception_date)
WHERE place_id IS NOT NULL;
```

Opening-hours logic:

```text
Check place/building status
       ↓
Check date exception
       ↓
If none, check regular opening hours
       ↓
Return expected opening state
```

If holiday information is incomplete, do not claim verified real-time opening status.

Expose whether the result is based on published hours.

---

# 16. Navigation Nodes

The Admin Navigation screen contains a walking network.

Node examples:

```text
Main Gate
North Gate
Buildings
Library
Cafeteria
Junction A
Junction B
Junction C
Parking
Health Center
```

Each navigation node needs real geographic coordinates.

```sql
CREATE TABLE campus_nav_nodes (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    site_id BIGINT NOT NULL
        REFERENCES campus_sites(id),

    node_code VARCHAR(50) NOT NULL,
    node_name VARCHAR(200) NOT NULL,

    node_type VARCHAR(30) NOT NULL,

    latitude NUMERIC(10,7) NOT NULL,
    longitude NUMERIC(10,7) NOT NULL,

    is_accessible BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    created_by BIGINT REFERENCES app_users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES app_users(id),
    updated_at TIMESTAMP,

    CONSTRAINT uk_campus_nav_node_code
        UNIQUE (site_id, node_code),

    CONSTRAINT chk_campus_nav_node_type
        CHECK (
            node_type IN (
                'ENTRANCE',
                'JUNCTION',
                'GATE',
                'POINT_OF_INTEREST',
                'OTHER'
            )
        ),

    CONSTRAINT chk_campus_nav_node_lat
        CHECK (latitude BETWEEN -90 AND 90),

    CONSTRAINT chk_campus_nav_node_lng
        CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_campus_nav_nodes_site
ON campus_nav_nodes(site_id)
WHERE is_active = TRUE
  AND is_deleted = FALSE;
```

Important:

- Coordinates must come from verified campus map data.
- Do not generate coordinates from screenshots.
- All nodes used by one route must belong to the same Campus.
- Disabled nodes cannot be used for navigation.

---

# 17. Navigation Edges

An edge connects two walking-network nodes.

Admin Figma displays:

```text
Route ID
Start Node
End Node
Distance
Walk Time
Surface
Accessibility
Status
Actions
```

Use the term `edge` internally because one student navigation route may contain multiple connected edges.

```sql
CREATE TABLE campus_nav_edges (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    site_id BIGINT NOT NULL
        REFERENCES campus_sites(id),

    edge_code VARCHAR(50) NOT NULL,

    start_node_id BIGINT NOT NULL
        REFERENCES campus_nav_nodes(id),

    end_node_id BIGINT NOT NULL
        REFERENCES campus_nav_nodes(id),

    distance_meters NUMERIC(10,2) NOT NULL,

    surface_type VARCHAR(40) NOT NULL,

    is_wheelchair_accessible BOOLEAN NOT NULL DEFAULT FALSE,
    is_bidirectional BOOLEAN NOT NULL DEFAULT TRUE,

    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    created_by BIGINT REFERENCES app_users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES app_users(id),
    updated_at TIMESTAMP,

    CONSTRAINT uk_campus_nav_edge_code
        UNIQUE (site_id, edge_code),

    CONSTRAINT chk_campus_nav_edge_distance
        CHECK (distance_meters > 0),

    CONSTRAINT chk_campus_nav_edge_nodes
        CHECK (start_node_id <> end_node_id),

    CONSTRAINT chk_campus_nav_edge_status
        CHECK (
            status IN (
                'ACTIVE',
                'INACTIVE',
                'UNDER_CONSTRUCTION'
            )
        ),

    CONSTRAINT chk_campus_nav_edge_surface
        CHECK (
            surface_type IN (
                'PAVED',
                'COVERED_WALKWAY',
                'GRAVEL',
                'STAIRS',
                'RAMP',
                'OTHER'
            )
        )
);

CREATE INDEX idx_campus_nav_edges_start
ON campus_nav_edges(start_node_id);

CREATE INDEX idx_campus_nav_edges_end
ON campus_nav_edges(end_node_id);

CREATE INDEX idx_campus_nav_edges_active
ON campus_nav_edges(site_id, status)
WHERE is_deleted = FALSE;
```

Business rules:

- Distance must represent the actual walking segment.
- Do not use straight-line distance as walking distance.
- Walk time is derived from distance and a configured walking-speed policy.
- A route under construction is excluded from normal navigation.
- An inaccessible edge is excluded when wheelchair-accessible navigation is requested.
- A stair edge must not be marked wheelchair-accessible without verified accessibility arrangements.
- One-way edges must be respected.

---

# 18. Route Geometry

A route needs geometry to display the walking path on a map.

Create ordered points for each edge:

```sql
CREATE TABLE campus_nav_edge_points (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    edge_id BIGINT NOT NULL
        REFERENCES campus_nav_edges(id),

    point_sequence INTEGER NOT NULL,

    latitude NUMERIC(10,7) NOT NULL,
    longitude NUMERIC(10,7) NOT NULL,

    CONSTRAINT uk_campus_edge_point_sequence
        UNIQUE (edge_id, point_sequence),

    CONSTRAINT chk_campus_edge_point_seq
        CHECK (point_sequence >= 0),

    CONSTRAINT chk_campus_edge_point_lat
        CHECK (latitude BETWEEN -90 AND 90),

    CONSTRAINT chk_campus_edge_point_lng
        CHECK (longitude BETWEEN -180 AND 180)
);
```

These points represent intermediate walkway geometry.

The server constructs the full edge geometry using:

```text
Start Node
+
Ordered Intermediate Points
+
End Node
```

If an edge has no geometry, the system must not claim an accurate turn-by-turn walkway line.

Actual geographic coordinates are essential for reliable map alignment.

---

# 19. Navigation Destinations

Associate buildings and places with graph nodes.

A Building may have multiple entrances.

A Place may share a Building entrance.

```sql
CREATE TABLE campus_nav_destinations (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    node_id BIGINT NOT NULL
        REFERENCES campus_nav_nodes(id),

    building_id BIGINT
        REFERENCES campus_buildings(id),

    place_id BIGINT
        REFERENCES campus_places(id),

    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_accessible_entrance BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_campus_nav_destination_owner
        CHECK (
            (building_id IS NOT NULL AND place_id IS NULL)
            OR
            (building_id IS NULL AND place_id IS NOT NULL)
        ),

    CONSTRAINT uk_campus_nav_destination_building_node
        UNIQUE (building_id, node_id),

    CONSTRAINT uk_campus_nav_destination_place_node
        UNIQUE (place_id, node_id)
);

CREATE UNIQUE INDEX uk_campus_primary_building_destination
ON campus_nav_destinations(building_id)
WHERE building_id IS NOT NULL
  AND is_primary = TRUE;

CREATE UNIQUE INDEX uk_campus_primary_place_destination
ON campus_nav_destinations(place_id)
WHERE place_id IS NOT NULL
  AND is_primary = TRUE;
```

Validation:

- Node and destination belong to the same Campus.
- Each published navigable Place or Building should have one primary destination.
- Additional accessible entrances may be defined.
- If a Place has no dedicated destination, the backend may use a verified associated Building entrance.
- If neither exists, navigation must report unavailable.
- A Room can use its Building's entrance as a destination, but this is not indoor room-level routing.

---

# 20. Migration Verification

Execute:

```sql
SELECT
    installed_rank,
    version,
    description,
    success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Inspect tables:

```sql
\dt campus_*
```

Verify FKs, CHECK constraints, unique constraints and indexes.

Confirm:

```text
Hibernate ddl-auto = validate
```

No automatically inserted demonstration Campus rows.

Before migrating to an existing database with academic room data, review any collisions with the current Course Section and Timetable schema.

---

# PART B — BACKEND IMPLEMENTATION

# 21. Package Structure

Follow current project conventions.

Recommended:

```text
feature/
└── campus/
    ├── controller/
    │   ├── admin/
    │   │   ├── AdminCampusSiteController
    │   │   ├── AdminCampusBuildingController
    │   │   ├── AdminCampusRoomController
    │   │   ├── AdminCampusPlaceController
    │   │   ├── AdminCampusMapController
    │   │   └── AdminCampusNavigationController
    │   │
    │   └── student/
    │       └── StudentCampusController
    │
    ├── dto/
    │   ├── request/
    │   ├── response/
    │   └── navigation/
    │
    ├── entity/
    ├── repository/
    ├── mapper/
    │
    ├── service/
    │   ├── CampusBuildingService
    │   ├── CampusRoomService
    │   ├── CampusPlaceService
    │   ├── CampusMapService
    │   ├── CampusNavigationService
    │   └── impl/
    │
    └── exception/
```

Constructor injection only.

Use DTOs for controllers.

Do not expose JPA Entities in HTTP responses.

Use `@Transactional(readOnly = true)` for queries and appropriate short transactions for writes.

---

# PART C — ADMIN BUILDING APIs

# 22. Buildings Endpoint Contract

Base:

```http
/api/v1/admin/campus/buildings
```

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/buildings` | Paginated list |
| GET | `/buildings/{id}` | Detail |
| POST | `/buildings` | Create |
| PUT | `/buildings/{id}` | Update |
| PATCH | `/buildings/{id}/status` | Status |
| DELETE | `/buildings/{id}` | Soft delete |
| GET | `/buildings/statistics` | Dashboard metrics |
| GET | `/buildings/{id}/rooms` | Rooms |
| PUT | `/buildings/{id}/opening-hours` | Weekly hours |
| PUT | `/buildings/{id}/opening-exceptions` | Holiday/special hours |

Use the full base path for actual controller mappings.

---

# 23. Create Building

```http
POST /api/v1/admin/campus/buildings
```

Example:

```json
{
  "siteId": 1,
  "buildingCode": "BLD-001",
  "buildingName": "Example Academic Building",
  "buildingNameTh": null,
  "description": "Academic teaching building",
  "totalFloors": 5,
  "areaSquareMeters": 9600.00,
  "status": "ACTIVE"
}
```

Validation:

```text
siteId exists
site is active and not deleted
buildingCode required
buildingCode unique in site
buildingName required
totalFloors > 0
areaSquareMeters positive when provided
status valid
```

Room Count is never accepted in the request.

---

# 24. Buildings List

```http
GET /api/v1/admin/campus/buildings
```

Supported filters:

```text
search
siteId
status
page
size
sort
```

Search:

```text
Building Code
Building Name
```

List response should include:

```text
id
buildingCode
buildingName
areaSquareMeters
totalFloors
totalRooms
openingHoursSummary
status
createdAt
updatedAt
```

Use the existing pagination response contract.

Derive `totalRooms` from non-deleted Rooms.

---

# 25. Building Statistics

```http
GET /api/v1/admin/campus/buildings/statistics
```

Example:

```json
{
  "totalBuildings": 12,
  "activeBuildings": 10,
  "underRenovationBuildings": 1,
  "inactiveBuildings": 1,
  "totalRooms": 526
}
```

These numbers are only illustrative.

All statistics must be calculated using database queries.

Do not create a statistics table.

---

# PART D — ADMIN ROOMS APIs

# 26. Room Endpoint Contract

Base:

```http
/api/v1/admin/campus/rooms
```

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/rooms` | Paginated list |
| GET | `/rooms/{id}` | Detail |
| POST | `/rooms` | Create |
| PUT | `/rooms/{id}` | Update |
| PATCH | `/rooms/{id}/status` | Declared status |
| DELETE | `/rooms/{id}` | Soft delete |
| GET | `/rooms/statistics` | Summary |
| GET | `/rooms/facilities` | Facility catalog |

---

# 27. Room Create Request

```json
{
  "buildingId": 3,
  "roomNumber": "BLD-201",
  "roomName": "Computer Lab A",
  "floorNumber": 2,
  "roomType": "COMPUTER_LAB",
  "capacity": 40,
  "status": "AVAILABLE",
  "facilityIds": [1, 2, 3]
}
```

Validation:

```text
Building exists
Building not deleted
Room Number unique inside Building
Floor valid
Room Type valid
Capacity positive
Facility IDs valid
No duplicate Facility IDs
```

Do not accept a Building name as the foreign key.

---

# 28. Room List Filters

```http
GET /api/v1/admin/campus/rooms
```

Supported:

```text
search
buildingId
floorNumber
roomType
status
page
size
```

Figma room list columns:

```text
Room Number
Room Name
Building
Floor
Type
Capacity
Status
Facilities
Actions
```

The response should include IDs and display names for related Building and Facilities.

---

# 29. Room Statistics

```http
GET /api/v1/admin/campus/rooms/statistics
```

Example:

```json
{
  "totalRooms": 18,
  "available": 9,
  "occupied": 4,
  "reserved": 3,
  "maintenance": 2
}
```

These statistics reflect stored declared statuses, not necessarily real-time timetable availability.

Do not present them as live occupancy unless integrated with an authoritative booking system.

---

# PART E — ADMIN CAMPUS PLACES APIs

# 30. Campus Places Endpoint Contract

Base:

```http
/api/v1/admin/campus/places
```

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/places` | Paginated list/cards |
| GET | `/places/{id}` | Detail |
| POST | `/places` | Create |
| PUT | `/places/{id}` | Update |
| PATCH | `/places/{id}/status` | Update status |
| PATCH | `/places/{id}/visibility` | Mobile visibility |
| PATCH | `/places/{id}/popular` | Popular flag |
| DELETE | `/places/{id}` | Soft delete |
| GET | `/places/statistics` | Statistics |
| PUT | `/places/{id}/opening-hours` | Weekly hours |
| PUT | `/places/{id}/opening-exceptions` | Special hours |
| PUT | `/places/{id}/facilities` | Facilities |

---

# 31. Create Campus Place Request

```json
{
  "siteId": 1,
  "categoryId": 2,
  "buildingId": 3,
  "placeCode": "LIB-01",
  "placeName": "Example University Library",
  "placeNameTh": null,
  "description": "University library and student study facilities",
  "floorLabel": "1F–5F",
  "phoneNumber": null,
  "isPopular": true,
  "isVisibleOnMobile": true,
  "status": "ACTIVE",
  "facilityIds": [1, 2, 3]
}
```

Required validations:

```text
Campus exists
Category exists and active
Building exists when supplied
Building belongs to Campus
Place Code unique in Campus
Place Name required
Phone format valid if provided
Facility IDs exist
Status valid
```

Location coordinates are not part of this request unless the existing project already has an approved location model.

Set navigation destinations separately using verified map nodes.

---

# 32. Place Statistics

The Admin design shows:

```text
Total Places
Open
Closed
Popular
```

Implement:

```http
GET /api/v1/admin/campus/places/statistics
```

Differentiate:

```text
ACTIVE / TEMPORARILY_CLOSED / INACTIVE
```

from time-dependent:

```text
Expected Open Now
```

If displaying an `openNow` statistic, calculate it using published hours and available exceptions.

Otherwise clearly label the operational-status counts.

Do not silently treat ACTIVE as physically open.

---

# 33. Place Categories and Facilities APIs

```http
GET  /api/v1/admin/campus/categories
POST /api/v1/admin/campus/categories
PUT  /api/v1/admin/campus/categories/{id}

GET  /api/v1/admin/campus/facilities
POST /api/v1/admin/campus/facilities
PUT  /api/v1/admin/campus/facilities/{id}
```

Reference records must not be deleted or deactivated in ways that invalidate existing Places without explicit dependency checks.

---

# PART F — ADMIN CAMPUS MAP

# 34. Campus Map API

Admin Figma displays:

```text
Map
Buildings
Locations
Navigation Nodes
Walking Routes
Filter by Type
Location Statistics
```

Implement:

```http
GET /api/v1/admin/campus/map
```

Supported parameters:

```text
siteId
includeBuildings
includePlaces
includeNodes
includeEdges
categoryId
status
```

Response:

```json
{
  "site": {
    "id": 1,
    "name": "Example Campus"
  },
  "buildings": [],
  "places": [],
  "navigationNodes": [],
  "navigationEdges": [],
  "summary": {
    "totalLocations": 0,
    "activeLocations": 0,
    "inactiveLocations": 0
  }
}
```

Each map marker must include a stable entity ID, entity type and verified geographic coordinates.

Map layer toggles are UI state and do not need database tables.

The map background in Figma is visual design material. It is not automatically a GPS-compatible geographic map.

Use a supported geospatial map source or a properly georeferenced campus map when implementing live GPS overlays.

---

# PART G — ADMIN NAVIGATION MANAGEMENT

# 35. Navigation Node APIs

Base:

```http
/api/v1/admin/campus/navigation/nodes
```

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/nodes` | List |
| GET | `/nodes/{id}` | Detail |
| POST | `/nodes` | Add Node |
| PUT | `/nodes/{id}` | Edit Node |
| DELETE | `/nodes/{id}` | Soft delete |

Create example:

```json
{
  "siteId": 1,
  "nodeCode": "JCT-A",
  "nodeName": "Junction A",
  "nodeType": "JUNCTION",
  "latitude": 13.0000000,
  "longitude": 100.0000000,
  "isAccessible": true,
  "isActive": true
}
```

These numeric coordinates are synthetic schema examples, not actual RSU GPS coordinates. They must never be copied into real university records.

A Node cannot be removed if doing so would leave active navigation edges or destinations referencing it.

---

# 36. Navigation Edge APIs

Base:

```http
/api/v1/admin/campus/navigation/edges
```

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/edges` | List |
| GET | `/edges/{id}` | Detail |
| POST | `/edges` | Connect Nodes |
| PUT | `/edges/{id}` | Edit Route Segment |
| PATCH | `/edges/{id}/status` | Status |
| DELETE | `/edges/{id}` | Soft delete |
| GET | `/edges/statistics` | Graph summary |

Create request:

```json
{
  "siteId": 1,
  "edgeCode": "R01",
  "startNodeId": 10,
  "endNodeId": 11,
  "distanceMeters": 180,
  "surfaceType": "COVERED_WALKWAY",
  "isWheelchairAccessible": true,
  "isBidirectional": true,
  "status": "ACTIVE",
  "intermediatePoints": []
}
```

Distance shown here is an illustrative design value.

Validate:

```text
Both nodes exist
Both nodes belong to same Campus
Nodes differ
Distance positive
Surface supported
Geometry valid when provided
Accessibility compatible with route
Status supported
```

Use a transaction to update an Edge and its geometry consistently.

---

# 37. Navigation Destination APIs

```http
GET    /api/v1/admin/campus/navigation/destinations
POST   /api/v1/admin/campus/navigation/destinations
PUT    /api/v1/admin/campus/navigation/destinations/{id}
DELETE /api/v1/admin/campus/navigation/destinations/{id}
```

Create request:

```json
{
  "nodeId": 11,
  "buildingId": null,
  "placeId": 7,
  "isPrimary": true,
  "isAccessibleEntrance": true
}
```

Exactly one of:

```text
buildingId
placeId
```

must be populated.

Ensure the Node and destination belong to the same Campus.

---

# 38. Navigation Graph Validation

Implement:

```http
GET /api/v1/admin/campus/navigation/validation
```

Recommended report:

```text
Disconnected nodes
Unreachable destinations
Invalid coordinates
Inactive edges
Missing primary entrances
Missing route geometry
Potential accessibility gaps
Cross-campus edge errors
```

Do not silently publish routes that cannot be calculated correctly.

The Admin UI can use this endpoint to identify incomplete map data before Student Mobile release.

---

# PART H — STUDENT MOBILE CAMPUS APIs

# 39. Student API Base

Use:

```http
/api/v1/student/campus
```

Student JWT authentication required.

Student must be active and not deleted.

Follow existing forced-password-change restrictions.

Do not expose Admin mutations through this prefix.

---

# 40. Student Endpoint Contract

| Method | Endpoint | Mobile Purpose |
|---|---|---|
| GET | `/sites` | Available campuses |
| GET | `/categories` | Filter chips |
| GET | `/locations` | Unified list |
| GET | `/places/{id}` | Place detail |
| GET | `/buildings/{id}` | Building detail |
| GET | `/buildings/{id}/rooms` | View Rooms |
| GET | `/rooms/{id}` | Room detail |
| GET | `/map` | Markers/route layers |
| POST | `/nearby` | GPS-based nearby list |
| POST | `/routes/preview` | Walking route |

Do not create separate Student CRUD APIs for Buildings or Places.

Students only read published data.

---

# 41. Mobile Category API

```http
GET /api/v1/student/campus/categories
```

Example:

```json
[
  {
    "code": "ALL",
    "name": "All"
  },
  {
    "code": "BUILDINGS",
    "name": "Buildings"
  },
  {
    "code": "FOOD",
    "name": "Food"
  },
  {
    "code": "LIBRARY",
    "name": "Library"
  },
  {
    "code": "STUDENT_SERVICES",
    "name": "Student Services"
  },
  {
    "code": "HEALTH",
    "name": "Health"
  },
  {
    "code": "PARKING",
    "name": "Parking"
  }
]
```

Return only active supported groups.

The Buildings group is backed by eligible Building records.

Other groups use Campus Place categories.

---

# 42. Unified Campus Locations API

The Mobile Campus screen combines Buildings and Places.

Implement:

```http
GET /api/v1/student/campus/locations
```

Query parameters:

```text
siteId
search
group
limit
cursor
```

Suggested:

```http
GET /api/v1/student/campus/locations?group=LIBRARY
```

Response:

```json
{
  "total": 1,
  "items": [
    {
      "id": 7,
      "locationType": "PLACE",
      "code": "LIB-01",
      "name": "Example University Library",
      "category": "Library",
      "mobileGroup": "LIBRARY",
      "description": "University study facilities",
      "latitude": null,
      "longitude": null,
      "status": "ACTIVE",
      "isPopular": true,
      "canNavigate": false
    }
  ]
}
```

This example has no navigation coordinates, so `canNavigate` is false.

Real location coordinates must be resolved from verified mapped destination Nodes.

Filter out:

```text
Deleted records
Unpublished Places
Inactive records when hidden by policy
```

Prevent duplicate representations of a Place and its Building in the same list when the result represents the same logical destination.

Use stable `locationType` and `id` values for opening detail screens.

---

# 43. Mobile Search

Support searching:

```text
Building Name
Building Code
Place Name
Place Code
Relevant Category
```

Examples:

```text
Library
Registrar
Cafeteria
Parking
Computer Science Building
```

Requirements:

- Case-insensitive.
- Trim whitespace.
- Paginated/bounded.
- Avoid N+1 queries.
- Do not allow arbitrary SQL sorting or filtering.
- Search only eligible published records.

No LLM is required for ordinary campus search.

---

# 44. Student Place Details

```http
GET /api/v1/student/campus/places/{id}
```

Fields:

```text
id
code
name
category
description
building
floor
phone
status
isPopular
openingHours
facilities
coordinates
canNavigate
canViewRooms
```

Example:

```json
{
  "id": 7,
  "code": "LIB-01",
  "name": "Example University Library",
  "category": "Library",
  "description": "Study rooms, digital resources and printing",
  "building": {
    "id": 3,
    "name": "Example Library Building"
  },
  "floorLabel": "1F–5F",
  "phoneNumber": null,
  "status": "ACTIVE",
  "openingHours": [
    {
      "dayOfWeek": 1,
      "opensAt": "08:00",
      "closesAt": "21:00",
      "closed": false
    }
  ],
  "facilities": [
    "Study Rooms",
    "Printing",
    "Wi-Fi"
  ],
  "isPopular": true,
  "canNavigate": false,
  "canViewRooms": true
}
```

Do not return distance or walk time without a known origin and valid routing data.

`View Rooms` should load rooms from the associated Building.

---

# 45. Student Building and Room APIs

```http
GET /api/v1/student/campus/buildings/{id}

GET /api/v1/student/campus/buildings/{id}/rooms

GET /api/v1/student/campus/rooms/{id}
```

Building response:

```text
Building Code
Building Name
Description
Total Floors
Opening Hours
Status
Coordinates
Rooms
```

Room response:

```text
Room Number
Room Name
Building
Floor
Room Type
Capacity
Declared Status
Facilities
```

Only return fields safe for student viewing.

If `course_section_meetings.room_id` exists, the academic timetable may link to this Room detail.

Do not change existing academic endpoints destructively.

---

# 46. Student Campus Map API

```http
GET /api/v1/student/campus/map?siteId=1
```

Return:

```text
Campus bounds or map center
Published Building markers
Published Place markers
Optional permitted navigation graph layer
```

Use stable IDs and numeric coordinates.

Do not return unnecessary internal audit information.

The Flutter map needs a valid geographic base map or georeferenced campus image.

---

# PART I — GPS AND NEARBY LOCATIONS

# 47. GPS Responsibilities

Mobile Flutter:

```text
Request device location permission
Read device latitude and longitude
Display user location
Handle denied permission
Update position while navigation is active
```

Backend:

```text
Validate coordinates
Resolve nearby mapped nodes
Calculate route distance where possible
Return eligible nearby destinations
Return navigation results
```

The backend does not request GPS device permissions.

Do not store continuous student GPS history in PostgreSQL.

Do not send Student GPS coordinates to Gemini for route calculation.

---

# 48. Nearby API

Use POST so precise coordinates are not placed in ordinary URL query strings.

```http
POST /api/v1/student/campus/nearby
```

Example:

```json
{
  "siteId": 1,
  "latitude": 13.0000000,
  "longitude": 100.0000000,
  "group": "ALL",
  "limit": 10,
  "accessibleOnly": false
}
```

Coordinates above are synthetic examples.

Response fields:

```text
Location ID
Location Type
Name
Category
Distance Meters
Estimated Walking Minutes
Can Navigate
```

Distance and walking-time values must be returned only after valid calculation.

If a complete walking route is unavailable:

```text
walkingDistanceMeters = null
estimatedWalkingMinutes = null
canNavigate = false
```

Optionally show explicitly labeled straight-line proximity, but never present it as walking-route distance.

---

# PART J — CAMPUS WALKING NAVIGATION

# 49. Navigation Workflow

Implement:

```text
Flutter GPS
     |
     v
Student Campus Route API
     |
     v
Validate Origin
     |
     v
Resolve Nearest Valid Walking Node
     |
     v
Resolve Destination Entrance
     |
     v
Load Active Campus Graph
     |
     v
Apply Accessibility Restrictions
     |
     v
Calculate Shortest Valid Path
     |
     v
Generate Route Geometry
     |
     v
Estimate Distance / Time
     |
     v
Return Route to Flutter
```

---

# 50. Routing Algorithm

Recommended:

```text
Dijkstra's Shortest Path
```

Use edge distance as weight.

A* may be introduced later if supported by accurate map geometry and an admissible heuristic.

Do not connect graph nodes merely because they are geographically close.

Only traverse verified walking edges.

Requirements:

```text
Exclude inactive nodes
Exclude deleted nodes
Exclude inactive edges
Exclude routes under construction
Respect one-way paths
Support accessible-only routing
Prefer valid destination entrances
```

A disconnected graph must return an explicit unavailable result.

Never invent a route because the user requested one.

---

# 51. Route Preview API

```http
POST /api/v1/student/campus/routes/preview
```

Example:

```json
{
  "siteId": 1,
  "origin": {
    "latitude": 13.0000000,
    "longitude": 100.0000000
  },
  "destination": {
    "type": "PLACE",
    "id": 7
  },
  "accessibleOnly": false
}
```

Coordinates are synthetic examples only.

Supported destination types:

```text
PLACE
BUILDING
ROOM
```

Room navigation stops at a verified Building entrance unless the system has an indoor routing model.

---

# 52. Route Response

Example response shape:

```json
{
  "routeId": "request-generated-id",
  "status": "ROUTE_FOUND",
  "destination": {
    "type": "PLACE",
    "id": 7,
    "name": "Example University Library"
  },
  "distanceMeters": null,
  "estimatedWalkingMinutes": null,
  "estimatedArrivalAt": null,
  "geometry": [],
  "instructions": [],
  "calculatedAt": null
}
```

Populate numeric and geometry fields only from a valid calculated route.

A successful real route must include:

```text
Measured route distance
Estimated walking time
Calculated timestamp
Polyline coordinates
Destination metadata
```

For navigation UI:

```text
Distance
Estimated Remaining Time
Arrival Time
Next Step
Route Overview
```

These values must come from the backend's actual route calculation or a clearly specified client-side progress calculation.

Do not hard-code Figma's example distance or arrival time.

---

# 53. Walking Time Calculation

Use:

```text
Estimated time =
route distance / configured walking speed
```

Example configuration:

```yaml
campus:
  navigation:
    walking-speed-meters-per-second: 1.3
    max-origin-snap-distance-meters: 75
```

Values are configurable estimates, not guaranteed walking performance.

Accessibility routing may require a different configurable walking-speed profile.

Do not store every derived walking-time value on the navigation Edge.

The route's distance is the sum of valid traversed walking edges, with appropriately modeled approach segments.

---

# 54. Turn Instructions

The Figma navigation screen contains instructions such as:

```text
Head north from your current location
```

Only generate turn instructions when valid geometry and bearings allow this.

An instruction should include:

```text
Instruction text
Distance until next maneuver
Optional bearing/direction
Associated segment
```

If the route graph lacks enough detail:

```text
Provide route overview
Do not invent turn-by-turn guidance
```

The Flutter app can update progress using its current GPS position.

No persistent PostgreSQL navigation session is required for the first version.

End Navigation is primarily a Flutter client action.

---

# 55. Navigation Errors

Required:

```text
CAMPUS_NOT_FOUND
CAMPUS_LOCATION_NOT_FOUND
CAMPUS_DESTINATION_NOT_MAPPED
CAMPUS_INVALID_COORDINATES
CAMPUS_ORIGIN_OUTSIDE_SUPPORTED_AREA
CAMPUS_NO_AVAILABLE_ROUTE
CAMPUS_ACCESSIBLE_ROUTE_NOT_FOUND
CAMPUS_ROUTE_UNDER_CONSTRUCTION
CAMPUS_NAVIGATION_GRAPH_INVALID
```

HTTP behavior:

| Condition | HTTP |
|---|---:|
| Route found | 200 |
| No valid route | 422 |
| Destination missing | 404 |
| Invalid coordinates | 400 |
| Student unauthorized | 401/403 |
| Backend unexpected error | 500 |

Follow the existing project's error response wrapper.

No raw SQL exceptions or internal graph details in client errors.

---

# PART K — ADMIN SECURITY

# 56. Admin Authorization

Reuse existing roles:

```text
SUPER_ADMIN
ADMIN
ACADEMIC_ADMIN
```

Implement permission checks consistent with current security.

Recommended:

```text
SUPER_ADMIN
→ Full campus management

ADMIN
→ Campus management based on granted permissions

ACADEMIC_ADMIN
→ Read campus data and manage only explicitly authorized academic-linked configuration
```

Do not automatically give every Admin permission to edit navigation infrastructure.

If current project supports only roles, use explicit role-based restrictions without introducing an unrelated authorization system.

Audit all campus mutations.

---

# 57. Student Authorization

Use:

```text
ROLE_STUDENT
```

For:

```text
/api/v1/student/campus/**
```

Verify the Student account is active.

Admin JWT should not automatically be treated as a Student JWT.

No Student endpoint may edit:

```text
Buildings
Rooms
Places
Facilities
Opening Hours
Navigation Nodes
Navigation Edges
```

---

# PART L — ACADEMIC INTEGRATION

# 58. Room Relationship with Academic Timetable

The existing academic system has:

```text
courses
course_sections
course_section_meetings (if implemented)
```

The Campus Room model is the preferred physical room catalog.

Target relationship:

```text
Course Section Meeting
         |
         v
Campus Room
         |
         v
Campus Building
         |
         v
Campus Navigation Destination
```

If existing:

```text
course_section_meetings.room_id
```

already references a room model, inspect it and reconcile before migration.

If meeting records still use free-text room names, retain them for compatibility while introducing a nullable FK in a separate migration after the room catalog is verified.

Do not auto-match ambiguous room labels.

Example student query:

```text
"Where is my next class?"
```

Expected backend workflow:

```text
Authenticated Student
      ↓
Next Class Service
      ↓
Course Section Meeting
      ↓
Room ID
      ↓
Building ID
      ↓
Campus Navigation Destination
```

If no mapped Room/Building exists, the assistant must state that navigation information is unavailable.

---

# PART M — FUTURE AI ASSISTANT INTEGRATION

# 59. Academic AI Campus Tools

After Campus APIs are tested, optionally expose controlled tools:

```text
search_campus_locations

get_campus_place_details

get_campus_building_details

get_campus_room_details

get_campus_opening_hours

get_campus_route
```

Architecture:

```text
Student Question
      ↓
Gemini
      ↓
Allowlisted Campus Tool
      ↓
Campus Service
      ↓
PostgreSQL / Route Graph
      ↓
Structured Result
      ↓
Gemini
      ↓
Natural-Language Answer
```

Sample requests:

```text
"Where is the registrar office?"

"Find the library."

"What facilities does the library have?"

"Is the health center open?"

"Take me to my next class."
```

Ground all campus answers in backend data.

The LLM must not create its own coordinates, walking paths or opening hours.

Never expose arbitrary SQL, map administration or route modification tools to the LLM.

If GPS is needed and unavailable, the assistant should request appropriate location permission through the mobile experience or explain the limitation.

---

# PART N — PERFORMANCE

# 60. Query Performance

Use indexes and efficient projection queries for:

```text
Building search
Room filters
Place category filtering
Mobile location search
Map marker loading
Route graph loading
```

Avoid N+1 queries for:

```text
Places → Buildings
Places → Facilities
Rooms → Buildings
Rooms → Facilities
```

Use PostgreSQL as the authoritative source.

Redis may cache:

```text
Published category lists
Campus Building lists
Campus Places
Static navigation graph
```

If caching is introduced:

- Use bounded TTLs.
- Invalidate relevant entries after Admin writes.
- Do not cache Student GPS locations.
- Do not serve stale closed-route information after a verified safety change.
- Do not make correctness dependent on Redis availability.

---

# PART O — REQUIRED TESTING

# 61. Migration Tests

Verify:

```text
[ ] All Campus tables created
[ ] Foreign keys valid
[ ] Duplicate Building Code rejected
[ ] Duplicate Room Number in same Building rejected
[ ] Same Room Number allowed in different Buildings
[ ] Duplicate Place Code rejected
[ ] Invalid latitude rejected
[ ] Invalid longitude rejected
[ ] Negative route distance rejected
[ ] Edge with same start/end rejected
[ ] Invalid opening hours rejected
[ ] Multiple primary destinations rejected
[ ] Flyway history successful
[ ] Hibernate validation successful
```

---

# 62. Admin API Tests

```text
[ ] Building CRUD
[ ] Building status
[ ] Building statistics
[ ] Room CRUD
[ ] Room filters
[ ] Room facilities
[ ] Place CRUD
[ ] Place category
[ ] Place opening hours
[ ] Place popularity
[ ] Place mobile visibility
[ ] Navigation node CRUD
[ ] Navigation edge CRUD
[ ] Destination mapping
[ ] Dependency validation on delete
[ ] Audit fields
[ ] Unauthorized mutations rejected
```

---

# 63. Student Mobile Tests

```text
[ ] Student JWT required
[ ] Campus categories returned
[ ] All locations returned correctly
[ ] Buildings filter correct
[ ] Food filter correct
[ ] Library filter correct
[ ] Student Services filter correct
[ ] Health filter correct
[ ] Parking filter correct
[ ] Search works
[ ] Place details accurate
[ ] Opening hours accurate
[ ] Facilities accurate
[ ] View Rooms accurate
[ ] Deleted records excluded
[ ] Unpublished records excluded
[ ] No GPS handled
[ ] Invalid GPS rejected
[ ] Nearby list handles disconnected destinations
```

---

# 64. Navigation Algorithm Tests

Use a deterministic small test graph.

Test:

```text
A → B → C
```

Then test alternative edges.

Required:

```text
[ ] Shortest route selected
[ ] Longer alternatives rejected
[ ] Route distance correctly summed
[ ] Estimated time calculated
[ ] One-way edges respected
[ ] Under construction edges excluded
[ ] Inactive edges excluded
[ ] Deleted nodes excluded
[ ] Accessible-only routing works
[ ] Inaccessible route excluded
[ ] Disconnected graph handled
[ ] Cross-campus nodes rejected
[ ] Missing destination handled
[ ] Route geometry returned in correct order
[ ] Student coordinates not persisted
```

Do not test only the happy path.

---

# 65. Recommended Test Technology

Use project-compatible:

```text
JUnit
Mockito
Spring Boot Test
MockMvc or WebTestClient
PostgreSQL Testcontainers
```

Do not depend on H2 as proof of PostgreSQL migration correctness.

Automated Campus tests must not call the real Gemini API.

Execute:

```bash
mvn test
mvn clean package
```

---

# PART P — POSTMAN VERIFICATION

# 66. Postman Workflow

Use test records in the development database.

First obtain Admin JWT.

Create records in this order:

```text
Campus Site
      ↓
Categories
      ↓
Facilities
      ↓
Buildings
      ↓
Rooms
      ↓
Campus Places
      ↓
Opening Hours
      ↓
Navigation Nodes
      ↓
Navigation Edges
      ↓
Destination Associations
      ↓
Graph Validation
```

Then obtain Student JWT.

Test:

```http
GET /api/v1/student/campus/categories

GET /api/v1/student/campus/locations

GET /api/v1/student/campus/places/{id}

GET /api/v1/student/campus/buildings/{id}/rooms

GET /api/v1/student/campus/map

POST /api/v1/student/campus/nearby

POST /api/v1/student/campus/routes/preview
```

Verify responses against PostgreSQL.

Test that Admin changes appear in Student reads.

Test routes after disabling a navigation edge.

Do not consider an endpoint correct merely because it returns HTTP 200.

---

# PART Q — IMPLEMENTATION SEQUENCE

# 67. Required Agent Workflow

```text
PHASE 1
Read AGENTS.md
Read spec-md/
Inspect project and migrations
        ↓
PHASE 2
Confirm Campus entity relationships
        ↓
PHASE 3
Create Flyway migrations
        ↓
PHASE 4
Run PostgreSQL migration validation
        ↓
PHASE 5
Implement Entities and Repositories
        ↓
PHASE 6
Implement Admin Buildings CRUD
        ↓
PHASE 7
Implement Admin Rooms CRUD
        ↓
PHASE 8
Implement Categories and Facilities
        ↓
PHASE 9
Implement Campus Places CRUD
        ↓
PHASE 10
Implement Opening Hours
        ↓
PHASE 11
Implement Navigation Nodes
        ↓
PHASE 12
Implement Navigation Edges
        ↓
PHASE 13
Implement Destination Mapping
        ↓
PHASE 14
Implement Graph Validation
        ↓
PHASE 15
Implement Admin Campus Map API
        ↓
PHASE 16
Implement Student Categories and Search
        ↓
PHASE 17
Implement Student Details and Rooms
        ↓
PHASE 18
Implement Student Map API
        ↓
PHASE 19
Implement Nearby Locations
        ↓
PHASE 20
Implement Shortest Path Routing
        ↓
PHASE 21
Implement Route Geometry and ETA
        ↓
PHASE 22
Add Admin/Student Security
        ↓
PHASE 23
Unit and Integration Tests
        ↓
PHASE 24
Postman Verification
        ↓
PHASE 25
Maven Build
        ↓
PHASE 26
Flutter API Integration
        ↓
PHASE 27
Future AI Campus Tool Integration
```

Complete the Flyway migration first and verify it before implementing dependent Java code.

If the agent works on this specification in one task, it must stop and report the failure at any phase that cannot be completed safely rather than skipping validation.

---

# PART R — OUT OF SCOPE

# 68. Do Not Implement

Do not build these merely because they appear related:

```text
Separate Campus Database
New Student Authentication
New Admin Authentication
RAG
Vector Database
New LLM Provider
Google Maps API Billing Integration
Live Traffic Prediction
Indoor Floor-by-Floor Routing
Continuous GPS Tracking Storage
Production Campus Data Migration
```

Indoor room-level navigation can be implemented as a later feature if verified building floor plans and indoor pathways become available.

The initial Campus feature focuses on outdoor walking paths, Building entrances, Place discovery and Room information.

---

# PART S — ACCEPTANCE CRITERIA

# 69. Completion Checklist

## Flyway

- [ ] Existing migration history inspected.
- [ ] Correct next migration versions selected.
- [ ] Normalized Campus tables created.
- [ ] PostgreSQL constraints applied.
- [ ] Foreign keys verified.
- [ ] Existing academic tables preserved.
- [ ] No demonstration data inserted.
- [ ] Flyway successful.

## Admin

- [ ] Building CRUD completed.
- [ ] Room CRUD completed.
- [ ] Campus Place CRUD completed.
- [ ] Category and Facility management completed.
- [ ] Opening Hours management completed.
- [ ] Campus Map data API completed.
- [ ] Navigation Node management completed.
- [ ] Navigation Edge management completed.
- [ ] Route validation completed.
- [ ] Dashboard statistics derived accurately.

## Mobile

- [ ] Unified Campus Locations API works.
- [ ] Search works.
- [ ] Category filters work.
- [ ] Place Details works.
- [ ] Building/Room details work.
- [ ] Facilities and Hours work.
- [ ] GPS-based Nearby API works when mapped data exists.
- [ ] Navigation API returns only valid calculated routes.
- [ ] Missing GPS and missing routes handled.
- [ ] Student-only read authorization enforced.

## Quality

- [ ] API responses match existing wrapper conventions.
- [ ] JPA entities are never returned directly.
- [ ] All Student requests require valid authorization.
- [ ] Campus mutations are audited.
- [ ] `mvn test` passes.
- [ ] `mvn clean package` passes.
- [ ] Postman verification passes.
- [ ] Existing academic and AI endpoints remain operational.

---

# 70. Expected Agent Report

After implementation, report:

```text
1. Existing backend modules inspected
2. Files created
3. Files modified
4. Actual Flyway migration versions
5. Database relationships
6. Admin endpoints implemented
7. Student endpoints implemented
8. Authentication and authorization rules
9. Map and routing implementation
10. Route validation results
11. PostgreSQL migration results
12. Automated tests
13. Postman verification
14. Maven build results
15. Academic timetable compatibility
16. Missing official GPS data or opening hours
17. Known limitations
18. Recommended next integration steps
```

Never claim that routing works with real university locations unless the graph has been populated and tested with verified coordinates and paths.

---

# 71. Final Architecture

```text
                    ADMIN WEB
                        |
                        v
              Campus Management API
                        |
         +--------------+---------------+
         |              |               |
     Buildings        Places        Navigation
         |              |               |
       Rooms        Facilities      Nodes / Edges
         |              |               |
         +--------------+---------------+
                        |
                        v
               PostgreSQL Database
                        ^
                        |
               Campus Query Services
                        ^
                        |
               Student Campus APIs
                        ^
                        |
                 Flutter Mobile
                        |
          +-------------+--------------+
          |             |              |
       Search       View Details     Navigation
          |             |              |
          +-------------+--------------+
                        |
                        v
               Student Campus Screen
```

**Final goal:** The Admin Web controls verified university campus data, and the Flutter mobile application displays that same data for campus discovery, location details, building rooms and walking navigation.

The existing AI Assistant can later call these Campus services through allowlisted tools, without changing the core Campus database design.