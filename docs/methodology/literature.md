# Literature, existing tools and originality

| Owner | Issue | Status |
|---|---|---|
| @sertugser | #20 | Draft for team review (originality statement to be agreed) |

Read on 2026-11-24. Numbers are quoted from the abstracts or documentation pages linked in each row; for the full method, read the paper.

## 1. Literature

Topics asked for in #20: legacy modernization with LLMs, behaviour-preserving migration (golden master / characterization tests) and AI-assisted code translation.

| # | Source | What they did | How they validated | What is missing for our case |
|---|---|---|---|---|
| L1 | Feathers, *Working Effectively with Legacy Code*, Prentice Hall, 2004 | Defines legacy code as code without tests and introduces **characterization tests**: tests that record what the code does today, before changing it. | The tests themselves are the check: behaviour before = behaviour after. | No AI; written for refactoring inside one code base, not for rebuilding a system in another language. |
| L2 | Fowler, [Strangler Fig](https://martinfowler.com/bliki/StranglerFigApplication.html), 2024 | Gradual modernization: build new parts next to the legacy system and move behaviour across piece by piece until the old system can be retired. | No validation method; the article notes that legacy behaviour is often hard to determine and partly unwanted. | Says *how to cut* the system, not *how to prove* the new part behaves the same. Our project name follows this idea. |
| L3 | Lachaux, Roziere et al., [Unsupervised Translation of Programming Languages](https://arxiv.org/abs/2006.03511) (TransCoder), 2020 | Neural translator trained on monolingual GitHub code; translates **functions** between C++, Java and Python. | Unit tests on a released set of 852 parallel functions; reported to outperform rule-based commercial tools. | Function level only; no repository, database or running system. |
| L4 | Pan et al., [Lost in Translation: A Study of Bugs Introduced by LLMs while Translating Code](https://arxiv.org/abs/2308.03109), ICSE 2024 | Translated 1,700 samples between C, C++, Go, Java and Python with several LLMs; built a taxonomy of 15 bug categories. | 10K+ tests and 1,748 manually labelled bugs. Only **2.1 % to 47.3 %** of translations were correct; symptom-based prompting improved results by 5.5 % on average. | Shows that AI translation needs strong checking, but checks only against tests that already exist; no business rules or requirements. |
| L5 | Yang et al., [Exploring and Unleashing the Power of LLMs in Automated Code Translation](https://arxiv.org/abs/2404.14646) (UniTrans), FSE 2024 | Generates test cases automatically, runs the translation against them and repairs failing translations in a loop (Python, Java, C++). | Execution of auto-generated tests on six translation settings. | The generated tests come from the source code, so they share its blind spots; no link to requirements. |
| L6 | Eniser et al., [Towards Translating Real-World Code with LLMs: A Study of Translating to Rust](https://arxiv.org/abs/2405.11514), 2024/25 | Translated real-world open-source code to Rust with five LLMs; tool FLOURINE repairs translations with counterexamples. | **Differential fuzzing**: same inputs to old and new code, compare outputs; no existing tests needed. Best LLM: 47 % of the benchmarks. | Closest to our golden master idea, but on standalone programs; no database state, users, roles or web flows. |
| L7 | Ibrahimzada et al., [AlphaTrans: Repository-Level Code Translation and Validation](https://arxiv.org/abs/2410.24117), FSE 2025 | Translates whole Java repositories to Python, fragment by fragment in reverse call order, including the tests. | 10 projects, 17,874 fragments: 96.4 % syntactically correct, but runtime behaviour validated for only 27.0 % and functional correctness for 25.1 %. Two developers needed about 20 h per project to fix the rest. | Even at repository level most behaviour is unvalidated, and validation depends on the project's own tests. |
| L8 | Nikolov et al., [How is Google using AI for internal code migrations?](https://arxiv.org/abs/2501.06972), 2025 | Experience report on LLM-based migrations inside Google. | None; the authors say it is not a research study and makes no comparisons. | No measured comparison of AI against manual work. |
| L9 | Ziftci et al., [Migrating Code At Scale With LLMs At Google](https://arxiv.org/abs/2504.09691), 2025 | LLM plus change-location discovery for a large migration; 39 migrations over 12 months. | 595 code changes, 74.45 % of them LLM-generated; developers **estimated** a 50 % time saving. | Time saving is an estimate, not a measurement; correctness checking is not described in the abstract. |
| L10 | Zan et al., [Multi-SWE-bench](https://arxiv.org/abs/2504.02605), 2025 | Benchmark of 1,632 real issues in Java, TypeScript, JavaScript, Go, Rust, C and C++ for AI agents. | Expert-annotated issues with tests (used in ADR-0001 for the language choice). | Measures bug fixing in an existing code base, not rebuilding a system. |

## 2. Existing tools

| Tool | What it offers for a PHP monolith like Moodle | Limits |
|---|---|---|
| General AI coding assistants (chat and agent tools) | Explain PHP code, draft Java code and tests from a file or requirement. | No built-in check that the result behaves like the legacy system; correctness depends on the prompt and the reviewer (see L4). |
| [Amazon Q Developer code transformation](https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/how-CT-works.html) | Upgrades Java applications (JDK 8/11/17 → 17/21) and libraries; rebuilds and runs the existing unit tests while fixing errors. | Java to Java only, no PHP; partial results need manual fixes; validation is limited to the existing unit tests. |
| Rector (PHP) | Rule-based automated refactoring and PHP version upgrades. | PHP to PHP; does not move code to another language or architecture. |
| Moodle's own APIs (web services, PHPUnit and Behat) | Read courses, enrolments, completion and grades from the running system over REST; Moodle's own tests describe parts of the behaviour. | Not a migration tool. We use the REST API as the **oracle** for golden master tests (`tests/golden-master/`) and Moodle's PHPUnit for the coverage baseline. |

## 3. Originality

Existing work translates code and checks it against the tests that the source project already has (L3–L5, L7), fuzzes standalone programs (L6), or reports time savings without measuring them (L8, L9). None of it combines a running legacy system as the reference with requirements written before the code. Our method differs in three ways: (1) every AI-assisted change is traced to a requirement ID extracted from the Moodle source (`REQ-<MODULE>-<NN>`, [SRS](../srs/SRS.md)) and logged with its outcome in `docs/ai-log/`; (2) preserved rules are checked by golden master tests that send the same input to the **running Moodle 5.3.1** and to the new system, including database state, roles and boundary cases; (3) we measure AI-assisted against manual work on time and review defects, and Moodle against the new system on maintainability, security, performance and usability KPIs ([KPI table](../srs/kpi.md)). The result is a modernization whose behaviour preservation is shown with evidence, not assumed.

*To be agreed by the team before the presentation (definition of done of #20).*

## Presentation slide (Nov 25)
**Related work and what is new in our method**
- AI code translation works only part of the time: 2.1–47.3 % correct translations (Pan et al., ICSE 2024); repository-level tools validate the behaviour of about a quarter of the code (AlphaTrans, FSE 2025)
- Behaviour checks in related work: existing unit tests, generated tests, or fuzzing of standalone programs
- Industry (Google, Amazon Q): large migrations, but estimated savings and Java-to-Java only
- **Our method:** requirements from the Moodle source → AI-assisted rewrite → golden master against the running Moodle → measured AI vs manual comparison and KPIs
