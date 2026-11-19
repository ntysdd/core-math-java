#!/usr/bin/env python3
"""Writes the JUnit classes of a group of functions from a small spec.

tools/mkvectors.py produces the vector files; this script produces the Java
side, so that the classes that differ in nothing but the function name and the
special values stay consistent.

usage: python tools/gentests.py atan asin acos
       python tools/gentests.py --all
"""

import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
TESTS = ROOT / "src" / "test" / "java" / "io" / "github" / "ntysdd" / "coremath"

# special: (label, argument expression, expected bits as a hex string) per
# format; the argument expression is what goes between the parentheses of the
# CrMath call.  A NaN expectation is written "nan".
SPEC = {
    "atan": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("atanf(+0.0)", "0.0f", "0x00000000"),
                ("atanf(-0.0)", "-0.0f", "0x80000000"),
                ("atanf(1.0)", "1.0f", "0x3f490fdb"),
                ("atanf(-1.0)", "-1.0f", "0xbf490fdb"),
                ("atanf(+Infinity)", "Float.POSITIVE_INFINITY", "0x3fc90fdb"),
                ("atanf(-Infinity)", "Float.NEGATIVE_INFINITY", "0xbfc90fdb"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("atan(+0.0)", "0.0", "0x0000000000000000"),
                ("atan(-0.0)", "-0.0", "0x8000000000000000"),
                ("atan(1.0)", "1.0", "0x3fe921fb54442d18"),
                ("atan(-1.0)", "-1.0", "0xbfe921fb54442d18"),
                ("atan(+Infinity)", "Double.POSITIVE_INFINITY",
                 "0x3ff921fb54442d18"),
                ("atan(-Infinity)", "Double.NEGATIVE_INFINITY",
                 "0xbff921fb54442d18"),
            ],
        },
    },
    "asin": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("asinf(+0.0)", "0.0f", "0x00000000"),
                ("asinf(-0.0)", "-0.0f", "0x80000000"),
                ("asinf(1.0)", "1.0f", "0x3fc90fdb"),
                ("asinf(-1.0)", "-1.0f", "0xbfc90fdb"),
                ("asinf(2.0)", "2.0f", "nan"),
                ("asinf(-2.0)", "-2.0f", "nan"),
                ("asinf(+Infinity)", "Float.POSITIVE_INFINITY", "nan"),
                ("asinf(-Infinity)", "Float.NEGATIVE_INFINITY", "nan"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("asin(+0.0)", "0.0", "0x0000000000000000"),
                ("asin(-0.0)", "-0.0", "0x8000000000000000"),
                ("asin(1.0)", "1.0", "0x3ff921fb54442d18"),
                ("asin(-1.0)", "-1.0", "0xbff921fb54442d18"),
                ("asin(2.0)", "2.0", "nan"),
                ("asin(-2.0)", "-2.0", "nan"),
                ("asin(+Infinity)", "Double.POSITIVE_INFINITY", "nan"),
                ("asin(-Infinity)", "Double.NEGATIVE_INFINITY", "nan"),
            ],
        },
    },
    "acos": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("acosf(+0.0)", "0.0f", "0x3fc90fdb"),
                ("acosf(-0.0)", "-0.0f", "0x3fc90fdb"),
                ("acosf(1.0)", "1.0f", "0x00000000"),
                ("acosf(-1.0)", "-1.0f", "0x40490fdb"),
                ("acosf(2.0)", "2.0f", "nan"),
                ("acosf(-2.0)", "-2.0f", "nan"),
                ("acosf(+Infinity)", "Float.POSITIVE_INFINITY", "nan"),
                ("acosf(-Infinity)", "Float.NEGATIVE_INFINITY", "nan"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("acos(+0.0)", "0.0", "0x3ff921fb54442d18"),
                ("acos(-0.0)", "-0.0", "0x3ff921fb54442d18"),
                ("acos(1.0)", "1.0", "0x0000000000000000"),
                ("acos(-1.0)", "-1.0", "0x400921fb54442d18"),
                ("acos(2.0)", "2.0", "nan"),
                ("acos(-2.0)", "-2.0", "nan"),
                ("acos(+Infinity)", "Double.POSITIVE_INFINITY", "nan"),
                ("acos(-Infinity)", "Double.NEGATIVE_INFINITY", "nan"),
            ],
        },
    },
    "sinh": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("sinhf(+0.0)", "0.0f", "0x00000000"),
                ("sinhf(-0.0)", "-0.0f", "0x80000000"),
                ("sinhf(+Infinity)", "Float.POSITIVE_INFINITY", "0x7f800000"),
                ("sinhf(-Infinity)", "Float.NEGATIVE_INFINITY", "0xff800000"),
                ("sinhf(Float.MAX_VALUE)", "Float.MAX_VALUE", "0x7f800000"),
                ("sinhf(-Float.MAX_VALUE)", "-Float.MAX_VALUE", "0xff800000"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("sinh(+0.0)", "0.0", "0x0000000000000000"),
                ("sinh(-0.0)", "-0.0", "0x8000000000000000"),
                ("sinh(+Infinity)", "Double.POSITIVE_INFINITY",
                 "0x7ff0000000000000"),
                ("sinh(-Infinity)", "Double.NEGATIVE_INFINITY",
                 "0xfff0000000000000"),
                ("sinh(Double.MAX_VALUE)", "Double.MAX_VALUE",
                 "0x7ff0000000000000"),
                ("sinh(-Double.MAX_VALUE)", "-Double.MAX_VALUE",
                 "0xfff0000000000000"),
            ],
        },
    },
    "cosh": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("coshf(+0.0)", "0.0f", "0x3f800000"),
                ("coshf(-0.0)", "-0.0f", "0x3f800000"),
                ("coshf(+Infinity)", "Float.POSITIVE_INFINITY", "0x7f800000"),
                ("coshf(-Infinity)", "Float.NEGATIVE_INFINITY", "0x7f800000"),
                ("coshf(Float.MAX_VALUE)", "Float.MAX_VALUE", "0x7f800000"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("cosh(+0.0)", "0.0", "0x3ff0000000000000"),
                ("cosh(-0.0)", "-0.0", "0x3ff0000000000000"),
                ("cosh(+Infinity)", "Double.POSITIVE_INFINITY",
                 "0x7ff0000000000000"),
                ("cosh(-Infinity)", "Double.NEGATIVE_INFINITY",
                 "0x7ff0000000000000"),
                ("cosh(Double.MAX_VALUE)", "Double.MAX_VALUE",
                 "0x7ff0000000000000"),
            ],
        },
    },
    "tanh": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("tanhf(+0.0)", "0.0f", "0x00000000"),
                ("tanhf(-0.0)", "-0.0f", "0x80000000"),
                ("tanhf(+Infinity)", "Float.POSITIVE_INFINITY", "0x3f800000"),
                ("tanhf(-Infinity)", "Float.NEGATIVE_INFINITY", "0xbf800000"),
                ("tanhf(Float.MAX_VALUE)", "Float.MAX_VALUE", "0x3f800000"),
                ("tanhf(-Float.MAX_VALUE)", "-Float.MAX_VALUE", "0xbf800000"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("tanh(+0.0)", "0.0", "0x0000000000000000"),
                ("tanh(-0.0)", "-0.0", "0x8000000000000000"),
                ("tanh(+Infinity)", "Double.POSITIVE_INFINITY",
                 "0x3ff0000000000000"),
                ("tanh(-Infinity)", "Double.NEGATIVE_INFINITY",
                 "0xbff0000000000000"),
                ("tanh(Double.MAX_VALUE)", "Double.MAX_VALUE",
                 "0x3ff0000000000000"),
                ("tanh(-Double.MAX_VALUE)", "-Double.MAX_VALUE",
                 "0xbff0000000000000"),
            ],
        },
    },
    "cbrt": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("cbrtf(+0.0)", "0.0f", "0x00000000"),
                ("cbrtf(-0.0)", "-0.0f", "0x80000000"),
                ("cbrtf(8.0)", "8.0f", "0x40000000"),
                ("cbrtf(-8.0)", "-8.0f", "0xc0000000"),
                ("cbrtf(+Infinity)", "Float.POSITIVE_INFINITY", "0x7f800000"),
                ("cbrtf(-Infinity)", "Float.NEGATIVE_INFINITY", "0xff800000"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("cbrt(+0.0)", "0.0", "0x0000000000000000"),
                ("cbrt(-0.0)", "-0.0", "0x8000000000000000"),
                ("cbrt(8.0)", "8.0", "0x4000000000000000"),
                ("cbrt(-8.0)", "-8.0", "0xc000000000000000"),
                ("cbrt(+Infinity)", "Double.POSITIVE_INFINITY",
                 "0x7ff0000000000000"),
                ("cbrt(-Infinity)", "Double.NEGATIVE_INFINITY",
                 "0xfff0000000000000"),
            ],
        },
    },
    "exp2": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("exp2f(+0.0)", "0.0f", "0x3f800000"),
                ("exp2f(-0.0)", "-0.0f", "0x3f800000"),
                ("exp2f(8.0)", "8.0f", "0x43800000"),
                ("exp2f(-8.0)", "-8.0f", "0x3b800000"),
                ("exp2f(+Infinity)", "Float.POSITIVE_INFINITY", "0x7f800000"),
                ("exp2f(-Infinity)", "Float.NEGATIVE_INFINITY", "0x00000000"),
                ("exp2f(Float.MAX_VALUE)", "Float.MAX_VALUE", "0x7f800000"),
                ("exp2f(-Float.MAX_VALUE)", "-Float.MAX_VALUE", "0x00000000"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("exp2(+0.0)", "0.0", "0x3ff0000000000000"),
                ("exp2(-0.0)", "-0.0", "0x3ff0000000000000"),
                ("exp2(8.0)", "8.0", "0x4070000000000000"),
                ("exp2(-8.0)", "-8.0", "0x3f70000000000000"),
                ("exp2(+Infinity)", "Double.POSITIVE_INFINITY",
                 "0x7ff0000000000000"),
                ("exp2(-Infinity)", "Double.NEGATIVE_INFINITY",
                 "0x0000000000000000"),
                ("exp2(Double.MAX_VALUE)", "Double.MAX_VALUE",
                 "0x7ff0000000000000"),
                ("exp2(-Double.MAX_VALUE)", "-Double.MAX_VALUE",
                 "0x0000000000000000"),
            ],
        },
    },
    "log1p": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("log1pf(+0.0)", "0.0f", "0x00000000"),
                ("log1pf(-0.0)", "-0.0f", "0x80000000"),
                ("log1pf(-1.0)", "-1.0f", "0xff800000"),
                ("log1pf(-2.0)", "-2.0f", "nan"),
                ("log1pf(+Infinity)", "Float.POSITIVE_INFINITY", "0x7f800000"),
                ("log1pf(-Infinity)", "Float.NEGATIVE_INFINITY", "nan"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("log1p(+0.0)", "0.0", "0x0000000000000000"),
                ("log1p(-0.0)", "-0.0", "0x8000000000000000"),
                ("log1p(-1.0)", "-1.0", "0xfff0000000000000"),
                ("log1p(-2.0)", "-2.0", "nan"),
                ("log1p(+Infinity)", "Double.POSITIVE_INFINITY",
                 "0x7ff0000000000000"),
                ("log1p(-Infinity)", "Double.NEGATIVE_INFINITY", "nan"),
            ],
        },
    },
    "expm1": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("expm1f(+0.0)", "0.0f", "0x00000000"),
                ("expm1f(-0.0)", "-0.0f", "0x80000000"),
                ("expm1f(+Infinity)", "Float.POSITIVE_INFINITY", "0x7f800000"),
                ("expm1f(-Infinity)", "Float.NEGATIVE_INFINITY", "0xbf800000"),
                ("expm1f(Float.MAX_VALUE)", "Float.MAX_VALUE", "0x7f800000"),
                ("expm1f(-Float.MAX_VALUE)", "-Float.MAX_VALUE", "0xbf800000"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("expm1(+0.0)", "0.0", "0x0000000000000000"),
                ("expm1(-0.0)", "-0.0", "0x8000000000000000"),
                ("expm1(+Infinity)", "Double.POSITIVE_INFINITY",
                 "0x7ff0000000000000"),
                ("expm1(-Infinity)", "Double.NEGATIVE_INFINITY",
                 "0xbff0000000000000"),
                ("expm1(Double.MAX_VALUE)", "Double.MAX_VALUE",
                 "0x7ff0000000000000"),
                ("expm1(-Double.MAX_VALUE)", "-Double.MAX_VALUE",
                 "0xbff0000000000000"),
            ],
        },
    },
    "sinpi": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("sinpif(+0.0)", "0.0f", "0x00000000"),
                ("sinpif(-0.0)", "-0.0f", "0x80000000"),
                ("sinpif(0.5)", "0.5f", "0x3f800000"),
                ("sinpif(-0.5)", "-0.5f", "0xbf800000"),
                ("sinpif(1.0)", "1.0f", "0x00000000"),
                ("sinpif(-1.0)", "-1.0f", "0x80000000"),
                ("sinpif(2.0)", "2.0f", "0x00000000"),
                ("sinpif(+Infinity)", "Float.POSITIVE_INFINITY", "nan"),
                ("sinpif(-Infinity)", "Float.NEGATIVE_INFINITY", "nan"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("sinpi(+0.0)", "0.0", "0x0000000000000000"),
                ("sinpi(-0.0)", "-0.0", "0x8000000000000000"),
                ("sinpi(0.5)", "0.5", "0x3ff0000000000000"),
                ("sinpi(-0.5)", "-0.5", "0xbff0000000000000"),
                ("sinpi(1.0)", "1.0", "0x0000000000000000"),
                ("sinpi(-1.0)", "-1.0", "0x8000000000000000"),
                ("sinpi(2.0)", "2.0", "0x0000000000000000"),
                ("sinpi(+Infinity)", "Double.POSITIVE_INFINITY", "nan"),
                ("sinpi(-Infinity)", "Double.NEGATIVE_INFINITY", "nan"),
            ],
        },
    },
    "cospi": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("cospif(+0.0)", "0.0f", "0x3f800000"),
                ("cospif(-0.0)", "-0.0f", "0x3f800000"),
                ("cospif(0.5)", "0.5f", "0x00000000"),
                ("cospif(-0.5)", "-0.5f", "0x00000000"),
                ("cospif(1.0)", "1.0f", "0xbf800000"),
                ("cospif(-1.0)", "-1.0f", "0xbf800000"),
                ("cospif(2.0)", "2.0f", "0x3f800000"),
                ("cospif(+Infinity)", "Float.POSITIVE_INFINITY", "nan"),
                ("cospif(-Infinity)", "Float.NEGATIVE_INFINITY", "nan"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("cospi(+0.0)", "0.0", "0x3ff0000000000000"),
                ("cospi(-0.0)", "-0.0", "0x3ff0000000000000"),
                ("cospi(0.5)", "0.5", "0x0000000000000000"),
                ("cospi(-0.5)", "-0.5", "0x0000000000000000"),
                ("cospi(1.0)", "1.0", "0xbff0000000000000"),
                ("cospi(-1.0)", "-1.0", "0xbff0000000000000"),
                ("cospi(2.0)", "2.0", "0x3ff0000000000000"),
                ("cospi(+Infinity)", "Double.POSITIVE_INFINITY", "nan"),
                ("cospi(-Infinity)", "Double.NEGATIVE_INFINITY", "nan"),
            ],
        },
    },
    "asinh": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("asinhf(+0.0)", "0.0f", "0x00000000"),
                ("asinhf(-0.0)", "-0.0f", "0x80000000"),
                ("asinhf(1.0)", "1.0f", "0x3f61a1b3"),
                ("asinhf(-1.0)", "-1.0f", "0xbf61a1b3"),
                ("asinhf(+Infinity)", "Float.POSITIVE_INFINITY", "0x7f800000"),
                ("asinhf(-Infinity)", "Float.NEGATIVE_INFINITY", "0xff800000"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("asinh(+0.0)", "0.0", "0x0000000000000000"),
                ("asinh(-0.0)", "-0.0", "0x8000000000000000"),
                ("asinh(1.0)", "1.0", "0x3fec34366179d427"),
                ("asinh(-1.0)", "-1.0", "0xbfec34366179d427"),
                ("asinh(+Infinity)", "Double.POSITIVE_INFINITY",
                 "0x7ff0000000000000"),
                ("asinh(-Infinity)", "Double.NEGATIVE_INFINITY",
                 "0xfff0000000000000"),
            ],
        },
    },
    "acosh": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("acoshf(1.0)", "1.0f", "0x00000000"),
                ("acoshf(0.5)", "0.5f", "nan"),
                ("acoshf(0.0)", "0.0f", "nan"),
                ("acoshf(-1.0)", "-1.0f", "nan"),
                ("acoshf(+Infinity)", "Float.POSITIVE_INFINITY", "0x7f800000"),
                ("acoshf(-Infinity)", "Float.NEGATIVE_INFINITY", "nan"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("acosh(1.0)", "1.0", "0x0000000000000000"),
                ("acosh(0.5)", "0.5", "nan"),
                ("acosh(0.0)", "0.0", "nan"),
                ("acosh(-1.0)", "-1.0", "nan"),
                ("acosh(+Infinity)", "Double.POSITIVE_INFINITY",
                 "0x7ff0000000000000"),
                ("acosh(-Infinity)", "Double.NEGATIVE_INFINITY", "nan"),
            ],
        },
    },
    "atanh": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("atanhf(+0.0)", "0.0f", "0x00000000"),
                ("atanhf(-0.0)", "-0.0f", "0x80000000"),
                ("atanhf(1.0)", "1.0f", "0x7f800000"),
                ("atanhf(-1.0)", "-1.0f", "0xff800000"),
                ("atanhf(2.0)", "2.0f", "nan"),
                ("atanhf(+Infinity)", "Float.POSITIVE_INFINITY", "nan"),
                ("atanhf(-Infinity)", "Float.NEGATIVE_INFINITY", "nan"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("atanh(+0.0)", "0.0", "0x0000000000000000"),
                ("atanh(-0.0)", "-0.0", "0x8000000000000000"),
                ("atanh(1.0)", "1.0", "0x7ff0000000000000"),
                ("atanh(-1.0)", "-1.0", "0xfff0000000000000"),
                ("atanh(2.0)", "2.0", "nan"),
                ("atanh(+Infinity)", "Double.POSITIVE_INFINITY", "nan"),
                ("atanh(-Infinity)", "Double.NEGATIVE_INFINITY", "nan"),
            ],
        },
    },
    "erf": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("erff(+0.0)", "0.0f", "0x00000000"),
                ("erff(-0.0)", "-0.0f", "0x80000000"),
                ("erff(0.5)", "0.5f", "0x3f053f7b"),
                ("erff(1.0)", "1.0f", "0x3f57bb3d"),
                ("erff(+Infinity)", "Float.POSITIVE_INFINITY", "0x3f800000"),
                ("erff(-Infinity)", "Float.NEGATIVE_INFINITY", "0xbf800000"),
                ("erff(Float.MAX_VALUE)", "Float.MAX_VALUE", "0x3f800000"),
                ("erff(-Float.MAX_VALUE)", "-Float.MAX_VALUE", "0xbf800000"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("erf(+0.0)", "0.0", "0x0000000000000000"),
                ("erf(-0.0)", "-0.0", "0x8000000000000000"),
                ("erf(0.5)", "0.5", "0x3fe0a7ef5c18edd2"),
                ("erf(1.0)", "1.0", "0x3feaf767a741088b"),
                ("erf(+Infinity)", "Double.POSITIVE_INFINITY",
                 "0x3ff0000000000000"),
                ("erf(-Infinity)", "Double.NEGATIVE_INFINITY",
                 "0xbff0000000000000"),
                ("erf(Double.MAX_VALUE)", "Double.MAX_VALUE",
                 "0x3ff0000000000000"),
                ("erf(-Double.MAX_VALUE)", "-Double.MAX_VALUE",
                 "0xbff0000000000000"),
            ],
        },
    },
    "erfc": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("erfcf(+0.0)", "0.0f", "0x3f800000"),
                ("erfcf(-0.0)", "-0.0f", "0x3f800000"),
                ("erfcf(1.0)", "1.0f", "0x3e21130b"),
                ("erfcf(+Infinity)", "Float.POSITIVE_INFINITY", "0x00000000"),
                ("erfcf(-Infinity)", "Float.NEGATIVE_INFINITY", "0x40000000"),
                ("erfcf(Float.MAX_VALUE)", "Float.MAX_VALUE", "0x00000000"),
                ("erfcf(-Float.MAX_VALUE)", "-Float.MAX_VALUE", "0x40000000"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("erfc(+0.0)", "0.0", "0x3ff0000000000000"),
                ("erfc(-0.0)", "-0.0", "0x3ff0000000000000"),
                ("erfc(1.0)", "1.0", "0x3fc4226162fbddd5"),
                ("erfc(+Infinity)", "Double.POSITIVE_INFINITY",
                 "0x0000000000000000"),
                ("erfc(-Infinity)", "Double.NEGATIVE_INFINITY",
                 "0x4000000000000000"),
                ("erfc(Double.MAX_VALUE)", "Double.MAX_VALUE",
                 "0x0000000000000000"),
                ("erfc(-Double.MAX_VALUE)", "-Double.MAX_VALUE",
                 "0x4000000000000000"),
            ],
        },
    },
    "tanpi": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("tanpif(+0.0)", "0.0f", "0x00000000"),
                ("tanpif(-0.0)", "-0.0f", "0x80000000"),
                ("tanpif(0.25)", "0.25f", "0x3f800000"),
                ("tanpif(-0.25)", "-0.25f", "0xbf800000"),
                ("tanpif(0.5)", "0.5f", "0x7f800000"),
                ("tanpif(-0.5)", "-0.5f", "0xff800000"),
                ("tanpif(1.0)", "1.0f", "0x80000000"),
                ("tanpif(-1.0)", "-1.0f", "0x00000000"),
                ("tanpif(+Infinity)", "Float.POSITIVE_INFINITY", "nan"),
                ("tanpif(-Infinity)", "Float.NEGATIVE_INFINITY", "nan"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("tanpi(+0.0)", "0.0", "0x0000000000000000"),
                ("tanpi(-0.0)", "-0.0", "0x8000000000000000"),
                ("tanpi(0.25)", "0.25", "0x3ff0000000000000"),
                ("tanpi(-0.25)", "-0.25", "0xbff0000000000000"),
                ("tanpi(0.5)", "0.5", "0x7ff0000000000000"),
                ("tanpi(-0.5)", "-0.5", "0xfff0000000000000"),
                ("tanpi(1.0)", "1.0", "0x8000000000000000"),
                ("tanpi(-1.0)", "-1.0", "0x0000000000000000"),
                ("tanpi(+Infinity)", "Double.POSITIVE_INFINITY", "nan"),
                ("tanpi(-Infinity)", "Double.NEGATIVE_INFINITY", "nan"),
            ],
        },
    },
    "atanpi": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("atanpif(+0.0)", "0.0f", "0x00000000"),
                ("atanpif(-0.0)", "-0.0f", "0x80000000"),
                ("atanpif(1.0)", "1.0f", "0x3e800000"),
                ("atanpif(-1.0)", "-1.0f", "0xbe800000"),
                ("atanpif(+Infinity)", "Float.POSITIVE_INFINITY", "0x3f000000"),
                ("atanpif(-Infinity)", "Float.NEGATIVE_INFINITY", "0xbf000000"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("atanpi(+0.0)", "0.0", "0x0000000000000000"),
                ("atanpi(-0.0)", "-0.0", "0x8000000000000000"),
                ("atanpi(1.0)", "1.0", "0x3fd0000000000000"),
                ("atanpi(-1.0)", "-1.0", "0xbfd0000000000000"),
                ("atanpi(+Infinity)", "Double.POSITIVE_INFINITY",
                 "0x3fe0000000000000"),
                ("atanpi(-Infinity)", "Double.NEGATIVE_INFINITY",
                 "0xbfe0000000000000"),
            ],
        },
    },
    "asinpi": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("asinpif(+0.0)", "0.0f", "0x00000000"),
                ("asinpif(-0.0)", "-0.0f", "0x80000000"),
                ("asinpif(0.5)", "0.5f", "0x3e2aaaab"),
                ("asinpif(1.0)", "1.0f", "0x3f000000"),
                ("asinpif(-1.0)", "-1.0f", "0xbf000000"),
                ("asinpif(2.0)", "2.0f", "nan"),
                ("asinpif(+Infinity)", "Float.POSITIVE_INFINITY", "nan"),
                ("asinpif(-Infinity)", "Float.NEGATIVE_INFINITY", "nan"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("asinpi(+0.0)", "0.0", "0x0000000000000000"),
                ("asinpi(-0.0)", "-0.0", "0x8000000000000000"),
                ("asinpi(0.5)", "0.5", "0x3fc5555555555555"),
                ("asinpi(1.0)", "1.0", "0x3fe0000000000000"),
                ("asinpi(-1.0)", "-1.0", "0xbfe0000000000000"),
                ("asinpi(2.0)", "2.0", "nan"),
                ("asinpi(+Infinity)", "Double.POSITIVE_INFINITY", "nan"),
                ("asinpi(-Infinity)", "Double.NEGATIVE_INFINITY", "nan"),
            ],
        },
    },
    "acospi": {
        32: {
            "arity": 1,
            "worst": False,
            "special": [
                ("acospif(+0.0)", "0.0f", "0x3f000000"),
                ("acospif(-0.0)", "-0.0f", "0x3f000000"),
                ("acospif(0.5)", "0.5f", "0x3eaaaaab"),
                ("acospif(1.0)", "1.0f", "0x00000000"),
                ("acospif(-1.0)", "-1.0f", "0x3f800000"),
                ("acospif(2.0)", "2.0f", "nan"),
                ("acospif(+Infinity)", "Float.POSITIVE_INFINITY", "nan"),
                ("acospif(-Infinity)", "Float.NEGATIVE_INFINITY", "nan"),
            ],
        },
        64: {
            "arity": 1,
            "worst": True,
            "special": [
                ("acospi(+0.0)", "0.0", "0x3fe0000000000000"),
                ("acospi(-0.0)", "-0.0", "0x3fe0000000000000"),
                ("acospi(0.5)", "0.5", "0x3fd5555555555555"),
                ("acospi(1.0)", "1.0", "0x0000000000000000"),
                ("acospi(-1.0)", "-1.0", "0x3ff0000000000000"),
                ("acospi(2.0)", "2.0", "nan"),
                ("acospi(+Infinity)", "Double.POSITIVE_INFINITY", "nan"),
                ("acospi(-Infinity)", "Double.NEGATIVE_INFINITY", "nan"),
            ],
        },
    },
}

