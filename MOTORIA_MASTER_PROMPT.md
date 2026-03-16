# Motoria.ai Master Prompt

You are working on the development of Motoria.ai.

Motoria.ai is an AI-driven automotive marketplace platform.

The goal is to build the digital infrastructure of the automotive market.

The platform must integrate:

- vehicle listings
- AI assisted publication
- vehicle photo analysis
- vehicle certification
- financing simulation
- inspection scheduling
- social media promotion
- partner photography centers
- conversational AI
- voice AI integration
- API monetization

---

# Technology Stack

Backend
Quarkus (Java)

Frontend
React + TypeScript

Database
PostgreSQL

Messaging
RabbitMQ

Cache
Redis

Storage
S3 compatible storage

Identity
WSO2 Identity Server

API Management
WSO2 API Manager

---

# Architecture Principles

Follow clean architecture.

Modules must be separated by domain.

Each module must contain:

domain  
dto  
repository  
service  
rest  
mapper  
integration  
event  
exception  

Controllers must remain thin.

Business logic must only exist in services.

Repositories must contain only persistence logic.

External integrations must be isolated.

---

# Security

Follow OWASP best practices.

Mandatory protections:

- input validation
- XSS protection
- CSRF protection
- secure authentication
- RBAC authorization
- secure file uploads
- rate limiting
- audit logging

---

# Messaging

RabbitMQ must be used for asynchronous flows.

Examples of events:

listing.created  
listing.updated  
listing.certification.requested  
listing.certification.completed  
finance.simulation.requested  
inspection.scheduled  

Each event must include:

correlation id  
retry strategy  
dead letter queue  

---

# Frontend Architecture

The frontend must be separated into:

client portal  
admin portal  
backoffice portal  

Use:

TypeScript  
React Hook Form  
TanStack Query  
reusable components  

---

# AI Capabilities

AI must support:

seller listing assistant  
buyer recommendation assistant  
vehicle benchmark comparison  
photo damage detection  
price recommendation  

AI decisions must be:

traceable  
auditable  
explainable  

---

# Voice AI

The platform integrates a voice AI agent.

Capabilities:

voice assisted listing  
voice assisted search  
voice support  

Voice must interact with backend services via APIs.

---

# Development Workflow

Agents define responsibilities.

Skills define development rules.

Prompts define specific tasks.

Agents must avoid anti patterns and maintain modular architecture.