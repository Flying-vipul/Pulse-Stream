# PulseStream

### Scalable Video Streaming Backend Platform

PulseStream is a production-oriented video streaming backend engineered with **Java, Spring Boot, PostgreSQL, FFmpeg, Azure Blob Storage, and Spring Security**.

The platform is designed around the core challenges of a modern streaming system: secure identity management, hierarchical media modeling, efficient database access, subscription management, cloud-based media storage, resumable media delivery, watch-state persistence, and operational tooling.

Rather than treating a streaming platform as a collection of CRUD endpoints, PulseStream focuses on the backend engineering problems that emerge when media, relational data, authentication, and external infrastructure interact inside a single system.

---

## Architecture

PulseStream currently follows a **layered monolithic architecture** based on the Controller → Service → Repository pattern.

```text
                         ┌─────────────────────┐
                         │     React Client    │
                         └──────────┬──────────┘
                                    │
                               REST / HTTP
                                    │
                                    ▼
                     ┌───────────────────────────┐
                     │       Spring Boot API     │
                     │                           │
                     │  Controllers              │
                     │  Services                 │
                     │  Security                 │
                     │  DTO / Validation         │
                     └─────────────┬─────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    │              │              │
                    ▼              ▼              ▼
               PostgreSQL       Cloud Services   FFmpeg
                    │              │              │
                    │        ┌─────┴─────┐        │
                    │        │           │        │
                    │    Azure Blob  Cloudinary   │
                    │        │                    │
                    │        │                    │
                    └────────┴────────────────────┘
```

The application deliberately keeps domain logic inside the monolith while externalizing media storage and compute-intensive media processing where appropriate.

---

## Core Capabilities

### Video Processing and Streaming

PulseStream uses **FFmpeg as the media processing engine**.

Uploaded source videos are first persisted to a temporary location so they can be consumed by FFmpeg. The application then generates multiple HLS renditions:

* 1080p
* 720p
* 480p

Each rendition is represented through its own playlist and media segments, with a master playlist coordinating the available streams.

```text
Source Video
     │
     ▼
Temporary Storage
     │
     ▼
    FFmpeg
     │
     ├───────────────┐
     ▼               ▼
  1080p            720p            480p
     │               │               │
     └───────────────┼───────────────┘
                     ▼
               HLS Playlists
                     +
               TS Media Segments
                     │
                     ▼
              Azure Blob Storage
                     │
                     ▼
               Master Playlist
```

The FFmpeg pipeline uses H.264 video encoding, AAC audio, independent HLS segments, VOD playlists, and separate bitrate configurations for each rendition. The service invokes FFmpeg through Java `ProcessBuilder`, captures its process output, waits for completion, and explicitly verifies its exit status.

A notable implementation detail is the use of `ProcessBuilder` instead of shell execution for FFmpeg invocation. This avoids Windows shell expansion problems involving FFmpeg's `%v` and `%03d` filename patterns.

---

## Cloud Media Storage

PulseStream separates application data from media storage.

After FFmpeg finishes processing a video, the generated HLS directory is uploaded to **Azure Blob Storage**, and the resulting master playlist URL is persisted in PostgreSQL.

```text
Application
     │
     ▼
FFmpeg Processing
     │
     ▼
HLS Output
 ├── master.m3u8
 ├── stream_0.m3u8
 ├── stream_1.m3u8
 ├── stream_2.m3u8
 └── *.ts segments
     │
     ▼
Azure Blob Storage
     │
     ▼
Persistent HTTPS Playback URL
```

The same pipeline is applied to both standalone movies and episodic content.

---

# Content Domain

PulseStream models media as a hierarchy rather than as flat video records.

```text
Content
 ├── Genres
 │
 └── Seasons
      └── Episodes
```

### Movies

A movie is represented directly by the `Content` entity and can reference a processed media asset.

### TV Shows

A TV show is represented as:

```text
TV Show
   │
   ├── Season 1
   │      ├── Episode 1
   │      ├── Episode 2
   │      └── Episode 3
   │
   └── Season 2
          ├── Episode 1
          └── Episode 2
```

