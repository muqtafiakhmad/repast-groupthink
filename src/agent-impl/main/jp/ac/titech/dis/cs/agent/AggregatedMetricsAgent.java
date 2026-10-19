package jp.ac.titech.dis.cs.agent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import jp.ac.titech.dis.cs.nk.NKGene;
import jp.ac.titech.dis.cs.nk.NKUtils;

public class AggregatedMetricsAgent {
	// constant collections
	private final List<ObserverAgent> observerAgents;
	private final List<DecisionMakerAgent> decisionMakerAgents;
	private final List<DevilAdvocateAgent> devilAdvocateAgents;
	
	private final Map<String, Double> accumulatedDMOpinions;
	private final Map<String, Double> accumulatedOBOpinions;
	private final Map<String, Double> accumulatedDVOpinions;
	
	// these states are changed every time collectMetrics is invoked
	private int uniqueOBOpinionCount = 0; 
	private double maxOBOpinionFitness = 0.0;
	private double minOBOpinionFitness = 0.0;
	
	private int uniqueDMOpinionCount = 0; 
	private double maxDMOpinionFitness = 0.0;
	private double minDMOpinionFitness = 0.0;
	
	private int uniqueDVOpinionCount = 0;
	private double maxDVOpinionFitness = 0.0;
	private double minDVOpinionFitness = 0.0;
	
	private final long simulationId;
	private final int scenarioId;
	private final int N;
	private final int K;
	private final int runId;
	private final int decisionMakerCount;
	private final int devilAdvocateCount;
	private final int observerCount;
	private final int dmInitialLoci;
	
	public AggregatedMetricsAgent(List<ObserverAgent> observerAgents,
			List<DecisionMakerAgent> decisionMakerAgents,
			List<DevilAdvocateAgent> devilAdvocateAgents,
			// context data
			int N, int K, int scenarioId, long simulationId, int runId, 
			int decisionMakerCount, int devilAdvocateCount, int observerCount, int dmInitialLoci) {
		super();
		this.observerAgents = observerAgents;
		this.decisionMakerAgents = decisionMakerAgents;
		this.devilAdvocateAgents = devilAdvocateAgents;
		this.accumulatedDMOpinions = new TreeMap<String, Double>();
		this.accumulatedOBOpinions = new TreeMap<String, Double>();
		this.accumulatedDVOpinions = new TreeMap<String, Double>();
		this.N = N;
		this.K = K;
		this.scenarioId = scenarioId;
		this.simulationId = simulationId;
		this.runId = runId;
		this.devilAdvocateCount = devilAdvocateCount;
		this.decisionMakerCount = decisionMakerCount;
		this.observerCount = observerCount;
		this.dmInitialLoci = dmInitialLoci;
	}
	
	public void collectMetrics(){
		Set<String> DMopinions = new HashSet<String>();
		double OBminFitness = AgentConstants.MAX_OPINION_FITNESS;
		double OBmaxFitness = AgentConstants.MIN_OPINION_FITNESS;
		
		Set<String> OBopinions = new HashSet<String>();
		double DMminFitness = AgentConstants.MAX_OPINION_FITNESS;
		double DMmaxFitness = AgentConstants.MIN_OPINION_FITNESS;
		
		Set<String> DVopinions = new HashSet<String>();
		double DVminFitness = AgentConstants.MAX_OPINION_FITNESS;
		double DVmaxFitness = AgentConstants.MIN_OPINION_FITNESS;
		
		for(ObserverAgent observer : this.observerAgents){
			NKGene<Boolean> opinion = observer.getOpinion().getOpinion();
			// update max-min
			Double opinionFitness = opinion.getFitness();
			if(OBminFitness > opinionFitness){
				OBminFitness = opinionFitness;
			}
			if(OBmaxFitness < opinionFitness){
				OBmaxFitness = opinionFitness;
			}
			// add to unique set
			String opinionString = NKUtils.getComponentListSignature(opinion.getInternalComponents());
			OBopinions.add(opinionString);
			// add to accumulated opinion
			this.accumulatedOBOpinions.put(opinionString, opinionFitness);
		}
		
		for(DecisionMakerAgent decisionMaker : this.decisionMakerAgents){
			NKGene<Boolean> opinion = decisionMaker.getOpinion().getOpinion();
			// update max-min
			Double opinionFitness = opinion.getFitness();
			if(DMminFitness > opinionFitness){
				DMminFitness = opinionFitness;
			}
			if(DMmaxFitness < opinionFitness){
				DMmaxFitness = opinionFitness;
			}
			// add to unique set
			String opinionString = NKUtils.getComponentListSignature(opinion.getInternalComponents());
			DMopinions.add(opinionString);
			// add to accumulated opinion
			this.accumulatedDMOpinions.put(opinionString, opinionFitness);
		}
		
		for(DevilAdvocateAgent devilAdvocate : this.devilAdvocateAgents){
			NKGene<Boolean> opinion = devilAdvocate.getOpinion().getOpinion();
			// update max-min
			Double opinionFitness = opinion.getFitness();
			if(DVminFitness > opinionFitness){
				DVminFitness = opinionFitness;
			}
			if(DVmaxFitness < opinionFitness){
				DVmaxFitness = opinionFitness;
			}
			// add to unique set
			String opinionString = NKUtils.getComponentListSignature(opinion.getInternalComponents());
			DVopinions.add(opinionString);
			// add to accumulated opinion
			this.accumulatedDVOpinions.put(opinionString, opinionFitness);
		}
		
		// set variables
		this.maxOBOpinionFitness = OBmaxFitness;
		this.minOBOpinionFitness = OBminFitness;
		this.uniqueOBOpinionCount = OBopinions.size();
		
		this.maxDMOpinionFitness = DMmaxFitness;
		this.minDMOpinionFitness = DMminFitness;
		this.uniqueDMOpinionCount = DMopinions.size();
		
		this.maxDVOpinionFitness = DVmaxFitness;
		this.minDVOpinionFitness = DVminFitness;
		this.uniqueDVOpinionCount = DVopinions.size();
	}

