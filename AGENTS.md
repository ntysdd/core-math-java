# AGENTS.md

## 1. Hard rules

1. Java 11 is the language level. Compile with `<release>11</release>`.
2. No third-party runtime dependencies: no libraries, no native code, no
   build-time code generation. The jar has to run on a bare JDK 11.
3. Nothing is added to the build beyond JUnit 5 (`junit-jupiter`) for tests:
   no assertion or mocking library, no benchmark harness, no formatter plugin,
   no static analyzer.
4. No reflection, no `Unsafe`, no `MethodHandles` tricks, no `VarHandle`, no
   `Class.forName`, no `setAccessible`. Bit access to floating-point values goes
   through `Double.doubleToRawLongBits`, `Double.longBitsToDouble`,
   `Float.floatToRawIntBits`, `Float.intBitsToFloat`.
5. No `module-info.java`. Plain classpath jar.
6. Everything committed is in English: identifiers, comments, Javadoc, commit
   messages, branch names, file names, documentation.
7. The public API is `CrMath`. For example, `CrMath.sin(double)` and
   `CrMath.sin(float)` correspond to `cr_sin` and `cr_sinf`. The translated code
   lives in `io.github.ntysdd.coremath.binary32` and
   `io.github.ntysdd.coremath.binary64`, one package per upstream directory;
   shared helpers such as `U128` live in `io.github.ntysdd.coremath.internal`.
   None of those are API, even where a class is `public` for package or test
   reasons.
8. Do not change the algorithm. No equivalent-looking rewrites, no simplified
   expressions, no reordered conditionals, no strength reduction, no
   re-associated floating-point arithmetic. `(a + b) + c` and `a + (b + c)` are
   different programs here.
9. Do not delete code that looks unreachable at run time. Keep the branch,
   translate it; unreachability is part of the correctness argument. This is
   about branches, not about `#if` variants, which are dropped according to the
   last row of the mapping table in §3.
10. No static mutable state. A translation unit may have `static final` constants
    and tables. Scratch objects are per call or per instance.
11. Upstream is the oracle. Do not add a second reference implementation (MPFR,
    `BigDecimal`, a hand-written high-precision `sin`) to demonstrate that the
    port is right. Whether CORE-MATH is correctly rounded is argued upstream;
    agreeing with CORE-MATH is what this repository tests.
12. Every class that does floating-point work is `strictfp`.

## 2. Translating a function

One upstream directory becomes one Java package, one C file becomes one Java
class.

```
core-math:  src/binary64/sin/sin.c      ->  io.github.ntysdd.coremath.binary64.Sin
core-math:  src/binary32/sin/sinf.c     ->  io.github.ntysdd.coremath.binary32.SinF
core-math:  src/binary64/sin/buildSC.c  ->  not translated (build-time tool)
```

* Mirror the structure: same order of declarations, initializers and branches. If
  the C file has two helper functions and one entry point, the Java class has two
  private static methods and one entry point.
* Mirror the names. Locals and tables keep their C spelling, including `z2`,
  `z4`, `aa`, `bb`, `s0`, `c0`, `PS`, `PC`, `_T`, `U1`. Classes and
  methods are renamed: `as_sinf_database` becomes `asSinfDatabase`, `cr_sinf`
  becomes `SinF.compute`.
* Copy tables verbatim, same order, one entry per line. Convert C hexadecimal
  float literals to Java hexadecimal literals without touching a bit, adding the
  `p` exponent that Java requires and C allows to be omitted. Do not regenerate
  tables at build time, do not reformat them as decimal.
* One statement per C statement. Do not fuse or split statements, do not hoist
  subexpressions into locals, do not invent temporaries the C code does not have.
* Short `//` comments from the C file are kept as they are. Do not wrap
  translated bodies in Javadoc. Explanations belong in the class Javadoc or
  nowhere.
* Every translated file starts with a header containing:
  1. upstream project, file path and commit hash,
  2. the original copyright line and MIT notice,
  3. the entry point it corresponds to (`cr_sin`, ...),
  4. one line on what was added locally, usually "translated without changes".
* When upstream changes, update by diffing the C file against the commit in the
  header and replaying the same change, and bump the hash in the same commit.

## 3. C to Java mapping

Add missing cases to this table instead of inventing a local convention.

