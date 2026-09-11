#!/usr/bin/env python3
#
# Copyright (c) 2026 MacArthur Foundation
# License: Expat (MIT) license.
#
# Pre-shade Keycloak classpath overlap check.
#
# Compares the .class entries in a set of jars -- the resolved `runtimeClasspath`
# of twilio-keycloak-provider (twilio + all its transitive deps, BEFORE the
# shadow plugin's exclude/relocate directives strip anything) -- against the
# .class entries in every jar on a Keycloak distribution's RUNTIME classpath.
#
# Purpose: detect, at the dependency-resolution layer (pre-shade), jars that
# twilio pulls in whose classes would collide with classes Keycloak itself
# ships. The shadow plugin's exclude(dependency(...)) removes a whole jar from
# the fat jar; relocate(...) renames colliding classes as a defense-in-depth
# fallback. Neither changes dependency RESOLUTION, so this pre-shade view shows
# the raw overlap the exclude/relocate config exists to neutralize. Per project
# policy, an overlap is acceptable ONLY when the twilio jar is excluded (keeps
# the fat jar small); relocation alone is NOT sufficient -- it is defensive
# against future Keycloak upgrades, not a substitute for an exclude. So the
# check FAILS on any overlapping twilio jar that is not excluded.
#
# This is the engine behind the `keycloak-classpath-overlap` GitHub Actions
# workflow (.github/workflows/keycloak-classpath-overlap.yml). Run locally:
#
#     python3 twilio-keycloak-provider/scripts/check_keycloak_classpath_overlap.py \
#         <twilio-runtime-classpath-dir> <unpacked-keycloak-root> <build.gradle.kts>
#
# The build.gradle.kts arg supplies the shadowJar exclude(dependency(...)) and
# relocate(...) directives used to classify each overlap as covered/uncovered.
#
# Inputs (positional args):
#   1) a directory containing the resolved twilio runtime-classpath jars
#      (produced by `gradlew -I scripts/collect-runtime-classpath.init.gradle.kts
#      collectRuntimeClasspathForOverlap`).
#   2) path to an UNPACKED Keycloak distribution root (the dir containing lib/).
#
# Output (stdout): a human-readable report grouped first by twilio-side jar,
# then by the Keycloak jar(s) each of its classes collides with. Exit code:
#   0  -- every overlapping twilio jar is excluded, or there is no overlap.
#   1  -- at least one overlapping twilio jar is NOT excluded (the fat jar
#         would ship its classes; add an exclude(dependency(...)) -- a
#         relocate(...) is defensive and does NOT make this pass).
#   2  -- usage / input error.
#
# When run inside GitHub Actions (GITHUB_ACTIONS=true) the report also emits
# ::notice:: / ::error:: workflow commands so the result shows up on the PR
# checks UI.
#
# Non-.class entries (resources, META-INF/services, META-INF/MANIFEST.MF,
# module-info.class, etc.) are intentionally NOT considered overlaps here --
# the concern being checked is duplicated CLASSES on the classpath.
import os
import re
import sys
import zipfile
from collections import defaultdict

# A versioned class entry in a multi-release jar, e.g.
# META-INF/versions/9/com/example/Foo.class -> (9, com/example/Foo.class).
_MR_ENTRY = re.compile(r"^META-INF/versions/(\d+)/(.+)$")

# The Java runtime version the provider targets at runtime (Keycloak's JRE).
# A multi-release jar's versioned class only overrides the base class for a
# runtime at or above that version, so versioned entries above this target do
# NOT participate in the classpath and must be ignored (otherwise a
# versions/21 entry would create a false collision with a base copy on a
# Java-17 target). Override with $OVERLAP_CHECK_JAVA_TARGET for other targets.
_JAVA_TARGET = int(os.environ.get("OVERLAP_CHECK_JAVA_TARGET", "17"))


def _is_multi_release(zf):
    """Return True if the jar's manifest declares Multi-Release: true."""
    if "META-INF/MANIFEST.MF" not in zf.namelist():
        return False
    try:
        raw = zf.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
    except KeyError:
        return False
    for line in raw.splitlines():
        if line.lower().startswith("multi-release"):
            return line.split(":", 1)[1].strip().lower() == "true"
    return False


