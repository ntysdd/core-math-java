#!/bin/sh
# Builds tools/cmref/cmref.c against a local CORE-MATH checkout.
#
#   CORE_MATH=/path/to/core-math sh tools/cmref/build.sh
#
# CORE_MATH defaults to a checkout next to this repository, ../core-math.
# The flags are the reference build of AGENTS.md section 5: -O2 with
# -ffp-contract=off, plus -mfma so that the __builtin_fma calls of the C
# sources become exact hardware fmas and nothing else is contracted.  The
# dialect is GNU C, because the sources use the asm keyword of the x86-64
# fast paths.
set -eu

here=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CORE_MATH=${CORE_MATH:-"$here/../../../core-math"}
CC=${CC:-gcc}
CFLAGS=${CFLAGS:--O2 -ffp-contract=off -mfma -fno-math-errno -std=gnu99}

if [ ! -d "$CORE_MATH/src/binary64" ]; then
  echo "cmref: no CORE-MATH checkout at $CORE_MATH" >&2
  echo "cmref: set CORE_MATH to the source tree" >&2
  exit 1
fi

out="$here/cmref"
case $(uname -s) in
  MINGW* | MSYS* | CYGWIN*) out="$here/cmref.exe" ;;
esac

srcs=""
for d in "$CORE_MATH"/src/binary64/*/; do
  n=$(basename "$d")
  [ "$n" = support ] && continue
  srcs="$srcs $d$n.c"
done
for d in "$CORE_MATH"/src/binary32/*/; do
  n=$(basename "$d")
  [ "$n" = support ] && continue
  srcs="$srcs $d${n}f.c"
done

# shellcheck disable=SC2086
$CC $CFLAGS -o "$out" "$here/cmref.c" $srcs -lm
echo "built $out from $CORE_MATH"
