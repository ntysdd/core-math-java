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
import re
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
    "cos": {
        32: "compute(): ax > 0x99000000, ax < 0x74000000, ax < 0x82a41896 "
            "and ax == 0x812d97c8, with ax = u<<1; asCosfBig(): "
            "ax >= 0xff<<24; the rows of as_cosf_database()",
        64: "compute(): e == 0x7ff and |x| <= 0x1.6a09e667f3bccp-27, with "
            "e = (t>>52)&0x7ff",
    },
    "tan": {
        32: "compute(): e < 127+28, e < 115, e < 102 and e < 0xff, with "
            "e = (u>>23)&0xff; the rounding test against the rows of st[]",
        64: "compute(): e == 0x7ff and |x| <= 0x1.d12ed0af1a27ep-27, with "
            "e = (t>>52)&0x7ff",
    },
    "exp": {
        32: "compute(): ax > 0x8562e42e, ax < 0x6f93813e and ax >= 0xff<<24 "
            "with ax = u<<1; u > 0xc2ce8ec0 and u > 0x42b17217 on the raw "
            "bits; the rounding test ub != lb",
        64: "compute(): |x| <= 0x1p-54, |x| >= 0x1.62e42fefa39fp+9, "
            "|x| >= 0x1.74910d52d3052p+9 and x < -0x1.6232bdd7abcd2p+9; "
            "the rounding test ub != lb",
    },
    "log": {
        32: "compute(): ux < 1<<23, ux >= 0x7f800000 and ux == 127<<23; "
            "the rounding test ub != lb and |x-1| < 0x1p-10 of the slow path",
        64: "compute(): e = (v>>52)-0x3ff against 0x400 and -0x3ff, and x = 1; "
            "the rounding test left == right",
    },
    "log2": {
        32: "compute(): ux >= 0x7f800000, ux < 1<<23 and m == 0 (powers of "
            "two); the rounding test lb == ub",
        64: "compute(): ex == 0, ex >= 0x7ff and m == 0 (powers of two); the "
            "rounding test lb == ub",
    },
    "log10": {
        32: "compute(): ux >= 0x7f800000, ux < 0x00800000, ux == 0 and "
            "ux == st[(ux>>24)&0xf] (the powers of ten); the rounding test",
        64: "compute(): e = (v>>52)-0x3ff against 0x400 and -0x3ff, and "
            "v == pow10[n] (the powers of ten); the rounding test",
    },
    "atan": {
        32: "compute(): ta = u&0x7fffffff against 0x4c700518 and "
            "0x7f800000, e against 127-13 and 127-25, and the |x| >= 1 "
            "switch of the reduction",
        64: "compute(): |x| against 0x1.b21c475e6362ap-8, 2^-27, "
            "0x1.2ded8e34a9035p+7, 0x1.d02967c31cdb5p+53 and 0x7ff<<52; "
            "asAtanRefine2(): i == 0 and i == 128 of the reduction",
    },
    "asin": {
        32: "compute(): ax = u<<1 against 0x7f<<24, 0x7ec29000, 115<<24 and "
            "0x7e<<24, and the entries 0x7e55688a and 0x7e107434 "
            "(0x1.75b8a2p-1 and 0x1.1f4b64p-1) of the accurate path",
        64: "compute(): ax = ix<<1 against 0x7fc0<<48, 0x7fe0<<48, "
            "0xffe0<<48 and 0x1.7137449123ef6p-26",
    },
    "acos": {
        32: "compute(): ax = u<<1 against 0x7f<<24, 0x7ec2a1dc, 0x40000000 "
            "and 0x7e<<24, and the entries 0x328885a3 and 0x39826222 of the "
            "accurate path",
        64: "compute(): ax = ix<<1 against 0x7fc0<<48, 0x7fe0<<48, "
            "0xffe0<<48, 2^-15 and x0 = 0x1.cb3b3869747f4p-55",
    },
    "sinh": {
        32: "compute(): ax = u<<1 against 0x8565a9f8, 0xff<<24, 0x7c000000, "
            "0x74250bfe (the stUarg entry) and 0x66000000",
        64: "compute(): |x| against 0.25, x0 = 0x1.7137449123ef7p-26, "
            "0x1.633ce8fb9f87dp+9, 5 and 36.736801; the rows of "
            "as_sinh_database()",
    },
    "cosh": {
        32: "compute(): ax = u<<1 against 0x8565a9f8, 0xff<<24, 0x7c000000, "
            "0x74000000 and 0x66000000",
        64: "compute(): |x| against 0.125, 2^-26, 0x1.633ce8fb9f87dp+9, 5 and "
            "36.736801; the rows of as_cosh_database()",
    },
    "tanh": {
        32: "compute(): e against 115 and 102, and ux<<1 against "
            "0x41102cb3<<1",
        64: "compute(): |x| against 0x1.30fc1931f09cap+4, 3.683, 0.25 and "
            "0x1.d12ed0af1a27fp-27",
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


def straddle_bits(bits, width):
    """The bit patterns around a constant the translation compares raw bits
    with, in the unsigned order of the C comparison."""
    sign = 1 << (width - 1)
    top = (1 << width) - 1
    rows = []
    for d in (-1, 0, 1):
        value = bits + d
        if 0 <= value <= top:
            rows.append((value,))
    return rows


def straddle_magnitude(bits, width):
    """The same, for a comparison of the magnitude, in both signs."""
    sign = 1 << (width - 1)
    return straddle_bits(bits, width) + [(sign | b,) for b, in straddle_bits(bits, width)]


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


def sin_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_sinf compares the doubled magnitude against these
        for ax in (0x99000000, 0x73000000, 0x66000000, 0x822d97c8,
                   0x7e75b8a2, 0x7f4f0654, 0x8c333330, 0xff << 24):
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
    return rows


def cos_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_cosf compares the doubled magnitude against these
        for ax in (0x99000000, 0x74000000, 0x82a41896, 0x812d97c8, 0xff << 24):
            rows += straddle_ax(ax, 32)
        # the rows of as_cosf_database()
        for arg in ("0x1.2d97c8p+2", "0x1.4555p+51", "0x1.48a858p+54",
                    "0x1.3170fp+63", "0x1.2b9622p+67"):
            rows += signed(float.fromhex(arg), 32)
        # the moderate path below 2^-11 and the huge path above 2^26
        rows += near(math.ldexp(1.0, -11), 32)
        rows += signed(math.ldexp(1.0, 126), 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_cos: |x| <= 0x1.6a09e667f3bccp-27, and the exponent 0x7ff cases
        rows += straddle_magnitude(0x3e46a09e667f3bcc, 64)
        for e in (0x3ff - 27, 0x3ff - 26, 0x7fe):
            rows += straddle_exponent(e, 64)
        for arg in ("0x1.6a09e667f3bccp-27", "0x1.6a09e667f3bcdp-27",
                    "0x1p+1000", "0x1.fffffffffffffp+1023"):
            rows += signed(float.fromhex(arg), 64)
        rows += near(math.ldexp(1.0, -27), 64)
    return rows


def tan_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_tanf: e = (u>>23)&0xff against 127+28, 115, 102 and 0xff
        for e in (0x7f + 27, 0x7f + 28, 114, 115, 101, 102, 0xfe):
            rows += straddle_exponent(e, 32)
        # the rows of the st[] table of the rounding test
        for arg in ("0x1.143ec4p+0", "0x1.ada6aap+27", "0x1.af61dap+48",
                    "0x1.0088bcp+52", "0x1.f90dfcp+72", "0x1.cc4e22p+85",
                    "0x1.a6ce12p+86", "0x1.6a0b76p+102"):
            rows += signed(float.fromhex(arg), 32)
        # the huge path above 2^28 and the small path below 2^-13
        rows += signed(math.ldexp(1.0, 127), 32)
        rows += signed(3.4028234663852886e38, 32)
        rows += near(math.ldexp(1.0, -13), 32)
    else:
        # cr_tan: |x| <= 0x1.d12ed0af1a27ep-27, and the exponent 0x7ff cases
        rows += straddle_magnitude(0x3e4d12ed0af1a27e, 64)
        for e in (0x3ff - 27, 0x3ff - 26, 0x7fe):
            rows += straddle_exponent(e, 64)
        for arg in ("0x1.d12ed0af1a27ep-27", "0x1.d12ed0af1a27fp-27",
                    "0x1p+1000", "0x1.fffffffffffffp+1023"):
            rows += signed(float.fromhex(arg), 64)
        rows += near(math.ldexp(1.0, -27), 64)
    return rows


def exp_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_expf: ux = u<<1 against these, then u on the raw bits
        for ax in (0x8562e42e, 0x6f93813e, 0xff << 24):
            rows += straddle_ax(ax, 32)
        for bits in (0xc2ce8ec0, 0x42b17217):
            rows += straddle_bits(bits, 32)
        for arg in ("0x1.93813ep-16", "0x1.62e42ep+6", "0x1.9d1d8p+6"):
            rows += signed(float.fromhex(arg), 32)
    else:
        # cr_exp: |x| <= 0x1p-54, |x| >= 0x1.62e42fefa39fp+9 and the two
        # underflow boundaries, all on the magnitude bits
        for bits in (0x3c90000000000000, 0x40862e42fefa39f0,
                     0x40874910d52d3052, 0x7ff0000000000000):
            rows += straddle_magnitude(bits, 64)
        rows += straddle_bits(0xc086232bdd7abcd2, 64)
        for arg in ("0x1p-54", "0x1.62e42fefa39fp+9", "0x1.74910d52d3052p+9",
                    "0x1.6232bdd7abcd2p+9", "0x1.fffffffffffffp+1023"):
            rows += signed(float.fromhex(arg), 64)
    return rows


def log_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_logf: ux < 1<<23 (subnormal) or ux >= 0x7f800000 (inf, NaN and
        # every negative), and ux == 127<<23 (x = 1)
        for bits in (0x00800000, 0x7f800000):
            rows += straddle_bits(bits, 32)
        rows += straddle_bits(0x3f800000, 32)
        for arg in ("0x1p-10", "0x1.fffffep-1", "0x1.000002p+0"):
            rows += signed(float.fromhex(arg), 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_log: e = (v>>52) - 0x3ff against 0x400 and -0x3ff (subnormal)
        for e in (0, 1, 0x3fe, 0x3ff, 0x7fe, 0x7ff):
            rows += straddle_exponent(e, 64)
        rows += straddle_bits(0x3ff0000000000000, 64)
        for arg in ("0x1.fffffffffffffp-1", "0x1.0000000000001p+0",
                    "0x1.fffffffffffffp+1023"):
            rows += signed(float.fromhex(arg), 64)
    return rows


def log2_cases(width):
    rows = specials(width)
    if width == 32:
        for bits in (0x00800000, 0x7f800000):
            rows += straddle_bits(bits, 32)
        # m == 0: the exact powers of two, where the result is the exponent
        for e in (-126, -1, 0, 1, 127):
            rows += signed(math.ldexp(1.0, e), 32)
        rows += straddle_bits(0x3f800000, 32)
        for arg in ("0x1.fffffep-1", "0x1.000002p+0"):
            rows += signed(float.fromhex(arg), 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        for e in (0, 1, 0x3fe, 0x3ff, 0x7fe, 0x7ff):
            rows += straddle_exponent(e, 64)
        for e in (-1022, -1, 0, 1, 1023):
            rows += signed(math.ldexp(1.0, e), 64)
        rows += straddle_bits(0x3ff0000000000000, 64)
        for arg in ("0x1.fffffffffffffp-1", "0x1.0000000000001p+0",
                    "0x1.fffffffffffffp+1023"):
            rows += signed(float.fromhex(arg), 64)
    return rows


def log10_cases(width):
    rows = specials(width)
    if width == 32:
        for bits in (0x00800000, 0x7f800000):
            rows += straddle_bits(bits, 32)
        # the exact powers of ten of the st[] table, 10^0 to 10^10
        for n in range(11):
            rows += signed(float("1e%d" % n), 32)
        rows += straddle_bits(0x3f800000, 32)
        for arg in ("0x1.fffffep-1", "0x1.000002p+0"):
            rows += signed(float.fromhex(arg), 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        for e in (0, 1, 0x3fe, 0x3ff, 0x7fe, 0x7ff):
            rows += straddle_exponent(e, 64)
        # the exact powers of ten of the pow10[] table, 10^0 to 10^22
        for n in range(23):
            rows += signed(float("1e%d" % n), 64)
        rows += straddle_bits(0x3ff0000000000000, 64)
        for arg in ("0x1.fffffffffffffp-1", "0x1.0000000000001p+0",
                    "0x1.fffffffffffffp+1023"):
            rows += signed(float.fromhex(arg), 64)
    return rows


def atan_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_atanf: ta = u&0x7fffffff against 0x4c700518 and 0x7f800000,
        # then e = (u>>23)&0xff against 127-13 and 127-25
        rows += straddle_bits(0x4c700518, 32)
        rows += straddle_magnitude(0x7f800000, 32)
        for e in (0x7f - 13, 0x7f - 25):
            rows += straddle_exponent(e, 32)
        rows += signed(1.0, 32)
        rows += near(1.0, 32)
    else:
        # cr_atan: |x| against 0x1.b21c475e6362ap-8, 2^-27,
        # 0x1.2ded8e34a9035p+7, 0x1.d02967c31cdb5p+53 and the NaN bound
        for bits in (0x3f7b21c475e6362a, 0x3e40000000000000,
                     0x4062ded8e34a9035, 0x434d02967c31cdb5,
                     0x7ff0000000000000):
            rows += straddle_magnitude(bits, 64)
        rows += signed(1.0, 64)
        rows += near(1.0, 64)
    return rows


def asin_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_asinf: ax = u<<1 against 0x7f<<24, 0x7ec29000, 115<<24 and
        # 0x7e<<24, and the two exceptional entries of the accurate path
        for ax in (0x7f << 24, 0x7ec29000, 115 << 24, 0x7e << 24):
            rows += straddle_ax(ax, 32)
        for ax in (0x7e55688a, 0x7e107434):
            rows += straddle_ax(ax, 32)
        rows += signed(1.0, 32)
        rows += near(1.0, 32)
        rows += near(-1.0, 32)
    else:
        # cr_asin: ax = ix<<1 against 0x7fc0<<48 (|x| > 0.5), 0x7fe0<<48
        # (|x| >= 1), 0xffe0<<48 (NaN) and 0x1.7137449123ef6p-26
        for ax in (0x7fc0000000000000, 0x7fe0000000000000, 0xffe0000000000000,
                   0x7cae26e892247dec):
            rows += straddle_ax(ax, 64)
        rows += signed(1.0, 64)
        rows += near(1.0, 64)
        rows += near(-1.0, 64)
    return rows


def acos_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_acosf: ax = u<<1 against 0x7f<<24, 0x7ec2a1dc, 0x40000000 and
        # 0x7e<<24, and the two exceptional entries compared on the raw bits
        for ax in (0x7f << 24, 0x7ec2a1dc, 0x40000000, 0x7e << 24):
            rows += straddle_ax(ax, 32)
        for bits in (0x328885a3, 0x39826222):
            rows += straddle_bits(bits, 32)
        rows += signed(1.0, 32)
        rows += near(1.0, 32)
        rows += near(-1.0, 32)
    else:
        # cr_acos: ax against 0x7fc0<<48, 0x7fe0<<48, 0xffe0<<48, 2^-15 and
        # x0 = 0x1.cb3b3869747f4p-55
        for ax in (0x7fc0000000000000, 0x7fe0000000000000, 0xffe0000000000000,
                   0x7e00000000000000, 0x791967670d2e8fe8):
            rows += straddle_ax(ax, 64)
        rows += signed(1.0, 64)
        rows += near(1.0, 64)
        rows += near(-1.0, 64)
    return rows


def c_table(name, width, marker="db[][3]"):
    """The first column of a table of the C source, both signs.

    The rows are the inputs of an exceptional database such as
    as_sinh_database(), so they belong in the vectors: they are the inputs
    where the translation deliberately departs from the generic path.
    """
    fname = name if width == 64 else name + "f"
    path = (UPSTREAM / "src" / ("binary64" if width == 64 else "binary32")
            / name / (fname + ".c"))
    text = path.read_text(encoding="utf-8")
    start = text.index(marker)
    end = text.index("};", start)
    rows = []
    for arg in re.findall(r"\{\s*(0x[0-9a-fA-F.]+p[-+]?[0-9]+)", text[start:end]):
        rows += signed(float.fromhex(arg), width)
    if not rows:
        sys.exit("mkvectors: no rows in the table %s of %s" % (marker, path))
    return rows


def sinh_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_sinhf: ax = u<<1 against 0x8565a9f8, 0xff<<24, 0x7c000000,
        # 0x74250bfe (the stUarg entry) and 0x66000000
        for ax in (0x8565a9f8, 0xff << 24, 0x7c000000, 0x74250bfe, 0x66000000):
            rows += straddle_ax(ax, 32)
        rows += signed(0.125, 32)
        rows += near(0.125, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_sinh: |x| against 0.25, x0 = 0x1.7137449123ef7p-26, the overflow
        # bound 0x1.633ce8fb9f87dp+9 and the two regime edges 5 and 36.736801
        for bits in (0x3fd0000000000000, 0x3e57137449123ef7,
                     0x408633ce8fb9f87d, 0x7ff0000000000000,
                     0x4014000000000000, 0x40425e4f7b2737fa):
            rows += straddle_magnitude(bits, 64)
        rows += signed(0.25, 64)
        rows += near(0.25, 64)
        # the rows of as_sinh_database()
        rows += c_table("sinh", 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def cosh_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_coshf: ax = u<<1 against 0x8565a9f8, 0xff<<24, 0x7c000000,
        # 0x74000000 and 0x66000000
        for ax in (0x8565a9f8, 0xff << 24, 0x7c000000, 0x74000000, 0x66000000):
            rows += straddle_ax(ax, 32)
        rows += signed(0.125, 32)
        rows += near(0.125, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_cosh: |x| against 0.125, 2^-26, the overflow bound and the two
        # regime edges
        for bits in (0x3fc0000000000000, 0x3e50000000000000,
                     0x408633ce8fb9f87d, 0x7ff0000000000000,
                     0x4014000000000000, 0x40425e4f7b2737fa):
            rows += straddle_magnitude(bits, 64)
        rows += signed(0.125, 64)
        rows += near(0.125, 64)
        # the rows of as_cosh_database()
        rows += c_table("cosh", 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def tanh_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_tanhf: e = (u>>23)&0xff against 115 and 102, and ux<<1 against
        # 0x41102cb3<<1
        for e in (101, 102, 114, 115, 0xfe):
            rows += straddle_exponent(e, 32)
        rows += straddle_ax(0x82205966, 32)
        rows += signed(1.0, 32)
        rows += near(1.0, 32)
    else:
        # cr_tanh: |x| against 0x1.30fc1931f09cap+4, inf, 3.683, 0.25 and
        # 0x1.d12ed0af1a27fp-27
        for bits in (0x40330fc1931f09ca, 0x7ff0000000000000,
                     0x400d76c8b4395810, 0x3fd0000000000000,
                     0x3e4d12ed0af1a27f):
            rows += straddle_magnitude(bits, 64)
        rows += signed(1.0, 64)
        rows += near(1.0, 64)
    return rows


BUILDERS = {"sin": sin_cases, "cos": cos_cases, "tan": tan_cases,
            "exp": exp_cases, "log": log_cases, "log2": log2_cases,
            "log10": log10_cases, "atan": atan_cases, "asin": asin_cases,
            "acos": acos_cases, "sinh": sinh_cases, "cosh": cosh_cases,
            "tanh": tanh_cases}


def directed(name, width):
    if name not in BUILDERS:
        raise SystemExit("mkvectors: no directed cases for %s yet" % name)
    return uniq(sorted(BUILDERS[name](width)))


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


def frontier(name, width):
    """The binary32 inputs where the float result and the binary64 result
    rounded to float disagree; cmref --frontier scans the whole domain."""
    if width != 32 or KIND[name] != "unary":
        return []
    done = subprocess.run([str(cref_path()), "--frontier", name],
                          capture_output=True, text=True, check=True)
    return [(int(line, 16),) for line in done.stdout.split()]


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


def header(name, width, kind, wc_name, wc_total, front):
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
    if front:
        lines += wrap("# double rounding: ",
                      "%d inputs of the binary32 domain where (float) cr_%s(x) "
                      "and cr_%sf(x) disagree, from tools/cmref --frontier; "
                      "they are among the directed cases" % (front, name, name))
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
        direct = directed(name, width)
        front = frontier(name, width)
        if front:
            direct = uniq(sorted(direct + front))
        sections = [
            ("directed", direct),
            ("worst cases", wc),
            ("random", random_cases(width, DRAWS[kind])),
        ]
        inputs = [row for _, rows in sections for row in rows]
        results = run(name, width, inputs)
        out = header(name, width, kind,
                     None if wc_path is None else wc_path.name, wc_total,
                     len(front))
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
