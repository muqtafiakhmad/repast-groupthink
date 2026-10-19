package jp.ac.titech.dis.cs.agent;

public class AgentConstants {
	public static final Integer DECISION_MAKER_GROUP_ID = 1;
	public static final Integer OBSERVER_GROUP_ID = 2;
	public static final Integer DEVIL_ADVOCATE_GROUP_ID = 3;
	
	public static final Double MAX_OPINION_FITNESS = 1.00;
	public static final Double MIN_OPINION_FITNESS = 0.00;
	
	public static final MoralityFunction INDIFFERENT_MORALITY_FUNCTION =
			new MoralityFunction()
			.setMoralityScore(DECISION_MAKER_GROUP_ID, 1.0)
			.setMoralityScore(DEVIL_ADVOCATE_GROUP_ID, 1.0)
			.setMoralityScore(OBSERVER_GROUP_ID, 1.0);
}
