package jp.ac.titech.dis.cs.nk;

import static jp.ac.titech.dis.cs.nk.NKTestSupport.assertTableComplete;
import static jp.ac.titech.dis.cs.nk.NKTestSupport.assertThrows;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for {@link NKLandscapeImpl}.
 *
 * <p>
 * The original suite only checked that {@code getFitness} returned something
 * non-null. These tests pin down the actual contract: fitness range, table
 * completeness, lowest-gene integrity, argument validation and reproducibility.
 * </p>
 */
public class TestNKLandscape {

	private static final int N = 12;
	private static final int K = 3;
	private static final long SEED = 20190218L;

	private NKLandscapeImpl nkLandscape;
	private List<List<Byte>> neighbors;

	@BeforeClass
	public void setUp() {
		this.neighbors = NKUtils.generateAdjacentNeighborhood(N, K);
		this.nkLandscape = new NKLandscapeImpl(N, K, this.neighbors,
				NKLandscapeImpl.DEFAULT_N_PARTICLE, NKLandscapeImpl.DEFAULT_ITERATIONS, SEED);
	}

	// ------------------------------------------------------------ fitness

	@Test
	public void testFitnessStaysWithinUnitRange() {
		this.nkLandscape.generateFitnessCache();
		for(int mask = 0; mask < (1 << N); mask += 37) {
			Double fitness = this.nkLandscape.getFitness(lociFor(mask));
			assertTrue(fitness >= 0.0 && fitness < 1.0,
					"fitness " + fitness + " is outside [0, 1)");
		}
	}

	@Test
	public void testFitnessIsStableForTheSameLoci() {
		List<Boolean> gene = lociFor(0b101100101101);
		Double first = this.nkLandscape.getFitness(gene);
		Double second = this.nkLandscape.getFitness(new ArrayList<Boolean>(gene));
		assertEquals(second, first, "the same loci must always give the same fitness");
	}

