package jp.ac.titech.dis.cs.agent;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import repast.simphony.context.Context;
import repast.simphony.context.DefaultContext;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.space.graph.Network;

/**
 * Tests for the Repast {@link Network} wiring the agents rely on.
 *
 * <p>
 * The original suite walked the adjacent nodes and printed them, asserting
 * nothing: a network that returned the wrong neighbours, or none at all, would
 * have passed.
 * </p>
 */
public class TestAgentNetwork {
	private final Network<Object> network;

	public TestAgentNetwork(){
		Context<Object> context = new DefaultContext<Object>();
		NetworkBuilder<Object> netBuilder = new NetworkBuilder<Object>("object network", context, true);
		this.network = netBuilder.buildNetwork();
	}

	@BeforeClass
	public void setUp() throws Exception {
		//no-op
	}

	@AfterClass
	public void tearDown() throws Exception {
		//no-op
	}

	@Test
	public void testCreateAndQueryNetwork(){
		// a is adjacent to 3 AElement and 2 BElement objects
		AElement a = new AElement();
		this.network.addEdge(a, new AElement());
		this.network.addEdge(a, new AElement());
		this.network.addEdge(a, new AElement());
		this.network.addEdge(a, new BElement());
		this.network.addEdge(a, new BElement());

		Set<Object> adjacent = new HashSet<Object>();
		int aCount = 0;
		int bCount = 0;
		for(Object adjacentObject : this.network.getAdjacent(a)){
			BaseElement baseElement = (BaseElement) adjacentObject;
			adjacent.add(baseElement);
			if(baseElement instanceof AElement) {
				aCount++;
			} else if(baseElement instanceof BElement) {
				bCount++;
			}
		}
		assertEquals(adjacent.size(), 5, "a must have exactly five neighbours");
		assertEquals(aCount, 3, "three of a's neighbours are AElement");
		assertEquals(bCount, 2, "two of a's neighbours are BElement");
	}

	/**
	 * Nodes handed to {@code addEdge} must be registered in the network: agents
	 * call {@code getAdjacent(this)} on every interaction, and Repast returns an
	 * error for a node the network does not know about.
	 */
	@Test
	public void testNodesAddedAreRegistered(){
		Context<Object> context = new DefaultContext<Object>();
		Network<Object> net = new NetworkBuilder<Object>("registration network", context, true).buildNetwork();
		AElement a = new AElement();
		BElement b = new BElement();
		net.addEdge(a, b);
		Set<Object> nodes = new HashSet<Object>();
		for(Object node : net.getNodes()) {
			nodes.add(node);
		}
		assertTrue(nodes.contains(a), "the first endpoint must be registered");
		assertTrue(nodes.contains(b), "the second endpoint must be registered");
		assertEquals(nodes.size(), 2, "exactly the two wired nodes belong to the network");
	}

	public static abstract class BaseElement{
		protected final Long timestamp;
		public BaseElement(){
			this.timestamp = System.currentTimeMillis();
		}
	}

	public static class AElement extends BaseElement{

		@Override
		public String toString() {
			return "AElement : "+this.timestamp;
		}
	}
	public static class BElement extends BaseElement{
		@Override
		public String toString() {
			return "BElement : "+this.timestamp;
		}
	}
}
