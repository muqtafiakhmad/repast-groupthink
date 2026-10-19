package jp.ac.titech.dis.cs.agent;

import jp.ac.titech.dis.cs.nk.NKGene;
import jp.ac.titech.dis.cs.nk.NKGeneFactory;
import repast.simphony.space.graph.Network;

public class ObserverAgent extends DiscussionAgent{

	public ObserverAgent(String agentId, NKGene<Boolean> initialOpinion, MoralityFunction moralityFunction, NKGeneFactory<Boolean> nkGeneFactory, Network<Object> agentNetwork) {
		super(initialOpinion, AgentConstants.OBSERVER_GROUP_ID, agentId, moralityFunction, nkGeneFactory, agentNetwork);
	}

}
