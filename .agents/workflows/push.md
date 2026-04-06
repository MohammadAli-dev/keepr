---
description: Stage all changes, commit with an auto-generated summary, and push to the current remote branch (no merge).
---

// turbo
1. Detect current branch: `git branch --show-current`
// turbo
2. Stage all modified and new files: `git add .`
3. Analyze the staged changes using `git diff --cached` and generate a concise, professional commit message.
// turbo
4. Commit the changes using the auto-generated message: `git commit -m "[GENERATED_MESSAGE]"`
// turbo
5. Push to the remote repository: `git push origin [current_branch]`
