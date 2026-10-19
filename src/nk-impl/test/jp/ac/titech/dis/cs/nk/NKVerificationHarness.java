package jp.ac.titech.dis.cs.nk;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Standalone verification harness for the NK model.
 *
 * <p>
 * It is deliberately independent of TestNG so it can be run from a plain
 * {@code java} invocation (see {@code verify-nk.sh} at the repository root).
 * Each section corresponds to one class of defect that was found in the model:
 * </p>
 *
 * <ol>
 *   <li>fitness table completeness was validated with {@code 2^(K+1)}, which in
 *       Java is a bitwise XOR, so truncated tables were accepted;</li>
 *   <li>the lowest fitness gene could carry a fitness value that did not match
 *       its loci;</li>
 *   <li>{@link NKGene} claimed to be immutable but shared its locus list with
 *       whoever mutated it;</li>
 *   <li>nothing in the model was seeded, so no run could be reproduced;</li>
 *   <li>neighbourhood generators and fitness queries accepted degenerate
 *       arguments;</li>
 *   <li>the test suite printed values instead of asserting them.</li>
 *   </ol>
 *
 * <p>
 * Sections 1-5 assert that each of those defects is fixed. Section 6 asserts the
 * NK semantics that the old tests only printed: fitness range, uniform fitness
 * contributions, and the K+1 arity of every component input set. The TestNG
 * classes under {@code src/nk-impl/test} were rewritten to assert the same
 * properties, so the suite fails rather than merely prints.
 * </p>
 *
 * <p>
 * Exit status is 0 when every check passes, 1 otherwise.
 * </p>
 *
 * @author Muqtafi Akhmad
 */
public class NKVerificationHarness {

	private static int passed = 0;
	private static final List<String> failures = new ArrayList<String>();
	private static String section = "";

	/** A check body that is allowed to throw. */
	private interface Check {
		void run() throws Exception;
	}

	public static void main(String[] args) throws Exception {
		System.out.println("NK model verification harness");
		System.out.println("=============================");

		sectionCompletenessValidation();
		sectionLowestFitnessGene();
		sectionGeneImmutability();
		sectionReproducibility();
		sectionArgumentValidation();
		sectionNkSemantics();

		System.out.println();
		System.out.println("-----------------------------");
		System.out.println("passed: " + passed + ", failed: " + failures.size());
		if(failures.isEmpty()) {
			System.out.println("RESULT: OK");
		} else {
			System.out.println("RESULT: FAILED");
			for(String failure : failures) {
				System.out.println("  - " + failure);
			}
		}
		System.exit(failures.isEmpty() ? 0 : 1);
	}

	// ------------------------------------------------------------------
	// 1. fitness table completeness validation
	// ------------------------------------------------------------------

