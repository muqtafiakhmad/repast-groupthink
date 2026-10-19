package jp.ac.titech.dis.cs.nk;

import static jp.ac.titech.dis.cs.nk.NKTestSupport.assertThrows;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for {@link NKGeneFactoryImpl}.
 *
 * <p>
 * The original suite printed the loci of the original and mutated genes without
 * comparing them, so the in-place mutation defect went unnoticed: mutating a
 * gene left the caller's gene with flipped loci and a stale fitness attached to
 * it.
 * </p>
 */
public class TestNKGeneFactory {

	private static final int N = 12;
	private static final int K = 3;
	private static final long SEED = 7L;

	private NKLandscapeImpl landscape;
	private NKGeneFactoryImpl factory;

	@BeforeClass
	public void setUp() {
		this.landscape = new NKLandscapeImpl(N, K,
				NKUtils.generateAdjacentNeighborhood(N, K), 40, 40, SEED);
		this.landscape.generateFitnessCache();
		this.factory = new NKGeneFactoryImpl(this.landscape);
	}

	// ------------------------------------------------------------- mutation

	@Test
	public void testMutationLeavesTheOriginalGeneIntact() {
		NKGene<Boolean> original = this.factory.generateByLociString(
				new ArrayList<Boolean>(Arrays.asList(true, true, true, true, true, true,
						true, true, true, true, true, true)), N, K);
		List<Boolean> lociBefore = new ArrayList<Boolean>(original.getInternalComponents());
		Double fitnessBefore = original.getFitness();

		NKGene<Boolean> mutated = this.factory.mutateComponent(original, 3, false);

		assertFalse(mutated == original, "the locus did change, so a new gene is required");
		assertEquals(original.getInternalComponents(), lociBefore,
				"mutating a gene must not edit the caller's gene");
		assertEquals(original.getFitness(), fitnessBefore,
				"mutating a gene must not touch the caller's gene fitness");
		assertEquals(fitnessBefore,
				this.landscape.getFitness(original.getInternalComponents()),
				"the original gene's stored fitness must still match its loci");
	}

	@Test
	public void testMutationProducesTheExpectedLoci() {
		NKGene<Boolean> original = this.factory.generateByLociString(
				new ArrayList<Boolean>(Arrays.asList(true, true, true, true, true, true,
						true, true, true, true, true, true)), N, K);
		NKGene<Boolean> mutated = this.factory.mutateComponent(original, 0, false);

		List<Boolean> expected = new ArrayList<Boolean>(original.getInternalComponents());
		expected.set(0, false);
		assertEquals(mutated.getInternalComponents(), expected);
		assertEquals(mutated.getFitness(),
				this.landscape.getFitness(mutated.getInternalComponents()),
				"the mutant's stored fitness must match its loci");
		assertFalse(mutated.getInternalComponents() == original.getInternalComponents(),
				"the two genes must not share one locus list");
	}

	@Test
	public void testMutatingToTheSameValueReturnsTheSameGene() {
		NKGene<Boolean> original = this.factory.generateByLociString(
				new ArrayList<Boolean>(Arrays.asList(true, true, true, true, true, true,
						true, true, true, true, true, true)), N, K);
		assertSame(this.factory.mutateComponent(original, 4, true), original,
				"a no-op mutation should hand back the same gene");
	}

	@Test
	public void testMutationRejectsAnOutOfRangeIndex() {
		final NKGene<Boolean> gene = this.factory.generateByLociString(
				NKUtils.generateRandomGene(N), N, K);
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { factory.mutateComponent(gene, -1, true); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { factory.mutateComponent(gene, N, true); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { factory.mutateComponent(gene, 0, null); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { factory.mutateComponent(null, 0, true); }
		});
	}

	// ---------------------------------------------------------- random genes

	@Test
	public void testRandomGeneFitnessMatchesTheLandscape() {
		for(int i = 0; i < 50; i++) {
			NKGene<Boolean> gene = this.factory.generateRandomGene(N, K);
			assertEquals(gene.getInternalComponents().size(), N);
			assertEquals(gene.getFitness(),
					this.landscape.getFitness(gene.getInternalComponents()),
					"a randomly drawn gene must carry the fitness the landscape gives it");
		}
	}

	@Test
	public void testSeededFactoryRepeatsItsGenes() {
		NKGeneFactoryImpl first = new NKGeneFactoryImpl(this.landscape, new Random(SEED));
		NKGeneFactoryImpl second = new NKGeneFactoryImpl(this.landscape, new Random(SEED));
		assertEquals(first.generateRandomGene(N, K).getInternalComponents(),
				second.generateRandomGene(N, K).getInternalComponents(),
				"two factories seeded alike must draw the same loci");
	}

	// ------------------------------------------------------- explicit loci

	@Test
	public void testGenerateByLociStringValidatesAgainstTheLandscape() {
		final List<Boolean> loci = NKUtils.generateRandomGene(N);
		assertThrows(IllegalArgumentException.class, new Runnable() {
			// N disagrees with the landscape
			public void run() { factory.generateByLociString(loci, N + 1, K); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			// K disagrees with the landscape
			public void run() { factory.generateByLociString(loci, N, K + 1); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			// too few loci
			public void run() { factory.generateByLociString(NKUtils.generateRandomGene(N - 1), N, K); }
		});
	}
}
