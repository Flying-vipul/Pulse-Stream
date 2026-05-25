# 🎬 PulseStream Backend API

![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-brightgreen?style=for-the-badge&logo=spring)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue?style=for-the-badge&logo=postgresql)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

**PulseStream** is an enterprise-grade, high-performance video streaming backend API. Engineered as a robust monolith, it is designed to handle complex relational data, hierarchical content delivery (Movies and TV Shows), and memory-optimized video streaming using HTTP 206 Partial Content protocols.

---

## 🚀 Advanced Architectural Features

This platform is built with a focus on high concurrency, data integrity, and strict architectural boundaries, moving beyond standard CRUD operations.

### 1. Memory-Optimized Video Streaming (HTTP 206)
Instead of loading massive `.mp4` files directly into server RAM, PulseStream utilizes Spring Boot's `ResourceRegion` to deliver video in byte-range chunks. This allows the client side to buffer efficiently and prevents server Out-Of-Memory (OOM) exceptions under heavy viewer load.

### 2. High-Performance JPA & Query Optimization
* **N+1 Query Eradication:** Utilizes `@NamedEntityGraph` and `@EntityGraph` to fetch deep relational hierarchies (e.g., fetching a Profile, its Watch History, and the associated Content) in a single optimized SQL join.
* **Composite Key Architecture:** Employs `@EmbeddedId` and `@MapsId` in intersection tables (`my_list`) to enforce strict, database-level data integrity without redundant surrogate keys.
* **Lazy Loading by Default:** All `@ManyToOne` and `@OneToMany` relationships are strictly mapped with `FetchType.LAZY` to prevent accidental data cascades.

### 3. Enterprise Validation Pipeline
Data validation is strictly decoupled from the `@Entity` layer. All incoming traffic is intercepted by immutable Data Transfer Objects (DTOs) equipped with advanced Regex and boundary constraints, backed by a global `@RestControllerAdvice` exception handler that returns standardized `400 Bad Request` JSON maps.

---

## 🧠 Domain-Driven Database Schema

The PostgreSQL 18 database is highly normalized and logically segmented into three core domains:

### 🛡️ Identity Domain
* **`users`**: Core account management, secure authentication credentials, and subscription tiers.
* **`profiles`**: Multi-profile support per user (similar to Netflix), enforcing `MaturityRating` constraints (e.g., G, PG-13, R).

### 🎞️ Content Domain
* **`content`**: The master table mapping the core media metadata (Titles, Release Dates, Media Types).
* **`seasons` & `episodes`**: Hierarchical one-to-many structures specifically mapping episodic TV show data and runtime tracking.
* **`genres`**: A many-to-many taxonomy mapped via a hidden `content_genres` bridge table.

### 📊 Interaction Domain
* **`watch_history`**: Tracks precise `stopped_at_seconds` timestamps using `@UpdateTimestamp`, powering the "Continue Watching" engine.
* **`my_list`**: Bookmark engine enforcing unique profile-to-content relationships via composite primary keys.

---

## 🛠️ Tech Stack

* **Core:** Java 25
* **Framework:** Spring Boot 4.0.6 (Web, Data JPA, Validation)
* **Database:** PostgreSQL 18
* **Build Tool:** Maven Wrapper
* **Utilities:** Lombok (Strictly controlled, avoiding `@Data` on entities)
* **Architecture Pattern:** Monolithic API / Controller-Service-Repository Pattern

---

## ⚙️ Local Setup & Installation

### Prerequisites
* JDK 25+
* PostgreSQL 18
* Maven

### 1. Clone the Repository
```bash
git clone [https://github.com/YourUsername/PulseStream.git](https://github.com/YourUsername/PulseStream.git)
cd PulseStream