	private static void sectionCompletenessValidation() {
		section("1. fitness table completeness validation");
		int n = 4;

		// an empty table can never be complete
		expectThrows("empty table is rejected (N=4, K=1)", IllegalStateException.class, new Check() {
			public void run() {
				new NKLandscapeImpl(4, 1, emptyTable(4), NKUtils.generateAdjacentNeighborhood(4, 1), null);
			}
		});

		// 6 of the required 16 entries must not pass for K=3
		expectThrows("partial table is rejected (6 of 16 entries, N=4, K=3)", IllegalStateException.class, new Check() {
			public void run() {
				new NKLandscapeImpl(4, 3, partialTable(4, 6), NKUtils.generateAdjacentNeighborhood(4, 3), null);
			}
		});

		// and a generated table really is complete, component by component;
		// built without a particle search so nothing has been materialised yet
		NKLandscapeImpl landscape = new NKLandscapeImpl(n, 2,
				(List<Map<String, Double>>) null,
				NKUtils.generateAdjacentNeighborhood(n, 2),
				(NKGene<Boolean>) null, 42L);
		check("table is incomplete before materialisation", !landscape.isComplete(),
				"isComplete() was true on a freshly built landscape");
		landscape.generateFitnessCache();
		check("table is complete after generateFitnessCache()", landscape.isComplete(),
				"isComplete() was false after enumerating every genotype");
		int required = 1 << (2 + 1);
		boolean exactSize = true;
		for(Map<String, Double> component : landscape.getComponentFitnessCache()) {
			exactSize &= (component.size() == required);
		}
		check("every component holds 2^(K+1) = " + required + " entries", exactSize,
				"component table sizes were " + sizes(landscape));

		// a truncated file on disk must not load
		expectThrows("truncated landscape file is rejected on load", IllegalStateException.class, new Check() {
			public void run() throws Exception {
				Path dir = Files.createTempDirectory("nk-harness");
				File file = new File(dir.toFile(), "truncated.json");
				NKLandscapeFactoryImpl factory = new NKLandscapeFactoryImpl();
				NKLandscape<Boolean> source = factory.generateRandomLandscape(4, 3,
						NKUtils.generateAdjacentNeighborhood(4, 3), 7L);
				factory.dumpLandscape(source, file.getAbsolutePath());

				ObjectMapper mapper = new ObjectMapper();
				ObjectNode root = (ObjectNode) mapper.readTree(file);
				ObjectNode firstComponent = (ObjectNode) root.get("componentFitnessCache").get(0);
				firstComponent.remove(firstComponent.fieldNames().next());
				mapper.writeValue(file, root);

				factory.loadLandscape(file.getAbsolutePath());
			}
		});
	}

	// ------------------------------------------------------------------
	// 2. lowest fitness gene integrity
	// ------------------------------------------------------------------

	private static void sectionLowestFitnessGene() {
		section("2. lowest fitness gene integrity");

		// the stored fitness must always be the fitness of the stored loci
		int inconsistent = 0;
		for(long seed = 0; seed < 12; seed++) {
			NKLandscapeImpl landscape = new NKLandscapeImpl(6, 2,
					NKUtils.generateAdjacentNeighborhood(6, 2), 40, 40, seed);
			landscape.generateFitnessCache();
			NKGene<Boolean> lowest = landscape.getLowestFitnessGene();
			Double recomputed = landscape.getFitness(lowest.getInternalComponents());
			if(!recomputed.equals(lowest.getFitness())) {
				inconsistent++;
				System.out.println("      seed " + seed + ": stored=" + lowest.getFitness()
						+ " recomputed=" + recomputed + " loci=" + lowest.getInternalComponents());
			}
		}
		check("stored fitness matches the stored loci (12 seeded landscapes)", inconsistent == 0,
				inconsistent + " of 12 landscapes reported a fitness that did not match their loci");

		// for N <= EXACT_OPTIMUM_MAX_N the value must be the true global minimum
		int notOptimal = 0;
		int notFlaggedExact = 0;
		for(long seed = 0; seed < 5; seed++) {
			NKLandscapeImpl landscape = new NKLandscapeImpl(10, 3,
					NKUtils.generateAdjacentNeighborhood(10, 3), 40, 40, seed);
			landscape.generateFitnessCache();
			NKGene<Boolean> lowest = landscape.getLowestFitnessGene();
			if(!Boolean.TRUE.equals(landscape.isLowestFitnessExact())) {
				notFlaggedExact++;
			}
			double bruteForceMinimum = Double.MAX_VALUE;
			for(int mask = 0; mask < (1 << 10); mask++) {
				bruteForceMinimum = Math.min(bruteForceMinimum, landscape.getFitness(lociFor(10, mask)));
			}
			if(Double.compare(lowest.getFitness(), bruteForceMinimum) != 0) {
				notOptimal++;
				System.out.println("      seed " + seed + ": reported=" + lowest.getFitness()
						+ " but the true minimum is " + bruteForceMinimum);
			}
		}
		check("reported gene is the true global minimum (N=10, 5 seeds)", notOptimal == 0,
				notOptimal + " of 5 landscapes reported a non-optimal gene");
		check("exactness is flagged for N <= " + NKLandscapeImpl.EXACT_OPTIMUM_MAX_N, notFlaggedExact == 0,
				notFlaggedExact + " of 5 landscapes did not flag the gene as exact");

		// a legacy file carrying a corrupt gene is repaired on load
		checkNoThrow("legacy file with a mismatched optimum is repaired on load", new Check() {
			public void run() throws Exception {
				Path dir = Files.createTempDirectory("nk-harness");
				File file = new File(dir.toFile(), "legacy.json");
				NKLandscapeFactoryImpl factory = new NKLandscapeFactoryImpl();
				NKLandscape<Boolean> source = factory.generateRandomLandscape(8, 3,
						NKUtils.generateAdjacentNeighborhood(8, 3), 11L);
				factory.dumpLandscape(source, file.getAbsolutePath());

				// corrupt the stored fitness the way the old local search could
				ObjectMapper mapper = new ObjectMapper();
				ObjectNode root = (ObjectNode) mapper.readTree(file);
				((ObjectNode) root.get("lowestFitnessGene")).put("fitness", 0.12345);
				mapper.writeValue(file, root);

				NKLandscape<Boolean> loaded = factory.loadLandscape(file.getAbsolutePath());
				NKGene<Boolean> gene = loaded.getLowestFitnessGene();
				Double recomputed = loaded.getFitness(gene.getInternalComponents());
				if(!recomputed.equals(gene.getFitness())) {
					throw new IllegalStateException("loaded gene still inconsistent: stored="
							+ gene.getFitness() + " recomputed=" + recomputed);
				}
			}
		});
	}