def list_classes(jar_path):
    """Return the set of logical .class entry names inside a jar.

    Excludes directories and module-info.class (a module declaration, not a
    normal class). For a multi-release jar (manifest declares
    Multi-Release: true), a versioned entry META-INF/versions/<n>/<logical>
    overrides the base <logical> class only on a runtime >= <n>, so:
      - a versioned entry with n <= the Java target (17 by default) is
        normalized to its logical path (it IS that class at runtime), and
      - a versioned entry with n > the target is dropped (it does not load on
        the target runtime).
    For a NON-multi-release jar, META-INF/versions/<n>/ entries are not
    classes that load and are dropped. This avoids false collisions from
    over-eager normalization (e.g. a versions/21 entry on a Java-17 target).

    Read errors (corrupt jar, missing file) propagate rather than returning an
    empty set, so an unreadable input fails the check loudly instead of
    looking like a jar with no overlapping classes.
    """
    classes = set()
    with zipfile.ZipFile(jar_path) as zf:
        multi_release = _is_multi_release(zf)
        for name in zf.namelist():
            if name.endswith("/") or not name.endswith(".class"):
                continue
            m = _MR_ENTRY.match(name)
            if m:
                # Versioned entry: only meaningful for an MR jar, and only if
                # the entry version is <= the target runtime.
                if not multi_release or int(m.group(1)) > _JAVA_TARGET:
                    continue
                logical = m.group(2)
            else:
                logical = name
            if logical == "module-info.class" or logical.endswith(
                "/module-info.class"
            ):
                continue
            classes.add(logical)
    return classes


def gather_twilio_jars(twilio_dir):
    """Return sorted absolute paths of every .jar under the given dir."""
    jars = []
    for dirpath, _dirs, files in os.walk(twilio_dir):
        for f in files:
            if f.endswith(".jar"):
                jars.append(os.path.abspath(os.path.join(dirpath, f)))
    return sorted(jars)


def gather_keycloak_jars(keycloak_root):
    """Find every .jar on the Keycloak RUNTIME classpath.

    Keycloak's Quarkus distribution lays jars out under lib/ as:

      lib/lib/main/*.jar       -- the main runtime classpath; this is what a
                                  provider's classloader sees as its parent.
      lib/lib/boot/*.jar       -- the boot/launcher classloader (parent of
                                  main); also visible to providers.
      lib/app/keycloak.jar     -- the Keycloak application itself.
      lib/quarkus/*.jar        -- Quarkus-generated/transformed bytecode
                                  loaded at runtime.
      lib/lib/deployment/*.jar -- build/augmentation-time ONLY. Used to
                                  generate lib/quarkus/* at build time and
                                  NOT on the runtime classpath, so EXCLUDED
                                  to avoid false-positive overlaps.
      providers/*.jar          -- user-dropped provider jars (where the shadow
                                  jar built here would be deployed).

    We recurse lib/ and providers/ but skip the lib/lib/deployment subtree.
    The lib/lib/{main,boot,deployment} layout has been stable across Keycloak
    26.x Quarkus releases.
    """
    jars = []
    deployment_subdir = os.path.join(keycloak_root, "lib", "lib", "deployment")
    for sub in ("lib", "providers"):
        base = os.path.join(keycloak_root, sub)
        if not os.path.isdir(base):
            continue
        for dirpath, dirs, files in os.walk(base):
            # Prune the build-time-only lib/lib/deployment subtree: clearing
            # dirs in-place stops os.walk (top-down by default) from descending
            # into it, and `continue` skips its own files.
            if os.path.abspath(dirpath) == os.path.abspath(deployment_subdir):
                dirs[:] = []
                continue
            for f in files:
                if f.endswith(".jar"):
                    jars.append(os.path.join(dirpath, f))
    return sorted(jars)


