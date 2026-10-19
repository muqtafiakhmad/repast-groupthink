package jp.ac.titech.dis.cs.simulation;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import jp.ac.titech.dis.cs.agent.AgentConstants;
import jp.ac.titech.dis.cs.agent.DecisionMakerAgent;
import jp.ac.titech.dis.cs.agent.DevilAdvocateAgent;
import jp.ac.titech.dis.cs.agent.ObserverAgent;
import jp.ac.titech.dis.cs.nk.NKComponent;
import jp.ac.titech.dis.cs.nk.NKGeneFactory;
import repast.simphony.context.Context;
import repast.simphony.context.DefaultContext;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.space.graph.Network;

/**
 * Tests for {@link AgentNetworkLoader}, which wires agents into the grid
 * described by a JSON network configuration.
 *
 * <p>
 * The loader previously trusted its input completely: a missing {@code m},
 * a position outside the grid, or fewer reserved cells than agents all surfaced
 * as bare {@link NullPointerException} or array exceptions with no indication of
 * which file or field was wrong. Each of those cases is now asserted to fail
 * with a message naming the file.
 * </p>
 */
public class TestAgentNetworkLoader {
	private static final int N = 8;
	private static final int K = 2;

	private NKComponent nkComponent;
	private Path tempDir;

	@BeforeClass
	public void setUp() throws Exception {
		NKComponent.Config config = new NKComponent.Config(N, K, null);
		config.setSeed(20190218L);
		this.nkComponent = new NKComponent(config);
		this.tempDir = Files.createTempDirectory("gt-network-loader");
	}

	@AfterClass(alwaysRun = true)
	public void tearDown() {
		deleteQuietly(tempDir.toFile());
	}

	// ------------------------------------------------------------------ helpers

	private Network<Object> newNetwork() {
		Context<Object> context = new DefaultContext<Object>();
		return new NetworkBuilder<Object>("loader network " + System.nanoTime(), context, true).buildNetwork();
	}

	private List<DecisionMakerAgent> decisionMakers(Network<Object> network, int count) {
		NKGeneFactory<Boolean> factory = this.nkComponent.getNkGeneFactory();
		List<DecisionMakerAgent> agents = new ArrayList<DecisionMakerAgent>();
		for (int i = 0; i < count; i++) {
			agents.add(new DecisionMakerAgent("DM" + (i + 1), factory.generateRandomGene(N, K),
					AgentConstants.INDIFFERENT_MORALITY_FUNCTION, factory, network));
		}
		return agents;
	}

	private List<ObserverAgent> observers(Network<Object> network, int count) {
		NKGeneFactory<Boolean> factory = this.nkComponent.getNkGeneFactory();
		List<ObserverAgent> agents = new ArrayList<ObserverAgent>();
		for (int i = 0; i < count; i++) {
			agents.add(new ObserverAgent("OB" + (i + 1), factory.generateRandomGene(N, K),
					AgentConstants.INDIFFERENT_MORALITY_FUNCTION, factory, network));
		}
		return agents;
	}

	private List<DevilAdvocateAgent> devilAdvocates(Network<Object> network, int count) {
		NKGeneFactory<Boolean> factory = this.nkComponent.getNkGeneFactory();
		List<DevilAdvocateAgent> agents = new ArrayList<DevilAdvocateAgent>();
		for (int i = 0; i < count; i++) {
			agents.add(new DevilAdvocateAgent("DV" + (i + 1), 0, factory.generateRandomGene(N, K),
					factory, network));
		}
		return agents;
	}

	/**
	 * A 3x2 layout: two decision makers on row 0, two observers on row 1, one
	 * devil's advocate at [2,0] and cell [2,1] deliberately left empty.
	 */
	private static final String WELL_FORMED_CONFIG =
			"{\"m\":3,\"n\":2,"
			+ "\"nodes\":[{\"id\":[0,0]},{\"id\":[0,1]},{\"id\":[1,0]},{\"id\":[1,1]},{\"id\":[2,0]}],"
			+ "\"decisionMakers\":[[0,0],[0,1]],"
			+ "\"observers\":[[1,0],[1,1]],"
			+ "\"devilAdvocateCandidates\":[[2,0]],"
			+ "\"links\":["
			+ "{\"source\":[0,0],\"target\":[1,0]},"
			+ "{\"source\":[0,1],\"target\":[1,1]},"
			+ "{\"source\":[2,0],\"target\":[0,0]},"
			+ "{\"source\":[0,0],\"target\":[2,1]}"
			+ "]}";

	private String writeConfig(String name, String json) throws IOException {
		File file = new File(this.tempDir.toFile(), name);
		Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
		return file.getAbsolutePath();
	}

	private Set<Object> neighbours(Network<Object> network, Object agent) {
		Set<Object> result = new HashSet<Object>();
		for (Object neighbour : network.getAdjacent(agent)) {
			result.add(neighbour);
		}
		return result;
	}

	private static void assertThrows(Class<? extends Throwable> expected, String messagePart, Runnable body) {
		try {
			body.run();
			fail("expected " + expected.getSimpleName() + " but nothing was thrown");
		} catch (Throwable thrown) {
			assertTrue(expected.isInstance(thrown),
					"expected " + expected.getSimpleName() + " but got " + thrown);
			if (messagePart != null) {
				assertTrue(String.valueOf(thrown.getMessage()).contains(messagePart),
						"the message should mention '" + messagePart + "', got: " + thrown.getMessage());
			}
		}
	}

	// -------------------------------------------------------------------- tests

