package jp.ac.titech.dis.cs.nk;

import static jp.ac.titech.dis.cs.nk.NKTestSupport.assertTableComplete;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Statistical checks on the fitness distribution of an NK landscape.
 *
 * <p>
 * The original version of this class printed values drawn straight from COLT's
 * {@link cern.jet.random.Normal} generator. It exercised a third-party random
 * number generator rather than the model, and it asserted nothing, so a
 * landscape that produced degenerate contributions would still have passed.
 * These tests measure the landscape itself: every characteristic fitness must
 * be a fair draw from [0, 1), and because a gene's fitness is their mean, mean
 * fitness over the whole landscape must concentrate near 0.5.
 * </p>
 */
public class TestDistribution {

	private static final int N = 12;
	private static final int K = 3;
	private static final long SEED = 4242L;

	private NKLandscapeImpl landscape;

	@BeforeClass
	public void setUp() {
		this.landscape = new NKLandscapeImpl(N, K,
				NKUtils.generateAdjacentNeighborhood(N, K), 40, 40, SEED);
		this.landscape.generateFitnessCache();
		assertTableComplete("landscape under test", this.landscape);
	}

	@Test
	public void testComponentContributionsAreFairDrawsFromTheUnitInterval() {
		int count = 0;
		double sum = 0.0;
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		for(Map<String, Double> component : this.landscape.getComponentFitnessCache()) {
			for(Double value : component.values()) {
				assertTrue(value >= 0.0 && value < 1.0,
						"contribution " + value + " is outside [0, 1)");
				sum += value;
				min = Math.min(min, value);
				max = Math.max(max, value);
				count++;
			}
		}
		// each of the N components holds one entry per input combination
		assertTrue(count == N * (1 << (K + 1)),
				"expected " + (N * (1 << (K + 1))) + " contributions, found " + count);
		double mean = sum / count;
		assertTrue(Math.abs(mean - 0.5) < 0.1,
				"mean contribution should be near 0.5 but was " + mean);
		// with 192 draws, seeing the tails is all but certain
		assertTrue(min < 0.25, "the smallest contribution was " + min + "; the table looks skewed");
		assertTrue(max > 0.75, "the largest contribution was " + max + "; the table looks skewed");
	}

	@Test
	public void testMeanFitnessConcentratesAroundHalf() {
		// a gene's fitness is the mean of N independent contributions, so the
		// landscape-wide mean must sit very close to 0.5
		int count = 0;
		double sum = 0.0;
		for(int mask = 0; mask < (1 << N); mask++) {
			sum += this.landscape.getFitness(lociFor(mask));
			count++;
		}
		double mean = sum / count;
		assertTrue(Math.abs(mean - 0.5) < 0.1,
				"mean fitness over all " + count + " genotypes was " + mean + ", expected ~0.5");
	}

	@Test
	public void testFitnessIsAMeanOfTheComponentContributions() {
		List<Boolean> gene = lociFor(0b101100101101);
		List<List<Boolean>> components = NKUtils.splitToGeneComponents(N, K, gene,
				this.landscape.getNeighbors());
		double sum = 0.0;
		for(int component = 0; component < N; component++) {
			Map<String, Double> table = this.landscape.getComponentFitnessCache().get(component);
			Double value = table.get(NKUtils.getComponentListSignature(components.get(component)));
			assertTrue(value != null, "component " + component + " has no entry for this input set");
			sum += value;
		}
		assertEquals(Double.valueOf(sum / N), this.landscape.getFitness(gene),
				"getFitness must equal the mean of the component contributions");
	}

	@Test
	public void testDistributionIsReproducibleFromItsSeed() {
		NKLandscapeImpl twin = new NKLandscapeImpl(N, K,
				NKUtils.generateAdjacentNeighborhood(N, K), 40, 40, SEED);
		twin.generateFitnessCache();
		assertEquals(twin.getComponentFitnessCache(), this.landscape.getComponentFitnessCache(),
				"the same seed must reproduce the same distribution");
	}

	private List<Boolean> lociFor(int mask) {
		List<Boolean> loci = new ArrayList<Boolean>(N);
		for(int bit = 0; bit < N; bit++) {
			loci.add(((mask >> bit) & 1) == 1);
		}
		return loci;
	}
}
