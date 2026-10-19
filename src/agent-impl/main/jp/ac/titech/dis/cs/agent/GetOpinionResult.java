package jp.ac.titech.dis.cs.agent;

import jp.ac.titech.dis.cs.nk.NKGene;

/**
 * opinion query result returned by agent
 * intended to be immutable object 
 * @author Muqtafi Akhmad
 *
 */
public class GetOpinionResult {
	private final Integer groupId;
	private final NKGene<Boolean> opinion;
	
	public GetOpinionResult(Integer groupId, NKGene<Boolean> opinion) {
		super();
		this.groupId = groupId;
		this.opinion = opinion;
	}
	public Integer getGroupId() {
		return groupId;
	}
	public NKGene<Boolean> getOpinion() {
		return opinion;
	}
}
