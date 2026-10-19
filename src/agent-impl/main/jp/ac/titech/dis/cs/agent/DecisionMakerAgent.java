package jp.ac.titech.dis.cs.agent;

import jp.ac.titech.dis.cs.nk.NKGene;
import jp.ac.titech.dis.cs.nk.NKGeneFactory;
import repast.simphony.space.graph.Network;

public class DecisionMakerAgent extends DiscussionAgent{

	public DecisionMakerAgent(String agentId, NKGene<Boolean> initialOpinion, MoralityFunction moralityFunction, NKGeneFactory<Boolean> nkGeneFactory, Network<Object> agentNetwork) {
		super(initialOpinion, AgentConstants.DECISION_MAKER_GROUP_ID, agentId, moralityFunction, nkGeneFactory, agentNetwork);
	}
	
	public DecisionMakerAgent(String agentId, NKGene<Boolean> initialOpinion, MoralityFunction moralityFunction, Double degreeOfConcurrenceSeeking, NKGeneFactory<Boolean> nkGeneFactory, Network<Object> agentNetwork) {
		super(initialOpinion, AgentConstants.DECISION_MAKER_GROUP_ID, agentId, moralityFunction, degreeOfConcurrenceSeeking, nkGeneFactory, agentNetwork);
	}
}
