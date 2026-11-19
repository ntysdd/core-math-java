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
    "cbrt": {
        32: "compute(): au = u<<1 against 1<<24 (subnormal) and 0xff<<24 "
            "(inf, NaN)",
        64: "compute(): ((e+1)&0x7ff) < 2, that is the subnormal case and the "
            "inf/NaN case, and ix == 0",
    },
    "exp2": {
        32: "compute(): the integer fast path (k in [0,9) and t<<(9+k) == 0), "
            "ux against 0x86000000, 0x65000000 and 0x79e7526e, t against "
            "0xc3000000 and 0xc3150000, and the entries 0x3b429d37, "
            "0xbcf3a937 and 0xb8d3d026",
        64: "compute(): |x| against 1024 and 0x1.71547652b82fep-54, and ix "
            "against -1075 and -1022",
    },
    "log1p": {
        32: "compute(): ux >= 0xbf800000, ax >= 0x7f800000, and |x| against "
            "0x1.1p-6 and 2^-25",
        64: "compute(): |x| against 0.0625, 2^-53, 2^-12 and 0x1.19bp-21, and "
            "the raw bits against 2^53, -1, 2^106 and 2^1024",
    },
    "expm1": {
        32: "compute(): ax = u<<1 against 0x7c400000, 0x676a09e8, "
            "0x8562e430 and 0xff<<24",
        64: "compute(): |x| against 0.25, 2^-53 and 0x1.62e42fefa39fp+9",
    },
    "sinpi": {
        32: "compute(): e == 0xff, |x| >= 2^17, |x| >= 2^23, |x| < 2^-14, "
            "and the integer test (m<<si) == 0",
        64: "compute(): e == 0x7ff, |x| >= 2^41, |x| >= 2^52, |x| <= "
            "0x1.2p-5 and |x| < 2^-960",
    },
    "cospi": {
        32: "compute(): e == 0xff, p = e-112 against 0, 32 and 64 "
            "(|x| < 2^-15, |x| >= 2^17, |x| >= 2^31), and the integer and "
            "half-integer tests",
        64: "compute(): e == 0x7ff, |x| >= 2^41, |x| >= 2^53, |x| <= 2^-12, "
            "0x1.2ccf6429be6621p-26, and the integer and half-integer tests",
    },
    "asinh": {
        32: "compute(): |x| against 0x1.02accep-2, 0x1.bb67aep-12 and the "
            "inf/NaN bound",
        64: "compute(): |x| against 0x1.bp-4, 0x1.7137449123ef7p-26, "
            "0x1.3p-6, 2^-12, 0x1.ap-26, 2^26, 1 and 2^52",
    },
    "acosh": {
        32: "compute(): x against 1, 0x1.99db23p-1 and the inf/NaN bound",
        64: "compute(): x against 1, 0x1.1e83e425aee63p+0, 111.75, 738, "
            "32896, 0x1.ap+31, 2^26 and 2^52",
    },
    "atanh": {
        32: "compute(): ax = u<<1 against 0x7a300000, 0x73713744 and "
            "0x7f000000, and |x| = 1, 1 < |x| and NaN through asSpecial()",
        64: "compute(): |x| against 1, 0.25 and 0x1.d12ed0af1a27fp-27",
    },
    "erf": {
        32: "compute(): |x| against 0x1.f5a888p+1, 0x1.cp-2 and the inf/NaN "
            "bound; the 1/16 and 1/32 grid of the polynomial table",
        64: "compute(): |x| against 0x1.7afb48dc96626p+2, the NaN bound and "
            "2^-61; the 1/16 and 1/32 grid of the polynomial table",
    },
    "erfc": {
        32: "compute(): the raw bits against -0x1.ea8f94p+1, -Inf/NaN, "
            "0x1.41bbf8p+3, +Inf/NaN, 0x1.7p-4, -0x1.d93ec4p-17 and "
            "0x1.c5bf88p-26",
        64: "compute(): the raw bits against -0x1.7744f8f74e94bp2, -Inf/NaN, "
            "0x1.b39dc41e48bfdp+4, +Inf/NaN and the 0x1.c5bf891b4ef6ap-54 "
            "bound",
    },
    "tanpi": {
        32: "compute(): e against 150<<23 (|x| > 2^23) and 0xff<<23, and the "
            "exact multiples of 1/4",
        64: "compute(): |x| against 2^-12, 2^46 and 2^53, and the exact "
            "multiples of 1/4",
    },
    "atanpi": {
        32: "compute(): e against 127+24 and 0xff, e against 127-13 and "
            "127-25, and 0x1.45f306p+124",
        64: "compute(): |x| against 0x1.b21c475e6362ap-8, 2^-54 and the NaN "
            "bound",
    },
    "asinpi": {
        32: "compute(): e >= 127 (|x| >= 1), e == 0xff, and 2^-12 where the "
            "polynomial index changes",
        64: "compute(): |x| against 2^-6, 2^-26, 2^-53, and the |x| = 1 and "
            "NaN branches",
    },
    "acospi": {
        32: "compute(): e >= 127 (|x| >= 1), e == 0xff, and 2^-12 where the "
            "polynomial index changes",
        64: "compute(): |x| against 0.75, 0x1.921fb54442d18p-54, and the "
            "|x| = 1 and NaN branches",
    },
    "rsqrt": {
        32: "compute(): ix against 0xff<<23 and 0, and the three low-bit "
            "patterns 0x2f7e2a, 0xbdf8a8 and 0x55b7bd that the correction "
            "factor of the C source serves",
        64: "compute(): the exponent against 0 (subnormal), 0x7fd and 0x7ff",
    },
    "exp10": {
        32: "compute(): ax = u<<1 against 0x84344134, 0x72adf1c6 and "
            "0xff<<24, and the raw bits against -0x1.66d3e8p+5 and "
            "0x1.344134p+5",
        64: "compute(): |x| against 0x1.34413509f79fep+8, inf and "
            "0x1.439b746e36b52p+8",
    },
    "exp2m1": {
        32: "compute(): the raw bits against -25, 128 and the NaN bound, and "
            "the eight small-argument bounds of the polynomial ladder",
        64: "compute(): the raw bits against -54, 1024, -Inf/NaN, 2^-51, "
            "2^-54 and 0.125",
    },
    "exp10m1": {
        32: "compute(): the raw bits against -7.52575, the magnitude against "
            "the overflow bound, and the nine-step ladder of small arguments",
        64: "compute(): the raw bits against -0x1.041704c068efp+4, the "
            "overflow bound, 2^-54, 0.0625, 2^-46 and -Inf/NaN",
    },
    "log10p1": {
        32: "compute(): the raw bits against -1, +Inf and NaN, the magnitude "
            "against 2^-25 and 2^-11, and the powers of ten of st[]",
        64: "compute(): x <= -1, x = -1, x = 0, the NaN/Inf branch and the "
            "0x16a09e667f3bcd bound of the reduction",
    },
    "log2p1": {
        32: "compute(): the raw bits against -1, +Inf and NaN, the magnitude "
            "against 0.0234375 and 0x1p-126*ln(2)",
        64: "compute(): x <= -1, x = -1, x = 0, the NaN/Inf branch and the "
            "0x16a09e667f3bcd bound of the reduction",
    },
    "tgamma": {
        32: "compute(): ax = u<<1 against 2^-18, and the integers and "
            "half-integers where gamma is exact or a pole",
        64: "compute(): |x| against 2^-112 and 2^-1024, the integers and "
            "half-integers, and the rows of as_tgamma_database()",
    },
    "lgamma": {
        32: "compute(): the integer cases and the 0x401ceccb, 0x401d95ca, "
            "0x402f95c2, 0x40301b93, 0x40492009 and 0x404940ef borders",
        64: "compute(): the integer cases, the exponent bounds of the "
            "reduction, and the rows of as_lgamma_database()",
    },
    "sincos": {
        32: "compute(): ax = u<<1 against 0x822d97c8, 0x99000000, "
            "0x73000000 and 0x66000000, and the multiples of pi/2",
        64: "compute(): |x| against 0x1.6a09e667f3bccp-27 and the exponent "
            "bounds of the reduction, and the multiples of pi/2",
    },
    "atan2": {
        32: "the quadrants and the axes of the two arguments, and the "
            "special values in either position",
        64: "the quadrants and the axes of the two arguments, and the "
            "special values in either position",
    },
    "hypot": {
        32: "the exact triples, the special values in either position and "
            "0x47efffffe0000000",
        64: "the exact triples, the subnormal pairs, the special values in "
            "either position and the 2^-52 ratio bounds",
    },
    "pow": {
        32: "the IEEE 754 special cases of pow with odd and even exponents",
        64: "the IEEE 754 special cases of pow with odd and even exponents",
    },
    "atan2pi": {
        32: "the quadrants and the axes of the two arguments, and the "
            "special values in either position",
        64: "the quadrants and the axes of the two arguments, and the "
            "special values in either position",
    },
    "compound": {
        32: "compound(x, 0), compound(0, y) and the integer exponents",
    },
}

