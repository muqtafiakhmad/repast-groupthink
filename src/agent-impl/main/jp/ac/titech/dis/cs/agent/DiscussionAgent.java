package jp.ac.titech.dis.cs.agent;

import java.util.ArrayList;
import java.util.List;

import jp.ac.titech.dis.cs.nk.NKGene;
import jp.ac.titech.dis.cs.nk.NKGeneFactory;
import repast.simphony.space.graph.Network;

public abstract class DiscussionAgent extends GroupthinkBaseAgent{
	
	protected final MoralityFunction moralityFunction;
	protected final Double degreeOfConcurrenceSeeking;
	// TODO add param constructor
	protected final Boolean enableWalk=false;
	
	public DiscussionAgent(NKGene<Boolean> initialOpinion, Integer groupId, String agentId,
							MoralityFunction moralityFunction, Double degreeOfConcurrenceSeeking,
							NKGeneFactory<Boolean> nkGeneFactory, Network<Object> agentNetwork) {
		super(initialOpinion, groupId, agentId, nkGeneFactory, agentNetwork);
		this.moralityFunction = moralityFunction;
		this.degreeOfConcurrenceSeeking = degreeOfConcurrenceSeeking;
	}
	
	public DiscussionAgent(NKGene<Boolean> initialOpinion, Integer groupId, String agentId,
			MoralityFunction moralityFunction,
			NKGeneFactory<Boolean> nkGeneFactory, Network<Object> agentNetwork) {
		super(initialOpinion, groupId, agentId, nkGeneFactory, agentNetwork);
		this.moralityFunction = moralityFunction;
		this.degreeOfConcurrenceSeeking = 0.0;
	}
	
	/**
	 * calculate utility to adopt opinion
	 * @param opinion
	 * @param neighborOpinionList
	 * @return
	 */
	private Double calculateUtility(GetOpinionResult opinion, List<GetOpinionResult> neighborOpinionList) {	
		Integer N = opinion.getOpinion().getN();
		Integer matchingCount = 0;
		List<Boolean> mostCommonLoci = AgentOpinionUtil.getHighestOccurrenceLoci(neighborOpinionList, this.getOpinion().getGroupId());
		// calculate matching locus from opinion compared with mostCommonLoci
		for(int iter=0; iter < N; iter++) {
			if(mostCommonLoci.get(iter).equals(opinion.getOpinion().getInternalComponents().get(iter))) {
				matchingCount++;
			}
		}
		Double concurrenceSeekingPreferenceScore = (matchingCount.doubleValue() / N.doubleValue()) * this.degreeOfConcurrenceSeeking;
		Double fitnessDifference = (opinion.getOpinion().getFitness() - this.opinion.getFitness());
		Double moralityPreferenceScore = (this.moralityFunction.getMoralityScore(opinion.getGroupId()));
		return fitnessDifference * (moralityPreferenceScore + concurrenceSeekingPreferenceScore);
	}
	
	/**
	 * 
	 */
	public void interact(){
        Iterable<Object> neighborAgentsIterable = this.agentNetwork.getAdjacent(this);
        NKGene<Boolean> bestOpinion = null;
        Double bestUtility = 0.0;
        // collect neighbor opinions
        List<GetOpinionResult> neighborOpinions = new ArrayList<GetOpinionResult>();
        for(Object neighborAgentObject: neighborAgentsIterable) {
        	GroupthinkBaseAgent neighbor = (GroupthinkBaseAgent) neighborAgentObject;
        	neighborOpinions.add(neighbor.getOpinion());
        }
        // select opinion with the highest utility
        for(GetOpinionResult neighborOpinion : neighborOpinions){
        	// filter only opinion with higher fitness
        	if(neighborOpinion.getOpinion().getFitness() > this.getOpinionFitness()) {
        		// calculate expected gain
        		Double calculatedUtility = calculateUtility(neighborOpinion, neighborOpinions);
        		if(calculatedUtility > bestUtility){
        			bestUtility = calculatedUtility;
        			bestOpinion = neighborOpinion.getOpinion();
        		}
        	}else {
        		// no-op
        	}
        }
        // learn from neighbor opinion
        if(bestOpinion != null){
        	int N = this.opinion.getN();
        	List<Boolean> neighborOpinionComponents = bestOpinion.getInternalComponents();
        	List<Boolean> opinionComponents = this.opinion.getInternalComponents();
        	for(int componentIndex = 0; componentIndex < N; componentIndex++){
        		//find non matching component and mutate own opinion
        		if(!neighborOpinionComponents.get(componentIndex).equals(opinionComponents.get(componentIndex))){
        			this.opinion = this.nkGeneFactory.mutateComponent(
        					this.opinion,
        					componentIndex,
        					neighborOpinionComponents.get(componentIndex));
        			break;
        		}
        	}
        }else if(this.enableWalk){
        	NKGene<Boolean> currentOpinion = this.opinion;
        	int N = currentOpinion.getN();
        	// try to walk by itself
        	for(int iter=0; iter < N; iter++) {
        		// 'flip' iter-th element
        		Boolean locus = currentOpinion.getInternalComponents().get(iter);
        		Boolean flipLocus = !locus;
        		NKGene<Boolean> triedOpinion = this.nkGeneFactory.mutateComponent(currentOpinion, iter, flipLocus);
        		if(triedOpinion.getFitness() > currentOpinion.getFitness()) {
        			// set as current opinion and end walk
        			this.opinion = triedOpinion;
        			break;
        		}
        		
        	}
        }
	}

	public Double getDegreeOfConcurrenceSeeking() {
		return degreeOfConcurrenceSeeking;
	}
}
