<div align="center">

<img src="https://readme-typing-svg.herokuapp.com?font=Fira+Code&weight=700&size=40&pause=1000&color=00B4D8&center=true&vCenter=true&width=800&lines=Digital+Banking+System;Distributed+Microservices+Architecture;Secure,+Scalable,+and+Fast" alt="Typing SVG" />

A modern, highly scalable digital banking system built with **Java**, **Spring Boot**, and **Apache Kafka**.

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java 17](https://img.shields.io/badge/Java_17-ED8B00?style=for-the-badge&logo=java&logoColor=white)](https://www.oracle.com/java/)
[![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-231F20?style=for-the-badge&logo=apache-kafka&logoColor=white)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)

</div>

---

## 🚀 Features

- 🔐 **API Gateway:** Centralized routing with Redis-based rate limiting.
- 💳 **Payment Processing:** Integrated with **Razorpay** for seamless transactions.
- 🛡️ **Fraud Detection:** Real-time event analysis to block suspicious transactions.
- ⚡ **Event-Driven Architecture:** Asynchronous communication powered by Apache Kafka.
- 🔔 **Real-Time Notifications:** Instant alerts for user transactions.

---

## 🏗️ Architecture

```mermaid
graph TD
    Client([📱 Client / Frontend]) -->|HTTP / REST| Gateway[🌐 API Gateway :8080]
    
    %% Services
    Gateway -->|Routes| Account[👤 Account Service :8081]
    Gateway -->|Routes| Transaction[💸 Transaction Service :8082]
    Gateway -->|Routes| Payment[💳 Payment Service :8083]
    
    %% Kafka Events
    Account -->|Publishes| Kafka{🐙 Apache Kafka}
    Transaction -->|Publishes| Kafka
    Payment -->|Publishes| Kafka
    
    %% Async Consumers
    Kafka -->|Consumes| Fraud[🚨 Fraud Detection :8084]
    Kafka -->|Consumes| Notification[🔔 Notification Service :8085]
    Kafka -->|Consumes| Transaction
    
    %% Databases
    Account --> DB1[(🐘 Account DB)]
    Transaction --> DB2[(🐘 Transaction DB)]
    Payment --> DB3[(🐘 Payment DB)]
    
    %% Third-party APIs & Caching
    Payment --> Razorpay([⚡ Razorpay API])
    Gateway -.-> Redis[(🔴 Redis)]
    Fraud -.-> Redis
```

---

## 📦 Microservices Overview

| Service | Port | Description | Tech |
| :--- | :--- | :--- | :--- |
| **API Gateway** | `8080` | Entry point, routing, and API rate limiting | Spring Cloud Gateway, Redis |
| **Account Service** | `8081` | Manages user accounts and balances | Spring Data JPA, PostgreSQL, Kafka |
| **Transaction Service** | `8082` | Handles deposits, withdrawals, and transfers | Spring Data JPA, PostgreSQL, Redis |
| **Payment Service** | `8083` | Third-party payment gateway integration | Razorpay SDK, PostgreSQL |
| **Fraud Detection** | `8084` | Monitors velocity and volume for fraud | Kafka Streams/Consumer, Redis |
| **Notification Service** | `8085` | Dispatches SMS/Email alerts asynchronously | Kafka Consumer |

---

## 🛠️ Tech Stack

- **Core:** Java 17, Spring Boot 3.x, Spring Cloud
- **Databases:** PostgreSQL (NeonDB), Redis
- **Messaging:** Apache Kafka
- **Build Tool:** Maven
- **Infrastructure:** Docker & Docker Compose

---

## 🚦 Getting Started

### Prerequisites
- JDK 17+
- Maven
- Docker & Docker Compose (for Kafka, Zookeeper, Redis)

### 1. Set Up Environment Variables
Create a `.env` file or export the following variables in your terminal to securely connect the services:

```bash
# Database Configurations
export DB_URL_ACCOUNT="jdbc:postgresql://<your-db-url>/account_db"
export DB_URL_TRANSACTION="jdbc:postgresql://<your-db-url>/transaction_db"
export DB_URL_PAYMENT="jdbc:postgresql://<your-db-url>/payment_db"
export DB_USERNAME="your-db-username"
export DB_PASSWORD="your-db-password"

# Razorpay Keys
export RAZORPAY_KEY_ID="rzp_test_xxxxxx"
export RAZORPAY_KEY_SECRET="your-razorpay-secret"
```

### 2. Start Infrastructure
Boot up the required infrastructure (Kafka and Redis) using Docker Compose:
```bash
docker-compose up -d
```

### 3. Build & Run Services
You can build and start all services using Maven. Navigate to each service directory:

```bash
# Example for Account Service
cd account-service
mvn clean install
mvn spring-boot:run
```

---

<div align="center">
  <i>Developed with ❤️ for secure and scalable banking.</i>
</div>