	@Test
	public void testLoadWiresTheConfiguredLinks() throws Exception {
		String path = writeConfig("well-formed.json", WELL_FORMED_CONFIG);
		Network<Object> network = newNetwork();
		List<DecisionMakerAgent> dms = decisionMakers(network, 2);
		List<ObserverAgent> obs = observers(network, 2);
		List<DevilAdvocateAgent> dvs = devilAdvocates(network, 1);

		new AgentNetworkLoader(dvs, dms, obs, network, path).load();

		// DM0 [0,0] <-> OB0 [1,0] and DV [2,0]
		Set<Object> dm0Neighbours = neighbours(network, dms.get(0));
		assertTrue(dm0Neighbours.contains(obs.get(0)), "DM0 must be linked to the observer at [1,0]");
		assertTrue(dm0Neighbours.contains(dvs.get(0)), "DM0 must be linked to the advocate at [2,0]");
		assertEquals(dm0Neighbours.size(), 2, "DM0 has exactly the two links that name it");
		// DM1 [0,1] <-> OB1 [1,1]
		Set<Object> dm1Neighbours = neighbours(network, dms.get(1));
		assertTrue(dm1Neighbours.contains(obs.get(1)), "DM1 must be linked to the observer at [1,1]");
		assertEquals(dm1Neighbours.size(), 1, "DM1 has exactly the one link that names it");
		// the link pointing at the empty cell [2,1] must be skipped, not fatal
		assertTrue(neighbours(network, dvs.get(0)).contains(dms.get(0)),
				"the advocate must be linked back to DM0");
	}

	@Test
	public void testLoadSkipsLinksToEmptyCells() throws Exception {
		// only one link, and it targets the unreserved cell [2,1]
		String path = writeConfig("empty-cell.json",
				"{\"m\":3,\"n\":2,\"decisionMakers\":[[0,0],[0,1]],\"observers\":[[1,0],[1,1]],"
				+ "\"devilAdvocateCandidates\":[[2,0]],"
				+ "\"links\":[{\"source\":[0,0],\"target\":[2,1]}]}");
		Network<Object> network = newNetwork();
		new AgentNetworkLoader(devilAdvocates(network, 1), decisionMakers(network, 2),
				observers(network, 2), network, path).load();
		// no exception, and no agent ended up with a phantom neighbour
		int edges = 0;
		for (Object node : network.getNodes()) {
			edges += neighbours(network, node).size();
		}
		assertEquals(edges, 0, "a link whose target cell is empty must be skipped entirely");
	}

	@Test
	public void testLoadReportsAMissingFile() {
		final String missing = new File(this.tempDir.toFile(), "absent.json").getAbsolutePath();
		final Network<Object> network = newNetwork();
		assertThrows(IllegalStateException.class, missing, new Runnable() {
			public void run() {
				new AgentNetworkLoader(devilAdvocates(network, 1), decisionMakers(network, 2),
						observers(network, 2), network, missing).load();
			}
		});
	}

	@Test
	public void testLoadRejectsAConfigWithoutRowCount() throws Exception {
		final String path = writeConfig("no-m.json",
				"{\"n\":2,\"decisionMakers\":[[0,0]],\"observers\":[[1,0]],"
				+ "\"devilAdvocateCandidates\":[],\"links\":[]}");
		final Network<Object> network = newNetwork();
		assertThrows(IllegalStateException.class, "'m'", new Runnable() {
			public void run() {
				new AgentNetworkLoader(devilAdvocates(network, 0), decisionMakers(network, 1),
						observers(network, 1), network, path).load();
			}
		});
	}

	@Test
	public void testLoadRejectsMoreAgentsThanReservedCells() throws Exception {
		// one reserved decision maker cell, but two agents are built
		final String path = writeConfig("too-few-cells.json",
				"{\"m\":2,\"n\":2,\"decisionMakers\":[[0,0]],\"observers\":[[1,0]],"
				+ "\"devilAdvocateCandidates\":[],\"links\":[]}");
		final Network<Object> network = newNetwork();
		assertThrows(IllegalStateException.class, "decisionMakers", new Runnable() {
			public void run() {
				new AgentNetworkLoader(devilAdvocates(network, 0), decisionMakers(network, 2),
						observers(network, 1), network, path).load();
			}
		});
	}

	@Test
	public void testLoadRejectsAPositionOutsideTheGrid() throws Exception {
		final String path = writeConfig("bad-position.json",
				"{\"m\":2,\"n\":2,\"decisionMakers\":[[0,9]],\"observers\":[[1,0]],"
				+ "\"devilAdvocateCandidates\":[],\"links\":[]}");
		final Network<Object> network = newNetwork();
		assertThrows(IllegalStateException.class, "outside", new Runnable() {
			public void run() {
				new AgentNetworkLoader(devilAdvocates(network, 0), decisionMakers(network, 1),
						observers(network, 1), network, path).load();
			}
		});
	}

	@Test
	public void testLoadRejectsAnEmptyFilePath() {
		final Network<Object> network = newNetwork();
		assertThrows(IllegalArgumentException.class, null, new Runnable() {
			public void run() {
				new AgentNetworkLoader(devilAdvocates(network, 0), decisionMakers(network, 1),
						observers(network, 1), network, "   ");
			}
		});
	}

	private static void deleteQuietly(File file) {
		if (file == null || !file.exists()) {
			return;
		}
		File[] children = file.listFiles();
		if (children != null) {
			for (File child : children) {
				deleteQuietly(child);
			}
		}
		file.delete();
	}
}
