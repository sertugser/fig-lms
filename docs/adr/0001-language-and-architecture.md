---
status: accepted
date: 2026-11-19
decision-makers: project team
---

# ADR-0001: Use Java 21 + Spring Boot 3 as a modular monolith, with PostgreSQL and React

## Context and Problem Statement

We are rewriting six Moodle sub-functions (identity and access, course and enrolment, advising, assignment and submission, grading, file handling) and adding one new module (automatic study plan). Moodle is a PHP monolith. No language is strong across the team, and most code will be written with AI coding assistants and then reviewed by us. We have seven weeks until the final presentation and must show measurable gains in maintainability, security and performance against Moodle.

Which language, framework and architecture style should the new system use so that AI-generated code is as likely as possible to be correct, and so that our mistakes are caught early?

## Decision Drivers

* AI agents should succeed as often as possible in the target language.
* The toolchain should catch AI mistakes before runtime (static typing, compiler).
* Module boundaries must be checkable automatically, so maintainability can be measured, not just claimed.
* Ready-made libraries for role-based access control and constraint solving (study plan module).
* A five-person team with seven weeks cannot afford distributed-systems infrastructure.

## Considered Options

* Java 21 + Spring Boot 3, modular monolith (Spring Modulith)
* C# + .NET 8, modular monolith
* TypeScript + NestJS
* Python + Django or FastAPI
* Microservices (any of the above languages)

## Decision Outcome

Chosen option: "Java 21 + Spring Boot 3, modular monolith (Spring Modulith)", with PostgreSQL as the database and React for the UI, because it is the only option where all three pieces of evidence point the same way: AI agents resolve more real issues in Java than in TypeScript or JavaScript, static typing catches part of the AI's errors at compile time, and the two tools we depend on (module-boundary verification and a constraint solver) are available for Spring Boot today.

Evidence:

* **AI success rate.** In Multi-SWE-bench, the best resolved rate was 23.44% for Java, 11.61% for TypeScript and 5.06% for JavaScript. The authors report Java second only to Python, and relate the low TS/JS results to event-driven, asynchronous code. C# is not part of the benchmark. ([Zan et al., 2025](https://arxiv.org/html/2504.02605v1))
* **Static types as a second reviewer.** About 15% of studied public JavaScript bugs would have been detected by TypeScript or Flow type annotations ([Gao, Bird, Barr, ICSE 2017](https://neverworkintheory.org/2021/09/08/to-type-or-not-to-type.html)). Constraining LLM output with a type system "reduces compilation errors by more than half" ([Mündler et al., PLDI 2025](https://arxiv.org/abs/2504.09246)).
* **Tooling.** Spring Modulith's `verify()` rejects dependency cycles between modules and any access to another module's internal packages ([Spring Modulith docs](https://docs.spring.io/spring-modulith/reference/verification.html)). Timefold provides a Spring Boot quickstart for school timetabling, close to our study plan problem ([Timefold](https://docs.timefold.ai/timefold-solver/latest/quickstart/spring-boot/spring-boot-quickstart)).
* **Monolith first.** Most successful microservice systems started as a monolith that was later split, and service boundaries are hard to get right early ([Fowler, Monolith First](https://www.martinfowler.com/bliki/MonolithFirst.html)).
* **Mainstream choice.** Java (29.4%), C# (27.8%) and TypeScript (43.6%) are all widely used, and PostgreSQL is the most used database (55.6%) ([Stack Overflow Developer Survey 2025](https://survey.stackoverflow.co/2025/technology/)). This criterion does not separate the options; it only shows that none is a risky niche choice.

### Consequences

* Good, because the compiler and Spring Modulith tests give two automatic checks on AI-generated code before a human reviews it.
* Good, because module boundaries become a CI-enforced rule (`ApplicationModules.verify()`), which we can report as a measurable maintainability improvement over Moodle.
* Good, because Spring Security, Spring Data JPA, Flyway and Testcontainers cover access control, persistence, schema versioning and integration tests without custom infrastructure.
* Good, because one deployable unit keeps Docker Compose, CI and golden master comparisons simple.
* Bad, because backend (Java) and frontend (TypeScript/React) use different languages, so team members must switch contexts.
* Bad, because Java and Spring are more verbose than Python or TypeScript, and Spring's annotations and auto-configuration have a learning curve.
* Bad, because a monolith scales as one unit; extracting a module into a separate service is left as future work.
* Neutral, because the evidence is not conclusive: the benchmark used 2024–2025 models (for example Claude 3.5/3.7 Sonnet and OpenAI o1) and measures bug fixing, not rewriting a system from scratch. We claim "the available evidence favours Java", not "Java is the best language for modernization".

### Confirmation

* CI runs `ApplicationModules.of(Application.class).verify()` on every pull request; a violation fails the build.
* Golden master tests for each preserved business rule pass against both Moodle and the new system.
* The AI log (`docs/ai-log/`) records accepted, modified and rejected AI output, so we can check whether compile-time errors were in fact caught early.

## Pros and Cons of the Options

### Java 21 + Spring Boot 3, modular monolith

* Good, because it has the highest non-Python AI resolve rate in Multi-SWE-bench.
* Good, because module-boundary verification (Spring Modulith) and a Spring Boot constraint solver (Timefold) exist.
* Bad, because it is verbose and needs a different language from the frontend.

### C# + .NET 8, modular monolith

* Good, because it is statically typed with strong access control (ASP.NET Identity, policies).
* Neutral, because modules can be separated as projects, but there is no equivalent of Spring Modulith's built-in verification in our comparison.
* Bad, because we found no comparable benchmark data on AI agents in C#.

### TypeScript + NestJS

* Good, because the same language would be used in backend and frontend.
* Bad, because AI agents scored roughly half of Java's best resolve rate (11.61% vs 23.44%).
* Bad, because module boundaries are enforced only by hand (lint rules).

### Python + Django or FastAPI

* Good, because Python was the strongest baseline in Multi-SWE-bench, and Django has mature access control.
* Bad, because dynamic typing gives the weakest compile-time check on AI output.
* Bad, because moving from one dynamic language (PHP) to another makes the modernization gain harder to show.

### Microservices

* Good, because modules could be deployed and scaled independently.
* Bad, because a five-person team would spend most of seven weeks on infrastructure (inter-service calls, distributed data, deployment) instead of measurement.

## More Information

* Full comparison and reasoning: project roadmap, section "Dil ve mimari seçimi" (language and architecture).
* Revisit this decision if a module must scale or deploy independently, or if new benchmark data on AI agents changes the language ranking. Revisiting means writing a new ADR that supersedes this one, not editing this file.