	public int getUniqueOBOpinionCount() {
		return uniqueOBOpinionCount;
	}


	public void setUniqueOBOpinionCount(int uniqueOBOpinionCount) {
		this.uniqueOBOpinionCount = uniqueOBOpinionCount;
	}


	public double getMaxOBOpinionFitness() {
		return maxOBOpinionFitness;
	}


	public void setMaxOBOpinionFitness(double maxOBOpinionFitness) {
		this.maxOBOpinionFitness = maxOBOpinionFitness;
	}


	public double getMinOBOpinionFitness() {
		return minOBOpinionFitness;
	}


	public void setMinOBOpinionFitness(double minOBOpinionFitness) {
		this.minOBOpinionFitness = minOBOpinionFitness;
	}


	public int getUniqueDMOpinionCount() {
		return uniqueDMOpinionCount;
	}


	public void setUniqueDMOpinionCount(int uniqueDMOpinionCount) {
		this.uniqueDMOpinionCount = uniqueDMOpinionCount;
	}


	public double getMaxDMOpinionFitness() {
		return maxDMOpinionFitness;
	}


	public void setMaxDMOpinionFitness(double maxDMOpinionFitness) {
		this.maxDMOpinionFitness = maxDMOpinionFitness;
	}


	public double getMinDMOpinionFitness() {
		return minDMOpinionFitness;
	}


	public void setMinDMOpinionFitness(double minDMOpinionFitness) {
		this.minDMOpinionFitness = minDMOpinionFitness;
	}
	
	public Integer getAccumulatedUniqueDMOpinionCount(){
		return this.accumulatedDMOpinions.size();
	}
	
	public Integer getAccumulatedUniqueOBOpinionCount(){
		return this.accumulatedOBOpinions.size();
	}
	
	public Integer getAccumulatedUniqueDVOpinionCount() {
		return this.accumulatedDVOpinions.size();
	}
	
	public Integer getN(){
		return this.N;
	}
	
	public Integer getK(){
		return this.K;
	}
	
	public Integer getScenarioId(){
		return this.scenarioId;
	}
	
	public Long getSimulationId(){
		return this.simulationId;
	}
	
	public Integer getRunId(){
		return this.runId;
	}

	public int getUniqueDVOpinionCount() {
		return uniqueDVOpinionCount;
	}

	public void setUniqueDVOpinionCount(int uniqueDVOpinionCount) {
		this.uniqueDVOpinionCount = uniqueDVOpinionCount;
	}

	public double getMaxDVOpinionFitness() {
		return maxDVOpinionFitness;
	}

	public void setMaxDVOpinionFitness(double maxDVOpinionFitness) {
		this.maxDVOpinionFitness = maxDVOpinionFitness;
	}

	public double getMinDVOpinionFitness() {
		return minDVOpinionFitness;
	}

	public void setMinDVOpinionFitness(double minDVOpinionFitness) {
		this.minDVOpinionFitness = minDVOpinionFitness;
	}

	public int getDecisionMakerCount() {
		return decisionMakerCount;
	}

	public int getDevilAdvocateCount() {
		return devilAdvocateCount;
	}

	public int getObserverCount() {
		return observerCount;
	}

	public int getDmInitialLoci() {
		return dmInitialLoci;
	}
}
