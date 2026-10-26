# core-math-java

Java port of [CORE-MATH](https://core-math.gitlabpages.inria.fr/): correctly
rounded (0.5 ulp) elementary math functions, translated from the C sources so
that the results are bit-for-bit identical to upstream, with one exception: the
bit pattern of a NaN result is not part of the promise; the promise is only
that the result is a NaN.

## Status

Early development.

## What this is

CORE-MATH provides correctly rounded implementations of the mathematical
functions in IEEE 754-2019, written in C and released under the MIT license. A
correctly rounded function has exactly one correct result for every input,
independent of language and compiler, so a faithful port produces the same bits
as the C code, including signed zeros, infinities and subnormals.

This is an unofficial port, not affiliated with the CORE-MATH project or with
INRIA. Where the Java code and the C code disagree, the C code is right. If
upstream itself is wrong, the bug is reported upstream rather than worked around
here: <https://gitlab.inria.fr/core-math/core-math/-/issues>.

The translation is mechanical: same control flow, same constant tables in the
same order, same variable names, so that anyone can put the C file and the Java
file next to each other and compare them by eye. `AGENTS.md` holds the
rules that keep it that way.

## Scope

| Function | binary32 (`float`) | binary64 (`double`) | Upstream source |
| --- | --- | --- | --- |
| `sin` | translated, tests pending | translated, tests pending | `src/binary{32,64}/sin` |
| `cos` | translated, tests pending | translated, tests pending | `src/binary{32,64}/cos` |
| `tan` | translated, tests pending | translated, tests pending | `src/binary{32,64}/tan` |
| `exp` | translated, tests pending | translated, tests pending | `src/binary{32,64}/exp` |
| `log` | translated, tests pending | translated, tests pending | `src/binary{32,64}/log` |
| `pow` | translated, tests pending | translated, tests pending | `src/binary{32,64}/pow` |
| `atan` | translated, tests pending | translated, tests pending | `src/binary{32,64}/atan` |
| `atan2` | translated, tests pending | translated, tests pending | `src/binary{32,64}/atan2` |
| `log2` | translated, tests pending | translated, tests pending | `src/binary{32,64}/log2` |
| `log10` | translated, tests pending | translated, tests pending | `src/binary{32,64}/log10` |

The translations compile and pass an offline smoke comparison, but none of them
is accompanied by the vectors the verification procedure asks for yet.

Nothing beyond the table above is scheduled. The longer-term direction, if the
port continues, is the function set of IEEE 754-2019: `exp`, `log`, `pow`, the
trigonometric family, their inverses and the hyperbolic variants.

## Requirements

* JDK 11 or newer. The code is compiled with `--release 11` and uses no API
  newer than Java 11.
* Maven 3.6 or newer to build and test.

## Usage

The API. See the scope table for which functions exist so far in which binary
format:

```java
import io.github.ntysdd.coremath.CrMath;

float  z = CrMath.sin(1.0f);    // cr_sinf, binary32
float  w = CrMath.cos(1.0f);    // cr_cosf, binary32
float  q = CrMath.tan(1.0f);    // cr_tanf, binary32
float  e = CrMath.exp(1.0f);    // cr_expf, binary32
float  g = CrMath.pow(2.0f, 3.0f); // cr_powf, binary32
double y = CrMath.sin(1.0);     // cr_sin,  binary64
double v = CrMath.cos(1.0);     // cr_cos,  binary64
double p = CrMath.tan(1.0);     // cr_tan,  binary64
double f = CrMath.exp(1.0);     // cr_exp,  binary64
double h = CrMath.pow(2.0, 3.0); // cr_pow, binary64
double a = CrMath.atan(1.0);    // cr_atan, binary64
double b = CrMath.atan2(1.0, 2.0); // cr_atan2, binary64
double c = CrMath.log2(3.0);   // cr_log2, binary64
double d = CrMath.log10(3.0);  // cr_log10, binary64
```

The two overloads are separate implementations.

## Verification

The C implementation is the oracle. This project claims only that the Java code
returns the bits the C code returns.

Testing is differential against a local build of upstream:

1. Build the upstream C source in a scratch directory outside this repository.
2. Generate vectors: a deterministic pseudo-random sample, the upstream worst
   cases (`sin.wc`, binary64 only; binary32 has no worst-case file and is swept
   exhaustively instead), and every special input that appears in the
   implementation.
3. Commit a representative subset as test resources, one line per case with the
   input and expected output as hex bit patterns, and assert exact equality in
   the JUnit tests. A line whose expected output is a NaN asserts that the port
   returns a NaN, not a particular bit pattern.

Large runs, such as the exhaustive sweep of all 2^32 binary32 inputs or a long
random sweep for binary64, happen offline in chunks: a C driver prints the
expected block, a throwaway Java runner compares it against the port, and the
loop is scripted. None of that is part of the default build.

The vectors are checked in as plain text.

## Building and testing

The commands are:

```bash
mvn -q verify          # compile, run tests, build the jar
mvn -q -Dtest=SinFTest test
```

## Developing

Correspondence between the two trees:

| CORE-MATH | core-math-java |
| --- | --- |
| `src/binary32/sin/sinf.c` (`cr_sinf`) | `io.github.ntysdd.coremath.binary32.SinF` |
| `src/binary64/sin/sin.c` (`cr_sin`) | `io.github.ntysdd.coremath.binary64.Sin` |
| `src/binary32/cos/cosf.c` (`cr_cosf`) | `io.github.ntysdd.coremath.binary32.CosF` |
| `src/binary64/cos/cos.c` (`cr_cos`) | `io.github.ntysdd.coremath.binary64.Cos` |
| `src/binary32/tan/tanf.c` (`cr_tanf`) | `io.github.ntysdd.coremath.binary32.TanF` |
| `src/binary64/tan/tan.c` (`cr_tan`) | `io.github.ntysdd.coremath.binary64.Tan` |
| `src/binary32/exp/expf.c` (`cr_expf`) | `io.github.ntysdd.coremath.binary32.ExpF` |
| `src/binary64/exp/exp.c` (`cr_exp`) | `io.github.ntysdd.coremath.binary64.Exp` |
| `src/binary32/log/logf.c` (`cr_logf`) | `io.github.ntysdd.coremath.binary32.LogF` |
| `src/binary64/log/log.c` (`cr_log`) and `dint.h` | `io.github.ntysdd.coremath.binary64.Log` |
| `src/binary32/pow/powf.c` (`cr_powf`) | `io.github.ntysdd.coremath.binary32.PowF` |
| `src/binary64/pow/pow.c` (`cr_pow`), `pow.h`, `dint.h` and `qint.h` | `io.github.ntysdd.coremath.binary64.Pow` |
| `src/binary32/atan/atanf.c` (`cr_atanf`) | `io.github.ntysdd.coremath.binary32.AtanF` |
| `src/binary64/atan/atan.c` (`cr_atan`) | `io.github.ntysdd.coremath.binary64.Atan` |
| `src/binary32/atan2/atan2f.c` (`cr_atan2f`) | `io.github.ntysdd.coremath.binary32.Atan2F` |
| `src/binary64/atan2/atan2.c` (`cr_atan2`) and `tint.h` | `io.github.ntysdd.coremath.binary64.Atan2` |
| `src/binary32/log2/log2f.c` (`cr_log2f`) | `io.github.ntysdd.coremath.binary32.Log2F` |
| `src/binary64/log2/log2.c` (`cr_log2`) | `io.github.ntysdd.coremath.binary64.Log2` |
| `src/binary32/log10/log10f.c` (`cr_log10f`) | `io.github.ntysdd.coremath.binary32.Log10F` |
| `src/binary64/log10/log10.c` (`cr_log10`) and `dint.h` | `io.github.ntysdd.coremath.binary64.Log10` |
| public entry points | `io.github.ntysdd.coremath.CrMath` |

`AGENTS.md` holds the rules for changing code here: translation
conventions, the C to Java mapping table, test requirements and commit format.

## License

MIT, see [LICENSE](LICENSE).

The copyright notices of the CORE-MATH contributors are kept in each translated file,
together with the upstream path and commit the file came from. Do not remove them.
