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
    klass = name.capitalize() + ("FTest" if width == 32 else "Test")
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
