package jp.ac.titech.dis.cs.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import jp.ac.titech.dis.cs.agent.DecisionMakerAgent;
import jp.ac.titech.dis.cs.agent.AggregatedMetricsAgent;
import jp.ac.titech.dis.cs.agent.DevilAdvocateAgent;
import jp.ac.titech.dis.cs.agent.ObserverAgent;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduledMethod;

/**
 * manage interaction in decision making
 * 
 * @author Muqtafi Akhmad
 *
 */
public class GroupthinkController {
	private final List<ObserverAgent> observerAgents;
	private final List<DecisionMakerAgent> decisionMakerAgents;
	private final List<DevilAdvocateAgent> devilAdvocateAgents;
	private final AggregatedMetricsAgent metricsAgent;
	private final Integer MAX_TICK_COUNT = 1000; // one thousands ticks
	private final ControlMechanism controlMechanism;
	private final Queue<DevilAdvocateAgent> devilAdvocateQueue;
	
	public static enum ControlMechanism{
		TRIGGER_ALL,
		TRIGGER_ONE
	}
	
	public GroupthinkController(List<ObserverAgent> observerAgents,
								List<DecisionMakerAgent> decisionMakerAgents,
								List<DevilAdvocateAgent> devilAdvocateAgents,
								AggregatedMetricsAgent metricsAgent){
		this.observerAgents = observerAgents;
		this.decisionMakerAgents = decisionMakerAgents;
		this.devilAdvocateAgents = devilAdvocateAgents;
		this.metricsAgent = metricsAgent;
		this.controlMechanism = ControlMechanism.TRIGGER_ONE;
		this.devilAdvocateQueue = new LinkedList<DevilAdvocateAgent>();
	}
	
	@ScheduledMethod(start = 1, interval = 1)
	public void cycle(){
		// condition to stop simulation
		if(RunEnvironment.getInstance().getCurrentSchedule().getTickCount() >= MAX_TICK_COUNT || this.decisionMakerReachConsensus()){
			RunEnvironment.getInstance().endRun();
		}else {
			// run interactions
			this.simulateInteraction();
			// collect metrics
			this.metricsAgent.collectMetrics();
		}
	}
	
	public void simulateInteraction(){
		if(this.controlMechanism.equals(ControlMechanism.TRIGGER_ALL)) {
			List<ObserverAgent> shuffledObservers = new ArrayList<ObserverAgent>(this.observerAgents);
			Collections.shuffle(shuffledObservers);
			List<DevilAdvocateAgent> shuffledDevilAdvocates = new ArrayList<DevilAdvocateAgent>(this.devilAdvocateAgents);
			Collections.shuffle(shuffledDevilAdvocates);
			List<DecisionMakerAgent> shuffledDecisionMakers = new ArrayList<DecisionMakerAgent>(this.decisionMakerAgents);
			Collections.shuffle(shuffledDecisionMakers);
			// run observer agents
			for(ObserverAgent observerAgent : shuffledObservers){
				observerAgent.interact();
			}
			// run devil advocate agents
			for(DevilAdvocateAgent devilAdvocateAgent : shuffledDevilAdvocates) {
				devilAdvocateAgent.interact();
			}
			// run decision maker agents
			for(DecisionMakerAgent decisionMakerAgent : shuffledDecisionMakers){
				decisionMakerAgent.interact();
			}			
		}else if (this.controlMechanism.equals(ControlMechanism.TRIGGER_ONE)) {
			// TODO all observer, one devil's advocate
			List<ObserverAgent> shuffledObservers = new ArrayList<ObserverAgent>(this.observerAgents);
			Collections.shuffle(shuffledObservers);
			// run observer agents
			for(ObserverAgent observerAgent : shuffledObservers){
				observerAgent.interact();
			}
			// trigger one devil advocate
			if(!this.devilAdvocateAgents.isEmpty()) {
				if(this.devilAdvocateQueue.isEmpty()) {
					List<DevilAdvocateAgent> shuffledDevilAdvocates = new ArrayList<DevilAdvocateAgent>(this.devilAdvocateAgents);
					Collections.shuffle(shuffledDevilAdvocates);
					this.devilAdvocateQueue.addAll(shuffledDevilAdvocates);
				}
				DevilAdvocateAgent triggeredDevilAdvocate = this.devilAdvocateQueue.poll();
				triggeredDevilAdvocate.interact();				
			}
			
			// trigger decision maker
			if(!this.decisionMakerAgents.isEmpty()) {
				List<DecisionMakerAgent> shuffledDecisionMakers = new ArrayList<DecisionMakerAgent>(this.decisionMakerAgents);
				Collections.shuffle(shuffledDecisionMakers);
				// find one that can learn
				for(DecisionMakerAgent decisionMaker: shuffledDecisionMakers) {
					boolean triggered = false;
					Iterable<Object> neighborAgentsIterable = decisionMaker.getAgentNetwork().getAdjacent(decisionMaker);
					for(Object iteratedObject: neighborAgentsIterable) {
						if(DecisionMakerAgent.class.isInstance(iteratedObject)) {
							DecisionMakerAgent neighborDecisionMaker = ((DecisionMakerAgent) iteratedObject);
							if(neighborDecisionMaker.getOpinionFitness() > decisionMaker.getOpinionFitness()) {
								decisionMaker.interact();
								triggered = true;
								break;
							}
						}
					}
					if(triggered) {
						break;
					}
				}
			}
		}else {
			throw new IllegalStateException("Unknown control mechanism "+this.controlMechanism);
		}
	}
	
	/**
	 * return true if decision maker already make consensus
	 * @return
	 */
	public boolean decisionMakerReachConsensus(){
		return decisionMakerReachConsensus(this.decisionMakerAgents);
	}
	
	private boolean decisionMakerReachConsensus(List<DecisionMakerAgent> decisionMakers) {
		int decisionMakerAgentCount = decisionMakers.size();
		if(decisionMakerAgentCount < 1) return true;
		// get first opinion
		List<Boolean> firstOpinion = decisionMakers.get(0).getOpinion().getOpinion().getInternalComponents();
		for(int decisionMakerIter = 1; decisionMakerIter < decisionMakerAgentCount; decisionMakerIter++){
			List<Boolean> agentOpinion = decisionMakers.get(decisionMakerIter).getOpinion().getOpinion().getInternalComponents();
			if(!agentOpinion.equals(firstOpinion)){
				// return false once one we found opinion that does not equals to first agent opinion
				return false;
			}
		}
		return true;
	}
}
