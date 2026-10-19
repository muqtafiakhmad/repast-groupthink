package jp.ac.titech.dis.cs.nk;

import static jp.ac.titech.dis.cs.nk.NKTestSupport.assertTableComplete;
import static jp.ac.titech.dis.cs.nk.NKTestSupport.assertThrows;
import static jp.ac.titech.dis.cs.nk.NKTestSupport.deleteQuietly;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jp.ac.titech.dis.cs.nk.NKLandscapePath.NeighborhoodType;

/**
 * Tests for {@link NKLandscapeFactoryImpl}: persisting a landscape and reading
 * it back.
 *
 * <p>
 * The original suite wrote to {@code C:\Users\Muqtafi Akhmad\...} and spread the
 * dump across one test and the load across another, so it only worked when the
 * whole class ran in one specific order on one specific machine. Everything
 * here writes into a private temporary directory and each test dumps and loads
 * for itself.
 * </p>
 *
 * <p>
 * Bulk landscape generation is still covered, but writes to a temporary
 * directory and generates a small number of files by default; raise it with
 * {@code -Dnk.test.batch.count=1000} when you actually want the batch.
 * </p>
 */
public class TestNKLandscapeFactory {

	/** Files per bulk generation test; override when you need a real batch. */
	private static final int BATCH_COUNT = Integer.getInteger("nk.test.batch.count", 3);

	private static final int N = 12;
	private static final int K = 3;
	private static final long SEED = 20190218L;

	private File outputDirectory;
	private NKLandscapeFactoryImpl factory;

	@BeforeClass
	public void setUp() throws Exception {
		this.outputDirectory = Files.createTempDirectory("gt-dynamics-test").toFile();
		this.factory = new NKLandscapeFactoryImpl();
	}

	@AfterClass(alwaysRun = true)
	public void tearDown() {
		deleteQuietly(this.outputDirectory);
	}

	// --------------------------------------------------------------- round trip

	@Test
	public void testDumpThenLoadRoundTrip() {
		NKLandscape<Boolean> generated = this.factory.generateRandomLandscape(
				N, K, NKUtils.generateAdjacentNeighborhood(N, K), SEED);
		File file = file("roundtrip.json");
		this.factory.dumpLandscape(generated, file.getAbsolutePath());

		NKLandscape<Boolean> loaded = this.factory.loadLandscape(file.getAbsolutePath());
		assertEquals(loaded.getN(), generated.getN(), "N did not survive the round trip");
		assertEquals(loaded.getK(), generated.getK(), "K did not survive the round trip");
		assertEquals(implOf(loaded).getNeighbors(), implOf(generated).getNeighbors(),
				"the dependency matrix did not survive the round trip");
		assertTableComplete("loaded table", (NKLandscapeImpl) loaded);
		assertEquals(tableOf(loaded), tableOf(generated),
				"the fitness table did not survive the round trip");
		assertEquals(loaded.getLowestFitnessGene().getInternalComponents(),
				generated.getLowestFitnessGene().getInternalComponents(),
				"the optimum did not survive the round trip");
		assertEquals(loaded.getLowestFitnessGene().getFitness(),
				generated.getLowestFitnessGene().getFitness(),
				"the optimum fitness did not survive the round trip");
	}

	@Test
	public void testLoadFromAConventionalPath() {
		NKLandscapePath path = new NKLandscapePath(
				outputDirectory.getAbsolutePath() + File.separator, N, K, NeighborhoodType.ADJACENT, 0);
		NKLandscape<Boolean> generated = this.factory.generateRandomLandscape(
				N, K, NKUtils.generateAdjacentNeighborhood(N, K), SEED);
		this.factory.dumpLandscape(generated, path.toString());

		NKLandscape<Boolean> loaded = this.factory.loadLandscape(path);
		assertTableComplete("loaded from " + path, (NKLandscapeImpl) loaded);
		assertEquals(tableOf(loaded), tableOf(generated));
	}

