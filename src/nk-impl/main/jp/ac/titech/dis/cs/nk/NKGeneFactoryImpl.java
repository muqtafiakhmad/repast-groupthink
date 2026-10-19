package jp.ac.titech.dis.cs.nk;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Creates genes evaluated against a fixed NK landscape.
 *
 * <p>
 * The factory owns the randomness used for random genes, so callers that need a
 * repeatable run can hand it a seeded {@link Random} (see the two-argument
 * constructor).
 * </p>
 */
public class NKGeneFactoryImpl implements NKGeneFactory<Boolean>{
	private final NKLandscape<Boolean> nkLandscape;
	private final Random random;

	public NKGeneFactoryImpl(NKLandscape<Boolean> nkLandscape) {
		this(nkLandscape, new Random());
	}

	/**
	 * @param nkLandscape landscape genes are evaluated against
	 * @param random source of randomness for {@link #generateRandomGene(Integer, Integer)}
	 */
	public NKGeneFactoryImpl(NKLandscape<Boolean> nkLandscape, Random random) {
		super();
		if(nkLandscape == null) {
			throw new IllegalArgumentException("nkLandscape must not be null");
		}
		if(random == null) {
			throw new IllegalArgumentException("random must not be null");
		}
		this.nkLandscape = nkLandscape;
		this.random = random;
	}

	@Override
	public NKGene<Boolean> generateRandomGene(Integer N, Integer K) {
		List<Boolean> internalComponents = NKUtils.generateRandomGene(N, this.random);
		return new NKGene<Boolean>(internalComponents, N, K, this.nkLandscape.getFitness(internalComponents));
	}

	/**
	 * Return a copy of {@code gene} with {@code componentIndex} set to
	 * {@code component}.
	 *
	 * <p>
	 * The input gene is never touched: its loci are copied first, so the caller
	 * keeps a valid gene (correct loci <em>and</em> correct fitness) afterwards.
	 * This matters because opinions are shared by reference between agents.
	 * </p>
	 */
	@Override
	public NKGene<Boolean> mutateComponent(NKGene<Boolean> gene, Integer componentIndex, Boolean component) {
		if(gene == null) {
			throw new IllegalArgumentException("gene must not be null");
		}
		if(componentIndex == null || componentIndex < 0 || componentIndex >= gene.getN()) {
			throw new IllegalArgumentException("componentIndex must be in [0, " + gene.getN()
					+ "), got " + componentIndex);
		}
		if(component == null) {
			throw new IllegalArgumentException("component must not be null");
		}
		List<Boolean> internalComponents = gene.getInternalComponents();
		// sanity check, if no mutation do nothing and then return the same object
		if(internalComponents.get(componentIndex).equals(component)){
			return gene;
		}
		// modify a copy: the caller's gene stays intact
		List<Boolean> mutatedComponents = new ArrayList<Boolean>(internalComponents);
		mutatedComponents.set(componentIndex, component);
		return new NKGene<Boolean>(mutatedComponents, gene.getN(), gene.getK(),
				this.nkLandscape.getFitness(mutatedComponents));
	}

	/**
	 * Build a gene from explicit loci; the loci are validated against the landscape
	 * and copied.
	 *
	 * @throws IllegalArgumentException if {@code loci} does not hold exactly
	 *                                  {@code N} values, or N disagrees with the
	 *                                  landscape
	 */
	@Override
	public NKGene<Boolean> generateByLociString(List<Boolean> loci, Integer N, Integer K) {
		if(N == null || !N.equals(this.nkLandscape.getN())) {
			throw new IllegalArgumentException("Requested N=" + N
					+ " but the landscape has N=" + this.nkLandscape.getN());
		}
		if(K == null || !K.equals(this.nkLandscape.getK())) {
			throw new IllegalArgumentException("Requested K=" + K
					+ " but the landscape has K=" + this.nkLandscape.getK());
		}
		return new NKGene<Boolean>(loci, N, K, this.nkLandscape.getFitness(loci));
	}
}
