#!/usr/bin/env python3
"""Writes the test vectors of core-math-java from a CORE-MATH checkout.

Build the reference printer first (AGENTS.md section 5):

    sh tools/cmref/build.sh        # CORE_MATH defaults to ../core-math

then

    python tools/mkvectors.py sin  # both formats
    python tools/mkvectors.py --all

The files go to src/test/resources/vectors/binary32/<name>.txt and
.../binary64/<name>.txt, with a header naming the upstream commit, the
reference build and how each section was produced.  Every row is one input
(the arguments in the C order) and the bits the C entry point returns.
"""

import math
import os
import pathlib
import struct
import subprocess
import sys
import textwrap

ROOT = pathlib.Path(__file__).resolve().parent.parent
UPSTREAM = pathlib.Path(os.environ.get("CORE_MATH", ROOT.parent / "core-math"))
VECTORS = ROOT / "src" / "test" / "resources" / "vectors"

COMMIT = "0ce0cbc15935e24c656280e5a46c34e89f301b7a"
REFERENCE_CC = "gcc 13.2.0, -O2 -ffp-contract=off -mfma"

SEED = 0x9E3779B97F4A7C15
RANDOM_CASES = 1000
WORST_CASES = 1000
M64 = (1 << 64) - 1

# one entry per CORE-MATH function: how many arguments the C entry point takes
# and which formats have it
KIND = {
    "sin": "unary", "cos": "unary", "tan": "unary", "exp": "unary",
    "log": "unary", "log2": "unary", "log10": "unary", "atan": "unary",
    "asin": "unary", "acos": "unary", "sinh": "unary", "cosh": "unary",
    "tanh": "unary", "cbrt": "unary", "exp2": "unary", "log1p": "unary",
    "expm1": "unary", "sinpi": "unary", "cospi": "unary", "asinh": "unary",
    "acosh": "unary", "atanh": "unary", "erf": "unary", "erfc": "unary",
    "tanpi": "unary", "atanpi": "unary", "asinpi": "unary", "acospi": "unary",
    "rsqrt": "unary", "exp10": "unary", "exp2m1": "unary",
    "exp10m1": "unary", "log10p1": "unary", "log2p1": "unary",
    "tgamma": "unary", "lgamma": "lgamma", "sincos": "sincos",
    "atan2": "binary", "hypot": "binary", "pow": "binary",
    "atan2pi": "binary", "compound": "binary32",
}

# the comparison thresholds of each translated function, for the file header
THRESHOLDS = {
    "sin": {
        32: "compute(): ax > 0x99000000, ax < 0x73000000, ax < 0x66000000, "
            "ax < 0x822d97c8, ax == 0x7e75b8a2, ax == 0x7f4f0654, "
            "ax == 0x8c333330 and ax >= 0xff<<24, with ax = u<<1; the rows of "
            "as_sinf_database()",
        64: "compute(): e < 0x3ff-26, e < 0x3ff+31 and e == 0x7ff, with "
            "e = (t>>52)&0x7ff; crSinModerate(): |x| < 0x1p-16; the "
            "0x1.7137449123ef6p-26 bound of the small path",
    },
}

COLUMNS = {
    "unary": "input output",
    "binary": "y x result",
    "binary32": "y x result",
    "sincos": "input sin cos",
    "lgamma": "input result signgam",
}

DRAWS = {
    "unary": 1,
    "binary": 2,
    "binary32": 2,
    "sincos": 1,
    "lgamma": 1,
}


def cref_path():
    for name in ("cmref", "cmref.exe"):
        path = ROOT / "tools" / "cmref" / name
        if path.exists():
            return path
    sys.exit("cmref is not built: run sh tools/cmref/build.sh")


def f32(value):
    return int.from_bytes(struct.pack(">f", value), "big")


def f64(value):
    return int.from_bytes(struct.pack(">d", value), "big")


def hx(bits, width):
    return "%016x" % bits if width == 64 else "%08x" % bits


def uniq(rows):
    seen = set()
    out = []
    for row in rows:
        if row not in seen:
            seen.add(row)
            out.append(row)
    return out


def specials(width):
    """AGENTS.md 5.1: the signed zeros, the infinities, NaNs with several
    payloads, the smallest subnormal and the largest finite value."""
    if width == 64:
        return [
            (0x0000000000000000,), (0x8000000000000000,),
            (0x7ff0000000000000,), (0xfff0000000000000,),
            (0x7ff8000000000000,), (0x7ff0000000000001,),
            (0xfff8000000000001,), (0x7fffffffffffffff,),
            (0x0000000000000001,), (0x8000000000000001,),
            (0x7fefffffffffffff,), (0x3ff0000000000000,),
            (0xbff0000000000000,),
        ]
    return [
        (0x00000000,), (0x80000000,), (0x7f800000,), (0xff800000,),
        (0x7fc00000,), (0x7f800001,), (0xffc00001,), (0x7fffffff,),
        (0x00000001,), (0x80000001,), (0x7f7fffff,), (0x3f800000,),
        (0xbf800000,),
    ]


