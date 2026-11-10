#!/usr/bin/env bash
# Rebuilds app/src/main/assets/adult-domains.txt from StevenBlack/hosts (porn-only extension, MIT licence).
# Entries already covered by a listed parent domain are dropped, since the filter blocks every subdomain anyway.
set -euo pipefail
cd "$(dirname "$0")/.."
SRC=https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts
OUT=app/src/main/assets/adult-domains.txt
tmp=$(mktemp)
curl -fsSL "$SRC" -o "$tmp"
date=$(grep -m1 '^# Date:' "$tmp" | sed 's/^# Date: //')
{
  echo "# Adult sites blocked by the Focus Home web filter. One domain per line; each also blocks its subdomains."
  echo "# Source: $SRC"
  echo "# Source date: $date"
  echo "# From StevenBlack/hosts, MIT License, Copyright (c) Steven Black. https://github.com/StevenBlack/hosts/blob/master/license.txt"
  echo "# Regenerate with scripts/update-adult-blocklist.sh."
  grep '^0\.0\.0\.0 ' "$tmp" | awk '{print tolower($2)}' | sed 's/\.$//' | grep -vE '^(0\.0\.0\.0|localhost)$' | sort -u \
    | awk -F. '{print NF"\t"$0}' | sort -n -k1,1 | cut -f2 \
    | awk '{n=split($0,p,"."); s=p[n]; dup=0; for(i=n-1;i>=1;i--){ if(s in seen){dup=1;break}; s=p[i]"."s } if(!dup && !($0 in seen)){seen[$0]=1; print}}' \
    | sort
} > "$OUT"
rm -f "$tmp"
echo "$(grep -vc '^#' "$OUT") domains written to $OUT"