	@Test
	public void testSeedSurvivesTheRoundTripAndTheRoundTripIsStable() {
		NKLandscape<Boolean> generated = this.factory.generateRandomLandscape(
				6, 2, NKUtils.generateAdjacentNeighborhood(6, 2), 4242L);
		File once = file("stable-once.json");
		File twice = file("stable-twice.json");
		this.factory.dumpLandscape(generated, once.getAbsolutePath());

		NKLandscape<Boolean> loaded = this.factory.loadLandscape(once.getAbsolutePath());
		assertEquals(loaded.getSeed(), Long.valueOf(4242L), "the seed did not survive the load");

		this.factory.dumpLandscape(loaded, twice.getAbsolutePath());
		try {
			assertEquals(Files.readAllBytes(twice.toPath()), Files.readAllBytes(once.toPath()),
					"dump(load(x)) must reproduce x byte for byte");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	// ------------------------------------------------------------ validation

	@Test
	public void testLoadRejectsAnIncompleteTable() {
		final File file = file("truncated.json");
		NKLandscape<Boolean> generated = this.factory.generateRandomLandscape(
				4, 3, NKUtils.generateAdjacentNeighborhood(4, 3), 11L);
		this.factory.dumpLandscape(generated, file.getAbsolutePath());
		tamper(file, new Tamper() {
			public void apply(ObjectMapper mapper, ObjectNode root) throws Exception {
				ObjectNode firstComponent = (ObjectNode) root.get("componentFitnessCache").get(0);
				firstComponent.remove(firstComponent.fieldNames().next());
			}
		});

		// 15 of the 16 entries K=3 requires must not load
		assertThrows(IllegalStateException.class, new Runnable() {
			public void run() { factory.loadLandscape(file.getAbsolutePath()); }
		});
	}

	@Test
	public void testLoadRepairsAnOptimumWhoseFitnessDoesNotMatchItsLoci() {
		final File file = file("corrupt-optimum.json");
		NKLandscape<Boolean> generated = this.factory.generateRandomLandscape(
				8, 3, NKUtils.generateAdjacentNeighborhood(8, 3), 13L);
		this.factory.dumpLandscape(generated, file.getAbsolutePath());
		tamper(file, new Tamper() {
			public void apply(ObjectMapper mapper, ObjectNode root) throws Exception {
				// a fitness value that belongs to some other gene
				((ObjectNode) root.get("lowestFitnessGene")).put("fitness", 0.12345);
			}
		});

		NKLandscape<Boolean> loaded = this.factory.loadLandscape(file.getAbsolutePath());
		NKGene<Boolean> gene = loaded.getLowestFitnessGene();
		assertEquals(loaded.getFitness(gene.getInternalComponents()), gene.getFitness(),
				"the load must have rebuilt an optimum whose loci and fitness agree");
		assertEquals(loaded.isLowestFitnessExact(), Boolean.TRUE,
				"N=8 is within EXACT_OPTIMUM_MAX_N so the rebuilt optimum must be flagged exact");
	}

	@Test
	public void testLoadRejectsUnreadableInput() {
		final File missing = new File(this.outputDirectory, "does-not-exist.json");
		assertThrows(IllegalStateException.class, new Runnable() {
			public void run() { factory.loadLandscape(missing.getAbsolutePath()); }
		});
	}

	// ------------------------------------------------------------ generation

	@Test
	public void testSeededGenerationIsReproducible() {
		List<List<Byte>> neighbors = NKUtils.generateAdjacentNeighborhood(N, K);
		NKLandscape<Boolean> first = this.factory.generateRandomLandscape(N, K, neighbors, SEED);
		NKLandscape<Boolean> second = this.factory.generateRandomLandscape(N, K, neighbors, SEED);
		NKLandscape<Boolean> other = this.factory.generateRandomLandscape(N, K, neighbors, SEED + 1);

		assertEquals(tableOf(second), tableOf(first), "the same seed must give the same table");
		assertFalse(tableOf(other).equals(tableOf(first)),
				"different seeds should give different tables");
		assertEquals(first.getSeed(), Long.valueOf(SEED), "the seed must be retained on the landscape");
	}

	@Test
	public void testGeneratedLandscapeIsComplete() {
		NKLandscapeImpl landscape = (NKLandscapeImpl) this.factory.generateRandomLandscape(
				N, K, NKUtils.generateAdjacentNeighborhood(N, K), SEED);
		assertTableComplete("generated landscape", landscape);
		assertTrue(landscape.neighborsComplete());
	}

	// ------------------------------------------------------ bulk generation

	@Test
	public void generateLandscapeBatchN12AdjacentNeighborhood() {
		generateBatch(12, 3, NeighborhoodType.ADJACENT, "n12k3");
	}

	@Test
	public void generateLandscapeBatchN12ModularNeighborhood() {
		// N=12 splits into modules of K+1 = 4
		generateBatch(12, 3, NeighborhoodType.MODULAR, "n12k3mod");
	}

	@Test
	public void generateLandscapeBatchN8ModularNeighborhood() {
		generateBatch(8, 3, NeighborhoodType.MODULAR, "n8k3mod");
	}

	@Test
	public void generateLandscapeBatchN7K6AdjacentNeighborhood() {
		generateBatch(7, 6, NeighborhoodType.ADJACENT, "n7k6");
	}

	/**
	 * Build and persist {@link #BATCH_COUNT} landscapes under the conventional
	 * file name, then assert that every one of them loads back complete.
	 */
	private void generateBatch(int n, int k, NeighborhoodType type, String prefix) {
		List<List<Byte>> neighbors = NKUtils.generateNeighborhood(type, n, k, new RandomSeed());
		NKTestSupport.assertWellFormed("batch neighbourhood N=" + n + " K=" + k, neighbors, n, k);

		for(int runId = 0; runId < BATCH_COUNT; runId++) {
			NKLandscapePath path = new NKLandscapePath(
					outputDirectory.getAbsolutePath() + File.separator, n, k, type, runId);
			NKLandscape<Boolean> landscape = this.factory.generateRandomLandscape(n, k, neighbors, (long) runId);
			this.factory.dumpLandscape(landscape, path.toString());

			NKLandscape<Boolean> reloaded = this.factory.loadLandscape(path);
			assertTableComplete(path.toString(), (NKLandscapeImpl) reloaded);
			assertEquals(reloaded.getSeed(), landscape.getSeed(), "seed lost for " + path);
		}
	}

	// --------------------------------------------------------------- helpers

	/** A deterministic per-batch source; batch contents depend only on the run id. */
	private static final class RandomSeed extends java.util.Random {
		private static final long serialVersionUID = 1L;
		RandomSeed() { super(0L); }
	}

	private interface Tamper {
		void apply(ObjectMapper mapper, ObjectNode root) throws Exception;
	}

	private File file(String name) {
		return new File(this.outputDirectory, name);
	}

	private static List<Map<String, Double>> tableOf(NKLandscape<Boolean> landscape) {
		return implOf(landscape).getComponentFitnessCache();
	}

	private static NKLandscapeImpl implOf(NKLandscape<Boolean> landscape) {
		return (NKLandscapeImpl) landscape;
	}

	private void tamper(File file, Tamper tamper) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode root = (ObjectNode) mapper.readTree(file);
			tamper.apply(mapper, root);
			mapper.writeValue(file, root);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
