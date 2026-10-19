package jp.ac.titech.dis.cs.simulation;

import java.util.List;
import java.util.Map;

public class InitialOpinionSet {
	private Map<String, List<Boolean>> decisionMakersOpinion;
	private Map<String, List<Boolean>> observersOpinion;
	private Map<String, List<Boolean>> devilAdvocatesOpinion;
	
	public InitialOpinionSet(Map<String, List<Boolean>> decisionMakersOpinion,
			Map<String, List<Boolean>> observersOpinion,
			Map<String, List<Boolean>> devilAdvocatesOpinion) {
		super();
		this.decisionMakersOpinion = decisionMakersOpinion;
		this.observersOpinion = observersOpinion;
		this.devilAdvocatesOpinion = devilAdvocatesOpinion;
	}
	public InitialOpinionSet() {
		super();
	}
	public Map<String, List<Boolean>> getDecisionMakersOpinion() {
		return decisionMakersOpinion;
	}
	public void setDecisionMakersOpinion(Map<String, List<Boolean>> decisionMakersOpinion) {
		this.decisionMakersOpinion = decisionMakersOpinion;
	}
	public Map<String, List<Boolean>> getObserversOpinion() {
		return observersOpinion;
	}
	public void setObserversOpinion(Map<String, List<Boolean>> observersOpinion) {
		this.observersOpinion = observersOpinion;
	}
	public Map<String, List<Boolean>> getDevilAdvocatesOpinion() {
		return devilAdvocatesOpinion;
	}
	public void setDevilAdvocatesOpinion(Map<String, List<Boolean>> devilAdvocatesOpinion) {
		this.devilAdvocatesOpinion = devilAdvocatesOpinion;
	}
	
}
