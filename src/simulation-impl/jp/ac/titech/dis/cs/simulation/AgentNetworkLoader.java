package jp.ac.titech.dis.cs.simulation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import jp.ac.titech.dis.cs.agent.DecisionMakerAgent;
import jp.ac.titech.dis.cs.agent.DevilAdvocateAgent;
import jp.ac.titech.dis.cs.agent.GroupthinkBaseAgent;
import jp.ac.titech.dis.cs.agent.ObserverAgent;
import repast.simphony.space.graph.Network;

public class AgentNetworkLoader {
	private final List<DevilAdvocateAgent> devilAdvocates;
	private final List<DecisionMakerAgent> decisionMakers;
	private final List<ObserverAgent> observerAgents;
	private final Network<Object> agentNetwork;
	private final String networkFilePath;
	public AgentNetworkLoader(List<DevilAdvocateAgent> devilAdvocates, List<DecisionMakerAgent> decisionMakers,
			List<ObserverAgent> observerAgents, Network<Object> agentNetwork, String networkFilePath) {
		super();
		if(devilAdvocates == null || decisionMakers == null || observerAgents == null) {
			throw new IllegalArgumentException("agent groups must not be null");
		}
		if(agentNetwork == null) {
			throw new IllegalArgumentException("agent network must not be null");
		}
		if(networkFilePath == null || networkFilePath.trim().isEmpty()) {
			throw new IllegalArgumentException("network file path must not be empty");
		}
		this.devilAdvocates = devilAdvocates;
		this.decisionMakers = decisionMakers;
		this.observerAgents = observerAgents;
		this.agentNetwork = agentNetwork;
		this.networkFilePath = networkFilePath;
	}
	public void load() {
		try {
			byte[] jsonData = Files.readAllBytes(Paths.get(this.networkFilePath));
			ObjectMapper objectMapper = new ObjectMapper();
			NetworkConfig networkConfig = objectMapper.readValue(jsonData, NetworkConfig.class);
			// an absent m/n/positions field would otherwise surface as a bare NPE
			validate(networkConfig);
			// build matrix m X n
			GroupthinkBaseAgent[][] agentMatrice = new GroupthinkBaseAgent[networkConfig.getM()][networkConfig.getN()];
			// put agents to matrices
			putAgentsToMatrices(agentMatrice,new ArrayList<GroupthinkBaseAgent>(this.observerAgents),networkConfig.observers);
			putAgentsToMatrices(agentMatrice,new ArrayList<GroupthinkBaseAgent>(this.devilAdvocates),networkConfig.devilAdvocateCandidates);
			putAgentsToMatrices(agentMatrice,new ArrayList<GroupthinkBaseAgent>(this.decisionMakers),networkConfig.decisionMakers);
			// create edges
			for(Link link:networkConfig.getLinks()) {
				List<Integer> source = link.getSource();
				List<Integer> target = link.getTarget();
				GroupthinkBaseAgent agentSource = agentAt(agentMatrice, source);
				GroupthinkBaseAgent agentTarget = agentAt(agentMatrice, target);
				if(agentSource != null && agentTarget != null) {
					this.agentNetwork.addEdge(agentSource, agentTarget);
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("could not read network configuration from "
					+ this.networkFilePath, e);
		}
	}

	/**
	 * Look up the agent placed at the given {@code [row, column]} coordinate,
	 * reporting the offending coordinate instead of a bare array exception.
	 */
	private static GroupthinkBaseAgent agentAt(GroupthinkBaseAgent[][] matrix, List<Integer> position) {
		if(position == null || position.size() != 2) {
			throw new IllegalStateException(
					"a node position must be a [row, column] pair, got " + position);
		}
		int row = position.get(0);
		int column = position.get(1);
		if(row < 0 || row >= matrix.length || column < 0 || column >= matrix[row].length) {
			throw new IllegalStateException("node position [" + row + ", " + column
					+ "] lies outside the " + matrix.length + "x" + matrix[0].length + " layout");
		}
		return matrix[row][column];
	}

	/**
	 * Every section the loader needs must be present: a missing field otherwise
	 * surfaces as a bare {@link NullPointerException}.
	 */
	private void validate(NetworkConfig networkConfig) {
		if(networkConfig.getM() == null || networkConfig.getM() < 1) {
			throw new IllegalStateException("network configuration " + this.networkFilePath
					+ " has no valid 'm' (row count)");
		}
		if(networkConfig.getN() == null || networkConfig.getN() < 1) {
			throw new IllegalStateException("network configuration " + this.networkFilePath
					+ " has no valid 'n' (column count)");
		}
		if(networkConfig.getLinks() == null) {
			throw new IllegalStateException("network configuration " + this.networkFilePath
					+ " has no 'links' section");
		}
		requirePositions(networkConfig, networkConfig.getDecisionMakers(), "decisionMakers");
		requirePositions(networkConfig, networkConfig.getObservers(), "observers");
		requirePositions(networkConfig, networkConfig.getDevilAdvocateCandidates(), "devilAdvocateCandidates");
	}

	private void requirePositions(NetworkConfig networkConfig, List<List<Integer>> positions, String name) {
		if(positions == null) {
			throw new IllegalStateException("network configuration " + this.networkFilePath
					+ " has no '" + name + "' section");
		}
		int available = positions.size();
		int required = name.equals("decisionMakers") ? this.decisionMakers.size()
				: name.equals("observers") ? this.observerAgents.size() : this.devilAdvocates.size();
		if(available < required) {
			throw new IllegalStateException("network configuration " + this.networkFilePath
					+ " reserves " + available + " grid cells for '" + name
					+ "' but " + required + " agents were built");
		}
	}
	
//	private void printMatrices(GroupthinkBaseAgent[][] agentMatrice, int m, int n) {
//		for(int i=0; i<m; i++) {
//			for(int j=0; j<m; j++) {
//				GroupthinkBaseAgent agent = agentMatrice[i][j];
//				if(agent == null) {
//					System.out.printf("\t []");
//				}else {
//					System.out.printf("\t %s", agent.getAgentId());
//				}
//			}
//			System.out.printf("\n");
//		}
//	}
	
//	private void printAdjacency() {
//		for(DecisionMakerAgent agent: this.decisionMakers) {
//			printAgentAdjacency(agent);
//		}
//		for(ObserverAgent agent: this.observerAgents) {
//			printAgentAdjacency(agent);
//		}
//		for(DevilAdvocateAgent agent: this.devilAdvocates) {
//			printAgentAdjacency(agent);
//		}
//	}
	
//	private void printAgentAdjacency(GroupthinkBaseAgent agent) {
//		StringBuffer buffer = new StringBuffer();
//		buffer.append(agent.getAgentId()).append(":");
//		Integer counter = 0;
//		for(Object neighbor: this.agentNetwork.getAdjacent(agent)) {
//			buffer.append(((GroupthinkBaseAgent) neighbor).getAgentId());
//			buffer.append(" ");
//			counter++;
//		}
//		buffer.append("(").append(counter).append(")");
//		System.out.printf("%s\n", buffer.toString());
//	}
	
	private void putAgentsToMatrices(GroupthinkBaseAgent[][] matrice, List<GroupthinkBaseAgent> agents, List<List<Integer>> positions) {
		int positionIter = 0;
		for(GroupthinkBaseAgent agent:agents) {
			// find the next reserved cell that is still free
			while(true) {
				if(positionIter >= positions.size()) {
					throw new IllegalStateException("ran out of reserved grid cells for agent "
							+ agent.getAgentId() + ": " + positions.size() + " cells for "
							+ agents.size() + " agents");
				}
				List<Integer> position = positions.get(positionIter);
				agentAt(matrice, position); // bounds check with a readable message
				if(matrice[position.get(0)][position.get(1)] == null) {
					break;
				}
				positionIter++;
			}
			List<Integer> position = positions.get(positionIter);
			// assign agent to matrice
			matrice[position.get(0)][position.get(1)] = agent;
		}
	}
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class NetworkConfig{
		private List<Node> nodes;
		private List<Link> links;
		private List<List<Integer>> decisionMakers;
		private List<List<Integer>> observers;
		private List<List<Integer>> devilAdvocateCandidates;
		private Integer m;
		private Integer n;
		
		public NetworkConfig() {
			super();
		}
		public List<Node> getNodes() {
			return nodes;
		}
		public void setNodes(List<Node> nodes) {
			this.nodes = nodes;
		}
		public List<Link> getLinks() {
			return links;
		}
		public void setLinks(List<Link> links) {
			this.links = links;
		}
		public List<List<Integer>> getDecisionMakers() {
			return decisionMakers;
		}
		public void setDecisionMakers(List<List<Integer>> decisionMakers) {
			this.decisionMakers = decisionMakers;
		}
		public List<List<Integer>> getObservers() {
			return observers;
		}
		public void setObservers(List<List<Integer>> observers) {
			this.observers = observers;
		}
		public List<List<Integer>> getDevilAdvocateCandidates() {
			return devilAdvocateCandidates;
		}
		public void setDevilAdvocateCandidates(List<List<Integer>> devilAdvocateCandidates) {
			this.devilAdvocateCandidates = devilAdvocateCandidates;
		}
		public Integer getM() {
			return m;
		}
		public void setM(Integer m) {
			this.m = m;
		}
		public Integer getN() {
			return n;
		}
		public void setN(Integer n) {
			this.n = n;
		}
	}
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Link{
		private List<Integer> source;
		private List<Integer> target;
		
		public Link() {
			super();
		}
		public List<Integer> getSource() {
			return source;
		}
		public void setSource(List<Integer> source) {
			this.source = source;
		}
		public List<Integer> getTarget() {
			return target;
		}
		public void setTarget(List<Integer> target) {
			this.target = target;
		}
		
	}
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Node{
		private List<Integer> id;
		
		public Node() {
			super();
		}

		public List<Integer> getId() {
			return id;
		}

		public void setId(List<Integer> id) {
			this.id = id;
		}
		
	}
}