| C | Java |
| --- | --- |
| `double`, `float` | `double`, `float` |
| `int`, `int32_t` | `int` |
| `long`, `int64_t`, `uint64_t` | `long`, unsigned views through `Long.compareUnsigned`, `Long.divideUnsigned`, `Long.toUnsignedString`; never mix signed and unsigned comparisons by accident |
| `unsigned char`, `uint8_t` | `int`, range-checked where the range matters |
| `u128` / `unsigned __int128` | `long hi` plus `long lo` with static helpers in `internal.U128`. 64x64 to 128 goes through `Math.multiplyHigh` with the unsigned correction, since `Math.unsignedMultiplyHigh` is Java 18 |
| `b64u64_u`, `b32u32_u` unions | `Double.doubleToRawLongBits` and friends, or a small `internal` value class with a comment naming the C union |
| `dint64_t` and similar structs | a small mutable `internal` class with the same field names (`lo`, `hi`, `ex`, `sgn`) and the same invariants, described in its Javadoc |
| `__builtin_fma`, `__builtin_fmaf` | `Math.fma`, both overloads |
| `__builtin_roundeven`, `rint` | `Math.rint` |
| `__builtin_round` | nothing direct: `Math.round` returns a `long` and saturates at the ends, `Math.rint` breaks ties to even. Upstream reaches it only from the software fallback of `roundeven_finite`, which the reference build does not compile |
| `__builtin_copysign`, `copysignf` | `Math.copySign` |
| `__builtin_fabs`, `fabsf` | `Math.abs`, which clears the sign bit and leaves every other bit alone, NaN payload included |
| `__builtin_ctz`, `clz`, `popcount` and their `l` / `ll` variants | `Long.numberOfTrailingZeros`, `numberOfLeadingZeros`, `bitCount`, or the `Integer` version when the C type is 32 bits. `clzl` counts on a 64-bit `long` under LP64, so it maps to the `Long` version. Zero is undefined upstream and returns the width in Java |
| `__builtin_floor`, `floorf` | `Math.floor`, which returns a `double`; cast where the C code stores into a `float` |
| `__builtin_fmax`, `fmaxf` | `Math.max`, except that `fmax` returns the other operand when one is NaN while `Math.max` propagates the NaN; check that the call site cannot see a NaN |
| `*(uint64_t*)&d` and other type punning | `Double.doubleToRawLongBits(d)` |
| `errno` assignments (`EDOM`, `EINVAL`, `ERANGE`) | omit. Java has no `errno`, and upstream assigns it only under `CORE_MATH_SUPPORT_ERRNO`, which no caller here can observe |
| `fetestexcept`, `feraiseexcept`, `fesetround` | omit, same reason. The JVM exposes no floating-point environment and round-to-nearest-even is the only mode |
| `exit(1)` in a verification path | `throw new IllegalStateException(...)` |
| `printf` in translated code | remove. The library does no logging |
| `__attribute__((noinline))`, `__builtin_expect` | nothing, the JIT decides and profiles branches itself |
| `#pragma STDC FENV_ACCESS` | nothing |
| `static const` tables | `private static final` fields, same order, same values |
| `#if`-selected variants | translate the one the reference build uses (GCC 10+ or Clang 17+, `-O2`, `-ffp-contract=off`, no FMA contraction unless the source calls `__builtin_fma`), drop the rest. This is the one place where code is removed rather than translated |

Floating-point behavior that has to survive the translation:

* Call `Math.fma` only where the C code calls `__builtin_fma`.
* Where the C code narrows a `double` to `float`, write the
  `(float)` cast.
* Where the C code builds a NaN by arithmetic (for example `0.0f/0.0f`, meant
  to raise `FE_INVALID`), write a NaN constant instead. Java has no
  floating-point exceptions; do not build NaNs by arithmetic.

## 4. Style

* Two-space indent, no tabs, no trailing whitespace, LF line endings, newline at
  end of file.
* Braces and line breaks follow the C file so the diffs line up. In layout, the
  only allowed differences are the ones the language forces: unions, pointers,
  `#define`. Renames and dropped `#if` variants are §2 and §3, not this section.
* Soft limit of 100 columns. If a C line is longer, break it the way the C code
  breaks it rather than reflowing.
* No wildcard imports, no fully qualified names inside bodies.
* No `final` on locals and parameters; it is noise in a mechanical translation.
  `final` on fields and constants is fine.
* Comments explain provenance and deviations only. Do not translate comments that
  were about the C build system, and do not restate the code in prose.

## 5. Tests

The oracle is a local build of upstream CORE-MATH; see §1 rule 11 for why nothing
else may serve as a reference.

Every function ships with tests in `src/test/java` covering:

1. Special values: `±0.0`, `±Infinity`, NaN with several payloads, the smallest
   subnormal (`Double.MIN_VALUE` in binary64, `Float.MIN_VALUE` in binary32), and
   `Double.MAX_VALUE` / `Float.MAX_VALUE`. Every NaN is asserted with `isNaN`,
   never with a bit comparison.