	// ------------------------------------------------------------------
	// 3. gene immutability
	// ------------------------------------------------------------------

	private static void sectionGeneImmutability() {
		section("3. gene immutability");

		List<Boolean> source = new ArrayList<Boolean>(Arrays.asList(true, true, true, true));
		NKGene<Boolean> gene = new NKGene<Boolean>(source, 4, 1, 0.5);
		source.set(0, false);
		check("constructor copies the caller's loci",
				gene.getInternalComponents().get(0).booleanValue() == true,
				"the gene followed an edit made to the list it was constructed from");

		boolean unmodifiable = false;
		try {
			gene.getInternalComponents().set(0, false);
		} catch (UnsupportedOperationException e) {
			unmodifiable = true;
		}
		check("getInternalComponents() is unmodifiable", unmodifiable,
				"the returned list allowed in-place edits");

		NKLandscapeImpl landscape = new NKLandscapeImpl(4, 1,
				NKUtils.generateAdjacentNeighborhood(4, 1), 10, 10, 5L);
		landscape.generateFitnessCache();
		NKGeneFactoryImpl factory = new NKGeneFactoryImpl(landscape);
		NKGene<Boolean> original = factory.generateByLociString(
				new ArrayList<Boolean>(Arrays.asList(true, true, true, true)), 4, 1);
		List<Boolean> originalLociBefore = new ArrayList<Boolean>(original.getInternalComponents());
		Double originalFitnessBefore = original.getFitness();

		NKGene<Boolean> mutated = factory.mutateComponent(original, 2, false);
		check("mutateComponent leaves the original loci untouched",
				originalLociBefore.equals(original.getInternalComponents()),
				"original loci changed from " + originalLociBefore + " to "
						+ original.getInternalComponents());
		check("the original gene's fitness still matches its loci",
				originalFitnessBefore.equals(original.getFitness())
						&& originalFitnessBefore.equals(landscape.getFitness(original.getInternalComponents())),
				"original fitness is now stale");
		check("original and mutant do not share a locus list",
				original.getInternalComponents() != mutated.getInternalComponents(),
				"both genes wrap the same List instance");
		check("the mutant's loci differ exactly where expected",
				!mutated.getInternalComponents().get(2).booleanValue()
						&& mutated.getInternalComponents().get(0).booleanValue(),
				"mutant loci were " + mutated.getInternalComponents());
		check("mutateComponent with an unchanged value returns the same gene",
				factory.mutateComponent(original, 0, true) == original,
				"a no-op mutation allocated a new gene");
	}

	// ------------------------------------------------------------------
	// 4. reproducibility
	// ------------------------------------------------------------------

