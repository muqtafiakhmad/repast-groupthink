package jp.ac.titech.dis.cs.agent;

import java.util.ArrayList;
import java.util.List;

import jp.ac.titech.dis.cs.nk.NKGene;
import jp.ac.titech.dis.cs.nk.NKGeneFactory;
import repast.simphony.space.graph.Network;

public class DevilAdvocateAgent extends GroupthinkBaseAgent{
	private final Integer overlappingLociCount;
	public DevilAdvocateAgent(String agentId, Integer overlappingLociCount, NKGene<Boolean> initialOpinion,
			NKGeneFactory<Boolean> nkGeneFactory, Network<Object> agentNetwork) {
		super(initialOpinion, AgentConstants.DEVIL_ADVOCATE_GROUP_ID, agentId, nkGeneFactory, agentNetwork);
		if(overlappingLociCount == null || overlappingLociCount < 0) {
			throw new IllegalArgumentException(
					"overlapping loci count must not be negative, got " + overlappingLociCount);
		}
		if(agentId == null) {
			throw new IllegalArgumentException("agent id must not be null");
		}
		if(initialOpinion == null) {
			throw new IllegalArgumentException("initial opinion must not be null");
		}
		if(overlappingLociCount >= initialOpinion.getN()) {
			throw new IllegalArgumentException("overlapping loci count " + overlappingLociCount
					+ " must be smaller than N=" + initialOpinion.getN());
		}
		this.overlappingLociCount = overlappingLociCount;
	}
	
	@Override
	public void interact() {
		// accumulate decision maker agent opinion
		Iterable<Object> neighborAgentsIterable = this.agentNetwork.getAdjacent(this);
		List<GetOpinionResult> opinions = new ArrayList<GetOpinionResult>();
		for(Object neighborAgentPointer: neighborAgentsIterable) {
			GroupthinkBaseAgent neighborAgent = (GroupthinkBaseAgent) neighborAgentPointer;
			GetOpinionResult neighborOpinion = neighborAgent.getOpinion();
			// collect opinion if decision maker
			if(neighborAgent.getGroupId().equals(AgentConstants.DECISION_MAKER_GROUP_ID)) {
				opinions.add(neighborOpinion);
			}
		}
		if(opinions.isEmpty()) {
			// no decision maker in range: this advocate has nothing to challenge and
			// keeps its opinion for this round
			return;
		}else {
			// if there is any opinion with higher fitness then try to challenge
			GetOpinionResult firstOpinion = opinions.get(0);
			Integer N = firstOpinion.getOpinion().getN();
			Integer K = firstOpinion.getOpinion().getK();
			List<Boolean> dvOpinionLoci = new ArrayList<Boolean>();
			
			// sanity check: an out-of-range value would index past the loci list below
			if(this.overlappingLociCount < 0 || this.overlappingLociCount >= N) {
				throw new IllegalStateException(this.getAgentId()
						+ " has an illegal overlapping loci count " + this.overlappingLociCount
						+ "; it must be between 0 and " + (N - 1) + " for N=" + N);
			}
			// calculate most common opinion
			List<Boolean> mostCommonLoci = AgentOpinionUtil.getHighestOccurrenceLoci(opinions); 
			// copy from most common opinion based on overlapping loci count
			for(int iter=0; iter < this.overlappingLociCount; iter++) {
				dvOpinionLoci.add(mostCommonLoci.get(iter));
			}
			// get inverse of most common opinion loci for the rest
			for(int iter=this.overlappingLociCount; iter < N; iter++) {
				if(mostCommonLoci.get(iter)) {
					dvOpinionLoci.add(false);
				}else {
					dvOpinionLoci.add(true);
				}
			}
			// update own's opinion
			this.opinion = this.nkGeneFactory.generateByLociString(dvOpinionLoci, N, K);
		}
	}

	public Integer getOverlappingLociCount() {
		return overlappingLociCount;
	}
}
