#!/bin/zsh
set -eu
cd "${0:A:h}"
exec .venv/bin/python scripts/restore.py
