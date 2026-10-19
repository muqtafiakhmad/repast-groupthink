#!/usr/bin/env bash
#
# Compile and test every module in the repository: nk-api, nk-impl,
# agent-impl and simulation-impl.
#
#   ./verify-all.sh                # harness + full TestNG suite
#   REPAST_HOME=... ./verify-all.sh
#   REPAST_CP=...  TESTNG_CP=... ./verify-all.sh   # explicit classpaths
#
# Requirements beyond ./verify-nk.sh:
#   * a Repast Simphony installation (agent/simulation modules import
#     repast.simphony.*). Located via REPAST_HOME, or auto-detected under
#     /Applications and $HOME; set REPAST_CP to supply the classpath directly.
#   * TestNG. Resolved with Maven when TESTNG_CP is unset (network on first run).
#
# Build output goes to bin/, which is already git-ignored.

set -euo pipefail
cd "$(dirname "$0")"

LIBS="lib/colt.jar:lib/jackson-core-2.9.6.jar:lib/jackson-databind-2.9.6.jar:lib/jackson-annotations-2.9.6.jar:lib/concurrent.jar"
OUT="bin/verify-all"
HARNESS="src/nk-impl/test/jp/ac/titech/dis/cs/nk/NKVerificationHarness.java"
TEST_CLASSES="jp.ac.titech.dis.cs.nk.TestNKUtils,\
jp.ac.titech.dis.cs.nk.TestNKLandscape,\
jp.ac.titech.dis.cs.nk.TestNKGeneFactory,\
jp.ac.titech.dis.cs.nk.TestNKLandscapeFactory,\
jp.ac.titech.dis.cs.nk.TestDistribution,\
jp.ac.titech.dis.cs.agent.TestAgentNetwork,\
jp.ac.titech.dis.cs.agent.TestSimpleConsensus,\
jp.ac.titech.dis.cs.agent.TestConsensusWithDevilAdvocate,\
jp.ac.titech.dis.cs.simulation.TestInitalOpinionSetFactory,\
jp.ac.titech.dis.cs.simulation.TestAgentNetworkLoader"

# ------------------------------------------------------------------ dependencies

resolve_repast() {
	if [ -n "${REPAST_CP:-}" ]; then
		printf '%s' "$REPAST_CP"
		return 0
	fi
	local home="${REPAST_HOME:-}" candidate
	if [ -z "$home" ]; then
		for candidate in /Applications/Repast\ Simphony* "${HOME:-}"/Repast\ Simphony*; do
			if [ -d "$candidate" ]; then
				home="$candidate"
				break
			fi
		done
	fi
	[ -n "$home" ] || return 1
	local core_jar
	core_jar="$(find "$home" -name 'repast.simphony.bin_and_src*.jar' 2>/dev/null | grep -v -i source | head -1 || true)"
	[ -n "$core_jar" ] || return 1
	printf '%s' "$core_jar"
	local libs
	libs="$(find "$home" -path '*repast.simphony.core*/lib/*.jar' 2>/dev/null | tr '\n' ':' || true)"
	[ -n "$libs" ] && printf ':%s' "${libs%:}"
	return 0
}

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

REPAST_CP="$(resolve_repast)" || {
	echo "!! Repast Simphony not found."
	echo "   Set REPAST_HOME=<install dir> or REPAST_CP=<classpath of repast jars>."
	echo "   ./verify-nk.sh covers the NK model without Repast."
	exit 2
}

TESTNG_CP="$(resolve_testng)" || {
	echo "!! TestNG not available. Set TESTNG_CP=<classpath> or install Maven."
	exit 2
}

FULL_CP="$LIBS:$REPAST_CP:$TESTNG_CP"

mkdir -p "$OUT"

echo "== compiling every module"
find src -name '*.java' > "$OUT/sources.txt"
javac -Xlint:all -nowarn -cp "$FULL_CP" -d "$OUT" @"$OUT/sources.txt"

echo
echo "== running the NK verification harness"
java -cp "$OUT:$FULL_CP" jp.ac.titech.dis.cs.nk.NKVerificationHarness

echo
echo "== running the TestNG suite (nk, agent and simulation modules)"
java -cp "$OUT:$FULL_CP" org.testng.TestNG \
	-d "$OUT/testng-out" -testclass "$TEST_CLASSES"