def parse_shadow_directives(build_file):
    """Parse exclude(dependency("g:a:v")) and relocate("from", "to") lines from
    the shadowJar task block of the given build.gradle.kts.

    Returns (excludes, relocates) where:
      excludes  = list of (group_re, artifact_re, version_re) compiled regexes
      relocates = list of source-prefix strings (the "from" of each relocate)
    A twilio jar is COVERED-by-exclude when its group:artifact:version matches
    an exclude triple. The relocates are parsed too, but only for an
    informational `[relocated]` annotation in the report -- relocation does
    NOT make an overlap pass (per project policy, excludes are required to
    keep the fat jar small; relocates are defensive only).

    Each coordinate part in an exclude directive is treated as a regex (the
    existing config uses literal names and `.*` wildcards, both valid regex),
    matched with re.fullmatch so a literal pattern does not prefix-match a
    longer coordinate (e.g. `gson` must not match `gson-extra`).
    """
    try:
        text = open(build_file).read()
    except FileNotFoundError as exc:
        sys.stderr.write(f"ERROR: could not read build file {build_file}: {exc}\n")
        return [], []

    excludes = []
    for m in re.finditer(r'exclude\(dependency\("([^"]+)"\)\)', text):
        parts = m.group(1).split(":")
        while len(parts) < 3:
            parts.append(".*")
        excludes.append(tuple(re.compile(p) for p in parts[:3]))
    relocates = []
    for m in re.finditer(r'relocate\("([^"]+)"\s*,\s*"([^"]+)"\)', text):
        relocates.append(m.group(1))
    return excludes, relocates


def jar_excluded(coord, excludes):
    """coord is (group, artifact, version); excludes is a list of regex triples.
    Return True if the coord matches any exclude triple."""
    for (g_re, a_re, v_re) in excludes:
        if (
            g_re.fullmatch(coord[0])
            and a_re.fullmatch(coord[1])
            and v_re.fullmatch(coord[2])
        ):
            return True
    return False


def class_relocated(fqn, relocates):
    """fqn is a dotted class name; return True if it starts with any relocate
    source prefix (the shadow plugin renames such classes so they no longer
    collide with Keycloak's copies)."""
    for prefix in relocates:
        if fqn == prefix or fqn.startswith(prefix + "."):
            return True
    return False


def load_manifest(twilio_dir):
    """Read MANIFEST.txt (written by collect-runtime-classpath.init.gradle.kts).
    Returns {jar_filename: (group, artifact, version)}. Missing manifest -> {}."""
    manifest = {}
    mf = os.path.join(twilio_dir, "MANIFEST.txt")
    if not os.path.isfile(mf):
        return manifest
    with open(mf) as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or "\t" not in line:
                continue
            coord, jarname = line.split("\t", 1)
            parts = coord.split(":")
            if len(parts) >= 3:
                manifest[jarname] = (parts[0], parts[1], parts[2])
    return manifest


