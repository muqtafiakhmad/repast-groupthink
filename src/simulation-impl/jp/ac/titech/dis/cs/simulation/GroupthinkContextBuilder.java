package jp.ac.titech.dis.cs.simulation;

import java.util.ArrayList;
import java.util.List;
import jp.ac.titech.dis.cs.agent.AgentConstants;
import jp.ac.titech.dis.cs.agent.DecisionMakerAgent;
import jp.ac.titech.dis.cs.agent.AggregatedMetricsAgent;
import jp.ac.titech.dis.cs.agent.DevilAdvocateAgent;
import jp.ac.titech.dis.cs.agent.MoralityFunction;
import jp.ac.titech.dis.cs.agent.ObserverAgent;
import jp.ac.titech.dis.cs.nk.NKComponent;
import jp.ac.titech.dis.cs.nk.NKGeneFactory;
import jp.ac.titech.dis.cs.nk.NKLandscape;
import jp.ac.titech.dis.cs.nk.NKLandscapePath;
import repast.simphony.context.Context;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.parameter.Parameters;
import repast.simphony.space.graph.Network;

public class GroupthinkContextBuilder implements ContextBuilder<Object>{
	private final Integer DEFAULT_DEVIL_ADVOCATE_COUNT = 0;
	
	@Override
	public Context build(Context<Object> context) {
		/**
		 *  parameters :
		 *  
		    N
		    K
		    scenario_id -> range [100, 1000)
		        first digit : scenario number
		        	1 : base scenario no groupthink
		        	2 : groupthink by insulation
		        	3 : groupthink that caused by ingroup favoritism in decision making
		        	4 : groupthink that caused by concurrence seeking behavior
		        second and third digit : value of observer morality function (...00 means zero) -> indifference already covered in scenario 1
		    run -> range : 1..N
		        does not change anything, just to determine the number of run combination 
		 */
		context.setId("gt-dynamics");
		Parameters params 				= RunEnvironment.getInstance().getParameters();
		Integer scenarioId 				= params.getInteger("scenario_id");
		Integer runId 					= params.getInteger("run_id");
		Integer devilAdvocateCount		= (params.getInteger("devil_advocate_count")) == null? DEFAULT_DEVIL_ADVOCATE_COUNT:(params.getInteger("devil_advocate_count"));
		Integer N 						= params.getInteger("n");
		Integer K 						= params.getInteger("k");
		Integer dmInitialLoci			= params.getInteger("dm_initial_loci");
		validateParams(scenarioId, runId, N, K, dmInitialLoci, devilAdvocateCount);

		// topology of the dependency matrix; overridable per batch
		NKLandscapePath.NeighborhoodType neighborhoodType = NKLandscapePath.NeighborhoodType
				.getFromString(resolve(params, "neighborhood", "gt.dynamics.neighborhood", "adj"));
		Integer observerCount			= 40;
		Integer decisionMakerCount		= 9 - devilAdvocateCount;
		// directories are resolved at runtime: optional Repast parameter, then JVM
		// system property, then a path relative to the working directory. Nothing
		// is tied to one machine's home directory any more.
		String landscapeFilePrefix 		= withSeparator(resolve(params, "landscape_dir", "gt.dynamics.landscape.dir", "landscape"));
		String opinionFilePrefix 		= withSeparator(resolve(params, "opinion_dir", "gt.dynamics.opinion.dir", "initial-loci"));
		String networkFilePath 			= resolve(params, "network_file", "gt.dynamics.network.file",
											"network" + java.io.File.separator + "gt-dynamics-network-20190218.json");

		Long simulationId	= System.currentTimeMillis();
		System.out.printf("*** starting run : %d***\n", runId);
		System.out.printf("Params N(%d), K(%d), scenario id(%d)\n", N, K, scenarioId);
		NetworkBuilder<Object> agentNetworkBuilder = new NetworkBuilder<Object>("agent_network", context, true);
		Network<Object> agentNetwork = agentNetworkBuilder.buildNetwork();
		
		List<DecisionMakerAgent> decisionMakers = new ArrayList<DecisionMakerAgent>();
		List<ObserverAgent> observers = new ArrayList<ObserverAgent>();
		List<DevilAdvocateAgent> devilAdvocates = new ArrayList<DevilAdvocateAgent>();
		
		NKLandscapePath nkLandscapePath = new NKLandscapePath(landscapeFilePrefix, N, K, neighborhoodType, runId);
		NKComponent nkComponent = new NKComponent(new NKComponent.Config(N,K,nkLandscapePath.toString()));
		NKLandscape<Boolean> nkLandscape = nkComponent.getNkLandscape();
		NKGeneFactory<Boolean> nkGeneFactory = nkComponent.getNkGeneFactory();
		InitialOpinionSetFactory initialOpinionSetFactory = new InitialOpinionSetFactory();
		
		InitialOpinionSet initialOpinionSet = initialOpinionSetFactory.load(new InitialOpinionSetFilePath(opinionFilePrefix, runId, N));
		ObserverAgentFactory observerAgentFactory = new ObserverAgentFactory(nkGeneFactory, N, K, agentNetwork, context, initialOpinionSet);
		DecisionMakerAgentFactory decisionMakerAgentFactory = new DecisionMakerAgentFactory(nkGeneFactory, nkLandscape, N, K, agentNetwork, context, initialOpinionSet, dmInitialLoci);
		DevilAdvocateAgentFactory devilAdvocateAgentFactory = new DevilAdvocateAgentFactory(nkGeneFactory, N, K, agentNetwork, context, initialOpinionSet);
		
		Integer scenario = scenarioId / 100;
		switch(scenario){
			case 1 : {
				System.out.printf("[base scenario] \n");
				// instantiate all groups
				observers = observerAgentFactory.get(AgentConstants.INDIFFERENT_MORALITY_FUNCTION, observerCount);
				decisionMakers = decisionMakerAgentFactory.get(AgentConstants.INDIFFERENT_MORALITY_FUNCTION, decisionMakerCount);
				devilAdvocates = devilAdvocateAgentFactory.get(devilAdvocateCount);
				break;
			}
			case 2 : {
				System.out.printf("[groupthink by insulation] \n");
				// instantiate all groups but observers
				observerCount = 0; // override to 0
				observers = observerAgentFactory.get(AgentConstants.INDIFFERENT_MORALITY_FUNCTION, observerCount);
				decisionMakers = decisionMakerAgentFactory.get(AgentConstants.INDIFFERENT_MORALITY_FUNCTION, decisionMakerCount);
				devilAdvocates = devilAdvocateAgentFactory.get(devilAdvocateCount);
				break;
			}
			case 3 : {
				System.out.printf("[groupthink by ingroup favoritism] \n");
				Integer outgroupScore = scenarioId % 100;
				Double outgroupMoralityScore = outgroupScore.doubleValue() / 100;
				
				// instantiate all groups
				observers = observerAgentFactory.get(AgentConstants.INDIFFERENT_MORALITY_FUNCTION, observerCount);
				decisionMakers = decisionMakerAgentFactory.get(new MoralityFunction()
						.setMoralityScore(AgentConstants.DECISION_MAKER_GROUP_ID, 1.0)
						.setMoralityScore(AgentConstants.DEVIL_ADVOCATE_GROUP_ID, 1.0)
						.setMoralityScore(AgentConstants.OBSERVER_GROUP_ID, outgroupMoralityScore), decisionMakerCount);
				devilAdvocates = devilAdvocateAgentFactory.get(devilAdvocateCount);
				break;
			}
			case 4 : {
				System.out.printf("[groupthink by concurrence seeking to majority]\n");
				Integer degreeOfConcurrenceSeekingScore = scenarioId % 100;
				Double degreeOfConcurrenceSeeking = degreeOfConcurrenceSeekingScore.doubleValue() / 100;
				
				// instantiate all groups
				observers = observerAgentFactory.get(AgentConstants.INDIFFERENT_MORALITY_FUNCTION, observerCount);
				decisionMakers = decisionMakerAgentFactory.get(AgentConstants.INDIFFERENT_MORALITY_FUNCTION, degreeOfConcurrenceSeeking, decisionMakerCount);
				devilAdvocates = devilAdvocateAgentFactory.get(devilAdvocateCount);
				break;
			}
			default : {
				throw new IllegalStateException("unknown scenario : "+scenario);
			}
		}
		// instantiate metrics agent
		AggregatedMetricsAgent metricsAgent = new AggregatedMetricsAgent(
				observers, decisionMakers, devilAdvocates,
				N, K,
				scenarioId, simulationId, runId, 
				decisionMakerCount, devilAdvocateCount, observerCount, dmInitialLoci);
		context.add(metricsAgent);
		// instantiate controller
		GroupthinkController gtDynamicsController = new GroupthinkController(observers, decisionMakers, devilAdvocates, metricsAgent);
		context.add(gtDynamicsController);
		// instantiate network
		AgentNetworkLoader networkLoader = new AgentNetworkLoader(
				devilAdvocates, decisionMakers, observers, agentNetwork, networkFilePath
			);
		networkLoader.load();
		return context;
	}
	