The persistence model uses:

* `Content`
* `Season`
* `Episode`
* `Genre`

with JPA relationships designed around the hierarchy. `Season` references its parent `Content`, while `Episode` references its parent `Season`. These relationships are configured to avoid unnecessary eager loading.

---

# Database Architecture

PulseStream uses **PostgreSQL with Hibernate/JPA** for transactional application data.

The schema is logically divided into three major areas.

## Identity

```text
users
profiles
```

The `User` entity represents the primary account, while profiles allow multiple viewing identities under the same account.

Profiles contain their own:

* profile name
* avatar
* maturity rating

This mirrors the multi-profile model commonly used by consumer streaming platforms.

## Content

```text
content
genres
content_genres
seasons
episodes
```

Content uses a normalized relational structure with a many-to-many relationship between content and genres and a hierarchical relationship between content, seasons, and episodes.

## User Interaction

```text
watch_history
my_list
payments
```

Watch history stores playback position and completion state, enabling a "Continue Watching" experience.

The `MyList` relationship uses a composite key consisting of:

```text
(profile_id, content_id)
```

through JPA `@EmbeddedId` and `@MapsId`. This models the profile-to-content relationship directly and prevents duplicate list entries at the identity level.

---

# JPA and Query Engineering

The project intentionally uses `FetchType.LAZY` for relational associations where loading related entities on every query would be unnecessary.

For read paths that require related entities, PulseStream uses JPA entity graphs to define targeted fetch plans.

For example, content repository queries explicitly request the genre relationship when it is required by the API response.

The purpose of this design is to avoid the opposite extremes of:

```text
Fetch everything
```

and:

```text
Trigger database queries repeatedly while traversing entities
```

The architecture therefore separates:

```text
Default relationship behavior
        ↓
LAZY
        ↓
Endpoint-specific fetch strategy
        ↓
EntityGraph / optimized query
```

This gives individual read operations control over how much of the relational graph they retrieve.

---

# Watch History

Watch history is modeled around the viewing profile and content being watched.

The service supports both:

```text
Movie
```

and:

```text
TV Episode
```

The system stores the playback position in seconds and calculates completion using the application's completion threshold.

The "Continue Watching" path converts stored history into a frontend-oriented DTO containing:

* content ID
* title
* thumbnail
* playback position
* total duration
* episode information when applicable

This allows the frontend to render continuation cards without exposing persistence-layer entities directly.

---

# Authentication and Security

PulseStream implements a stateless security architecture using **Spring Security and JWT**.

The request lifecycle is:

```text
HTTP Request
     │
     ▼
AuthTokenFilter
     │
     ▼
Authorization: Bearer <JWT>
     │
     ▼
JWT Validation
     │
     ▼
User Lookup
     │
     ▼
Authentication Object
     │
     ▼
SecurityContext
     │
     ▼
Protected Controller
```

The application uses:

* BCrypt password hashing
* JWT authentication
* role-based authorization
* stateless sessions
* Spring Security filters
* authenticated admin routes
* public media routes
* OAuth2 login

The security configuration distinguishes between public resources, authentication routes, media endpoints, admin endpoints, and authenticated application operations.

---

# Email Verification and Account Protection

The authentication service includes an OTP-based account verification flow.

OTP handling includes:

* cryptographically secure generation
* expiration
* attempt counting
* temporary account lockout
* verification state
* password reset support

The implementation uses `SecureRandom` for OTP generation and tracks failed attempts before applying a temporary account lock.

---

# OAuth2 Authentication

PulseStream also supports OAuth2 authentication.

The OAuth2 success flow is:

```text
OAuth Provider
      │
      ▼
Spring Security OAuth2
      │
      ▼
OAuth2LoginSuccessHandler
      │
      ├── Locate existing user
      │
      └── Create user when required
              │
              ▼
          JWT Generation
              │
              ▼
        Frontend Redirect
```

Existing users retain their stored account information and roles, while first-time OAuth users can be provisioned automatically.

---

# Subscription and Payments

PulseStream integrates with **Razorpay** for subscription payments.