def signed(value, width):
    """The value and its negation, as bit patterns."""
    bits = (f64 if width == 64 else f32)(value)
    return [(bits,), ((1 << (width - 1)) | bits,)]


def straddle_ax(ax, width):
    """The floats whose doubled magnitude straddles an unsigned test of
    ax = u<<1 against ax, in both signs."""
    top = 0x7f7fffff if width == 32 else 0x7fefffffffffffff
    u = ax >> 1
    rows = []
    for du in (-1, 0, 1):
        if 0 <= u + du <= top:
            rows.append((u + du,))
            rows.append(((1 << (width - 1)) | (u + du),))
    return rows


def straddle_exponent(e, width):
    """The floats of exponent e with a zero and a full mantissa, both signs."""
    if width == 64:
        shift, mant = 52, 0xfffffffffffff
    else:
        shift, mant = 23, 0x7fffff
    rows = []
    for m in (0, mant):
        bits = e << shift | m
        rows.append((bits,))
        rows.append(((1 << (width - 1)) | bits,))
    return rows


def near(value, width, steps=2):
    """The value and its neighbours in its format, both signs."""
    out = []
    x = value
    for _ in range(steps):
        x = math.nextafter(x, -math.inf)
    for _ in range(2 * steps + 1):
        out += signed(x, width)
        x = math.nextafter(x, math.inf)
    return out


