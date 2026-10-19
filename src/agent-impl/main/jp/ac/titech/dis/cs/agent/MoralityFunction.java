package jp.ac.titech.dis.cs.agent;

import java.util.HashMap;
import java.util.Map;

public class MoralityFunction {
	private final Map<Integer, Double> moralityScoreMap;
	
	public MoralityFunction(){
		this.moralityScoreMap =  new HashMap<Integer, Double>();
	}
	
	public MoralityFunction setMoralityScore(Integer groupId, Double moralityScore){
		this.moralityScoreMap.put(groupId, moralityScore);
		return this;
	}
	
	/**
	 * return morality score for given group id
	 * throws exception if group id not found
	 * @return
	 */
	public Double getMoralityScore(Integer groupId){
		Double moralityScore = this.moralityScoreMap.get(groupId);
		if(moralityScore != null){
			return moralityScore;
		}else{
			throw new IllegalStateException("could not return morality score for group id : "+groupId);
		}
	}
}
