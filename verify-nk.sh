#!/usr/bin/env bash
#
# Compile and run the NK model verification.
#
#   ./verify-nk.sh                # build + run the verification harness
#   TESTNG_CP=... ./verify-nk.sh  # additionally compile and run the TestNG suite
#
# The harness (src/nk-impl/test/.../NKVerificationHarness.java) needs nothing
# beyond the jars in lib/, so it runs anywhere with a JDK. The TestNG suite
# needs a TestNG classpath: either point TESTNG_CP at one, or let the script
# resolve it with Maven (network access on the first run only).
#
# Build output goes to bin/, which is already git-ignored.

set -euo pipefail
cd "$(dirname "$0")"

LIBS="lib/colt.jar:lib/jackson-core-2.9.6.jar:lib/jackson-databind-2.9.6.jar:lib/jackson-annotations-2.9.6.jar"
OUT="bin/verify-nk"
HARNESS="src/nk-impl/test/jp/ac/titech/dis/cs/nk/NKVerificationHarness.java"
TEST_CLASSES="jp.ac.titech.dis.cs.nk.TestNKUtils,\
jp.ac.titech.dis.cs.nk.TestNKLandscape,\
jp.ac.titech.dis.cs.nk.TestNKGeneFactory,\
jp.ac.titech.dis.cs.nk.TestNKLandscapeFactory,\
jp.ac.titech.dis.cs.nk.TestDistribution"

mkdir -p "$OUT"

echo "== compiling nk-api, nk-impl and the verification harness"
find src/nk-api/main src/nk-impl/main -name '*.java' > "$OUT/main-sources.txt"
javac -Xlint:all -nowarn -cp "$LIBS" -d "$OUT" @"$OUT/main-sources.txt" "$HARNESS"

echo
echo "== running the verification harness"
java -cp "$OUT:$LIBS" jp.ac.titech.dis.cs.nk.NKVerificationHarness

# ------------------------------------------------------------------
# Optional: the TestNG suite
# ------------------------------------------------------------------

resolve_testng() {
	if [ -n "${TESTNG_CP:-}" ]; then
		printf '%s' "$TESTNG_CP"
		return 0
	fi
	command -v mvn >/dev/null 2>&1 || return 1
	local work
	work="$(pwd)/$OUT/mvn"
	mkdir -p "$work"
	cat > "$work/pom.xml" <<'POM'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>tmp</groupId><artifactId>gt-dynamics-test-classpath</artifactId><version>1</version>
  <dependencies>
    <dependency><groupId>org.testng</groupId><artifactId>testng</artifactId><version>7.10.2</version></dependency>
  </dependencies>
</project>
POM
	(cd "$work" && mvn -q -B dependency:build-classpath -Dmdep.outputFile="$work/testng.cp") >/dev/null 2>&1 || return 1
	[ -s "$work/testng.cp" ] || return 1
	cat "$work/testng.cp"
}

echo
if TESTNG_CP="$(resolve_testng)"; then
	echo "== compiling the TestNG suite"
	find src/nk-api/main src/nk-impl/main src/nk-impl/test -name '*.java' > "$OUT/sources.txt"
	javac -nowarn -cp "$OUT:$LIBS:$TESTNG_CP" -d "$OUT" @"$OUT/sources.txt"
	echo "== running the TestNG suite"
	java -cp "$OUT:$LIBS:$TESTNG_CP" org.testng.TestNG \
		-d "$OUT/testng-out" -testclass "$TEST_CLASSES"
else
	echo "== TestNG not available (set TESTNG_CP or install Maven); suite skipped"
	echo "   harness results above are the verification result"
fi