	/**
	 * Resolve a configurable path. Order: optional Repast batch parameter (so a
	 * batch config can redirect output without code changes), then a JVM system
	 * property, then a fallback relative to the working directory.
	 */
	private static String resolve(Parameters params, String parameterName, String systemProperty, String fallback) {
		if(params != null) {
			try {
				String value = params.getString(parameterName);
				if(value != null && !value.trim().isEmpty()) {
					return value.trim();
				}
			} catch (RuntimeException notDefined) {
				// parameter absent from this run's schema; fall through
			}
		}
		String property = System.getProperty(systemProperty);
		if(property != null && !property.trim().isEmpty()) {
			return property.trim();
		}
		return fallback;
	}

	private static String withSeparator(String path) {
		if(path.isEmpty() || path.endsWith("/") || path.endsWith("\\")) {
			return path;
		}
		return path + java.io.File.separator;
	}

	/**
	 * Fail fast with a message naming the parameter, rather than an
	 * {@link NullPointerException} deep inside a scenario branch.
	 */
	private void validateParams(Integer scenarioId, Integer runId, Integer N, Integer K,
			Integer dmInitialLoci, Integer devilAdvocateCount) {
		if(scenarioId == null) {
			throw new IllegalStateException("parameter 'scenario_id' is required");
		}
		if(runId == null) {
			throw new IllegalStateException("parameter 'run_id' is required");
		}
		if(N == null || N < 1) {
			throw new IllegalStateException("parameter 'n' must be a positive number, got " + N);
		}
		if(K == null || K < 0 || K >= N) {
			throw new IllegalStateException("parameter 'k' must satisfy 0 <= k < n, got k=" + K + " n=" + N);
		}
		if(dmInitialLoci == null || dmInitialLoci < 0 || dmInitialLoci > N) {
			throw new IllegalStateException(
					"parameter 'dm_initial_loci' must be between 0 and n, got " + dmInitialLoci + " for n=" + N);
		}
		if(devilAdvocateCount == null || devilAdvocateCount < 0 || devilAdvocateCount >= 9) {
			throw new IllegalStateException(
					"parameter 'devil_advocate_count' must be between 0 and 8 (9 agents total), got "
							+ devilAdvocateCount);
		}
	}

