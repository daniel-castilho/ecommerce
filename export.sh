#!/usr/bin/env bash
#
# export.sh — Generates a clean, concise dump of the project
# for AI analysis (omits unnecessary files)
#
set -euo pipefail

PROJECT_DIR="${1:-.}"
OUTPUT="${2:-project_dump.txt}"

# ============================================================
# DIRECTORIES TO IGNORE
# ============================================================

EXCLUDED_DIRS=(
    ".git"
    ".idea"
    ".vscode"
    ".localstack"
    "target"
    "build"
    "dist"
    "node_modules"
    "out"
    ".cursor"
    ".agent"
)

EXCLUDED_FILES=(
    "project_dump.txt"
    "projeto_completo.txt"
    "conversa.txt"
    "ls.txt"
    "*.iml"
    "*.log"
    "*.class"
    "*.jar"
    "*.war"
    "*.ear"
    "*.pem"
    "*.crt"
    "*.key"
    "online-shop-deploy.bat"
    "export.sh"
)

# ============================================================

PROJECT_DIR="$(cd "$PROJECT_DIR" && pwd)"
OUTPUT_ABS="$(cd "$(dirname "$OUTPUT")" 2>/dev/null && pwd)/$(basename "$OUTPUT")" 2>/dev/null || OUTPUT_ABS="$PROJECT_DIR/$OUTPUT"

: > "$OUTPUT"

echo "Project: $PROJECT_DIR"
echo "Output file: $OUTPUT_ABS"
echo "Generating clean dump..."

# Build the directory-prune expression
FIND_PRUNE_EXPR=()
for d in "${EXCLUDED_DIRS[@]}"; do
    FIND_PRUNE_EXPR+=(-name "$d" -o)
done
unset 'FIND_PRUNE_EXPR[${#FIND_PRUNE_EXPR[@]}-1]'

should_ignore_file() {
    local base_name
    base_name="$(basename "$1")"

    for excluded in "${EXCLUDED_FILES[@]}"; do
        if [[ "$base_name" == $excluded ]]; then
            return 0
        fi
    done
    return 1
}

total=0
ignored=0

while IFS= read -r -d '' file; do
    if should_ignore_file "$file"; then
        ignored=$((ignored + 1))
        continue
    fi

    # skip the output file itself
    if [[ "$(cd "$(dirname "$file")" && pwd)/$(basename "$file")" == "$OUTPUT_ABS" ]]; then
        continue
    fi

    # skip binary files
    if ! grep -Iq . "$file" 2>/dev/null; then
        ignored=$((ignored + 1))
        continue
    fi

    relative_path="${file#"$PROJECT_DIR"/}"

    {
        echo "===================================================================="
        echo "FILE: $relative_path"
        echo "===================================================================="
        cat "$file"
        echo ""
        echo ""
    } >> "$OUTPUT"

    total=$((total + 1))
done < <(find "$PROJECT_DIR" \( "${FIND_PRUNE_EXPR[@]}" \) -prune -o -type f -print0)

echo ""
echo "Done."
echo "Files included: $total"
echo "Files ignored: $ignored"
echo "Output written to: $OUTPUT_ABS"
