package jp.ac.titech.dis.cs.nk;

import java.util.List;

/**
 * A Kauffman NK fitness landscape: a search space of N binary loci in which the
 * fitness contribution of locus i depends on locus i itself plus K other loci
 * (K+1 inputs in total), and the fitness of a gene is the mean of its N
 * characteristic fitnesses.
 *
 * <p>
 * N and K describe the landscape, not an individual query, so they are exposed
 * through {@link #getN()} / {@link #getK()} instead of being passed to
 * {@link #getFitness(List)}. Passing them per call allowed a caller to silently
 * evaluate a gene against the wrong landscape.
 * </p>
 *
 * @author Muqtafi Akhmad
 *
 * @param <TComponent> type of a single locus value
 */
public interface NKLandscape<TComponent>{
	/**
	 * @return the number of loci of this landscape
	 */
	Integer getN();

	/**
	 * @return the number of loci each locus depends on, besides itself
	 */
	Integer getK();

	/**
	 * Seed this landscape was generated from.
	 *
	 * <p>
	 * Never {@code null}: when no seed is supplied a random one is drawn and kept,
	 * so a freshly generated landscape can always be reproduced. Note that for a
	 * landscape loaded from a file written before seed tracking existed, the seed
	 * is assigned at load time and does <em>not</em> reproduce that file's
	 * contents; the file remains the authoritative artefact.
	 * </p>
	 *
	 * @return the seed, never {@code null}
	 */
	Long getSeed();

	/**
	 * Fitness of a gene: the mean of its N characteristic fitnesses, each drawn
	 * uniformly from [0, 1) for the gene's (self + K neighbours) input combination.
	 *
	 * @param geneComponentList the N loci of the gene
	 * @return fitness in [0, 1)
	 * @throws IllegalArgumentException if {@code geneComponentList} is null, does
	 *                                  not hold exactly {@link #getN()} loci, or
	 *                                  contains a null locus
	 */
	Double getFitness(List<TComponent> geneComponentList);

	/**
	 * Gene with the lowest fitness on this landscape.
	 *
	 * <p>
	 * When {@link #isLowestFitnessExact()} is {@link Boolean#TRUE} this is a proven
	 * global minimum, found by enumerating every genotype. Otherwise it is the best
	 * point found by a seeded multi-start local search.
	 * </p>
	 *
	 * @return never {@code null}; computed on first access when none was supplied
	 */
	NKGene<TComponent> getLowestFitnessGene();

	/**
	 * Whether {@link #getLowestFitnessGene()} is a proven global minimum.
	 *
	 * @return {@link Boolean#TRUE} when proven exact, {@link Boolean#FALSE} when it
	 *         is only a local optimum. Legacy files that predate this flag yield
	 *         {@code null} (unknown) until the value is refreshed.
	 */
	Boolean isLowestFitnessExact();
}
