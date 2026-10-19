package jp.ac.titech.dis.cs.agent;

import java.util.List;

import repast.simphony.space.graph.Network;

public interface NetworkFormationBuilder{
	/**
	 * Wire the given agents into the given network and return it.
	 */
	Network<Object> configureNetwork(Network<Object> network, List<ObserverAgent> observers, List<DecisionMakerAgent> decisionMakers, List<DevilAdvocateAgent> devilAdvocates);
}