	private class ObserverAgentFactory{
		private Integer counter = 0;
		private final NKGeneFactory<Boolean> nkGeneFactory;
		private final Integer N;
		private final Integer K;
		private final Network<Object> agentNetwork;
		private final Context<Object> context;
		private final InitialOpinionSet initialOpinionSet;
		
		public ObserverAgentFactory(NKGeneFactory<Boolean> nkGeneFactory, Integer N, Integer K, Network<Object> agentNetwork, Context<Object> context, InitialOpinionSet initialOpinionSet){
			this.nkGeneFactory = nkGeneFactory;
			this.N = N;
			this.K = K;
			this.agentNetwork = agentNetwork;
			this.context = context;
			this.initialOpinionSet = initialOpinionSet;
		}
		
		public ObserverAgent get(MoralityFunction moralityFunction) {
			this.counter++;
			String agentId = "OB"+this.counter;
			ObserverAgent agent = new ObserverAgent(
					agentId,
					this.nkGeneFactory.generateByLociString(this.initialOpinionSet.getObserversOpinion().get(agentId), N, K),
					moralityFunction,
					this.nkGeneFactory,
					this.agentNetwork
					);
			this.context.add(agent);
			return agent;
		}
		
		public List<ObserverAgent> get(MoralityFunction moralityFunction, Integer count){
			List<ObserverAgent> result = new ArrayList<ObserverAgent>();
			for(int i = 0; i < count; i++) {
				result.add(this.get(moralityFunction));
			}
			return result;
		}
	}
	
	private class DecisionMakerAgentFactory{
		private Integer counter = 0;
		private final NKGeneFactory<Boolean> nkGeneFactory;
		private final NKLandscape<Boolean> nkLandscape;
		private final Integer N;
		private final Integer K;
		private final Network<Object> agentNetwork;
		private final Context<Object> context;
		private final InitialOpinionSet initialOpinionSet;
		private final Integer dmInitialLoci;
		
