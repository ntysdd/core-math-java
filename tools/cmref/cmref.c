/*
 * cmref - prints the CORE-MATH result bits of the inputs it reads.
 *
 * A development tool, not part of the Maven build.  It exists so that the
 * vectors under src/test/resources/vectors can be regenerated from a local
 * checkout of CORE-MATH, as AGENTS.md section 5 describes; tools/mkvectors.py
 * drives it.
 *
 * usage: cmref <function> <32|64> < inputs
 *   unary functions: one hexadecimal bit pattern per line
 *   binary functions: two per line, y then x, the C argument order
 * prints "<input bits> <result bits>" per line, with a third column for
 * lgamma (signgam) and for sincos (the cosine; the sine is the second
 * column, the order of cr_sincos).
 *
 *   cmref --frontier <function>
 * scans the whole binary32 domain and prints, one per line as a hexadecimal
 * bit pattern, the inputs whose binary32 result differs from the binary64
 * result rounded to float.  Those are the inputs where the correctly rounded
 * float result is decided by the last bit, the binary32 analogue of an
 * upstream .wc file; tools/mkvectors.py adds them to the directed cases.
 */

#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

extern float cr_sinf (float);
extern double cr_sin (double);
extern float cr_cosf (float);
extern double cr_cos (double);
extern float cr_tanf (float);
extern double cr_tan (double);
extern float cr_expf (float);
extern double cr_exp (double);
extern float cr_logf (float);
extern double cr_log (double);
extern float cr_log2f (float);
extern double cr_log2 (double);
extern float cr_log10f (float);
extern double cr_log10 (double);
extern float cr_atanf (float);
extern double cr_atan (double);
extern float cr_asinf (float);
extern double cr_asin (double);
extern float cr_acosf (float);
extern double cr_acos (double);
extern float cr_sinhf (float);
extern double cr_sinh (double);
extern float cr_coshf (float);
extern double cr_cosh (double);
extern float cr_tanhf (float);
extern double cr_tanh (double);
extern float cr_cbrtf (float);
extern double cr_cbrt (double);
extern float cr_exp2f (float);
extern double cr_exp2 (double);
extern float cr_log1pf (float);
extern double cr_log1p (double);
extern float cr_expm1f (float);
extern double cr_expm1 (double);
extern float cr_sinpif (float);
extern double cr_sinpi (double);
extern float cr_cospif (float);
extern double cr_cospi (double);
extern float cr_asinhf (float);
extern double cr_asinh (double);
extern float cr_acoshf (float);
extern double cr_acosh (double);
extern float cr_atanhf (float);
extern double cr_atanh (double);
extern float cr_erff (float);
extern double cr_erf (double);
extern float cr_erfcf (float);
extern double cr_erfc (double);
extern float cr_tanpif (float);
extern double cr_tanpi (double);
extern float cr_atanpif (float);
extern double cr_atanpi (double);
extern float cr_asinpif (float);
extern double cr_asinpi (double);
extern float cr_acospif (float);
extern double cr_acospi (double);
extern float cr_rsqrtf (float);
extern double cr_rsqrt (double);
extern float cr_exp10f (float);
extern double cr_exp10 (double);
extern float cr_exp2m1f (float);
extern double cr_exp2m1 (double);
extern float cr_exp10m1f (float);
extern double cr_exp10m1 (double);
extern float cr_log10p1f (float);
extern double cr_log10p1 (double);
extern float cr_log2p1f (float);
extern double cr_log2p1 (double);
extern float cr_tgammaf (float);
extern double cr_tgamma (double);
extern float cr_lgammaf (float);
extern double cr_lgamma (double);
extern float cr_atan2f (float, float);
extern double cr_atan2 (double, double);
extern float cr_hypotf (float, float);
extern double cr_hypot (double, double);
extern float cr_powf (float, float);
extern double cr_pow (double, double);
extern float cr_atan2pif (float, float);
extern double cr_atan2pi (double, double);
extern float cr_compoundf (float, float);
extern void cr_sincosf (float, float *, float *);
extern void cr_sincos (double, double *, double *);

typedef struct {
  const char *name;
  float (*f32) (float);
  double (*f64) (double);
} unary_t;

