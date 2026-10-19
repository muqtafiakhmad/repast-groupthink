package jp.ac.titech.dis.cs.agent;

import jp.ac.titech.dis.cs.nk.NKGene;
import jp.ac.titech.dis.cs.nk.NKGeneFactory;
import repast.simphony.space.graph.Network;

public abstract class GroupthinkBaseAgent {
	protected NKGene<Boolean> opinion;
	protected final Integer groupId;
	protected final String agentId;
	protected final NKGeneFactory<Boolean> nkGeneFactory;
	protected final Network<Object> agentNetwork;
	
	public GroupthinkBaseAgent(NKGene<Boolean> initialOpinion, Integer groupId, String agentId, NKGeneFactory<Boolean> nkGeneFactory, Network<Object> agentNetwork) {
		this.opinion = initialOpinion;
		this.groupId = groupId;
		this.agentId = agentId;
		this.nkGeneFactory = nkGeneFactory;
		this.agentNetwork = agentNetwork;
	}
	
	/**
	 * agent interaction method, should be implemented by subclasses
	 */
	public abstract void interact();
	
	/**
	 * return agent opinion with additional information
	 * @return
	 */
	public GetOpinionResult getOpinion(){
		return new GetOpinionResult(this.groupId, this.opinion);
	}

	public Integer getGroupId() {
		return groupId;
	}

	public String getAgentId() {
		return agentId;
	}

	public Double getOpinionFitness() {
		return this.opinion.getFitness();
	}
	
	public int getN() {
		return this.opinion.getN();
	}
	
	public int getK() {
		return this.opinion.getK();
	}

	public NKGeneFactory<Boolean> getNkGeneFactory() {
		return nkGeneFactory;
	}

	public Network<Object> getAgentNetwork() {
		return agentNetwork;
	}
}