The subscription model supports multiple plan tiers and handles upgrades using the price difference between the current plan and the requested plan.

```text
Current Plan
     │
     ▼
Target Plan
     │
     ▼
Calculate Upgrade Amount
     │
     ▼
Razorpay Order
     │
     ▼
Payment
     │
     ▼
Signature Verification
     │
     ▼
Payment Persistence
     │
     ▼
Subscription Upgrade
```

The system also stores payment records and prevents processing the same Razorpay order more than once.

---

# API Design

The application exposes dedicated controllers for:

```text
Authentication
Content
Profiles
Watch History
Payments
Administration
System Monitoring
Migration
```

Content APIs support:

* pagination
* filtering by media type
* configurable sorting
* watchlist operations
* public catalog access

Pagination is implemented using Spring Data's `Pageable` abstraction rather than retrieving the entire catalog into application memory.

---

# Administrative Content Pipeline

Administrative media ingestion is intentionally separated into multiple steps for episodic content.

```text
Create Series
      │
      ▼
Create Season
      │
      ▼
Upload Episode
      │
      ▼
FFmpeg Processing
      │
      ▼
Azure Storage
      │
      ▼
Persist Playback URL
```

This avoids treating a TV series as a single upload operation and gives the persistence model a natural hierarchy.

---

# Data Migration Tooling

PulseStream includes dedicated migration tooling for moving historical local HLS assets into Azure Blob Storage.

The migration controller supports:

* dry-run inspection
* bulk HLS migration
* migration status reporting
* local cleanup
* detection of malformed/encoded Azure URLs
* corrective URL migration

This provides a controlled path for evolving the application's storage architecture without rebuilding all media from scratch.

---

# Operational Design

The project includes a system-monitoring endpoint for measuring application storage usage during local media operations.

The repository also contains dedicated configuration and operational components rather than placing infrastructure logic directly into controllers and entities.

The architecture is intentionally positioned so that future operational capabilities such as:

```text
metrics
distributed caching
background media workers
queue processing
distributed tracing
```

can be introduced without replacing the core domain model.

---

# Technology Stack

| Category         | Technology                              |
| ---------------- | --------------------------------------- |
| Language         | Java 21                                 |
| Framework        | Spring Boot 3.5.16                      |
| Web              | Spring MVC                              |
| Security         | Spring Security                         |
| Authentication   | JWT + OAuth2                            |
| Persistence      | Spring Data JPA / Hibernate             |
| Database         | PostgreSQL                              |
| Media Processing | FFmpeg                                  |
| Streaming Format | HLS                                     |
| Media Storage    | Azure Blob Storage                      |
| Image Storage    | Cloudinary                              |
| Payments         | Razorpay                                |
| Email            | Resend                                  |
| Build            | Maven                                   |
| Object Mapping   | ModelMapper                             |
| Utilities        | Lombok                                  |
| Testing          | Spring Boot Test / Spring Security Test |

The current build configuration explicitly targets Java 21 and Spring Boot 3.5.16.

---

# Project Structure

```text
src/main/java/com/netflix/streaming/platform
│
├── config
│   ├── AppConfig
│   ├── CloudinaryConfig
│   ├── DataSeeder
│   └── ResourceWebConfig
│
├── controller
│   ├── AdminContentController
│   ├── AuthController
│   ├── ContentController
│   ├── MigrationController
│   ├── PaymentController
│   ├── ProfileController
│   ├── SystemMonitorController
│   └── WatchHistoryController
│
├── exceptions
│   ├── APIException
│   ├── MyGlobalExceptionHandler
│   └── ResourceNotFoundException
│
├── model
│   ├── Content
│   ├── Episode
│   ├── Genre
│   ├── MyList
│   ├── MyListId
│   ├── Payment
│   ├── Profile
│   ├── Season
│   └── User
│
├── payload
│   ├── ContentDTO
│   ├── EpisodeDTO
│   ├── ProfileDTO
│   ├── WatchHistoryDTO
│   ├── PaymentRequestDTO
│   └── ...
│
├── repositories
│   ├── ContentRepository
│   ├── EpisodeRepository
│   ├── ProfileRepository
│   ├── SeasonRepository
│   ├── UserRepository
│   └── WatchHistoryRepository
│
├── security
│   ├── AuthUtil
│   ├── CorsConfig
│   ├── OAuth2LoginSuccessHandler
│   ├── WebConfig
│   ├── WebSecurityConfig
│   │
│   ├── jwt
│   │   ├── AuthEntryPointJwt
│   │   ├── AuthTokenFilter
│   │   └── JwtUtils
│   │
│   └── request / response
│
└── service
    ├── AuthService
    ├── ContentService
    ├── FileService
    ├── PaymentService
    ├── ProfileService
    ├── WatchHistoryService
    ├── VideoProcessingService
    ├── AzureBlobService
    └── ...
```

