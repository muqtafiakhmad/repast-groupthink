package jp.ac.titech.dis.cs.agent;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import jp.ac.titech.dis.cs.nk.NKComponent;
import jp.ac.titech.dis.cs.nk.NKGene;
import jp.ac.titech.dis.cs.nk.NKGeneFactory;
import jp.ac.titech.dis.cs.simulation.GroupthinkController;
import repast.simphony.context.Context;
import repast.simphony.context.DefaultContext;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.space.graph.Network;

/**
 * Consensus tests for a group of decision makers with no devil's advocate.
 *
 * <p>
 * The original suite printed the state of every agent before and after the run
 * and asserted nothing, so a model that never converged still passed. These
 * tests bound the simulation (the unbounded {@code while(!consensus)} loop was
 * one hang away from freezing the suite), assert that consensus is actually
 * reached, and check that every opinion still agrees with the landscape it was
 * evaluated against.
 * </p>
 */
public class TestSimpleConsensus {
	private NKComponent nkComponent;
	private static final int DM_COUNT = 9;
	private static final int N = 12;
	private static final int K = 3;
	/** guard against the original unbounded consensus loop */
	private static final int MAX_ITERATIONS = 5000;

	public TestSimpleConsensus(){
		this.nkComponent = new NKComponent(seededConfig());
	}

	/** a fixed seed so the suite is reproducible run to run */
	private static NKComponent.Config seededConfig(){
		NKComponent.Config config = new NKComponent.Config(N, K, null);
		config.setSeed(20190218L);
		return config;
	}

	@BeforeClass
	public void setUp() throws Exception {
		//no-op
	}

	@AfterClass
	public void tearDown() throws Exception {
		//no-op
	}

	/**
	 * initialize agents with given network configuration
	 */
	private List<DecisionMakerAgent> initializeAgents(Network<Object> agentNetwork){
		List<DecisionMakerAgent> decisionMakerAgents = new ArrayList<DecisionMakerAgent>();
		NKGeneFactory<Boolean> nkGeneFactory = this.nkComponent.getNkGeneFactory();
		for(int iter = 0; iter < DM_COUNT; iter++){
			decisionMakerAgents.add(new DecisionMakerAgent(
					"DM"+iter,
					nkGeneFactory.generateRandomGene(N, K),
					AgentConstants.INDIFFERENT_MORALITY_FUNCTION,
					nkGeneFactory,
					agentNetwork));
		}
		return decisionMakerAgents;
	}

	private Network<Object> createEmptyNetwork(){
		Context<Object> context = new DefaultContext<Object>();
		NetworkBuilder<Object> netBuilder = new NetworkBuilder<Object>("agent_network_"+System.currentTimeMillis(), context, true);
		return netBuilder.buildNetwork();
	}

	/**
	 * Drive the model until the decision makers agree, asserting that it does so
	 * within {@link #MAX_ITERATIONS}.
	 *
	 * @return the number of interactions it took
	 */
	private int runUntilConsensus(GroupthinkController orchestratorAgent){
		int iterations = 0;
		while(!orchestratorAgent.decisionMakerReachConsensus()){
			if(iterations++ >= MAX_ITERATIONS) {
				assertTrue(false, "decision makers failed to reach consensus within "
						+ MAX_ITERATIONS + " interactions");
				return iterations;
			}
			orchestratorAgent.simulateInteraction();
		}
		return iterations;
	}

	/**
	 * Every opinion must still be a gene the landscape can score: the stored
	 * fitness has to match the loci it carries (defect (2) showed this could
	 * silently diverge).
	 */
	private void assertOpinionsAgreeWithTheLandscape(List<? extends GroupthinkBaseAgent> agents){
		for(GroupthinkBaseAgent agent : agents){
			NKGene<Boolean> opinion = agent.getOpinion().getOpinion();
			assertEquals(opinion.getFitness(),
					this.nkComponent.getNkLandscape().getFitness(opinion.getInternalComponents()),
					agent.getAgentId() + "'s stored fitness does not match its loci");
			assertEquals(opinion.getInternalComponents().size(), N,
					agent.getAgentId() + "'s opinion must cover all " + N + " loci");
		}
	}

