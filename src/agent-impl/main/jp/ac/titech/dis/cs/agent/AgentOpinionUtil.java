package jp.ac.titech.dis.cs.agent;

import java.util.ArrayList;
import java.util.List;

import jp.ac.titech.dis.cs.nk.NKGene;

public abstract class AgentOpinionUtil {
	public static List<Boolean> getHighestOccurrenceLoci(List<GetOpinionResult> opinions){
		return getHighestOccurrenceLoci(opinions, null);
	}
	
	/**
	 * 
	 * @param opinions
	 * @param groupId filtering group id, null means take into account all opinions
	 * @return
	 */
	public static List<Boolean> getHighestOccurrenceLoci(List<GetOpinionResult> opinions, Integer groupId){
		if(opinions.isEmpty()) {
			throw new IllegalStateException("can't collect from empty opinion list");
		}
		NKGene<Boolean> firstOpinion = opinions.get(0).getOpinion();
		Integer N = firstOpinion.getN();
		// initializations
		Integer consideredOpinionCount = 0;
		List<Boolean> result = new ArrayList<Boolean>();
		List<Integer> trueCounter = new ArrayList<Integer>();
		// initialize 'true' counter
		for(int iter = 0; iter < N; iter++) {
			trueCounter.add(0);
		}
		// count occurence of 'true' locus
		for(GetOpinionResult opinionResult: opinions) {
			if(groupId != null && !opinionResult.getGroupId().equals(groupId)) {
				// no-op, skip this opinion
			}else {
				consideredOpinionCount++;
				// take into account
				for(int iter = 0; iter < N; iter++) {
					if(opinionResult.getOpinion().getInternalComponents().get(iter)) {
						trueCounter.set(iter, trueCounter.get(iter) + 1);
					}
				}	
			}
		}
		// set the value of result
		for(int iter=0; iter<N; iter++) {
			if(trueCounter.get(iter) >= consideredOpinionCount/2) {
				result.add(true);
			}else {
				result.add(false);
			}
		}
		return result;
	}
}