	private static void sectionReproducibility() {
		section("4. reproducibility");

		NKLandscapeFactoryImpl factory = new NKLandscapeFactoryImpl();
		List<List<Byte>> neighbors = NKUtils.generateAdjacentNeighborhood(8, 3);

		NKLandscape<Boolean> first = factory.generateRandomLandscape(8, 3, neighbors, 12345L);
		NKLandscape<Boolean> second = factory.generateRandomLandscape(8, 3, neighbors, 12345L);
		NKLandscape<Boolean> other = factory.generateRandomLandscape(8, 3, neighbors, 999L);

		check("same seed produces the same fitness table",
				tablesEqual(first, second), "two landscapes built from seed 12345 differ");
		check("same seed produces the same lowest gene",
				first.getLowestFitnessGene().getInternalComponents().equals(
						second.getLowestFitnessGene().getInternalComponents())
						&& first.getLowestFitnessGene().getFitness().equals(
						second.getLowestFitnessGene().getFitness()),
				"lowest genes differ between two landscapes built from seed 12345");
		check("different seeds produce different fitness tables",
				!tablesEqual(first, other), "seed 12345 and seed 999 produced identical tables");

		check("the seed is retained on the landscape",
				Long.valueOf(12345L).equals(first.getSeed()),
				"getSeed() returned " + first.getSeed());

		List<Boolean> a = NKUtils.generateRandomGene(16, new Random(77L));
		List<Boolean> b = NKUtils.generateRandomGene(16, new Random(77L));
		List<Boolean> c = NKUtils.generateRandomGene(16, new Random(78L));
		check("seeded gene generation repeats", a.equals(b),
				"two draws from seed 77 differ: " + a + " vs " + b);
		check("seeded gene generation responds to the seed", !a.equals(c),
				"seed 77 and seed 78 produced the same gene");

		List<List<Byte>> ringA = NKUtils.generateRandomNeighborhood(8, 3, new Random(5L));
		List<List<Byte>> ringB = NKUtils.generateRandomNeighborhood(8, 3, new Random(5L));
		check("seeded random neighbourhood repeats", ringA.equals(ringB),
				"two dependency matrices from seed 5 differ");

		// seed survives a dump/load round trip, and the round trip is stable
		checkNoThrow("seed and optimum survive dump/load and the round trip is stable", new Check() {
			public void run() throws Exception {
				Path dir = Files.createTempDirectory("nk-harness");
				File once = new File(dir.toFile(), "once.json");
				File twice = new File(dir.toFile(), "twice.json");
				NKLandscapeFactoryImpl localFactory = new NKLandscapeFactoryImpl();
				NKLandscape<Boolean> generated = localFactory.generateRandomLandscape(6, 2,
						NKUtils.generateAdjacentNeighborhood(6, 2), 4242L);
				localFactory.dumpLandscape(generated, once.getAbsolutePath());
				NKLandscape<Boolean> loaded = localFactory.loadLandscape(once.getAbsolutePath());
				if(!Long.valueOf(4242L).equals(loaded.getSeed())) {
					throw new IllegalStateException("seed lost on load: " + loaded.getSeed());
				}
				localFactory.dumpLandscape(loaded, twice.getAbsolutePath());
				byte[] first = Files.readAllBytes(once.toPath());
				byte[] second = Files.readAllBytes(twice.toPath());
				if(!Arrays.equals(first, second)) {
					throw new IllegalStateException("dump(load(x)) differs from x");
				}
			}
		});
	}

	// ------------------------------------------------------------------
	// 5. argument validation and topology guards
	// ------------------------------------------------------------------