static const unary_t UNARY_FUNCTIONS[] = {
  { "sin", cr_sinf, cr_sin },
  { "cos", cr_cosf, cr_cos },
  { "tan", cr_tanf, cr_tan },
  { "exp", cr_expf, cr_exp },
  { "log", cr_logf, cr_log },
  { "log2", cr_log2f, cr_log2 },
  { "log10", cr_log10f, cr_log10 },
  { "atan", cr_atanf, cr_atan },
  { "asin", cr_asinf, cr_asin },
  { "acos", cr_acosf, cr_acos },
  { "sinh", cr_sinhf, cr_sinh },
  { "cosh", cr_coshf, cr_cosh },
  { "tanh", cr_tanhf, cr_tanh },
  { "cbrt", cr_cbrtf, cr_cbrt },
  { "exp2", cr_exp2f, cr_exp2 },
  { "log1p", cr_log1pf, cr_log1p },
  { "expm1", cr_expm1f, cr_expm1 },
  { "sinpi", cr_sinpif, cr_sinpi },
  { "cospi", cr_cospif, cr_cospi },
  { "asinh", cr_asinhf, cr_asinh },
  { "acosh", cr_acoshf, cr_acosh },
  { "atanh", cr_atanhf, cr_atanh },
  { "erf", cr_erff, cr_erf },
  { "erfc", cr_erfcf, cr_erfc },
  { "tanpi", cr_tanpif, cr_tanpi },
  { "atanpi", cr_atanpif, cr_atanpi },
  { "asinpi", cr_asinpif, cr_asinpi },
  { "acospi", cr_acospif, cr_acospi },
  { "rsqrt", cr_rsqrtf, cr_rsqrt },
  { "exp10", cr_exp10f, cr_exp10 },
  { "exp2m1", cr_exp2m1f, cr_exp2m1 },
  { "exp10m1", cr_exp10m1f, cr_exp10m1 },
  { "log10p1", cr_log10p1f, cr_log10p1 },
  { "log2p1", cr_log2p1f, cr_log2p1 },
  { "tgamma", cr_tgammaf, cr_tgamma },
};

typedef struct {
  const char *name;
  float (*f32) (float, float);
  double (*f64) (double, double);
} binary_t;

static const binary_t BINARY_FUNCTIONS[] = {
  { "atan2", cr_atan2f, cr_atan2 },
  { "hypot", cr_hypotf, cr_hypot },
  { "pow", cr_powf, cr_pow },
  { "atan2pi", cr_atan2pif, cr_atan2pi },
  { "compound", cr_compoundf, NULL },
};

#define COUNT(a) (sizeof (a) / sizeof (a[0]))

static int cmp_bits (const void *pa, const void *pb)
{
  uint32_t a = *(const uint32_t *) pa, b = *(const uint32_t *) pb;
  return a < b ? -1 : a > b;
}

/* the binary32 domain, one input at a time: keep the inputs where the float
   result and the binary64 result rounded to float disagree, NaN payload
   differences aside.  OpenMP spreads it over the cores when the build has
   it; without it the pragma is a comment and the scan is just slower. */
static int frontier (const char *name)
{
  const unary_t *f = NULL;
  for (size_t i = 0; i < COUNT (UNARY_FUNCTIONS); i++)
    if (strcmp (name, UNARY_FUNCTIONS[i].name) == 0)
      f = &UNARY_FUNCTIONS[i];
  if (f == NULL)
    {
      fprintf (stderr, "cmref: --frontier needs a unary function, not %s\n", name);
      return 2;
    }

  size_t cap = 1 << 20, n = 0;
  uint32_t *hits = malloc (cap * sizeof *hits);
  if (hits == NULL)
    {
      fprintf (stderr, "cmref: out of memory\n");
      return 2;
    }
#pragma omp parallel for schedule (dynamic)
  for (int high = 0; high < (1 << 16); high++)
    for (int low = 0; low < (1 << 16); low++)
      {
        uint32_t u = ((uint32_t) high << 16) | (uint32_t) low;
        float x;
        memcpy (&x, &u, 4);
        float y32 = f->f32 (x), y64 = (float) f->f64 ((double) x);
        uint32_t a, b;
        memcpy (&a, &y32, 4);
        memcpy (&b, &y64, 4);
        if (a != b && !(isnan (y32) && isnan (y64)))
          {
#pragma omp critical
            {
              if (n < cap) hits[n++] = u;
            }
          }
      }
  qsort (hits, n, sizeof *hits, cmp_bits);
  for (size_t i = 0; i < n; i++) printf ("%08x\n", (unsigned) hits[i]);
  if (n == cap)
    fprintf (stderr, "cmref: %zu inputs, the list is truncated\n", n);
  else
    fprintf (stderr, "cmref: %zu inputs of the binary32 domain\n", n);
  free (hits);
  return 0;
}

/* reads one hexadecimal bit pattern, skipping comments and blank lines */
static int get_bits (char *line, uint64_t *v)
{
  if (line[0] == '#' || line[0] == '\n') return 0;
  char *end;
  *v = strtoull (line, &end, 16);
  return end != line;
}

