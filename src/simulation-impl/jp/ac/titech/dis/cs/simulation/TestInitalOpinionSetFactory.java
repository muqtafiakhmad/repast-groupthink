package jp.ac.titech.dis.cs.simulation;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TestInitalOpinionSetFactory {
	private final InitialOpinionSetFactory initialOpinionSetFactory;
	private final int numDecisionMakers = 10;
	private final int numDevilAdvocate = 10;
	private final int numObservers = 50;
	private File opinionDir;

	public TestInitalOpinionSetFactory() {
		this.initialOpinionSetFactory = new InitialOpinionSetFactory();
	}

	@BeforeClass
	public void setUp() throws IOException {
		Path dir = Files.createTempDirectory("gt-opinions-test");
		this.opinionDir = dir.toFile();
	}

	@AfterClass(alwaysRun = true)
	public void tearDown() {
		deleteQuietly(this.opinionDir);
	}

	@Test
	public void testGenerateRandomProducesValidOpinionSet() {
		int N = 12;
		InitialOpinionSet opinionSet = this.initialOpinionSetFactory.generateRandom(N, this.numDecisionMakers,
				this.numObservers, this.numDevilAdvocate);
		assertNotNull(opinionSet);
		Map<String, List<Boolean>> dm = opinionSet.getDecisionMakersOpinion();
		assertEquals(dm.size(), this.numDecisionMakers, "one opinion per decision maker");
		for (Map.Entry<String, List<Boolean>> entry : dm.entrySet()) {
			assertEquals(entry.getValue().size(), N, entry.getKey() + " must cover all " + N + " loci");
			for (int i = 0; i < N; i++) {
				assertNotNull(entry.getValue().get(i), entry.getKey() + " locus " + i + " must not be null");
			}
		}
		assertEquals(opinionSet.getObserversOpinion().size(), this.numObservers, "one opinion per observer");
		assertEquals(opinionSet.getDevilAdvocatesOpinion().size(), this.numDevilAdvocate,
				"one opinion per devil's advocate");
		// agent ids follow the DM<n>/OB<n>/DV<n> convention the builders rely on
		assertTrue(dm.containsKey("DM1") && dm.containsKey("DM" + this.numDecisionMakers),
				"decision maker ids must run DM1.." + this.numDecisionMakers);
	}

	@Test
	public void testSeededGenerationIsReproducible() {
		long seed = 4242L;
		InitialOpinionSet first = this.initialOpinionSetFactory.generateRandom(8, 4, 5, 2, seed);
		InitialOpinionSet second = this.initialOpinionSetFactory.generateRandom(8, 4, 5, 2, seed);
		InitialOpinionSet other = this.initialOpinionSetFactory.generateRandom(8, 4, 5, 2, seed + 1);

		assertEquals(second.getDecisionMakersOpinion(), first.getDecisionMakersOpinion(),
				"the same seed must reproduce the decision maker opinions");
		assertEquals(second.getObserversOpinion(), first.getObserversOpinion(),
				"the same seed must reproduce the observer opinions");
		assertEquals(second.getDevilAdvocatesOpinion(), first.getDevilAdvocatesOpinion(),
				"the same seed must reproduce the devil advocate opinions");
		assertFalse(other.getDecisionMakersOpinion().equals(first.getDecisionMakersOpinion()),
				"a different seed should give different opinions");
	}

	@Test
	public void testGenerateRandomValidatesItsArguments() {
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { initialOpinionSetFactory.generateRandom(0, 1, 1, 1, 1L); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { initialOpinionSetFactory.generateRandom(8, 0, 1, 1, 1L); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { initialOpinionSetFactory.generateRandom(8, 1, -1, 1, 1L); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { initialOpinionSetFactory.generateRandom(8, 1, 1, -1, 1L); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { initialOpinionSetFactory.generateRandom(8, 1, 1, 1, null); }
		});
	}

	@Test
	public void testSaveThenLoadRoundTrip() {
		int N = 7;
		InitialOpinionSet generated = this.initialOpinionSetFactory.generateRandom(N, 3, 4, 1, 99L);
		InitialOpinionSetFilePath path = new InitialOpinionSetFilePath(
				this.opinionDir.getAbsolutePath() + File.separator, 1, N);
		this.initialOpinionSetFactory.save(generated, path);

		File written = new File(path.toString());
		assertTrue(written.exists(), "save must write " + written);
		assertTrue(written.length() > 0, "the written file must not be empty");

		InitialOpinionSet loaded = this.initialOpinionSetFactory.load(path);
		assertEquals(loaded.getDecisionMakersOpinion(), generated.getDecisionMakersOpinion(),
				"decision maker opinions must survive the round trip");
		assertEquals(loaded.getObserversOpinion(), generated.getObserversOpinion(),
				"observer opinions must survive the round trip");
		assertEquals(loaded.getDevilAdvocatesOpinion(), generated.getDevilAdvocatesOpinion(),
				"devil advocate opinions must survive the round trip");
	}

	@Test
	public void testSaveCreatesMissingParentDirectory() {
		File nested = new File(this.opinionDir, "does-not-exist-yet");
		InitialOpinionSetFilePath path = new InitialOpinionSetFilePath(
				nested.getAbsolutePath() + File.separator, 5, 6);
		this.initialOpinionSetFactory.save(
				this.initialOpinionSetFactory.generateRandom(6, 2, 1, 1, 3L), path);
		assertTrue(new File(path.toString()).exists(), "save must create the directory it writes into");
	}

	@Test
	public void testLoadReportsTheMissingFilePath() {
		final InitialOpinionSetFilePath missing = new InitialOpinionSetFilePath(
				this.opinionDir.getAbsolutePath() + File.separator, 999, 8);
		try {
			this.initialOpinionSetFactory.load(missing);
			fail("loading a file that was never written must fail");
		} catch (IllegalStateException expected) {
			assertTrue(String.valueOf(expected.getMessage()).contains(missing.toString()),
					"the failure must name the path it tried, got: " + expected.getMessage());
		}
	}

	@Test
	public void testFilePathAlwaysSeparatesThePrefix() {
		// a prefix without a trailing separator must still produce a usable path
		InitialOpinionSetFilePath without = new InitialOpinionSetFilePath("initial-loci", 1, 12);
		InitialOpinionSetFilePath with = new InitialOpinionSetFilePath(
				"initial-loci" + File.separator, 1, 12);
		assertEquals(without.toString(), with.toString(),
				"a prefix with and without a separator must name the same file");

		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { new InitialOpinionSetFilePath(null, 1, 12); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { new InitialOpinionSetFilePath("dir", null, 12); }
		});
		assertThrows(IllegalArgumentException.class, new Runnable() {
			public void run() { new InitialOpinionSetFilePath("dir", 1, 0); }
		});
	}

	static void assertThrows(Class<? extends Throwable> expected, Runnable body) {
		try {
			body.run();
			fail("expected " + expected.getSimpleName() + " but nothing was thrown");
		} catch (Throwable thrown) {
			assertTrue(expected.isInstance(thrown),
					"expected " + expected.getSimpleName() + " but got " + thrown);
		}
	}

	private void deleteQuietly(File file) {
		if (file == null || !file.exists()) {
			return;
		}
		File[] files = file.listFiles();
		if (files != null) {
			for (File f : files) {
				deleteQuietly(f);
			}
		}
		file.delete();
	}
}