	private static void sectionArgumentValidation() {
		section("5. argument validation and topology guards");

		expectThrows("adjacent neighbourhood rejects K >= N", IllegalArgumentException.class, new Check() {
			public void run() { NKUtils.generateAdjacentNeighborhood(8, 8); }
		});
		expectThrows("adjacent neighbourhood rejects K < 0", IllegalArgumentException.class, new Check() {
			public void run() { NKUtils.generateAdjacentNeighborhood(8, -1); }
		});
		expectThrows("adjacent neighbourhood rejects N < 1", IllegalArgumentException.class, new Check() {
			public void run() { NKUtils.generateAdjacentNeighborhood(0, 0); }
		});
		expectThrows("modular neighbourhood rejects N not divisible by K+1", IllegalArgumentException.class, new Check() {
			public void run() { NKUtils.generateModularNeighborhood(8, 2); }
		});
		expectThrows("modular reset rejects N not divisible by K+1", IllegalArgumentException.class, new Check() {
			public void run() { NKUtils.resetInitialLociModular(new ArrayList<Boolean>(Arrays.asList(true, true, true, true, true, true, true, true)), 8, 2, 1); }
		});
		expectThrows("modular reset rejects numReset beyond a module", IllegalArgumentException.class, new Check() {
			public void run() { NKUtils.resetInitialLociModular(new ArrayList<Boolean>(Arrays.asList(true, true, true, true, true, true, true, true)), 8, 3, 5); }
		});
		expectThrows("adjacent reset rejects numReset beyond the gene", IllegalArgumentException.class, new Check() {
			public void run() { NKUtils.resetInitialLociAdjacent(NKUtils.generateRandomGene(4), 5); }
		});
		expectThrows("fitness query rejects the wrong number of loci", IllegalArgumentException.class, new Check() {
			public void run() {
				NKLandscapeImpl landscape = new NKLandscapeImpl(4, 1,
						NKUtils.generateAdjacentNeighborhood(4, 1), 5, 5, 1L);
				landscape.getFitness(NKUtils.generateRandomGene(3));
			}
		});
		expectThrows("landscape rejects K > N-1", IllegalArgumentException.class, new Check() {
			public void run() { new NKLandscapeImpl(4, 4, NKUtils.generateAdjacentNeighborhood(4, 3), (NKGene<Boolean>) null); }
		});
		expectThrows("fitness table refuses to be enumerated past MAX_EXHAUSTIVE_N", IllegalStateException.class, new Check() {
			public void run() {
				NKLandscapeImpl landscape = new NKLandscapeImpl(NKLandscapeImpl.MAX_EXHAUSTIVE_N + 1, 1,
						NKUtils.generateAdjacentNeighborhood(NKLandscapeImpl.MAX_EXHAUSTIVE_N + 1, 1),
						5, 5, 1L);
				landscape.generateFitnessCache();
			}
		});
		expectThrows("a self-dependency is rejected", IllegalStateException.class, new Check() {
			public void run() {
				List<List<Byte>> broken = NKUtils.generateAdjacentNeighborhood(4, 1);
				broken.get(0).set(0, NKUtils.EXIST_DEPENDENCY);
				broken.get(0).set(1, NKUtils.NO_DEPENDENCY);
				new NKLandscapeImpl(4, 1, broken, (NKGene<Boolean>) null);
			}
		});

		// every topology produces a well formed matrix for the parameters it supports
		// (a modular matrix additionally needs N divisible by K+1)
		int[][] topologyParameters = {
				{ 9, 4 },   // ADJACENT : any K <= N-1
				{ 9, 2 },   // MODULAR  : N must be a multiple of K+1 = 3
				{ 9, 4 },   // RANDOM   : any K <= N-1
		};
		NKLandscapePath.NeighborhoodType[] topologies = NKLandscapePath.NeighborhoodType.values();
		for(int topology = 0; topology < topologies.length; topology++) {
			int topologyN = topologyParameters[topology][0];
			int topologyK = topologyParameters[topology][1];
			List<List<Byte>> matrix = NKUtils.generateNeighborhood(
					topologies[topology], topologyN, topologyK, new Random(3L));
			check(topologies[topology] + " neighbourhood has " + topologyN + " rows with exactly K="
						+ topologyK + " dependencies and no self-loop",
					isWellFormed(matrix, topologyN, topologyK), "matrix was\n" + render(matrix));
		}

		// and the string form used to name landscape files round trips
		check("neighbourhood type names round trip",
				NKLandscapePath.NeighborhoodType.getFromString("adj") == NKLandscapePath.NeighborhoodType.ADJACENT
						&& NKLandscapePath.NeighborhoodType.getFromString("MOD") == NKLandscapePath.NeighborhoodType.MODULAR
						&& NKLandscapePath.NeighborhoodType.getFromString("rand") == NKLandscapePath.NeighborhoodType.RANDOM,
				"a neighbourhood name did not map back to its enum constant");
	}