---

# Engineering Principles

PulseStream is built around several core principles:

### Separation of Responsibilities

HTTP handling, application logic, persistence, authentication, media processing, and cloud storage are represented by separate components.

### Controlled Data Access

JPA relationships use explicit fetch strategies rather than relying on unrestricted entity traversal.

### Cloud-Oriented Media Storage

The database stores media metadata and playback references while large binary assets are externalized to object storage.

### Security at the Backend Boundary

Authentication and authorization are enforced by Spring Security rather than relying on frontend restrictions.

### Transactional Persistence

Database mutations such as user management, subscription updates, watch-state persistence, and payment records are handled through transactional service boundaries.

### Evolution Through Explicit Components

Migration tooling, storage abstractions, DTOs, and service interfaces make it possible to evolve individual subsystems without rewriting the application.

---

# Local Development

## Prerequisites

* JDK 21
* PostgreSQL
* Maven
* FFmpeg

For media processing and cloud-backed features, appropriate Azure, Cloudinary, Razorpay, OAuth2, and email credentials are also required.

## Clone

```bash
git clone https://github.com/Flying-vipul/Pulse-Stream.git
cd Pulse-Stream
```

## Configure Environment

Create the required environment variables for the services used by your environment.

Typical configuration includes:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD

JWT_SECRET
JWT_EXPIRATION

AZURE_STORAGE_CONNECTION
AZURE_STORAGE_ACCOUNT
AZURE_STORAGE_CONTAINER

CLOUDINARY_CLOUD_NAME
CLOUDINARY_API_KEY
CLOUDINARY_API_SECRET

RAZORPAY_KEY_ID
RAZORPAY_KEY_SECRET

OAUTH2_CLIENT_ID
OAUTH2_CLIENT_SECRET
```

Never commit production credentials, private keys, access tokens, or provider secrets to source control.

## Run

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

---

# Design Notes

PulseStream intentionally begins as a **modular monolith**.

This keeps the domain model cohesive while allowing compute-heavy or independently scalable workloads to be separated later.

Potential future evolution includes:

```text
Current
   │
   ▼
Modular Monolith
   │
   ├── Redis for high-read workloads
   │
   ├── Asynchronous media processing
   │
   ├── Message-based job orchestration
   │
   ├── CDN-backed media delivery
   │
   ├── Distributed observability
   │
   └── Independently scalable media workers
```

The architecture is therefore designed to evolve from a well-structured monolith into a selectively distributed system rather than introducing microservices prematurely.

---

# Why PulseStream?

The project is intentionally focused on backend engineering problems that appear beyond conventional CRUD applications:

* hierarchical relational modeling
* secure authentication and authorization
* OTP-based account protection
* OAuth2 integration
* subscription and payment workflows
* video transcoding
* HLS media packaging
* multi-quality media delivery
* cloud object storage
* watch-state persistence
* JPA fetch optimization
* composite-key relational modeling
* database-driven pagination
* storage migration tooling
* operational system monitoring

The objective is not simply to reproduce a streaming application's interface.

The objective is to understand and implement the **backend systems behind the experience**.

---

# License

Distributed under the Apache License 2.0.

See `LICENSE` for more information.

---

# Author

**Vipul Choudhary**

GitHub:
https://github.com/Flying-vipul

Repository:
https://github.com/Flying-vipul/Pulse-Stream