def _report(twilio_dir, keycloak_root, build_file, twilio_jars, kc_jars_scanned,
             excludes, relocates, manifest, overlap,
             total_overlapping_classes, twilio_jars_with_overlap):
    print("=" * 78)
    print("Pre-shade twilio-dependency vs Keycloak-runtime class overlap report")
    print("=" * 78)
    print(f"Twilio runtime-classpath dir: {twilio_dir}")
    print(f"  jars scanned:                {len(twilio_jars)}")
    print(f"Keycloak root:                {keycloak_root}")
    print(f"  jars scanned:               {kc_jars_scanned}")
    print(f"Build file (exclude/relocate): {build_file}")
    print(f"  excludes parsed:            {len(excludes)}")
    print(f"  relocates parsed:           {len(relocates)}")
    print(f"Twilio jars with overlap:     {twilio_jars_with_overlap}")
    print(f"Overlapping classes (total):  {total_overlapping_classes}")
    print("-" * 78)

    if not overlap:
        print("OK: no .class entry in any twilio runtime-classpath jar is also")
        print("present in a Keycloak runtime jar. Twilio pulls in nothing that")
        print("collides with Keycloak's runtime classpath.")
        if os.environ.get("GITHUB_ACTIONS") == "true":
            print(
                f"::notice::No pre-shade class overlap between the twilio "
                f"runtime classpath ({len(twilio_jars)} jars) and Keycloak "
                f"runtime ({kc_jars_scanned} jars)."
            )
        return 0

    # Classify each overlapping twilio jar. Per project policy an overlap is
    # acceptable ONLY when the jar is excluded (the whole jar is removed from
    # the fat jar, keeping it small). Relocation is defensive only and does
    # NOT make an overlap pass, so any overlapping jar that is not excluded is
    # a failure. Colliding classes that happen to fall under a relocate()
    # prefix are still annotated [relocated] in the breakdown for context.
    uncovered = defaultdict(lambda: defaultdict(list))
    covered_exclude = []
    for tjar in overlap:
        coord = manifest.get(os.path.basename(tjar))
        if coord and jar_excluded(coord, excludes):
            covered_exclude.append(tjar)
            continue
        for cls in overlap[tjar]:
            for kj in overlap[tjar][cls]:
                uncovered[tjar][cls].append(kj)

    uncovered_classes = sum(len(c) for c in uncovered.values())
    uncovered_jars = len(uncovered)
    print(f"COVERED by exclude(...):      {len(covered_exclude)} twilio jar(s)")
    print(f"NOT EXCLUDED (fail):          {uncovered_jars} twilio jar(s), "
          f"{uncovered_classes} class(es)")
    print("-" * 78)
    _print_breakdown(overlap, covered_exclude, uncovered, manifest,
                     keycloak_root, relocates)
    return _verdict(uncovered, uncovered_classes, uncovered_jars,
                    total_overlapping_classes, twilio_jars_with_overlap,
                    keycloak_root, manifest)


def _print_breakdown(overlap, covered_exclude, uncovered, manifest,
                     keycloak_root, relocates):
    for tjar in sorted(overlap):
        base = os.path.basename(tjar)
        coord = manifest.get(base)
        coord_str = ":".join(coord) if coord else "(unknown coords)"
        classes = overlap[tjar]
        if tjar in covered_exclude:
            status = "COVERED-exclude"
        else:
            status = "NOT-EXCLUDED"
        print(f"  Twilio-side jar: {base}  [{coord_str}]  "
              f"({len(classes)} overlapping class(es))  -> {status}")
        by_kc = defaultdict(list)
        for cls, kjars in classes.items():
            for kj in kjars:
                by_kc[kj].append(cls)
        for kj in sorted(by_kc):
            rel = os.path.relpath(kj, keycloak_root)
            print(f"    Keycloak jar: {rel}  ({len(by_kc[kj])} class(es))")
            for c in sorted(by_kc[kj]):
                fqn = c[:-len(".class")].replace("/", ".")
                note = "  [relocated]" if class_relocated(fqn, relocates) else ""
                print(f"      - {fqn}{note}")
        print()


def _verdict(uncovered, uncovered_classes, uncovered_jars,
             total_overlapping_classes, twilio_jars_with_overlap,
             keycloak_root, manifest):
    if uncovered:
        print(f"FAIL: {uncovered_classes} class(es) across {uncovered_jars} "
              f"twilio-side jar(s) overlap with Keycloak runtime jars and are "
              f"NOT excluded by any exclude(dependency(...)) in the shadowJar "
              f"task. The shadow jar would ship these classes (relocation is "
              f"defensive only and does not satisfy this check). Add an "
              f"exclude(dependency(...)) for each overlapping jar to keep the "
              f"fat jar small.")
        if os.environ.get("GITHUB_ACTIONS") == "true":
            print(
                f"::error::Pre-shade not-excluded overlap: {uncovered_classes} "
                f"class(es) across {uncovered_jars} twilio jar(s) collide "
                f"with Keycloak runtime jars and are not excluded in the "
                f"shadowJar task; see the job log."
            )
        print("NOT-EXCLUDED overlaps (only):")
        for tjar in sorted(uncovered):
            base = os.path.basename(tjar)
            coord = manifest.get(base)
            coord_str = ":".join(coord) if coord else "(unknown coords)"
            print(f"  Twilio-side jar: {base}  [{coord_str}]  "
                  f"({len(uncovered[tjar])} not-excluded class(es))")
            by_kc = defaultdict(list)
            for cls, kjars in uncovered[tjar].items():
                for kj in kjars:
                    by_kc[kj].append(cls)
            for kj in sorted(by_kc):
                rel = os.path.relpath(kj, keycloak_root)
                print(f"    Keycloak jar: {rel}  ({len(by_kc[kj])} class(es))")
                for c in sorted(by_kc[kj]):
                    fqn = c[:-len(".class")].replace("/", ".")
                    print(f"      - {fqn}")
            print()
        return 1

    print("OK: every pre-shade overlap is excluded -- each overlapping twilio")
    print("jar matches an exclude(dependency(...)) in the shadowJar task, so")
    print("the whole jar is removed from the fat jar. (relocate(...) directives")
    print("are defensive and were not counted as coverage.)")
    if os.environ.get("GITHUB_ACTIONS") == "true":
        print(
            f"::notice::All pre-shade overlaps excluded: "
            f"{total_overlapping_classes} class(es) across "
            f"{twilio_jars_with_overlap} twilio jar(s) overlap Keycloak "
            f"runtime but every overlapping jar is excluded in the shadowJar "
            f"task."
        )
    return 0