# the class name of the translation where it does not follow from the
# function name by capitalising it
JAVA_NAME = {
    "sinpi": "SinPi", "cospi": "CosPi", "tanpi": "TanPi",
    "atanpi": "AtanPi", "asinpi": "AsinPi", "acospi": "AcosPi",
    "atan2": "Atan2", "atan2pi": "Atan2Pi", "exp2m1": "Exp2M1",
    "exp10m1": "Exp10M1", "log10p1": "Log10P1", "log2p1": "Log2P1",
}

HEADER = '''package io.github.ntysdd.coremath.{package};

import static io.github.ntysdd.coremath.test.Vectors.assertBits;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ntysdd.coremath.CrMath;
import io.github.ntysdd.coremath.test.Vectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests of {{@link CrMath#{name}({java_type})}} against the vectors of
 * {{@code src/test/resources/vectors/{package}/{name}.txt}}, which the C entry point
 * {{@code {entry}}} produced{wc}.
 *
 * <p>The smallest subnormal, the largest finite value and the neighbours of
 * every threshold of the translation are among the directed vectors.
 */
class {klass} {{

  private static final Vectors V = Vectors.load("/vectors/{package}/{name}.txt");

{hex_method}
  private static Stream<Arguments> directed() {{
    return Vectors.stream(V.directed);
  }}
{wc_method}
  private static Stream<Arguments> random() {{
    return Vectors.stream(V.random);
  }}

  @Test
  void specialValues() {{
{special}
  }}

'''