	public void baseTest(NetworkFormationBuilder networkFormationBuilder, int decisionMakerCount, int devilAdvocateCount, int observerCount){
		Network<Object> network = createEmptyNetwork();
		List<DecisionMakerAgent> decisionMakers = initializeAgents(network);
		List<ObserverAgent> observers = new ArrayList<ObserverAgent>();
		List<DevilAdvocateAgent> devilAdvocates = new ArrayList<DevilAdvocateAgent>();

		GroupthinkController orchestratorAgent =
				new GroupthinkController(observers, decisionMakers, devilAdvocates,
				new AggregatedMetricsAgent(observers, decisionMakers, devilAdvocates, N, K, 0, System.currentTimeMillis(), 0, decisionMakerCount, devilAdvocateCount, observerCount, 0));
		// create network
		network = networkFormationBuilder.configureNetwork(network, observers, decisionMakers, devilAdvocates);
		// opinions must be consistent with the landscape before any interaction
		assertOpinionsAgreeWithTheLandscape(decisionMakers);
		// simulate, bounded so a non-converging run fails instead of hanging
		runUntilConsensus(orchestratorAgent);
		// consensus means every decision maker holds the identical loci
		List<Boolean> first = decisionMakers.get(0).getOpinion().getOpinion().getInternalComponents();
		for(DecisionMakerAgent agent : decisionMakers){
			assertEquals(agent.getOpinion().getOpinion().getInternalComponents(), first,
					agent.getAgentId() + " diverged from the consensus opinion");
		}
		// and the opinions still agree with the landscape after the run
		assertOpinionsAgreeWithTheLandscape(decisionMakers);
	}

	private int getNeighborCount(Network<Object> network, Object agent){
		Iterator<Object> objectIterator = network.getAdjacent(agent).iterator();
		int count = 0;
		while(objectIterator.hasNext()){
			objectIterator.next();
			count++;
		}
		return count;
	}

	@Test
	public void testCyclicFormation(){
		baseTest(new NetworkFormationBuilder(){
			@Override
			public Network<Object> configureNetwork(Network<Object> network, List<ObserverAgent> observers,
					List<DecisionMakerAgent> decisionMakers, List<DevilAdvocateAgent> devilAdvocates) {
				for(int iter = 1; iter < DM_COUNT; iter++){
					network.addEdge(decisionMakers.get(iter), decisionMakers.get(iter-1));
				}
				network.addEdge(decisionMakers.get(0), decisionMakers.get(DM_COUNT-1));
				// each agent should have 2 neighbours
				for(DecisionMakerAgent agent : decisionMakers){
					assertEquals(getNeighborCount(network, agent), 2,
							agent.getAgentId() + " must have two neighbours on a ring");
				}
				return network;
			}},
				DM_COUNT, 0, 0);
	}

	@Test
	public void testCompleteGraphFormation(){
		baseTest(new NetworkFormationBuilder(){
			@Override
			public Network<Object> configureNetwork(Network<Object> network, List<ObserverAgent> observers,
					List<DecisionMakerAgent> decisionMakers, List<DevilAdvocateAgent> devilAdvocates) {
				for(int i = 0; i < DM_COUNT; i++){
					for(int j = 0; j < DM_COUNT; j++){
						if(j != i){
							network.addEdge(decisionMakers.get(i), decisionMakers.get(j));
						}
					}
				}
				// each agent should have DM_COUNT-1 neighbours
				for(DecisionMakerAgent agent : decisionMakers){
					assertEquals(getNeighborCount(network, agent), DM_COUNT - 1,
							agent.getAgentId() + " must see every other agent");
				}
				return network;
			}},
				DM_COUNT,0,0);
	}
}