	// ------------------------------------------------------------------
	// 6. NK semantics (what the old tests only printed)
	// ------------------------------------------------------------------

	private static void sectionNkSemantics() {
		section("6. NK semantics");

		int n = 8;
		List<List<Byte>> neighbors = NKUtils.generateAdjacentNeighborhood(n, 0);
		NKLandscapeImpl landscape = new NKLandscapeImpl(n, 0, neighbors, 10, 10, 2024L);
		landscape.generateFitnessCache();

		// fitness is a mean of N contributions in [0,1)
		boolean inRange = true;
		double sum = 0.0;
		int count = 0;
		for(int mask = 0; mask < (1 << n); mask++) {
			Double fitness = landscape.getFitness(lociFor(n, mask));
			inRange &= (fitness >= 0.0 && fitness < 1.0);
			sum += fitness;
			count++;
		}
		check("fitness of every genotype lies in [0, 1)", inRange,
				"some fitness fell outside [0, 1)");

		// contributions are uniform on [0,1), so mean fitness concentrates on 0.5
		check("mean fitness is near 0.5 (" + String.format("%.4f", sum / count) + ")",
				Math.abs(sum / count - 0.5) < 0.1,
				"mean fitness was " + (sum / count));

		double contributionSum = 0.0;
		int contributionCount = 0;
		boolean contributionsInRange = true;
		for(Map<String, Double> component : landscape.getComponentFitnessCache()) {
			for(Double value : component.values()) {
				contributionsInRange &= (value >= 0.0 && value < 1.0);
				contributionSum += value;
				contributionCount++;
			}
		}
		check("component contributions lie in [0, 1)", contributionsInRange,
				"a contribution fell outside [0, 1)");
		check("component contributions are uniform (mean "
						+ String.format("%.4f", contributionSum / contributionCount) + ")",
				Math.abs(contributionSum / contributionCount - 0.5) < 0.1,
				"mean contribution was " + (contributionSum / contributionCount));

		// for K=0 every locus owns exactly one component, so flipping one locus
		// changes exactly one component's contribution
		List<List<Boolean>> before = NKUtils.splitToGeneComponents(n, 0, lociFor(n, 0), neighbors);
		List<Boolean> flip0 = lociFor(n, 0);
		flip0.set(0, !flip0.get(0));
		List<List<Boolean>> after = NKUtils.splitToGeneComponents(n, 0, flip0, neighbors);
		int changed = 0;
		for(int component = 0; component < n; component++) {
			if(!before.get(component).equals(after.get(component))) {
				changed++;
			}
		}
		check("with K=0 flipping one locus feeds exactly one component", changed == 1,
				"flipping locus 0 changed " + changed + " component input sets");

		// K+1 arity and ordering of the split
		List<Boolean> gene = lociFor(n, 5);
		List<List<Boolean>> split = NKUtils.splitToGeneComponents(n, 0, gene, neighbors);
		boolean arityAndOrder = (split.size() == n);
		for(int component = 0; component < n; component++) {
			List<Boolean> inputs = split.get(component);
			arityAndOrder &= (inputs.size() == 1);
			arityAndOrder &= (inputs.get(0) == gene.get(component));
		}
		check("each component input set starts with its own locus and holds K+1 values",
				arityAndOrder, "split was " + split);

		// a gene's fitness is the mean of its component contributions
		double mean = 0.0;
		List<List<Boolean>> components = NKUtils.splitToGeneComponents(n, 0, gene, neighbors);
		for(int component = 0; component < n; component++) {
			mean += landscape.getComponentFitnessCache().get(component)
					.get(NKUtils.getComponentListSignature(components.get(component)));
		}
		mean /= n;
		check("fitness equals the mean of the component contributions",
				Double.compare(mean, landscape.getFitness(gene)) == 0,
				"mean of contributions was " + mean + " but getFitness returned "
						+ landscape.getFitness(gene));
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private static List<Boolean> lociFor(int n, int mask) {
		List<Boolean> loci = new ArrayList<Boolean>(n);
		for(int bit = 0; bit < n; bit++) {
			loci.add(((mask >> bit) & 1) == 1);
		}
		return loci;
	}

	private static boolean isWellFormed(List<List<Byte>> matrix, int n, int k) {
		if(matrix.size() != n) {
			return false;
		}
		for(int row = 0; row < n; row++) {
			List<Byte> line = matrix.get(row);
			if(line == null || line.size() != n) {
				return false;
			}
			int dependencies = 0;
			for(int column = 0; column < n; column++) {
				Byte value = line.get(column);
				if(!NKUtils.isDependencyMarker(value)) {
					return false;
				}
				if(NKUtils.isExistingDependency(value)) {
					if(column == row) {
						return false;
					}
					dependencies++;
				}
			}
			if(dependencies != k) {
				return false;
			}
		}
		return true;
	}

	private static String render(List<List<Byte>> matrix) {
		StringBuilder builder = new StringBuilder();
		for(List<Byte> row : matrix) {
			builder.append("  ").append(row).append("\n");
		}
		return builder.toString();
	}

	private static List<Map<String, Double>> emptyTable(int n) {
		List<Map<String, Double>> table = new ArrayList<Map<String, Double>>();
		for(int i = 0; i < n; i++) {
			table.add(new java.util.HashMap<String, Double>());
		}
		return table;
	}

	private static List<Map<String, Double>> partialTable(int n, int entries) {
		List<Map<String, Double>> table = emptyTable(n);
		for(Map<String, Double> component : table) {
			for(int i = 0; i < entries; i++) {
				component.put(Integer.toBinaryString(i), 0.5);
			}
		}
		return table;
	}

	private static String sizes(NKLandscapeImpl landscape) {
		StringBuilder builder = new StringBuilder("[");
		for(Map<String, Double> component : landscape.getComponentFitnessCache()) {
			builder.append(component.size()).append(" ");
		}
		return builder.append("]").toString();
	}

	private static boolean tablesEqual(NKLandscape<Boolean> a, NKLandscape<Boolean> b) {
		NKLandscapeImpl left = (NKLandscapeImpl) a;
		NKLandscapeImpl right = (NKLandscapeImpl) b;
		if(left.getComponentFitnessCache().size() != right.getComponentFitnessCache().size()) {
			return false;
		}
		for(int i = 0; i < left.getComponentFitnessCache().size(); i++) {
			if(!left.getComponentFitnessCache().get(i).equals(right.getComponentFitnessCache().get(i))) {
				return false;
			}
		}
		return true;
	}

	/** Start a named section; section names appear in the failure summary. */
	private static void section(String title) {
		section = title;
		System.out.println();
		System.out.println("== " + title);
	}

	private static void check(String name, boolean condition, String detail) {
		if(condition) {
			passed++;
			System.out.println("  [PASS] " + name);
		} else {
			String failure = section + " / " + name + " :: " + detail;
			failures.add(failure);
			System.out.println("  [FAIL] " + name);
			System.out.println("         " + detail);
		}
	}

	private static void expectThrows(String name, Class<? extends Throwable> expected, Check body) {
		try {
			body.run();
			check(name, false, "expected " + expected.getSimpleName() + " but nothing was thrown");
		} catch (Throwable thrown) {
			check(name, expected.isInstance(thrown),
					"expected " + expected.getSimpleName() + " but got "
							+ thrown.getClass().getName() + ": " + thrown.getMessage());
		}
	}

	/** Assert that a body completes without throwing anything. */
	private static void checkNoThrow(String name, Check body) {
		try {
			body.run();
			check(name, true, "");
		} catch (Throwable thrown) {
			check(name, false, thrown.getClass().getName() + ": " + thrown.getMessage());
		}
	}
}