def main(argv):
    names = sorted(SPEC) if argv and argv[0] == "--all" else argv
    for name in names:
        if name not in SPEC:
            sys.exit("gentests: no spec for " + name)
        for width in (32, 64):
            emit(name, width)


def emit(name, width):
    spec = SPEC[name][width]
    package = "binary32" if width == 32 else "binary64"
    suffix = "" if width == 64 else "f"
    klass = JAVA_NAME.get(name, name.capitalize()) + ("FTest" if width == 32
                                                       else "Test")
    java_type = "float" if width == 32 else "double"
    box = "Float" if width == 32 else "Double"
    arity = spec["arity"]
    params = ("long input, long expected" if arity == 1
              else "long y, long x, long expected")
    arg = "input" if arity == 1 else "y, x"

    body = [HEADER.format(
        package=package, name=name, java_type=java_type, entry="cr_" + name + suffix,
        klass=klass, hex_method=hex_method(name, width, arity, suffix),
        wc_method=wc_method(spec["worst"]), special="\n".join(special(name, width)),
        wc=wc_javadoc(spec["worst"], name),
    )]
    body.append(checks(name, width, arity, params, arg))
    print((TESTS / package / (klass + ".java")).relative_to(ROOT))
    (TESTS / package / (klass + ".java")).write_text(
        "".join(body), encoding="utf-8", newline="\n")


