package jp.ac.titech.dis.cs.nk;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.testng.annotations.Test;

/**
 * Tests for the NK helpers. Every expectation is asserted: the original suite
 * printed the generated matrices and loci without checking them, so it passed
 * even when the generators produced degenerate output.
 */
public class TestNKUtils {

	// ---------------------------------------------------------- random genes

	@Test
	public void testGenerateRandomGeneProducesTheRequestedLength() {
		assertEquals(NKUtils.generateRandomGene(5).size(), 5);
		assertEquals(NKUtils.generateRandomGene(12).size(), 12);
	}

	@Test
	public void testGenerateRandomGeneRejectsInvalidLength() {
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { NKUtils.generateRandomGene(0); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { NKUtils.generateRandomGene(-3); }
		});
	}

	@Test
	public void testGenerateRandomGeneRepeatsForTheSameSeed() {
		List<Boolean> first = NKUtils.generateRandomGene(64, new Random(7L));
		List<Boolean> second = NKUtils.generateRandomGene(64, new Random(7L));
		List<Boolean> other = NKUtils.generateRandomGene(64, new Random(8L));
		assertEquals(first, second, "the same seed must give the same gene");
		assertFalse(first.equals(other), "different seeds should give different genes");
	}

	// -------------------------------------------------------- neighbourhoods

	@Test
	public void testGenerateAdjacentNeighborhood() {
		int[][] cases = { { 8, 0 }, { 8, 2 }, { 8, 4 }, { 8, 6 }, { 12, 3 }, { 7, 6 } };
		for(int[] testCase : cases) {
			List<List<Byte>> neighborhood =
					NKUtils.generateAdjacentNeighborhood(testCase[0], testCase[1]);
			assertWellFormed("adjacent N=" + testCase[0] + " K=" + testCase[1],
					neighborhood, testCase[0], testCase[1]);
		}
	}

	@Test
	public void testAdjacentNeighborhoodLinksEachLocusToItsSuccessors() {
		// N=5, K=2 => locus i depends on (i+1)%5 and (i+2)%5
		List<List<Byte>> neighborhood = NKUtils.generateAdjacentNeighborhood(5, 2);
		for(int locus = 0; locus < 5; locus++) {
			List<Byte> row = neighborhood.get(locus);
			assertEquals(row.get((locus + 1) % 5), NKUtils.EXIST_DEPENDENCY);
			assertEquals(row.get((locus + 2) % 5), NKUtils.EXIST_DEPENDENCY);
			assertEquals(row.get(locus), NKUtils.NO_DEPENDENCY, "a locus must not depend on itself");
		}
	}

	@Test
	public void testGenerateAdjacentNeighborhoodRejectsInvalidNK() {
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { NKUtils.generateAdjacentNeighborhood(8, 8); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { NKUtils.generateAdjacentNeighborhood(8, 9); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { NKUtils.generateAdjacentNeighborhood(8, -1); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { NKUtils.generateAdjacentNeighborhood(0, 0); }
		});
	}

	@Test
	public void testGenerateModularNeighborhood() {
		int[][] cases = { { 8, 1 }, { 8, 3 }, { 12, 3 }, { 9, 2 } };
		for(int[] testCase : cases) {
			List<List<Byte>> neighborhood =
					NKUtils.generateModularNeighborhood(testCase[0], testCase[1]);
			assertWellFormed("modular N=" + testCase[0] + " K=" + testCase[1],
					neighborhood, testCase[0], testCase[1]);
		}
	}

	@Test
	public void testModularNeighborhoodGroupsLociIntoModules() {
		// N=8, K=3 => modules {0..3} and {4..7}
		List<List<Byte>> neighborhood = NKUtils.generateModularNeighborhood(8, 3);
		for(int locus = 0; locus < 8; locus++) {
			int module = locus / 4;
			for(int other = 0; other < 8; other++) {
				Byte expected = (other != locus && other / 4 == module)
						? NKUtils.EXIST_DEPENDENCY : NKUtils.NO_DEPENDENCY;
				assertEquals(neighborhood.get(locus).get(other), expected,
						"locus " + locus + " -> " + other);
			}
		}
	}

	@Test
	public void testGenerateModularNeighborhoodRejectsIndivisibleN() {
		// module size would be K+1 = 3, which does not tile 8 loci
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { NKUtils.generateModularNeighborhood(8, 2); }
		});
	}

	@Test
	public void testGenerateRandomNeighborhood() {
		List<List<Byte>> matrix = NKUtils.generateRandomNeighborhood(9, 4, new Random(1L));
		assertWellFormed("random N=9 K=4", matrix, 9, 4);

		List<List<Byte>> same = NKUtils.generateRandomNeighborhood(9, 4, new Random(1L));
		List<List<Byte>> different = NKUtils.generateRandomNeighborhood(9, 4, new Random(2L));
		assertEquals(matrix, same, "the same seed must give the same dependency matrix");
		assertFalse(matrix.equals(different), "different seeds should give different matrices");
	}

	@Test
	public void testGenerateRandomNeighborhoodRejectsInvalidArguments() {
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { NKUtils.generateRandomNeighborhood(8, 8, new Random(1L)); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { NKUtils.generateRandomNeighborhood(8, 2, null); }
		});
	}

	@Test
	public void testGenerateNeighborhoodByType() {
		assertWellFormed("adjacent via dispatch",
				NKUtils.generateNeighborhood(NKLandscapePath.NeighborhoodType.ADJACENT, 9, 4, new Random(1L)),
				9, 4);
		// modular is only valid where N is a multiple of K+1
		assertWellFormed("modular via dispatch",
				NKUtils.generateNeighborhood(NKLandscapePath.NeighborhoodType.MODULAR, 9, 2, new Random(1L)),
				9, 2);
		assertWellFormed("random via dispatch",
				NKUtils.generateNeighborhood(NKLandscapePath.NeighborhoodType.RANDOM, 9, 4, new Random(1L)),
				9, 4);
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { NKUtils.generateNeighborhood(null, 8, 2, new Random(1L)); }
		});
	}

	// ------------------------------------------------------------- split / keys

	@Test
	public void testSplitGeneComponents() {
		// N=3, K=2: every locus depends on both others
		List<List<Byte>> neighborhood = NKUtils.generateAdjacentNeighborhood(3, 2);
		List<Boolean> gene = new ArrayList<Boolean>(Arrays.asList(true, false, false));
		List<List<Boolean>> split = NKUtils.splitToGeneComponents(3, 2, gene, neighborhood);

		assertEquals(split.size(), 3);
		for(int locus = 0; locus < 3; locus++) {
			assertEquals(split.get(locus).size(), 3, "each component needs K+1 inputs");
			// the locus itself always comes first, then the dependencies by index
			assertEquals(split.get(locus).get(0), gene.get(locus),
					"the first input of a component must be the locus itself");
		}
		// locus 0 sits at index 0, so its dependencies (1 and 2) follow in index order
		assertEquals(split.get(0), new ArrayList<Boolean>(Arrays.asList(true, false, false)));
		// locus 2 sits at index 2, so its dependencies are read from the top of the ring
		assertEquals(split.get(2), new ArrayList<Boolean>(Arrays.asList(false, true, false)));
	}

	@Test
	public void testSplitGeneComponentsRejectsMismatchedArguments() {
		final List<List<Byte>> neighborhood = NKUtils.generateAdjacentNeighborhood(5, 1);
		final List<Boolean> shortGene = NKUtils.generateRandomGene(4);
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { NKUtils.splitToGeneComponents(5, 1, shortGene, neighborhood); }
		});
	}

	@Test
	public void testComponentListSignature() {
		assertEquals(NKUtils.getComponentListSignature(
				new ArrayList<Boolean>(Arrays.asList(true, false, true))), "101");
		assertEquals(NKUtils.getComponentListSignature(
				new ArrayList<Boolean>(Arrays.asList(false, false))), "00");
		try {
			NKUtils.getComponentListSignature(null);
			fail("a null component list must be rejected");
		} catch (IllegalArgumentException expected) {
			// expected
		}
	}

	// --------------------------------------------------------- initial loci

	@Test
	public void testResetAdjacent() {
		List<Boolean> original = new ArrayList<Boolean>(Arrays.asList(false, false, false, false));
		List<Boolean> reset = NKUtils.resetInitialLociAdjacent(original, 2);
		assertEquals(reset, new ArrayList<Boolean>(Arrays.asList(true, true, false, false)));
		assertEquals(original, new ArrayList<Boolean>(Arrays.asList(false, false, false, false)),
				"the input must not be modified");
	}

	@Test
	public void testResetAdjacentRejectsOutOfRangeCount() {
		final List<Boolean> four = NKUtils.generateRandomGene(4);
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { NKUtils.resetInitialLociAdjacent(four, 5); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { NKUtils.resetInitialLociAdjacent(four, -1); }
		});
	}

	@Test
	public void testResetModular() {
		// N=8, K=3 => two modules of 4; resetting 3 loci sets the first 3 of each module
		List<Boolean> original = new ArrayList<Boolean>(Arrays.asList(
				false, false, false, false, false, false, false, false));
		List<Boolean> reset = NKUtils.resetInitialLociModular(original, 8, 3, 3);
		assertEquals(reset, new ArrayList<Boolean>(Arrays.asList(
				true, true, true, false, true, true, true, false)));
	}

	@Test
	public void testResetModularRejectsInconsistentArguments() {
		final List<Boolean> eight = NKUtils.generateRandomGene(8);
		assertThrows(IllegalArgumentException.class, new Runnable() {
			// 8 is not a multiple of K+1 = 3
			public void run() { NKUtils.resetInitialLociModular(eight, 8, 2, 1); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			// numReset may not exceed one module of K+1 = 4 loci
			public void run() { NKUtils.resetInitialLociModular(eight, 8, 3, 5); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			// opinion length disagrees with N
			public void run() { NKUtils.resetInitialLociModular(eight, 7, 6, 1); }
		});
	}

	// --------------------------------------------------------------- helpers

	/**
	 * Assert the defining property of an NK dependency matrix: N x N, only 0/1
	 * entries, exactly K dependencies per row, and no self-dependency.
	 */
	static void assertWellFormed(String message, List<List<Byte>> matrix, int n, int k) {
		NKTestSupport.assertWellFormed(message, matrix, n, k);
	}

	private static void assertThrows(Class<? extends Throwable> expected, Runnable body) {
		NKTestSupport.assertThrows(expected, body);
	}
}