	@Test
	public void testFitnessRejectsTheWrongNumberOfLoci() {
		final NKLandscapeImpl landscape = this.nkLandscape;
		final List<Boolean> shortGene = NKUtils.generateRandomGene(N - 1);
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { landscape.getFitness(shortGene); }
		});
		final List<Boolean> nullGene = null;
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { landscape.getFitness(nullGene); }
		});
	}

	// ------------------------------------------------- fitness table integrity

	@Test
	public void testGenerateFitnessCacheCompletesTheTable() {
		// built without a search, so nothing has been materialised yet
		NKLandscapeImpl lazilyBuilt = new NKLandscapeImpl(N, K,
				(List<java.util.Map<String, Double>>) null, this.neighbors,
				(NKGene<Boolean>) null, SEED);
		assertTrue(!lazilyBuilt.isComplete(),
				"a landscape built lazily must start with an incomplete table");
		lazilyBuilt.generateFitnessCache();
		assertTableComplete("generated table", lazilyBuilt);
	}

	@Test
	public void testIncompleteCacheIsRejected() {
		final List<List<Byte>> nb = NKUtils.generateAdjacentNeighborhood(4, 1);
		assertThrows(IllegalStateException.class, new Runnable() {
			public void run() {
				new NKLandscapeImpl(4, 1, emptyTable(4), nb, (NKGene<Boolean>) null);
			}
		});
		//6 of the 16 entries needed for K=3 must also be rejected
		assertThrows(IllegalStateException.class, new Runnable() {
			public void run() {
				new NKLandscapeImpl(4, 3, partialTable(4, 6),
						NKUtils.generateAdjacentNeighborhood(4, 3), (NKGene<Boolean>) null);
			}
		});
	}

	@Test
	public void testEnumerationLimitIsEnforced() {
		final int beyondLimit = NKLandscapeImpl.MAX_EXHAUSTIVE_N + 1;
		assertThrows(IllegalStateException.class, new Runnable() {
			public void run() {
				NKLandscapeImpl landscape = new NKLandscapeImpl(beyondLimit, 1,
						(List<java.util.Map<String, Double>>) null,
						NKUtils.generateAdjacentNeighborhood(beyondLimit, 1),
						(NKGene<Boolean>) null);
				landscape.generateFitnessCache();
			}
		});
	}

	// ------------------------------------------------------- lowest fitness gene

	@Test
	public void testLowestFitnessGeneMatchesItsOwnLoci() {
		this.nkLandscape.generateFitnessCache();
		NKGene<Boolean> lowest = this.nkLandscape.getLowestFitnessGene();
		assertEquals(this.nkLandscape.getFitness(lowest.getInternalComponents()), lowest.getFitness(),
				"the stored fitness must be the fitness of the stored loci");
	}

	@Test
	public void testLowestFitnessGeneIsTheGlobalMinimum() {
		this.nkLandscape.generateFitnessCache();
		NKGene<Boolean> lowest = this.nkLandscape.getLowestFitnessGene();
		double bruteForce = Double.MAX_VALUE;
		for(int mask = 0; mask < (1 << N); mask++) {
			bruteForce = Math.min(bruteForce, this.nkLandscape.getFitness(lociFor(mask)));
		}
		assertEquals(lowest.getFitness(), Double.valueOf(bruteForce),
				"the reported gene must be the true minimum for N=" + N);
		assertEquals(this.nkLandscape.isLowestFitnessExact(), Boolean.TRUE,
				"N=" + N + " is within EXACT_OPTIMUM_MAX_N so the optimum must be flagged exact");
	}

	// ------------------------------------------------------------- seeding

	@Test
	public void testSameSeedProducesTheSameLandscape() {
		NKLandscapeImpl first = new NKLandscapeImpl(N, K, this.neighbors,
				NKLandscapeImpl.DEFAULT_N_PARTICLE, NKLandscapeImpl.DEFAULT_ITERATIONS, SEED);
		NKLandscapeImpl second = new NKLandscapeImpl(N, K, this.neighbors,
				NKLandscapeImpl.DEFAULT_N_PARTICLE, NKLandscapeImpl.DEFAULT_ITERATIONS, SEED);
		assertEquals(second.getSeed(), first.getSeed(), "the seed must be retained");
		assertEquals(second.getLowestFitnessGene().getInternalComponents(),
				first.getLowestFitnessGene().getInternalComponents(),
				"the same seed must give the same optimum");
		assertEquals(second.getLowestFitnessGene().getFitness(),
				first.getLowestFitnessGene().getFitness(),
				"the same seed must give the same optimum fitness");
	}

	@Test
	public void testDifferentSeedsProduceDifferentTables() {
		NKLandscapeImpl first = new NKLandscapeImpl(N, K, this.neighbors, 10, 10, 1L);
		NKLandscapeImpl second = new NKLandscapeImpl(N, K, this.neighbors, 10, 10, 2L);
		assertFalse(first.getComponentFitnessCache().equals(second.getComponentFitnessCache()),
				"different seeds should give different fitness tables");
	}

	// -------------------------------------------------------- argument validation

	@Test
	public void testInvalidNAndKAreRejected() {
		final List<List<Byte>> validNeighbors = NKUtils.generateAdjacentNeighborhood(4, 1);
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { new NKLandscapeImpl(0, 1, validNeighbors, (NKGene<Boolean>) null); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			// K must not exceed N-1
			public void run() { new NKLandscapeImpl(4, 4, validNeighbors, (NKGene<Boolean>) null); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { new NKLandscapeImpl(4, -1, validNeighbors, (NKGene<Boolean>) null); }
		});
	}

	@Test
	public void testSelfDependencyIsRejected() {
		final List<List<Byte>> broken = NKUtils.generateAdjacentNeighborhood(4, 1);
		broken.get(0).set(0, NKUtils.EXIST_DEPENDENCY);
		broken.get(0).set(1, NKUtils.NO_DEPENDENCY);
		assertThrows(IllegalStateException.class, new Runnable() {
			public void run() { new NKLandscapeImpl(4, 1, broken, (NKGene<Boolean>) null); }
		});
	}

	@Test
	public void testWellFormedNeighbourhoodIsAccepted() {
		NKLandscapeImpl landscape = new NKLandscapeImpl(4, 3,
				NKUtils.generateAdjacentNeighborhood(4, 3), (NKGene<Boolean>) null);
		assertTrue(landscape.neighborsComplete(), "a valid matrix must be accepted");
		assertEquals(landscape.getN(), Integer.valueOf(4));
		assertEquals(landscape.getK(), Integer.valueOf(3));
	}

	// --------------------------------------------------------------- helpers

	private List<Boolean> lociFor(int mask) {
		List<Boolean> loci = new ArrayList<Boolean>(N);
		for(int bit = 0; bit < N; bit++) {
			loci.add(((mask >> bit) & 1) == 1);
		}
		return loci;
	}

	private static List<java.util.Map<String, Double>> emptyTable(int n) {
		List<java.util.Map<String, Double>> table = new ArrayList<java.util.Map<String, Double>>();
		for(int i = 0; i < n; i++) {
			table.add(new java.util.HashMap<String, Double>());
		}
		return table;
	}

	private static List<java.util.Map<String, Double>> partialTable(int n, int entries) {
		List<java.util.Map<String, Double>> table = emptyTable(n);
		for(java.util.Map<String, Double> component : table) {
			for(int i = 0; i < entries; i++) {
				component.put(Integer.toBinaryString(i), 0.5);
			}
		}
		return table;
	}
}
