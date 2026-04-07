---
description: Perform a clean build, execute all tests, and report results.
---

// turbo
1. Ensure the backend infrastructure is running: `docker compose up -d`
// turbo
2. Perform a clean build and compilation: `./mvnw clean compile`
// turbo
3. Execute the full test suite: `./mvnw test`
4. Summarize the test results from `target/surefire-reports` and provide a final success/failure report.
