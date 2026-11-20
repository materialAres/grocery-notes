#!/usr/bin/env bash
# Avvia Claude Code in un container che vede SOLO questa cartella di progetto.
# Uso: .sandbox/run.sh            -> claude in auto mode (skip permissions)
#      .sandbox/run.sh bash       -> shell nel container
set -euo pipefail
cd "$(dirname "$0")/.."
IMAGE=claude-sandbox-shop-stickers

if ! podman image exists "$IMAGE"; then
  podman build -t "$IMAGE" -f .sandbox/Containerfile .sandbox
fi

if [ $# -eq 0 ]; then set -- claude --dangerously-skip-permissions; fi

# Volumi nominati: login/config di Claude e cache Gradle, separati dal tuo $HOME reale
exec podman run -it --rm \
  --userns=keep-id \
  --security-opt no-new-privileges \
  --cap-drop=ALL \
  -e CLAUDE_CONFIG_DIR=/home/node/.claude \
  -v "$PWD":/work:Z \
  -v claude-sandbox-config:/home/node/.claude \
  -v claude-sandbox-gradle:/home/node/.gradle \
  -w /work \
  "$IMAGE" "$@"