2. Branch boundaries: every comparison threshold in the translated code, tested
   on both sides. Take the constants from the C source.
3. The exceptional entries embedded in the C source, for example the rows of
   `as_sinf_database` that the C entry point can actually reach. Reaching those
   inputs through `CrMath` is enough; do not call the translated helper directly
   to manufacture coverage, and do not hold up a review over a row that upstream
   itself never reaches.
4. A sample of upstream worst cases from `sin.wc` (binary64 only; the binary32
   directory has no worst-case file, exhaustive is the only option there).
5. A deterministic pseudo-random sweep, using a small xorshift in the test class
   with a fixed seed. No external RNG, no `ThreadLocalRandom`.

Test rules:

* Expected values are hex bit patterns committed as resources, one case per line.
  A NaN expected value keeps the bits the C build printed, since they document
  what the reference did, but the assertion is `isNaN`.
* Tests are deterministic, offline, and finish in seconds under `mvn verify`.
  Long runs are documented in the README as manual procedures.
* Do not weaken an assertion to make it pass. If a vector disagrees with the
  port, the port is wrong until shown otherwise. If it disagrees with the C code,
  either the generator is wrong or upstream has a bug, and an upstream bug is
  reported upstream.

### Generating vectors

Vectors are produced outside the build, from a local checkout of upstream:

1. Check out the commit recorded in the Java file header.
2. Build the function with reference flags: GCC 10+ or Clang 17+ at `-O2`,
   with `-ffp-contract=off`, no `-ffast-math`. FMA happens only where the
   source calls `__builtin_fma`.
3. Feed the input set through the C entry point and print the raw bit patterns,
   one line per case: `<input> <output>` in hexadecimal.
4. Commit the file as `src/test/resources/vectors/<type>/<name>.txt`, with a `#`
   header naming the upstream commit, the compiler flags and how the input set
   was produced.

The generator is a throwaway C program of a few dozen lines and is not part of
the build. If it turns out to be worth keeping, it goes under `tools/` and is
still never compiled by Maven.

## 6. Commands

```bash
mvn -q verify                 # compile, test, build the jar
mvn -q -Dtest=SinTest test    # one test class
mvn -q -DskipTests package    # jar only
```

`pom.xml` stays minimal: `maven-compiler-plugin` with `<release>11</release>` and
`maven-surefire-plugin`. A new plugin needs an entry in this file explaining why.

## 7. Commits

* English, imperative mood, Conventional Commits prefixes: `feat:`, `fix:`,
  `test:`, `docs:`, `chore:`, `refactor:`.
* One function, or one coherent translation step, per commit. For example:

  ```
  feat: translate cr_sinf from CORE-MATH binary32/sin

  Upstream: gitlab.inria.fr/core-math/core-math @ <commit>
  Source:   src/binary32/sin/sinf.c
  Java:     io.github.ntysdd.coremath.binary32.SinF

  Mechanical translation. Tested against the special values, the branch
  boundaries and the exceptional inputs of as_sinf_database; the random sweep
  and the exhaustive binary32 sweep were run offline as described in the README.
  ```

* Do not commit `target/`, IDE metadata, or vectors that the documented
  procedure cannot regenerate.
* Work on `master` for now. No long-lived branches, no merge commits unless a
  branch turns out to be necessary.

## 8. Before asking for review

* The file header names the upstream path and the commit hash.
* The upstream copyright line and MIT notice are in the file.
* The C file and the Java file line up: same order, same names, same constants,
  same branches.
* Nothing departs from the C code beyond the mechanical mapping in §3, and
  anything that would have needed approval went through §9 first.
* Every class doing floating-point work is `strictfp`.
* NaN results are asserted with `isNaN`, and no C `0.0f/0.0f` was carried over
  as arithmetic (§3).
* Tests cover special values, branch boundaries, the reachable exceptional entries
  and a sample of worst cases, and `mvn verify` is green.
* `CrMath` has the `float` and `double` overloads and Javadoc that states correct
  rounding and the special-case results.
* No new dependency, no reflection, no `Unsafe`, no `module-info.java`, no
  non-English text.
* The scope table in the README matches what actually exists.

## 9. Ask before doing these

* Adding a dependency, a plugin or a module.
* Changing `CrMath` signatures or adding a public class.
* Deviating from the C control flow, for performance or for anything else.
* Working around an upstream bug instead of reporting it.
* Introducing anything that makes the C to Java comparison harder: helper
  abstractions, new data structures, generic containers replacing fixed-size
  arrays.

## 10. Out of scope

Benchmarks and cycle counts, JPMS, OSGi, Android, `long double` and binary128,
the three directed rounding modes, FMA policy variants, coverage thresholds, API
stability promises.
