#!/bin/bash
# Codex sandbox setup — runs before every task
# Configures git identity and remote so Codex can push autonomously.
# Requires GITHUB_TOKEN secret set in Codex Settings → Environment.

set -e

git config user.email "marek@designleaf.co.uk"
git config user.name "Marek Sima"

# Add remote only if it doesn't already exist
if ! git remote get-url origin &>/dev/null; then
  git remote add origin "https://MarekDesignLeaf:${GITHUB_TOKEN}@github.com/MarekDesignLeaf/Secretary_Android.git"
else
  # Update URL in case token changed
  git remote set-url origin "https://MarekDesignLeaf:${GITHUB_TOKEN}@github.com/MarekDesignLeaf/Secretary_Android.git"
fi

echo "✓ Git remote configured: $(git remote get-url origin | sed 's/:[^@]*@/:***@/')"
echo "✓ Git identity: $(git config user.name) <$(git config user.email)>"