def hex_method(name, width, arity, suffix):
    if arity == 1:
        return ('  private static String hex(long input) {\n'
                '    return "%s%s(0x" + Vectors.hex(input, %d) + ")";\n'
                '  }\n\n' % (name, suffix, width))
    return ('  private static String hex(long y, long x) {\n'
            '    return "%s%s(0x" + Vectors.hex(y, %d) + ", 0x"\n'
            '        + Vectors.hex(x, %d) + ")";\n  }\n\n'
            % (name, suffix, width, width))


def call(method, width, arity):
    conv = ("Float.intBitsToFloat((int) %s)" if width == 32
            else "Double.longBitsToDouble(%s)")
    if arity == 1:
        return "CrMath.%s(%s)" % (method, conv % "input")
    return "CrMath.%s(%s, %s)" % (method, conv % "y", conv % "x")


def special(name, width):
    spec = SPEC[name][width]
    box = "Float" if width == 32 else "Double"
    lines = []
    for label, expr, expected in spec["special"]:
        if expected == "nan":
            lines.append('    assertTrue(%s.isNaN(CrMath.%s(%s)), "%s");'
                         % (box, name, expr, label))
        else:
            lines.append('    assertBits(%sL, CrMath.%s(%s), "%s");'
                         % (expected, name, expr, label))
    conv = ("Float.intBitsToFloat" if width == 32 else "Double.longBitsToDouble")
    payloads = (["0x7fc00000", "0x7f800001", "0xffc00001", "0x7fffffff"]
                if width == 32 else
                ["0x7ff8000000000000L", "0x7ff0000000000001L",
                 "0xfff8000000000001L", "0x7fffffffffffffffL"])
    names = ["quiet NaN", "signalling NaN, smallest payload",
             "negative NaN with a payload", "NaN with every payload bit set"]
    for payload, label in zip(payloads, names):
        lines.append('    assertTrue(%s.isNaN(CrMath.%s(%s(%s))),'
                     % (box, name, conv, payload))
        lines.append('        "%s(%s, %s)");'
                     % (name + ("f" if width == 32 else ""),
                        payload.replace("L", ""), label))
    return lines


