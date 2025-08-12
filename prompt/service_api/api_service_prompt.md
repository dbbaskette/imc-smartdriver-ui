⚙️ AI Code Assistant: API Service Prompt
Objective: Build a production-ready, high-performance API service.

I. FOUNDATION
This prompt inherits all principles from _base/core_prompt.md. Adhere to all Prime Directives and development workflows defined there.

II. TECHNICAL STACK & PRACTICES (JAVA)
Language & Framework: Use Java 21+ and Spring Boot 3.5+.

Build Tool: Use Maven for dependency management.

Architecture: Design clean, RESTful APIs. Prefer explicit wiring over reflection or annotation magic.

Code Structure: Organize code into packages by domain/feature. Use small, pure functions and services.

III. TESTING & SECURITY
Testing Strategy: Use TDD (write tests first with JUnit/Mockito) for complex logic. Write tests after implementation for simple components.

Security Rules: Always validate inputs. Never construct SQL directly; use prepared statements or a JPA/ORM provider. Use SecureRandom for any cryptographic or security-sensitive randomness.