COLUMNS = {
    "unary": "input output",
    "binary": "y x result",
    "binary32": "y x result",
    "sincos": "input sin cos",
    "lgamma": "input result signgam, the 32-bit pattern of the int, "
              "so 00000001 is 1 and ffffffff is -1",
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
    for arg in re.findall(r"\{\s*([-+]?0x[0-9a-fA-F.]+p[-+]?[0-9]+)", text[start:end]):
        value = float.fromhex(arg)
        rows.append(((f64 if width == 64 else f32)(value),))
        rows.append(((f64 if width == 64 else f32)(-value),))
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


def cbrt_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_cbrtf: au = u<<1 against 1<<24 (subnormal) and 0xff<<24 (inf, NaN)
        for ax in (1 << 24, 0xff << 24):
            rows += straddle_ax(ax, 32)
        for v in (1.0, 2.0, 8.0, 0.5):
            rows += signed(v, 32)
            rows += near(v, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_cbrt: ((e+1)&0x7ff) < 2 selects the subnormal and the inf/NaN case
        for e in (0, 1, 0x7fe, 0x7ff):
            rows += straddle_exponent(e, 64)
        for v in (1.0, 2.0, 4.0, 8.0, 0.5):
            rows += signed(v, 64)
            rows += near(v, 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def exp2_cases(width):
    rows = specials(width)
    if width == 32:
        # the exact integers of the fast path, then the boundaries of the rest
        for i in (1, 2, 3, 8, 16, 127, 128, 255, 256, 511, 512,
                  -1, -23, -126, -127, -149, -150):
            rows += [(f32(float(i)),), (f32(float(i)) | 0x80000000,)]
        for ax in (0x86000000, 0x65000000, 0x79e7526e):
            rows += straddle_ax(ax, 32)
        for bits in (0xc3000000, 0xc3150000, 0x3b429d37, 0xbcf3a937,
                     0xb8d3d026):
            rows += straddle_bits(bits, 32)
        rows += signed(0.5, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_exp2: |x| against 1024 and 0x1.71547652b82fep-54, then the raw
        # bits against -1075 and -1022
        for ax in (0x8120000000000000, 0xffe0000000000000):
            rows += straddle_ax(ax, 64)
        for bits in (0xc090cc0000000000, 0x792e2a8eca5705fc,
                     0xc08ff00000000000):
            rows += straddle_bits(bits, 64)
        for i in (1, 2, 3, 64, 1023, 1024, -1, -1022, -1074, -1075):
            rows += signed(float(i), 64)
        rows += signed(0.5, 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def log1p_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_log1pf: ux >= 0xbf800000 (x <= -1) and ax >= 0x7f800000 on the
        # raw bits, then |x| against 0x1.1p-6 and 2^-25
        for bits in (0xbf800000, 0x7f800000):
            rows += straddle_bits(bits, 32)
        for ax in (0x3c880000, 0x33000000):
            rows += straddle_ax(ax, 32)
        rows += near(-1.0, 32)
        rows += signed(float.fromhex("0x1.fffffep-1"), 32)
        rows += signed(1.0, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_log1p: |x| against 0.0625, 2^-53, 2^-12 and 0x1.19bp-21 on the
        # doubled magnitude, then the raw bits against 2^53, -1, 2^106 and 2^1024
        for ax in (0x7f60000000000000, 0x7940000000000000,
                   0x7e60000000000000, 0x7d43360000000000):
            rows += straddle_ax(ax, 64)
        for bits in (0x4340000000000000, 0xbff0000000000000,
                     0x4690000000000000, 0x7ff0000000000000):
            rows += straddle_bits(bits, 64)
        rows += near(-1.0, 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp-1"), 64)
        rows += signed(1.0, 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def expm1_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_expm1f: ax = u<<1 against 0x7c400000, 0x676a09e8, 0x8562e430
        # and 0xff<<24
        for ax in (0x7c400000, 0x676a09e8, 0x8562e430, 0xff << 24):
            rows += straddle_ax(ax, 32)
        rows += signed(0.15625, 32)
        rows += near(0.15625, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_expm1: |x| against 0.25, 2^-53 and 0x1.62e42fefa39fp+9
        for bits in (0x3fd0000000000000, 0x3ca0000000000000,
                     0x40862e42fefa39f0, 0x7ff0000000000000):
            rows += straddle_magnitude(bits, 64)
        rows += signed(0.25, 64)
        rows += near(0.25, 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def sinpi_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_sinpif: e == 0xff, |x| >= 2^17, |x| >= 2^23, |x| < 2^-14 and the
        # integer test m<<si == 0
        for e in (0x7f + 16, 0x7f + 17, 0x7f + 22, 0x7f + 23, 0xff,
                  0x7f - 15, 0x7f - 14):
            rows += straddle_exponent(e, 32)
        for i in list(range(-8, 9)) + [2 ** 17, 2 ** 23, 2 ** 30]:
            rows += signed(float(i), 32)
        for h in (0.5, 1.5, 2.5, float.fromhex("0x1p-15"),
                  float.fromhex("0x1p-14")):
            rows += signed(h, 32)
        rows += near(float(2 ** 17), 32)
    else:
        # cr_sinpi: e == 0x7ff, |x| >= 2^41, |x| >= 52, |x| <= 0x1.2p-5 and
        # the small-x bound 2^-960
        for e in (0x3ff + 40, 0x3ff + 41, 0x3ff + 52, 0x3ff + 53, 0x7ff,
                  0x3ff - 961, 0x3ff - 960):
            rows += straddle_exponent(e, 64)
        for i in list(range(-8, 9)) + [2 ** 41, 2 ** 52, 2 ** 53]:
            rows += signed(float(i), 64)
        for h in (0.5, 1.5, 2.5, float.fromhex("0x1p-6"),
                  float.fromhex("0x1p-5")):
            rows += signed(h, 64)
        rows += near(float.fromhex("0x1.2p-5"), 64)
    return rows


def cospi_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_cospif: e == 0xff, p = e-112 (|x| < 2^-15, |x| >= 2^17, >= 2^31)
        # and the 0x1.9f03p-129 bound under which -x*x would underflow
        for e in (0x7f - 16, 0x7f - 15, 0x7f + 17, 0x7f + 18, 0x7f + 31,
                  0x7f + 32, 0xff):
            rows += straddle_exponent(e, 32)
        for i in list(range(-8, 9)) + [2 ** 17, 2 ** 23, 2 ** 30]:
            rows += signed(float(i), 32)
        for h in (0.5, 1.5, 2.5, 3.5):
            rows += signed(h, 32)
        rows += signed(float.fromhex("0x1.9f03p-129"), 32)
        rows += near(float.fromhex("0x1.9f03p-129"), 32)
    else:
        # cr_cospi: e == 0x7ff, |x| >= 2^41, |x| >= 2^53, |x| <= 2^-12 and
        # the 0x1.2ccf6429be6621p-26 bound
        for e in (0x3ff + 40, 0x3ff + 41, 0x3ff + 52, 0x3ff + 53, 0x7ff):
            rows += straddle_exponent(e, 64)
        for i in list(range(-8, 9)) + [2 ** 41, 2 ** 52, 2 ** 53]:
            rows += signed(float(i), 64)
        for h in (0.5, 1.5, 2.5, 3.5):
            rows += signed(h, 64)
        rows += straddle_ax(0x3f30000000000000, 64)
        rows += straddle_ax(0x7c599ed2857cc442, 64)
        rows += signed(float.fromhex("0x1p-12"), 64)
    return rows


def asinh_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_asinhf: |x| against 0x1.02accep-2, 0x1.bb67aep-12 and the
        # inf/NaN bound
        for bits in (0x3e815667, 0x39ddb3d7, 0x7f800000):
            rows += straddle_bits(bits, 32)
        rows += signed(1.0, 32)
        rows += near(1.0, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_asinh: |x| against 0x1.bp-4, 0x1.7137449123ef7p-26, 0x1.3p-6,
        # 2^-12, 0x1.ap-26, 2^26, 1 and 2^52
        for bits in (0x3fbb000000000000, 0x3e57137449123ef7,
                     0x3f93000000000000, 0x3f30000000000000,
                     0x3e5a000000000000, 0x4190000000000000,
                     0x3ff0000000000000, 0x4330000000000000,
                     0x7ff0000000000000):
            rows += straddle_magnitude(bits, 64)
        rows += signed(1.0, 64)
        rows += near(1.0, 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def acosh_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_acoshf: x against 1, 0x1.99db23p-1 and the inf/NaN bound
        for bits in (0x3f800000, 0x3f99db23, 0x7f800000):
            rows += straddle_bits(bits, 32)
        rows += signed(1.0, 32)
        rows += near(1.0, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_acosh: x against 1, 0x1.1e83e425aee63p+0, 111.75, 738, 32896,
        # 0x1.ap+31, 2^26 and 2^52
        for bits in (0x3ff0000000000000, 0x3ff1e83e425aee63,
                     0x405bf00000000000, 0x4087100000000000,
                     0x40e0100000000000, 0x41ea000000000000,
                     0x4190000000000000, 0x4330000000000000,
                     0x7ff0000000000000):
            rows += straddle_bits(bits, 64)
        rows += near(1.0, 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def atanh_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_atanhf: ax = u<<1 against 0x7a300000, 0x73713744 and 0x7f000000
        for ax in (0x7a300000, 0x73713744, 0x7f000000):
            rows += straddle_ax(ax, 32)
        rows += signed(2.0, 32)
        rows += near(1.0, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_atanh: |x| against 1, 0.25 and 0x1.d12ed0af1a27fp-27
        for bits in (0x3ff0000000000000, 0x3fd0000000000000,
                     0x3e4d12ed0af1a27f, 0x7ff0000000000000):
            rows += straddle_magnitude(bits, 64)
        rows += near(1.0, 64)
        rows += signed(2.0, 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def erf_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_erff: |x| against 0x1.f5a888p+1 and 0x1.cp-2, the inf/NaN bound,
        # and the 1/16 grid on which the polynomial table changes row
        for bits in (0x407ad444, 0x3ee00000, 0xff << 23):
            rows += straddle_bits(bits, 32)
        for k in range(0, 65):
            rows += signed(k / 16.0, 32)
        for k in range(0, 33):
            rows += signed(k / 32.0, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_erf: |x| against 0x1.7afb48dc96626p+2, the NaN bound and 2^-61,
        # and the same 1/16 and 1/32 grid
        for bits in (0x4017afb48dc96626, 0x7ff0000000000000):
            rows += straddle_bits(bits, 64)
        for e in (0x3ff - 62, 0x3ff - 61):
            rows += straddle_exponent(e, 64)
        for k in range(0, 65):
            rows += signed(k / 16.0, 64)
        for k in range(0, 33):
            rows += signed(k / 32.0, 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def erfc_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_erfcf: the raw bits against the two tails and the 0x40051000
        # split of the polynomial, and |x| against the three small bounds
        for bits in (0xc07547ca, 0xff800000, 0x4120ddfc, 0x7f800000,
                     0x3db80000, 0xb76c9f62, 0x32e2dfc4, 0x40051000):
            rows += straddle_bits(bits, 32)
        for arg in ("0x1.ea8f94p+1", "0x1.41bbf8p+3", "0x1.7p-4",
                    "0x1.c5bf88p-26"):
            rows += signed(float.fromhex(arg), 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_erfc: the raw bits against the negative tail and the 0x1.7744f8f74e94bp2,
        # 0x1.c5bf891b4ef6ap-54 and 0x1.b39dc41e48bfdp+4 bounds
        for bits in (0xc017744f8f74e94b, 0xfff0000000000000,
                     0x403b39dc41e48bfd, 0x7ff0000000000000):
            rows += straddle_bits(bits, 64)
        for arg in ("0x1.7744f8f74e94bp+2", "0x1.c5bf891b4ef6ap-54",
                    "0x1.b39dc41e48bfdp+4"):
            rows += signed(float.fromhex(arg), 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def tanpi_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_tanpif: e against 150<<23 and 0xff<<23, and the exact multiples
        # of 1/4 that the fast path turns into +-0, +-1 and +-Infinity
        for e in (0x7f + 23, 0x7f + 24, 0xff):
            rows += straddle_exponent(e, 32)
        for k in range(-8, 9):
            rows += signed(k / 4.0, 32)
            rows += signed(k + 0.5, 32)
        rows += signed(float.fromhex("0x1.45f3p-128"), 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_tanpi: |x| against 2^-12, 2^46 and 2^53, and the same multiples
        for e in (0x3ff - 13, 0x3ff - 12, 0x3ff + 45, 0x3ff + 46,
                  0x3ff + 53, 0x7ff):
            rows += straddle_exponent(e, 64)
        for k in range(-8, 9):
            rows += signed(k / 4.0, 64)
            rows += signed(k + 0.5, 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def atanpi_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_atanpif: e against 127+24 and 0xff, e against 127-13 and 127-25,
        # and the 0x1.45f306p+124 bound under which 1/x would underflow
        rows += straddle_ax((0x7f + 25) << 24, 32)
        rows += straddle_ax(0xff << 24, 32)
        for e in (0x7f - 14, 0x7f - 13, 0x7f - 26, 0x7f - 25):
            rows += straddle_exponent(e, 32)
        rows += signed(float.fromhex("0x1.45f306p+124"), 32)
        rows += signed(1.0, 32)
        rows += near(1.0, 32)
    else:
        # cr_atanpi: |x| against 0x1.b21c475e6362ap-8, 2^-54 and the NaN bound
        for bits in (0x3f7b21c475e6362a, 0x3c90000000000000,
                     0x7ff0000000000000):
            rows += straddle_magnitude(bits, 64)
        rows += signed(1.0, 64)
        rows += near(1.0, 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def asinpi_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_asinpif: e >= 127 (|x| >= 1), e == 0xff, the index i of the
        # polynomial from s = 146-e, and the 2^-12 edge of that index
        for e in (0x7f - 1, 0x7f, 0x7f + 1, 0xff):
            rows += straddle_exponent(e, 32)
        rows += signed(1.0, 32)
        rows += near(1.0, 32)
        rows += near(-1.0, 32)
        for v in (2.0, 0.5, 0.25):
            rows += signed(v, 32)
        rows += signed(float.fromhex("0x1p-12"), 32)
        rows += near(float.fromhex("0x1p-12"), 32)
    else:
        # cr_asinpi: |x| against 2^-6, 2^-26, 2^-53 and the |x| = 1 and NaN
        # branches
        for e in (0x3ff - 7, 0x3ff - 6, 0x3ff - 27, 0x3ff - 26,
                  0x3ff - 54, 0x3ff - 53, 0x3ff - 1, 0x3ff, 0x7ff):
            rows += straddle_exponent(e, 64)
        rows += signed(1.0, 64)
        rows += near(1.0, 64)
        rows += near(-1.0, 64)
        rows += signed(0.5, 64)
    return rows


def acospi_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_acospif: e >= 127 (|x| >= 1), e == 0xff, the polynomial index,
        # and the 2^-12 edge of that index
        for e in (0x7f - 1, 0x7f, 0x7f + 1, 0xff):
            rows += straddle_exponent(e, 32)
        rows += signed(1.0, 32)
        rows += near(1.0, 32)
        rows += near(-1.0, 32)
        rows += signed(0.75, 32)
        rows += near(0.75, 32)
        rows += signed(float.fromhex("0x1p-12"), 32)
        rows += near(float.fromhex("0x1p-12"), 32)
    else:
        # cr_acospi: |x| against 0.75, 0x1.921fb54442d18p-54 and the |x| = 1
        # and NaN branches
        for e in (0x3ff - 1, 0x3ff, 0x7ff):
            rows += straddle_exponent(e, 64)
        rows += signed(1.0, 64)
        rows += near(1.0, 64)
        rows += near(-1.0, 64)
        rows += signed(0.75, 64)
        rows += near(0.75, 64)
        rows += signed(float.fromhex("0x1.921fb54442d18p-54"), 64)
        rows += near(float.fromhex("0x1.921fb54442d18p-54"), 64)
    return rows


def rsqrt_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_rsqrtf: ix against 0xff<<23 and 0, and the three low-bit patterns
        # that the C source corrects with a factor just below 1
        rows += straddle_bits(0xff << 23, 32)
        for bits in (0x2f7e2a, 0xbdf8a8, 0x55b7bd):
            rows += straddle_bits(bits, 32)
        rows += signed(1.0, 32)
        rows += near(1.0, 32)
        rows += signed(float.fromhex("0x1p-126"), 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_rsqrt: the exponent against 0, 0x7fd and 0x7ff
        for e in (0, 1, 0x7fc, 0x7fd, 0x7fe, 0x7ff):
            rows += straddle_exponent(e, 64)
        rows += signed(1.0, 64)
        rows += near(1.0, 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def exp10_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_exp10f: ax = u<<1 against 0x84344134, 0x72adf1c6 and 0xff<<24,
        # and the raw bits against -0x1.66d3e8p+5 and 0x1.344134p+5
        for ax in (0x84344134, 0x72adf1c6, 0xff << 24):
            rows += straddle_ax(ax, 32)
        for bits in (0xc23369f4, 0x80000000):
            rows += straddle_bits(bits, 32)
        for i in (-1, 0, 1, 2, 10, 38, 39, -38, -45, -46):
            rows += signed(float(i), 32)
        rows += signed(0.5, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_exp10: |x| against 0x1.34413509f79fep+8, inf and
        # 0x1.439b746e36b52p+8, and the exact integers
        for bits in (0x40734413509f79fe, 0x7ff0000000000000,
                     0x407439b746e36b52):
            rows += straddle_magnitude(bits, 64)
        for i in (-1, 0, 1, 2, 10, 22, 23, 100, 308, 309, -308, -323, -324):
            rows += signed(float(i), 64)
        rows += signed(0.5, 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def exp2m1_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_exp2m1f: the raw bits against -25, 128 and the NaN bound, and
        # the ladder of small-argument bounds of the polynomial
        for bits in (0xc1c80000, 0x43000000, 0xff << 23):
            rows += straddle_bits(bits, 32)
        for bits in (0x3df95f1f, 0x3d67a4cc, 0x3caa2fee, 0x3bac1405,
                     0x3a358876, 0x37d32ef6, 0x331fdd82, 0x2538aa3b):
            rows += straddle_magnitude(bits, 32)
        rows += signed(1.0, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_exp2m1: the raw bits against -54, 1024, -Inf/NaN, 2^-51 and the
        # 2^-54 and 0.125 bounds
        for bits in (0xc04b000000000000, 0x4090000000000000,
                     0xfff0000000000000, 0x3cc0527dbd87e24d,
                     0x3970000000000000, 0x3fc0000000000000):
            rows += straddle_bits(bits, 64)
        rows += signed(1.0, 64)
        rows += signed(float.fromhex("0x1p-1022"), 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def exp10m1_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_exp10m1f: the raw bits against -7.52575, the magnitude against
        # the overflow bound and the nine-step ladder of small arguments
        rows += straddle_bits(0xc0f0d2f1, 32)
        for ax in (0x421a209a, 0x3d89c604, 0x3d1622fb, 0x3c8b76a3,
                   0x3bcced04, 0x3acf33eb, 0x395a966b, 0x36fe4a4b,
                   0x32407f39, 0x245e5bd9):
            rows += straddle_magnitude(ax, 32)
        for bits in (0x2c994b7b, 0xb6fa215b, 0xff800000):
            rows += straddle_bits(bits, 32)
        rows += signed(1.0, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_exp10m1: the raw bits against -0x1.041704c068efp+4, the overflow
        # bound, 2^-54, 0.0625, 2^-46 and -Inf/NaN
        for bits in (0xc03041704c068ef0, 0x40734413509f79fe,
                     0x3c90000000000000, 0x3fb0000000000000,
                     0x3970000000000000, 0xfff0000000000000):
            rows += straddle_bits(bits, 64)
        rows += signed(1.0, 64)
        rows += signed(float.fromhex("0x1p-1022"), 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def log10p1_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_log10p1f: the raw bits against -1, +Inf and NaN, the magnitude
        # against 2^-25 and 2^-11, and the powers of ten of st[]
        for bits in (0xbf800000, 0x7f800000, 0xff000000):
            rows += straddle_bits(bits, 32)
        for ax in (0x33000000, 0x3d100000):
            rows += straddle_magnitude(ax, 32)
        rows += near(-1.0, 32)
        rows += signed(-0.5, 32)
        rows += signed(0.5, 32)
        for n in range(-30, 31, 6):
            rows += signed(float("1e%d" % n), 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        rows += near(-1.0, 64)
        rows += straddle_bits(0x16a09e667f3bcd, 64)
        rows += signed(-0.5, 64)
        rows += signed(0.5, 64)
        rows += signed(1.0, 64)
        for n in range(-300, 301, 60):
            rows += signed(float("1e%d" % n), 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def log2p1_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_log2p1f: the raw bits against -1, +Inf and NaN, the magnitude
        # against 0.0234375 and 0x1p-126*ln(2), and the powers of two
        for bits in (0xbf800000, 0x7f800000, 0xff000000):
            rows += straddle_bits(bits, 32)
        for ax in (0x3cc00000, 0x58b90b):
            rows += straddle_magnitude(ax, 32)
        rows += near(-1.0, 32)
        rows += signed(-0.5, 32)
        rows += signed(0.5, 32)
        for k in range(-30, 31, 6):
            rows += signed(math.ldexp(1.0, k), 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        rows += near(-1.0, 64)
        rows += straddle_bits(0x16a09e667f3bcd, 64)
        rows += signed(-0.5, 64)
        rows += signed(0.5, 64)
        rows += signed(1.0, 64)
        for k in range(-300, 301, 60):
            rows += signed(math.ldexp(1.0, k), 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def tgamma_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_tgammaf: ax = u<<1 against 0x6d000000 (|x| < 2^-18), and the
        # integers and half-integers where gamma is exact or a pole
        rows += straddle_ax(0x6d000000, 32)
        for i in range(-8, 9):
            rows += signed(float(i), 32)
            rows += signed(i + 0.5, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_tgamma: |x| against 2^-112 and 2^-1024, and the integers and
        # half-integers; the rows of as_tgamma_database()
        rows += straddle_magnitude(0x71e0000000000000, 64)
        for i in range(-8, 9):
            rows += signed(float(i), 64)
            rows += signed(i + 0.5, 64)
        rows += c_table("tgamma", 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def lgamma_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_lgammaf: the integer cases and the four range borders
        for i in range(-8, 9):
            rows += signed(float(i), 32)
            rows += signed(i + 0.5, 32)
        for bits in (0x401ceccb, 0x401d95ca, 0x402f95c2, 0x40301b93,
                     0x40492009, 0x404940ef):
            rows += straddle_bits(bits, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_lgamma: the integer cases, the range borders of the ubrd[] table
        # and the rows of as_lgamma_database()
        for i in range(-8, 9):
            rows += signed(float(i), 64)
            rows += signed(i + 0.5, 64)
        for e in (0x3ff - 1, 0x3ff, 0x3ff + 1, 0x7fe):
            rows += straddle_exponent(e, 64)
        rows += c_table("lgamma", 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def sincos_cases(width):
    rows = specials(width)
    if width == 32:
        # cr_sincosf: ax = u<<1 against 0x822d97c8, 0x99000000, 0x73000000
        # and 0x66000000, and the multiples of pi/2
        for ax in (0x822d97c8, 0x99000000, 0x73000000, 0x66000000):
            rows += straddle_ax(ax, 32)
        for k in range(-8, 9):
            rows += signed(k * 1.5707963267948966, 32)
            rows += signed(k * 3.141592653589793, 32)
        rows += signed(3.4028234663852886e38, 32)
    else:
        # cr_sincos: |x| against 0x1.6a09e667f3bccp-27 and the exponent bounds
        # of the reduction, and the multiples of pi/2
        rows += straddle_magnitude(0x3e46a09e667f3bcc, 64)
        for e in (0x3ff - 27, 0x3ff - 26, 0x3ff + 30, 0x3ff + 31, 0x7fe):
            rows += straddle_exponent(e, 64)
        for k in range(-8, 9):
            rows += signed(k * 1.5707963267948966, 64)
            rows += signed(k * 3.141592653589793, 64)
        rows += signed(float.fromhex("0x1.fffffffffffffp+1023"), 64)
    return rows


def specials2(width):
    """The special values in both argument positions: each of them with 1 as
    the other argument, and the full cross product, which carries every pair an
    IEEE 754 special case of a two-argument function is stated for."""
    one = (f64 if width == 64 else f32)(1.0)
    rows = []
    for (v,) in specials(width):
        rows.append((v, one))
        rows.append((one, v))
    for (y,) in specials(width):
        for (x,) in specials(width):
            rows.append((y, x))
    return rows


def atan2_cases(width):
    rows = specials2(width)
    # the quadrants and the axes, where atan2 is exact or a pole
    for y in (0, 1.0, 2.0, -1.0, -2.0, 0.5):
        for x in (0, 1.0, 2.0, -1.0, -2.0, 0.5):
            rows.append((signed(y, width)[0][0], signed(x, width)[0][0]))
            rows.append((signed(-y, width)[0][0], signed(-x, width)[0][0]))
    for v in (1.0, 2.0, 4.0, 8.0, 0.25):
        rows.append((signed(v, width)[0][0], signed(1.0, width)[0][0]))
        rows.append((signed(1.0, width)[0][0], signed(v, width)[0][0]))
    rows.append((f64(1.0) if width == 64 else f32(1.0),) * 1 +
                (signed(float.fromhex("0x1.fffffffffffffp+1023"), width)[0][0]
                 if width == 64 else f32(3.4028234663852886e38),))
    return rows


def hypot_cases(width):
    rows = specials2(width)
    # the exact triples, the subnormal pairs and the magnitude ratios
    for y, x in ((3.0, 4.0), (4.0, 3.0), (5.0, 12.0), (1.0, 1.0), (1.0, 2.0)):
        rows.append((signed(y, width)[0][0], signed(x, width)[0][0]))
    tiny = float.fromhex("0x1p-1070") if width == 64 else float.fromhex("0x1p-140")
    rows.append((signed(tiny, width)[0][0], signed(tiny, width)[0][0]))
    if width == 64:
        for bits in (0x39b0000000000000, 0x3c9fffffffffff80,
                     0x10000000000000):
            rows.append((bits, signed(1.0, 64)[0][0]))
            rows.append((signed(1.0, 64)[0][0], bits))
    return rows


def atan2pi_cases(width):
    rows = atan2_cases(width)
    return rows


def pow_cases(width):
    rows = specials2(width)
    # the IEEE 754 special cases of pow, with both odd and even exponents
    for y in (0, 1.0, 2.0, 3.0, 10.0, -1.0, -2.0, -3.0, 0.5, -0.5):
        for x in (0, 1.0, -1.0, 2.0, -2.0, 10.0, 0.5):
            rows.append((signed(x, width)[0][0], signed(y, width)[0][0]))
            rows.append((signed(x, width)[1][0], signed(y, width)[0][0]))
    return rows


def compound_cases(width):
    rows = specials2(width)
    # compound(x, 0) is 1, compound(0, y) is 1, and the integer exponents
    for y in (0, 1.0, 2.0, -1.0, 0.5, 10.0):
        for x in (0, 1.0, -1.0, 0.5, -0.5, 2.0, -2.0):
            rows.append((signed(x, width)[0][0], signed(y, width)[0][0]))
            rows.append((signed(x, width)[1][0], signed(y, width)[0][0]))
    return rows


BUILDERS = {"sin": sin_cases, "cos": cos_cases, "tan": tan_cases,
            "exp": exp_cases, "log": log_cases, "log2": log2_cases,
            "log10": log10_cases, "atan": atan_cases, "asin": asin_cases,
            "acos": acos_cases, "sinh": sinh_cases, "cosh": cosh_cases,
            "tanh": tanh_cases, "cbrt": cbrt_cases, "exp2": exp2_cases,
            "log1p": log1p_cases, "expm1": expm1_cases,
            "sinpi": sinpi_cases, "cospi": cospi_cases,
            "asinh": asinh_cases, "acosh": acosh_cases, "atanh": atanh_cases,
            "erf": erf_cases, "erfc": erfc_cases, "tanpi": tanpi_cases,
            "atanpi": atanpi_cases, "asinpi": asinpi_cases,
            "acospi": acospi_cases, "rsqrt": rsqrt_cases,
            "exp10": exp10_cases, "exp2m1": exp2m1_cases,
            "exp10m1": exp10m1_cases, "log10p1": log10p1_cases,
            "log2p1": log2p1_cases, "tgamma": tgamma_cases,
            "lgamma": lgamma_cases, "sincos": sincos_cases,
            "atan2": atan2_cases, "hypot": hypot_cases, "pow": pow_cases,
            "atan2pi": atan2pi_cases, "compound": compound_cases}


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
    elif KIND[name] != "unary":
        lines += wrap("# double rounding: ",
                      "no scan.  --frontier walks the whole binary32 domain "
                      "of a one-argument function, and a two-argument function "
                      "has 2^64 argument pairs, so the directed grid and the "
                      "random sweep stand in for it")
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


def format_row(kind, width, row):
    """The hexadecimal text of one result row.

    The signgam column of lgamma is an int, so it goes out as the 32-bit
    pattern of that int and ffffffff stands for -1.
    """
    fields = [hx(v, width) for v in row]
    if kind == "lgamma":
        fields[-1] = hx(row[-1] & 0xffffffff, 32)
    return " ".join(fields)


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
                out.append(format_row(kind, width, results[at]))
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