def directed(name, width):
    if name != "sin":
        raise SystemExit("mkvectors: no directed cases for %s yet" % name)
    rows = specials(width)
    if width == 32:
        # cr_sinf compares the doubled magnitude against these
        for ax in (0x99000000, 0x73000000, 0x66000000, 0x822d97c8,
                   0x7e75b8a2, 0x7f4f0654, 0x8c333330):
            rows += straddle_ax(ax, 32)
        # the rows of as_sinf_database(), the last one unreachable
        for arg in ("0x1.33333p+13", "0x1.75b8a2p-1", "0x1.4f0654p+0",
                    "0x1.2d97c8p+3"):
            rows += signed(float.fromhex(arg), 32)
        # the huge path, where cr_sinf folds the argument modulo 2pi
        rows += signed(math.ldexp(1.0, 126), 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        for e in (0x3ff - 27, 0x3ff - 26, 0x3ff + 30, 0x3ff + 31, 0x7fe):
            rows += straddle_exponent(e, 64)
        for arg in ("0x1.7137449123ef6p-26", "0x1p-16", "0x1p+31",
                    "0x1.0000000000001p+31", "0x1.54a9ad28f0a25p+225",
                    "0x1p+1000", "0x1p-1022", "0x1.fffffffffffffp+1023"):
            rows += signed(float.fromhex(arg), 64)
        rows += near(math.ldexp(1.0, 31), 64)
        rows += near(math.ldexp(1.0, -26), 64)
    return uniq(sorted(rows))


def wc_tokens(line, width):
    """The numeric tokens of one line of an upstream .wc file.

    The files spell an input as a C hexadecimal float, sometimes with the
    binary32 f suffix, sometimes as +inf, -nan, +snan or -0, and some lines
    carry trailing junk that the upstream parsers ignore.
    """
    sign = 1 << (width - 1)
    inf = 0x7ff0000000000000 if width == 64 else 0x7f800000
    nan = {"nan": 0x7ff8000000000000 if width == 64 else 0x7fc00000,
           "qnan": 0x7ff8000000000000 if width == 64 else 0x7fc00000,
           "snan": 0x7ff4000000000000 if width == 64 else 0x7fa00000}
    out = []
    for raw in line.split("#")[0].replace(",", " ").split():
        tok = raw.strip()
        core = tok.lstrip("+-").lower()
        negative = tok.startswith("-")
        if core in nan:
            out.append(nan[core] | (sign if negative else 0))
        elif core == "inf":
            out.append(inf | (sign if negative else 0))
        else:
            if tok.startswith(("0x", "-0x", "+0x")) and tok.endswith("f"):
                tok = tok[:-1]
            try:
                value = float.fromhex(tok)
            except ValueError:
                continue
            out.append((f64 if width == 64 else f32)(value))
    return out


def worst_cases(name, width, kind):
    """A uniform sample of the upstream worst-case file, if there is one.

    Returns the rows, the file path (or None) and the number of rows in it.
    """
    path = (UPSTREAM / "src" / ("binary64" if width == 64 else "binary32")
            / name / (name + ("" if width == 64 else "f") + ".wc"))
    if not path.exists():
        return [], None, 0
    rows = []
    with path.open(encoding="utf-8") as f:
        for line in f:
            line = line.split("#")[0].strip()
            if not line:
                continue
            nargs = 2 if kind in ("binary", "binary32") else 1
            fields = wc_tokens(line, width)
            if len(fields) < nargs:
                continue
            rows.append(tuple(fields[:nargs]))
    if not rows:
        return [], path, 0
    step = max(1, len(rows) // WORST_CASES)
    return rows[::step][:WORST_CASES], path, len(rows)


def xorshift(count):
    state = SEED
    out = []
    for _ in range(count):
        state ^= (state << 13) & M64
        state ^= state >> 7
        state ^= (state << 17) & M64
        out.append(state)
    return out


def random_cases(width, draws):
    rows = []
    values = xorshift(RANDOM_CASES * draws)
    for i in range(RANDOM_CASES):
        row = []
        for j in range(draws):
            value = values[i * draws + j]
            row.append(value & 0xffffffff if width == 32 else value)
        rows.append(tuple(row))
    return rows


def run(name, width, rows):
    text = "".join(" ".join(hx(v, width) for v in row) + "\n" for row in rows)
    done = subprocess.run([str(cref_path()), name, str(width)], input=text,
                          capture_output=True, text=True, check=True)
    if done.stderr:
        sys.exit("cmref: " + done.stderr.strip())
    results = [tuple(int(v, 16) for v in line.split())
               for line in done.stdout.splitlines()]
    if len(results) != len(rows):
        sys.exit("cmref returned %d rows for %d inputs"
                 % (len(results), len(rows)))
    for row, result in zip(rows, results):
        if result[:len(row)] != row:
            sys.exit("cmref echoed %r for the input %r" % (result, row))
    return results


def wrap(prefix, text, width=88):
    """A comment paragraph, wrapped so that no line goes far past 88 columns."""
    return textwrap.wrap(prefix + text, width=width, subsequent_indent="#   ",
                         break_long_words=False, break_on_hyphens=False)


def header(name, width, kind, wc_name, wc_total):
    lines = []
    if width == 64:
        source = "src/binary64/%s/%s.c (cr_%s)" % (name, name, name)
    else:
        source = "src/binary32/%s/%sf.c (cr_%sf)" % (name, name, name)
    lines.append("# core-math-java test vectors: %s, binary%d."
                 % (name + ("" if width == 64 else "f"), width))
    lines.append("# upstream: https://gitlab.inria.fr/core-math/core-math")
    lines.append("# source:   " + source)
    lines.append("# commit:   " + COMMIT)
    lines.append("# reference build: " + REFERENCE_CC
                 + " (see tools/cmref/build.sh)")
    lines.append("# columns:  " + COLUMNS[kind])
    lines.append("#")
    lines.append("# Written by tools/mkvectors.py; every result is the bits the")
    lines.append("# C entry point returns for that input.")
    lines.append("#")
    lines.append("# directed: the values of AGENTS.md 5.1, both sides of every")
    lines.append("# comparison threshold of the translation, and the exceptional")
    lines.append("# entries the entry point can reach.")
    if name in THRESHOLDS:
        lines += wrap("# thresholds: ", THRESHOLDS[name][width])
    else:
        lines.append("# thresholds: none checked yet")
    if wc_name is None:
        lines.append("# worst cases: no upstream .wc file for this format")
    else:
        lines += wrap("# worst cases: ",
                      "every %dth of the %d rows of %s"
                      % (max(1, wc_total // WORST_CASES), wc_total, wc_name))
    lines += wrap("# random: ",
                  "xorshift64 from seed 0x%016x, %s per case, %d cases, in the"
                  " order they were drawn"
                  % (SEED, "one draw" if DRAWS[kind] == 1
                     else "%d draws" % DRAWS[kind], RANDOM_CASES))
    return lines


def build(name):
    kind = KIND[name]
    widths = [32] if kind == "binary32" else [32, 64]
    for width in widths:
        wc, wc_path, wc_total = worst_cases(name, width, kind)
        sections = [
            ("directed", directed(name, width)),
            ("worst cases", wc),
            ("random", random_cases(width, DRAWS[kind])),
        ]
        inputs = [row for _, rows in sections for row in rows]
        results = run(name, width, inputs)
        out = header(name, width, kind,
                     None if wc_path is None else wc_path.name, wc_total)
        at = 0
        for title, rows in sections:
            if not rows:
                continue
            if title == "random":
                out.append("#--- random seed=0x%016x draws=%d"
                           % (SEED, DRAWS[kind]))
            else:
                out.append("#--- " + title)
            for _ in rows:
                out.append(" ".join(hx(v, width) for v in results[at]))
                at += 1
        path = (VECTORS / ("binary64" if width == 64 else "binary32")
                / (name + ".txt"))
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")
        print("%s: %d rows" % (path.relative_to(ROOT), len(inputs)))


def main(argv):
    if not argv or argv[0] in ("-h", "--help"):
        print(__doc__.strip())
        return 1
    names = sorted(KIND) if argv[0] == "--all" else argv
    for name in names:
        if name not in KIND:
            sys.exit("unknown function: " + name)
        build(name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
