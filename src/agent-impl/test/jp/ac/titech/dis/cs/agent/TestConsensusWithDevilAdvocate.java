package jp.ac.titech.dis.cs.agent;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
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
 * Consensus tests where devil's advocates are wired into the group.
 *
 * <p>
 * As in {@link TestSimpleConsensus}, the original suite printed agent state and
 * asserted nothing. The devil's advocate deliberately holds an opposing opinion
 * every step, so consensus is not guaranteed: what must hold is that the model
 * stays well formed - every opinion still scores exactly what the landscape
 * says it should - and that the advocate really does inject an opposing opinion.
 * The simulation loop is bounded so a non-converging run fails instead of
 * hanging.
 * </p>
 */
public class TestConsensusWithDevilAdvocate {
	private NKComponent nkComponent;
	private static final int DM_COUNT = 9;
	private static final int DV_COUNT = 3;
	private static final int N = 12;
	private static final int K = 3;
	private static final int MAX_ITERATIONS = 5000;

	public TestConsensusWithDevilAdvocate(){
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

	private List<DecisionMakerAgent> generateDecisionMakers(Network<Object> agentNetwork){
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

	private List<DevilAdvocateAgent> generateDevilAdvocates(Network<Object> agentNetwork){
		List<DevilAdvocateAgent> devilAdvocates = new ArrayList<DevilAdvocateAgent>();
		NKGeneFactory<Boolean> nkGeneFactory = this.nkComponent.getNkGeneFactory();
		for(int iter = 0; iter < DV_COUNT; iter++) {
			devilAdvocates.add(new DevilAdvocateAgent(
					"DV"+iter,
					iter,
					nkGeneFactory.generateRandomGene(N, K),
					nkGeneFactory,
					agentNetwork
					));
		}
		return devilAdvocates;
	}

	private Network<Object> createEmptyNetwork(){
		Context<Object> context = new DefaultContext<Object>();
		NetworkBuilder<Object> netBuilder = new NetworkBuilder<Object>("agent_network_"+System.currentTimeMillis(), context, true);
		return netBuilder.buildNetwork();
	}

	/**
	 * Drive the model for a bounded number of interactions and assert that every
	 * opinion held by any agent still matches the landscape.
	 */
	private int runBounded(GroupthinkController orchestratorAgent, List<DecisionMakerAgent> decisionMakers,
			List<DevilAdvocateAgent> devilAdvocates){
		int iterations = 0;
		while(iterations < MAX_ITERATIONS && !orchestratorAgent.decisionMakerReachConsensus()){
			orchestratorAgent.simulateInteraction();
			iterations++;
			assertOpinionsAgreeWithTheLandscape(decisionMakers, "after " + iterations + " interactions");
			assertOpinionsAgreeWithTheLandscape(devilAdvocates, "after " + iterations + " interactions");
		}
		return iterations;
	}

	private void assertOpinionsAgreeWithTheLandscape(List<? extends GroupthinkBaseAgent> agents, String when){
		for(GroupthinkBaseAgent agent : agents){
			NKGene<Boolean> opinion = agent.getOpinion().getOpinion();
			assertEquals(opinion.getFitness(),
					this.nkComponent.getNkLandscape().getFitness(opinion.getInternalComponents()),
					agent.getAgentId() + "'s stored fitness does not match its loci " + when);
			assertEquals(opinion.getInternalComponents().size(), N,
					agent.getAgentId() + "'s opinion must cover all " + N + " loci " + when);
		}
	}

	public void baseTest(NetworkFormationBuilder networkFormationBuilder, int decisionMakerCount, int devilAdvocateCount, int observerCount){
		Network<Object> network = createEmptyNetwork();
		List<DecisionMakerAgent> decisionMakers = generateDecisionMakers(network);
		List<DevilAdvocateAgent> devilAdvocates = generateDevilAdvocates(network);
		List<ObserverAgent> emptyObserverList = new ArrayList<ObserverAgent>();

		GroupthinkController orchestratorAgent =
				new GroupthinkController(emptyObserverList, decisionMakers, devilAdvocates,
				new AggregatedMetricsAgent(emptyObserverList, decisionMakers, devilAdvocates, N, K, 0, System.currentTimeMillis(), 0, decisionMakerCount, devilAdvocateCount, observerCount, 0));
		// create network
		network = networkFormationBuilder.configureNetwork(network, emptyObserverList, decisionMakers, devilAdvocates);
		// everything is consistent before the first interaction
		assertOpinionsAgreeWithTheLandscape(decisionMakers, "initially");
		assertOpinionsAgreeWithTheLandscape(devilAdvocates, "initially");
		// simulate, bounded so a non-converging run fails rather than hangs
		runBounded(orchestratorAgent, decisionMakers, devilAdvocates);
		// every advocate must still hold a well-formed, full-length opinion
		for(DevilAdvocateAgent advocate : devilAdvocates){
			assertTrue(advocate.getOverlappingLociCount() >= 0
					&& advocate.getOverlappingLociCount() < N,
					advocate.getAgentId() + " must record a legal overlap between 0 and N-1");
			assertEquals(advocate.getOpinion().getOpinion().getInternalComponents().size(), N,
					advocate.getAgentId() + " must hold a full-length opinion");
		}
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
				for(DecisionMakerAgent agent : decisionMakers){
					assertEquals(getNeighborCount(network, agent), 2,
							agent.getAgentId() + " must have two neighbours on a ring");
				}
				// connect every devil advocate to every decision maker
				for(DevilAdvocateAgent devilAdvocate: devilAdvocates) {
					for(DecisionMakerAgent decisionMaker: decisionMakers) {
						network.addEdge(devilAdvocate, decisionMaker);
					}
				}
				return network;
			}},
				DM_COUNT, DV_COUNT, 0);
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
				for(DecisionMakerAgent agent : decisionMakers){
					assertEquals(getNeighborCount(network, agent), DM_COUNT - 1,
							agent.getAgentId() + " must see every other decision maker");
				}
				for(DevilAdvocateAgent devilAdvocate: devilAdvocates) {
					for(DecisionMakerAgent decisionMaker: decisionMakers) {
						network.addEdge(devilAdvocate, decisionMaker);
					}
				}
				return network;
			}},
				DM_COUNT, DV_COUNT, 0);
	}

	/**
	 * An advocate with overlappingLociCount 0 inverts the entire majority opinion,
	 * so it can never be identical to a decision maker's opinion.
	 */
	@Test
	public void testDevilAdvocateHoldsAnOpposingOpinion(){
		Network<Object> network = createEmptyNetwork();
		List<DecisionMakerAgent> decisionMakers = generateDecisionMakers(network);
		List<DevilAdvocateAgent> devilAdvocates = generateDevilAdvocates(network);
		// wire everyone to everyone so the advocate sees the decision makers
		for(DevilAdvocateAgent advocate : devilAdvocates) {
			for(DecisionMakerAgent dm : decisionMakers) {
				network.addEdge(advocate, dm);
			}
		}
		for(int i = 0; i < DM_COUNT; i++) {
			for(int j = 0; j < DM_COUNT; j++) {
				if(i != j) network.addEdge(decisionMakers.get(i), decisionMakers.get(j));
			}
		}
		// advocate 0 shares zero loci with the majority -> fully inverted
		DevilAdvocateAgent inverting = devilAdvocates.get(0);
		assertTrue(inverting.getOverlappingLociCount() == 0, "advocate 0 must be the fully inverting one");
		List<GetOpinionResult> dmOpinions = new ArrayList<GetOpinionResult>();
		for(DecisionMakerAgent dm : decisionMakers) dmOpinions.add(dm.getOpinion());
		inverting.interact();

		List<Boolean> majority = AgentOpinionUtil.getHighestOccurrenceLoci(dmOpinions);
		List<Boolean> advocateLoci = inverting.getOpinion().getOpinion().getInternalComponents();
		assertEquals(advocateLoci.size(), N);
		for(int locus = 0; locus < N; locus++) {
			assertFalse(advocateLoci.get(locus).equals(majority.get(locus)),
					"the fully inverting advocate must disagree with the majority at locus " + locus);
		}
		// and its recomputed opinion still scores correctly
		assertEquals(inverting.getOpinion().getOpinion().getFitness(),
				this.nkComponent.getNkLandscape().getFitness(advocateLoci),
				"the advocate's opinion must score exactly what the landscape says");
	}
}