		public DecisionMakerAgentFactory(NKGeneFactory<Boolean> nkGeneFactory, NKLandscape<Boolean> nkLandscape,
										Integer N, Integer K,
										Network<Object> agentNetwork, Context<Object> context,
										InitialOpinionSet initialOpinionSet, Integer dmInitialLoci) {
			this.nkGeneFactory = nkGeneFactory;
			this.nkLandscape = nkLandscape;
			this.N= N;
			this.K = K;
			this.agentNetwork = agentNetwork;
			this.context = context;
			this.initialOpinionSet = initialOpinionSet;
			this.dmInitialLoci = dmInitialLoci;
		}
		
		public DecisionMakerAgent get(MoralityFunction moralityFunction) {
			this.counter++;
			String agentId = "DM" + this.counter;
			List<Boolean> initialLoci = this.initialOpinionSet.getDecisionMakersOpinion().get(agentId);
			List<Boolean> lowestFitnessLoci = this.nkLandscape.getLowestFitnessGene().getInternalComponents();
			for(int iter=0; iter<this.dmInitialLoci; iter++) {
				initialLoci.set(iter, lowestFitnessLoci.get(iter));
			}
			DecisionMakerAgent agent = new DecisionMakerAgent(
					agentId,
					this.nkGeneFactory.generateByLociString(initialLoci, N, K),
					moralityFunction,
					this.nkGeneFactory,
					this.agentNetwork
					);
			this.context.add(agent);
			return agent;
		}
		
		public DecisionMakerAgent get(MoralityFunction moralityFunction, Double degreeOfConcurrenceSeeking) {
			this.counter++;
			String agentId = "DM" + this.counter; 
			List<Boolean> initialLoci = this.initialOpinionSet.getDecisionMakersOpinion().get(agentId);
			List<Boolean> lowestFitnessLoci = this.nkLandscape.getLowestFitnessGene().getInternalComponents();
			for(int iter=0; iter<this.dmInitialLoci; iter++) {
				initialLoci.set(iter, lowestFitnessLoci.get(iter));
			}
			DecisionMakerAgent agent = new DecisionMakerAgent(
					agentId,
					this.nkGeneFactory.generateByLociString(initialLoci, N, K),
					moralityFunction,
					degreeOfConcurrenceSeeking,
					this.nkGeneFactory,
					this.agentNetwork
					);
			this.context.add(agent);
			return agent;
		}
		
		public List<DecisionMakerAgent> get(MoralityFunction moralityFunction, Integer count){
			List<DecisionMakerAgent> result = new ArrayList<DecisionMakerAgent>();
			for(int i = 0; i < count; i++) {
				result.add(this.get(moralityFunction));
			}
			return result;
		}
		
		public List<DecisionMakerAgent> get(MoralityFunction moralityFunction, Double degreeOfConcurrenceSeeking, Integer count){
			List<DecisionMakerAgent> result = new ArrayList<DecisionMakerAgent>();
			for(int i = 0; i < count; i++) {
				result.add(this.get(moralityFunction, degreeOfConcurrenceSeeking));
			}
			return result;
		}
	}
	
	private class DevilAdvocateAgentFactory{
		private Integer counter = 0;
		private final NKGeneFactory<Boolean> nkGeneFactory;
		private final Integer N;
		private final Integer K;
		private final Network<Object> agentNetwork;
		private final Context<Object> context;
		private final InitialOpinionSet initialOpinionSet;
		
		public DevilAdvocateAgentFactory(NKGeneFactory<Boolean> nkGeneFactory, Integer n, Integer k,
				Network<Object> agentNetwork, Context<Object> context, InitialOpinionSet initialOpinionSet) {
			super();
			this.nkGeneFactory = nkGeneFactory;
			N = n;
			K = k;
			this.agentNetwork = agentNetwork;
			this.context = context;
			this.initialOpinionSet = initialOpinionSet;
		}
		
		public DevilAdvocateAgent get() {
			this.counter++;
			String agentId = "DV"+this.counter;
			DevilAdvocateAgent agent = new DevilAdvocateAgent(
					agentId,
					0, // always set 0
					this.nkGeneFactory.generateByLociString(this.initialOpinionSet.getDevilAdvocatesOpinion().get(agentId), N, K),
					this.nkGeneFactory,
					this.agentNetwork
					);
			this.context.add(agent);
			return agent;
		}
		
		public List<DevilAdvocateAgent> get(Integer count){
			List<DevilAdvocateAgent> result = new ArrayList<DevilAdvocateAgent>();
			for(int i = 0; i < count; i++) {
				result.add(this.get());
			}
			return result;
		}
	}
}