def main():
    if len(sys.argv) != 4:
        sys.stderr.write(
            "usage: check_keycloak_classpath_overlap.py "
            "<twilio-runtime-classpath-dir> <keycloak-root> "
            "<build.gradle.kts>\n"
        )
        return 2

    twilio_dir = sys.argv[1]
    keycloak_root = sys.argv[2]
    build_file = sys.argv[3]

    if not os.path.isdir(twilio_dir):
        sys.stderr.write(f"ERROR: twilio dir not found: {twilio_dir}\n")
        return 2
    if not os.path.isdir(keycloak_root):
        sys.stderr.write(f"ERROR: keycloak root not found: {keycloak_root}\n")
        return 2
    if not os.path.isfile(build_file):
        sys.stderr.write(f"ERROR: build file not found: {build_file}\n")
        return 2

    twilio_jars = gather_twilio_jars(twilio_dir)
    if not twilio_jars:
        sys.stderr.write(f"ERROR: no .jar files under {twilio_dir}\n")
        return 2
    kc_jars = gather_keycloak_jars(keycloak_root)
    if not kc_jars:
        sys.stderr.write(
            f"ERROR: no .jar files under {keycloak_root}/lib or "
            f"{keycloak_root}/providers\n"
        )
        return 2

    excludes, relocates = parse_shadow_directives(build_file)
    manifest = load_manifest(twilio_dir)

    # Scan the jars. list_classes propagates read errors (BadZipFile /
    # FileNotFoundError / OSError) so an unreadable input is not silently
    # treated as an empty class set; catch those at this CLI boundary and
    # return the documented input-error code (2) rather than letting a Python
    # traceback exit with status 1 (which the header reserves for an
    # uncovered overlap).
    try:
        kc_index = defaultdict(list)
        kc_jars_scanned = 0
        for jar in kc_jars:
            kc_jars_scanned += 1
            for cls in list_classes(jar):
                kc_index[cls].append(jar)

        # overlap: twilio jar path -> { class FQN -> [keycloak jar paths] }
        overlap = defaultdict(lambda: defaultdict(list))
        for tjar in twilio_jars:
            for cls in list_classes(tjar):
                if cls in kc_index:
                    for kj in kc_index[cls]:
                        overlap[tjar][cls].append(kj)
    except (zipfile.BadZipFile, OSError) as exc:
        sys.stderr.write(f"ERROR: could not read a jar: {exc}\n")
        return 2

    total_overlapping_classes = sum(len(c) for c in overlap.values())
    twilio_jars_with_overlap = len(overlap)
    return _report(
        twilio_dir, keycloak_root, build_file, twilio_jars, kc_jars_scanned,
        excludes, relocates, manifest, overlap,
        total_overlapping_classes, twilio_jars_with_overlap,
    )


if __name__ == "__main__":
    sys.exit(main())
