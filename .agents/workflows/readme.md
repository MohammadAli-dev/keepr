---
description: Automatically update README.md to reflect recent features, updates, fixes, and architectural improvements.
---

1. Analyze the current codebase and project status files (e.g., `README.md`, `SPRINT.md`, `SPRINT5.md`).
2. Review the diff of all changes made during the latest sprint or since the last commit (`git diff HEAD~1` or similar).
3. Enhance the `README.md` to reflect the latest state:
    - Update the **"Current Product State"** section to reflect newly working features.
    - Add/update any **"High-Level Architecture"** or **"Detailed Architecture Breakdown"** details.
    - Record any new **"Core Design Principles"** if they were established.
    - Update **"System Capabilities"** and **"System Limitations"**.
4. **Non-Destructive Update**: Ensure that the structure and formatting of the existing `README.md` are preserved. Only add or update relevant information to ensure it remains the Single Source of Truth.
5. Provide a summary of the additions/modifications made to the `README.md`.