def checks(name, width, arity, params, arg):
    call_expr = call(name, width, arity)
    directed = ("  @ParameterizedTest\n  @MethodSource(\"directed\")\n"
                "  void directedCases(%s) {\n"
                "    assertBits(expected, %s, hex(%s));\n  }\n\n"
                % (params, call_expr, arg))
    worst = ""
    if SPEC[name][width]["worst"]:
        worst = ("  @ParameterizedTest\n  @MethodSource(\"worstCases\")\n"
                 "  void upstreamWorstCases(%s) {\n"
                 "    assertBits(expected, %s, hex(%s));\n  }\n\n"
                 % (params, call_expr, arg))
    random = ("  @ParameterizedTest\n  @MethodSource(\"random\")\n"
              "  void randomSweep(%s) {\n"
              "    assertBits(expected, %s, hex(%s));\n  }\n}\n"
              % (params, call_expr, arg))
    return directed + worst + random


def wc_method(worst):
    if not worst:
        return ""
    return ("\n  private static Stream<Arguments> worstCases() {\n"
            "    return Vectors.stream(V.worstCases);\n  }\n")


def wc_javadoc(worst, name):
    if not worst:
        return ""
    return (", and against a sample of the upstream worst\n * cases {@code %s.wc}"
            % name)


if __name__ == "__main__":
    main(sys.argv[1:])
