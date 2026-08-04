#!/usr/bin/env bash
#
# export.sh — Gera um dump limpo e conciso do projeto
# para análise por IAs (evita arquivos desnecessários)
#
set -euo pipefail

PROJETO_DIR="${1:-.}"
SAIDA="${2:-projeto_completo.txt}"

# ============================================================
# ARQUIVOS E DIRETÓRIOS QUE DEVEM SER IGNORADOS
# ============================================================

DIRS_EXCLUIDOS=(
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

ARQUIVOS_EXCLUIDOS=(
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

PROJETO_DIR="$(cd "$PROJETO_DIR" && pwd)"
SAIDA_ABS="$(cd "$(dirname "$SAIDA")" 2>/dev/null && pwd)/$(basename "$SAIDA")" 2>/dev/null || SAIDA_ABS="$PROJETO_DIR/$SAIDA"

: > "$SAIDA"

echo "Projeto: $PROJETO_DIR"
echo "Arquivo de saída: $SAIDA_ABS"
echo "Gerando dump limpo..."

# Monta expressão de exclusão de diretórios
FIND_PRUNE_EXPR=()
for d in "${DIRS_EXCLUIDOS[@]}"; do
    FIND_PRUNE_EXPR+=(-name "$d" -o)
done
unset 'FIND_PRUNE_EXPR[${#FIND_PRUNE_EXPR[@]}-1]'

deve_ignorar_arquivo() {
    local nome_base
    nome_base="$(basename "$1")"

    for excluido in "${ARQUIVOS_EXCLUIDOS[@]}"; do
        if [[ "$nome_base" == $excluido ]]; then
            return 0
        fi
    done
    return 1
}

total=0
ignorados=0

while IFS= read -r -d '' arquivo; do
    if deve_ignorar_arquivo "$arquivo"; then
        ignorados=$((ignorados + 1))
        continue
    fi

    # pula o próprio arquivo de saída
    if [[ "$(cd "$(dirname "$arquivo")" && pwd)/$(basename "$arquivo")" == "$SAIDA_ABS" ]]; then
        continue
    fi

    # pula arquivos binários
    if ! grep -Iq . "$arquivo" 2>/dev/null; then
        ignorados=$((ignorados + 1))
        continue
    fi

    caminho_relativo="${arquivo#"$PROJETO_DIR"/}"

    {
        echo "===================================================================="
        echo "ARQUIVO: $caminho_relativo"
        echo "===================================================================="
        cat "$arquivo"
        echo ""
        echo ""
    } >> "$SAIDA"

    total=$((total + 1))
done < <(find "$PROJETO_DIR" \( "${FIND_PRUNE_EXPR[@]}" \) -prune -o -type f -print0)

echo ""
echo "Concluído."
echo "Arquivos incluídos: $total"
echo "Arquivos ignorados: $ignorados"
echo "Saída gerada em: $SAIDA_ABS"