int main (int argc, char *argv[])
{
  if (argc == 3 && strcmp (argv[1], "--frontier") == 0)
    return frontier (argv[2]);
  if (argc != 3 || (strcmp (argv[2], "32") != 0 && strcmp (argv[2], "64") != 0))
    {
      fprintf (stderr, "usage: cmref <function> <32|64>\n");
      fprintf (stderr, "       cmref --frontier <function>\n");
      return 2;
    }
  const char *name = argv[1];
  int wide = argv[2][0] == '6';
  char line[512];
  uint64_t b;

  if (strcmp (name, "sincos") == 0)
    {
      while (fgets (line, sizeof line, stdin))
        if (get_bits (line, &b))
          {
            if (wide)
              {
                double x, s, c;
                uint64_t xb = b, sb, cb;
                memcpy (&x, &xb, 8);
                cr_sincos (x, &s, &c);
                memcpy (&sb, &s, 8);
                memcpy (&cb, &c, 8);
                printf ("%016llx %016llx %016llx\n", (unsigned long long) xb,
                        (unsigned long long) sb, (unsigned long long) cb);
              }
            else
              {
                float x, s, c;
                uint32_t xb = (uint32_t) b, sb, cb;
                memcpy (&x, &xb, 4);
                cr_sincosf (x, &s, &c);
                memcpy (&sb, &s, 4);
                memcpy (&cb, &c, 4);
                printf ("%08x %08x %08x\n", (unsigned) xb, (unsigned) sb,
                        (unsigned) cb);
              }
          }
      return 0;
    }

  if (strcmp (name, "lgamma") == 0)
    {
      while (fgets (line, sizeof line, stdin))
        if (get_bits (line, &b))
          {
            if (wide)
              {
                double x, y;
                uint64_t xb = b, yb;
                memcpy (&x, &xb, 8);
                y = cr_lgamma (x);
                memcpy (&yb, &y, 8);
                printf ("%016llx %016llx %d\n", (unsigned long long) xb,
                        (unsigned long long) yb, signgam);
              }
            else
              {
                float x, y;
                uint32_t xb = (uint32_t) b, yb;
                memcpy (&x, &xb, 4);
                y = cr_lgammaf (x);
                memcpy (&yb, &y, 4);
                printf ("%08x %08x %d\n", (unsigned) xb, (unsigned) yb, signgam);
              }
          }
      return 0;
    }

  for (size_t i = 0; i < COUNT (UNARY_FUNCTIONS); i++)
    if (strcmp (name, UNARY_FUNCTIONS[i].name) == 0)
      {
        while (fgets (line, sizeof line, stdin))
          if (get_bits (line, &b))
            {
              if (wide)
                {
                  double x, y;
                  uint64_t xb = b, yb;
                  memcpy (&x, &xb, 8);
                  y = UNARY_FUNCTIONS[i].f64 (x);
                  memcpy (&yb, &y, 8);
                  printf ("%016llx %016llx\n", (unsigned long long) xb,
                          (unsigned long long) yb);
                }
              else
                {
                  float x, y;
                  uint32_t xb = (uint32_t) b, yb;
                  memcpy (&x, &xb, 4);
                  y = UNARY_FUNCTIONS[i].f32 (x);
                  memcpy (&yb, &y, 4);
                  printf ("%08x %08x\n", (unsigned) xb, (unsigned) yb);
                }
            }
        return 0;
      }

  for (size_t i = 0; i < COUNT (BINARY_FUNCTIONS); i++)
    if (strcmp (name, BINARY_FUNCTIONS[i].name) == 0)
      {
        while (fgets (line, sizeof line, stdin))
          {
            if (line[0] == '#' || line[0] == '\n') continue;
            char *end;
            unsigned long long yb = strtoull (line, &end, 16);
            if (end == line) continue;
            unsigned long long xb = strtoull (end, &end, 16);
            if (wide)
              {
                double y, x, z;
                uint64_t zb;
                memcpy (&y, &yb, 8);
                memcpy (&x, &xb, 8);
                z = BINARY_FUNCTIONS[i].f64 (y, x);
                memcpy (&zb, &z, 8);
                printf ("%016llx %016llx %016llx\n", yb, xb,
                        (unsigned long long) zb);
              }
            else
              {
                float y, x, z;
                uint32_t yb32 = (uint32_t) yb, xb32 = (uint32_t) xb, zb;
                memcpy (&y, &yb32, 4);
                memcpy (&x, &xb32, 4);
                z = BINARY_FUNCTIONS[i].f32 (y, x);
                memcpy (&zb, &z, 4);
                printf ("%08x %08x %08x\n", (unsigned) yb32, (unsigned) xb32,
                        (unsigned) zb);
              }
          }
        return 0;
      }

  fprintf (stderr, "cmref: unknown function %s\n", name);
  return 2;
}
