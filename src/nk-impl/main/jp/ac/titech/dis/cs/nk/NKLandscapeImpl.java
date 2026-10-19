package jp.ac.titech.dis.cs.nk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import cern.jet.random.Uniform;
import cern.jet.random.engine.MersenneTwister;

/**
 * Kauffman NK fitness landscape backed by a per-component fitness table.
 *
 * <p>
 * Each of the N components owns a table mapping every one of its 2^(K+1) input
 * combinations (the locus itself plus its K dependencies) to a value drawn
 * uniformly from [0, 1) and rounded to {@link #FITNESS_RANGE} decimals. The
 * fitness of a gene is the mean of its N component values.
 * </p>
 *
 * <p>
 * The table is filled lazily on first lookup, which makes repeated lookups
 * cheap and, more importantly, makes a landscape fully determined by its table:
 * two landscapes built from the same table behave identically regardless of the
 * random number generator behind them.
 * </p>
 */
public class NKLandscapeImpl implements NKLandscape<Boolean>{
	private Integer N;
	private Integer K;
	private List<Map<String, Double>> componentFitnessCache;
	private List<List<Byte>> neighbors;
	private NKGene<Boolean> lowestFitnessGene;
	private Boolean lowestFitnessExact;
	private final Long seed;

	/** Number of decimals kept for each characteristic fitness value. */
	public static final Integer FITNESS_RANGE = 4;
	/** Starting points for the multi-start search used on large landscapes. */
	public static final int DEFAULT_N_PARTICLE = 40;
	/** Maximum number of downhill moves each starting point may take. */
	public static final int DEFAULT_ITERATIONS = 40;
	/**
	 * Upper bound for the exhaustive walks in {@link #generateFitnessCache()} and
	 * {@link #findLowestFitnessGeneExhaustive()}: 2^N genotypes. Past this the walk
	 * is refused with an explanatory error instead of exhausting the heap.
	 */
	public static final int MAX_EXHAUSTIVE_N = 20;
	/**
	 * For N at or below this value the lowest fitness gene is obtained by
	 * enumerating every genotype, so it is a proven global minimum. Larger
	 * landscapes fall back to a seeded multi-start local search.
	 */
	public static final int EXACT_OPTIMUM_MAX_N = 16;
	/**
	 * Decorrelates the search's random stream from the fitness table's stream; both
	 * are derived from the same seed.
	 */
	private static final long SEARCH_SEED_SALT = 0x9E3779B97F4A7C15L;

	private final Uniform uniform;

	public NKLandscapeImpl(Integer N, Integer K, List<Map<String,Double>> inputCache, List<List<Byte>> neighbors, NKGene<Boolean> lowestFitnessGene) {
		this(N, K, inputCache, neighbors, lowestFitnessGene, null);
	}

	/**
	 * Full constructor.
	 *
	 * @param N number of loci, must be &gt;= 1
	 * @param K dependencies per locus, must be in [0, N-1]
	 * @param inputCache a complete fitness table to adopt, or {@code null} to start
	 *                   empty and fill lazily
	 * @param neighbors N x N dependency matrix with exactly K dependencies per row
	 * @param lowestFitnessGene known lowest gene, or {@code null} to derive one on
	 *                          first access
	 * @param seed seed for the fitness draws; when {@code null} a random one is drawn
	 * @throws IllegalArgumentException if N, K or the dependency matrix are invalid
	 * @throws IllegalStateException if {@code inputCache} is not a complete table for
	 *                               these N and K
	 */
	public NKLandscapeImpl(Integer N, Integer K, List<Map<String,Double>> inputCache, List<List<Byte>> neighbors, NKGene<Boolean> lowestFitnessGene, Long seed) {
		super();
		if(N == null || N < 1) {
			throw new IllegalArgumentException("N must be >= 1, got " + N);
		}
		if(K == null || K < 0 || K > N - 1) {
			throw new IllegalArgumentException(
					"K must be between 0 and N-1, got N=" + N + ", K=" + K);
		}
		this.N = N;
		this.K = K;
		// always end up with a concrete seed so the landscape can be reproduced
		this.seed = (seed != null) ? seed : new Random().nextLong();
		if(inputCache != null) {
			this.componentFitnessCache = inputCache;
			if(!this.isComplete()) {
				throw new IllegalStateException("Incomplete input cache: expected "
						+ this.expectedEntriesPerComponent() + " entries for each of the " + N
						+ " components");
			}
		}else {
			this.componentFitnessCache = new ArrayList<Map<String, Double>>(N);
			for(Integer i = 0; i < N; i++){
				this.componentFitnessCache.add(Collections.synchronizedMap(new HashMap<String, Double>()));
			}
		}
		this.neighbors = neighbors;
		if(!this.neighborsComplete()) {
			throw new IllegalStateException("Incomplete neighbors: expected an " + N + "x" + N
					+ " dependency matrix with exactly K=" + K
					+ " dependencies per locus and no self-dependency");
		}
		this.lowestFitnessGene = lowestFitnessGene;
		// by default return 0.0 until 1.0
		// (COLT's engine only takes an int seed, so fold the 64-bit seed down to 32 bits)
		this.uniform = new Uniform(new MersenneTwister(Long.hashCode(this.seed)));
	}

	public NKLandscapeImpl(Integer N, Integer K, List<Map<String,Double>> inputCache, List<List<Byte>> neighbors, int nParticle, int iterations) {
		this(N, K, inputCache, neighbors, nParticle, iterations, null);
	}

	public NKLandscapeImpl(Integer N, Integer K, List<Map<String,Double>> inputCache, List<List<Byte>> neighbors, int nParticle, int iterations, Long seed) {
		this(N, K, inputCache, neighbors, (NKGene<Boolean>) null, seed);
		this.setLowestFitnessGene(this.calculateLowestFitnessGene(nParticle, iterations));
	}

	public NKLandscapeImpl(Integer N, Integer K, List<List<Byte>> neighbors, NKGene<Boolean> lowestFitnessGene) {
		this(N, K, null, neighbors, lowestFitnessGene);
	}

	public NKLandscapeImpl(Integer N, Integer K, List<List<Byte>> neighbors, int nParticle, int iterations) {
		this(N, K, null, neighbors, nParticle, iterations, null);
	}

	public NKLandscapeImpl(Integer N, Integer K, List<List<Byte>> neighbors, int nParticle, int iterations, Long seed) {
		this(N, K, null, neighbors, nParticle, iterations, seed);
	}

	@Override
	public Double getFitness(List<Boolean> geneComponentList) {
		validateLoci(geneComponentList);
		double acc = 0.0;
		List<List<Boolean>> geneComponentWithNeighbors = NKUtils.splitToGeneComponents(N, K, geneComponentList, this.neighbors);
		for(int componentIndex = 0; componentIndex < N; componentIndex++){
			acc += getCharacteristicFitness(componentIndex, geneComponentWithNeighbors.get(componentIndex));
		}
		return acc / N;
	}

	/**
	 * Look up (creating on demand) the characteristic fitness of one component for
	 * one input combination. Synchronised on the landscape because Repast may tick
	 * agents concurrently.
	 */
	private synchronized Double getCharacteristicFitness(int componentIndex, List<Boolean> componentWithNeighbor){
		Map<String, Double> fitnessCacheForComponent = this.componentFitnessCache.get(componentIndex);
		String componentKey = NKUtils.getComponentListSignature(componentWithNeighbor);
		if(!fitnessCacheForComponent.containsKey(componentKey)){
			// put random double value from generator, rounded to FITNESS_RANGE decimals
			Double fitness = this.uniform.nextDouble();
			fitness = fitness * Math.pow(10,FITNESS_RANGE);
			fitness = Math.round(fitness)/Math.pow(10,FITNESS_RANGE);
			fitnessCacheForComponent.put(componentKey, fitness);
		}
		return fitnessCacheForComponent.get(componentKey);
	}

	/**
	 * Materialise the whole fitness table by walking every genotype once, using a
	 * single reusable locus list (an odometer) instead of a recursive copy per
	 * node, so the walk costs O(2^N) list writes rather than O(N * 2^N) list
	 * allocations.
	 *
	 * @throws IllegalStateException if N exceeds {@link #MAX_EXHAUSTIVE_N}
	 */
	public void generateFitnessCache() {
		if(this.N > MAX_EXHAUSTIVE_N) {
			throw new IllegalStateException("Refusing to enumerate the 2^N genotype space for N="
					+ this.N + " (limit MAX_EXHAUSTIVE_N=" + MAX_EXHAUSTIVE_N
					+ "). Reduce N, or pre-generate the landscape outside the model and load it.");
		}
		List<Boolean> loci = new ArrayList<Boolean>(this.N);
		for(int iter=0; iter < this.N; iter++) {
			loci.add(Boolean.FALSE);
		}
		long total = 1L << this.N;
		for(long visited = 0; visited < total; visited++) {
			this.getFitness(loci);
			incrementLoci(loci);
		}
	}

	/**
	 * Add one to a locus list treated as a little-endian binary number, enumerating
	 * every genotype exactly once.
	 */
	private static void incrementLoci(List<Boolean> loci) {
		int index = 0;
		while(index < loci.size() && loci.get(index).booleanValue()) {
			loci.set(index, Boolean.FALSE);
			index++;
		}
		if(index < loci.size()) {
			loci.set(index, Boolean.TRUE);
		}
	}

	/**
	 * @return true when every component holds exactly 2^(K+1) entries, i.e. the
	 *         table has been materialised for all input combinations
	 */
	public boolean isComplete() {
		if(this.componentFitnessCache == null || this.componentFitnessCache.size() != this.N) {
			return false;
		}
		long required = expectedEntriesPerComponent();
		for(Map<String,Double> characteristicCache: this.componentFitnessCache) {
			if(characteristicCache == null || characteristicCache.size() != required) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @return 2^(K+1): how many input combinations each component must cover
	 * @throws IllegalStateException if K+1 would overflow a 63-bit shift
	 */
	private long expectedEntriesPerComponent() {
		if(this.K < 0 || this.K > 62) {
			throw new IllegalStateException("K=" + this.K + " is out of range");
		}
		return 1L << (this.K + 1);
	}

	/**
	 * @return true when the dependency matrix is exactly N x N, carries only 0/1
	 *         entries, gives every locus K dependencies, and never makes a locus
	 *         depend on itself
	 */
	public boolean neighborsComplete() {
		if(this.neighbors == null || this.neighbors.size() != this.N) {
			return false;
		}
		for(int locus = 0; locus < this.N; locus++) {
			List<Byte> neighborForLocus = this.neighbors.get(locus);
			if(neighborForLocus == null || neighborForLocus.size() != this.N) {
				return false;
			}
			// the number of 1 in the list should be the exact number as K
			int numberOfDependency = 0;
			for(int other = 0; other < this.N; other++) {
				Byte dependency = neighborForLocus.get(other);
				if(!NKUtils.isDependencyMarker(dependency)) {
					return false;
				}
				if(NKUtils.isExistingDependency(dependency)) {
					if(other == locus) {
						// a locus is part of its own input set already; depending on
						// itself here would double count it
						return false;
					}
					numberOfDependency++;
				}
			}
			if(numberOfDependency != this.K) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @throws IllegalArgumentException if the loci are null, are not exactly N, or
	 *                                  contain a null
	 */
	private void validateLoci(List<Boolean> geneComponentList) {
		if(geneComponentList == null) {
			throw new IllegalArgumentException("Loci must not be null");
		}
		if(geneComponentList.size() != this.N) {
			throw new IllegalArgumentException("Expected " + this.N + " loci for this landscape but got "
					+ geneComponentList.size());
		}
		for(int locus = 0; locus < this.N; locus++) {
			if(geneComponentList.get(locus) == null) {
				throw new IllegalArgumentException("Locus " + locus + " is null");
			}
		}
	}

	public Integer getN() {
		return N;
	}

	public void setN(Integer n) {
		N = n;
	}

	public Integer getK() {
		return K;
	}

	public void setK(Integer k) {
		K = k;
	}

	@Override
	public Long getSeed() {
		return this.seed;
	}

	public List<Map<String, Double>> getComponentFitnessCache() {
		return componentFitnessCache;
	}

	public List<List<Byte>> getNeighbors() {
		return neighbors;
	}

	/**
	 * Lowest known gene; when none was supplied (for instance because the file
	 * predates the field) it is derived here rather than failing later with a null
	 * pointer in the caller.
	 */
	@Override
	public synchronized NKGene<Boolean> getLowestFitnessGene() {
		if(this.lowestFitnessGene == null) {
			this.setLowestFitnessGene(this.calculateLowestFitnessGene(DEFAULT_N_PARTICLE, DEFAULT_ITERATIONS));
		}
		return this.lowestFitnessGene;
	}

	public void setLowestFitnessGene(NKGene<Boolean> lowestFitnessGene) {
		this.lowestFitnessGene = lowestFitnessGene;
	}

	@Override
	public Boolean isLowestFitnessExact() {
		return this.lowestFitnessExact;
	}

	public void setLowestFitnessExact(Boolean lowestFitnessExact) {
		this.lowestFitnessExact = lowestFitnessExact;
	}

	public void setComponentFitnessCache(List<Map<String, Double>> componentFitnessCache) {
		this.componentFitnessCache = componentFitnessCache;
	}

	public void setNeighbors(List<List<Byte>> neighbors) {
		this.neighbors = neighbors;
	}

	public Uniform getUniform() {
		return uniform;
	}

	/**
	 * Re-derive the lowest fitness gene and its exactness flag.
	 *
	 * <p>
	 * Used when loading a persisted landscape: files written before the gene was
	 * validated could carry a gene whose loci did not match its stored fitness (and
	 * those files are common), and files written before exactness was tracked carry
	 * no flag at all. Recomputing is cheap - for N at most
	 * {@link #EXACT_OPTIMUM_MAX_N} it is a walk over an already complete table -
	 * and it guarantees {@link #getLowestFitnessGene()} is a proven global minimum
	 * for every landscape the model is actually used with (N is 7 to 12 in the
	 * batch configuration).
	 *
	 * @return the refreshed lowest gene, never null
	 */
	public synchronized NKGene<Boolean> refreshLowestFitnessGene() {
		if(this.N <= EXACT_OPTIMUM_MAX_N) {
			// authoritative: enumerate the whole space
			this.setLowestFitnessGene(
					this.calculateLowestFitnessGene(DEFAULT_N_PARTICLE, DEFAULT_ITERATIONS));
			return this.lowestFitnessGene;
		}
		NKGene<Boolean> stored = this.lowestFitnessGene;
		if(stored != null && stored.getFitness() != null) {
			// trust the file if it is self-consistent; the search is only a heuristic
			// at this size, so re-running it would not be an improvement
			try {
				Double recomputed = getFitness(stored.getInternalComponents());
				if(recomputed.equals(stored.getFitness())) {
					if(this.lowestFitnessExact == null) {
						this.lowestFitnessExact = Boolean.FALSE;
					}
					return stored;
				}
			} catch (IllegalArgumentException e) {
				// malformed stored gene: fall through and rebuild it
			}
			System.out.println("*** warning: discarded a lowest-fitness gene whose loci did not "
					+ "match its fitness for N=" + this.N + ", K=" + this.K + "; recomputed it");
		}
		this.setLowestFitnessGene(this.calculateLowestFitnessGene(DEFAULT_N_PARTICLE, DEFAULT_ITERATIONS));
		return this.lowestFitnessGene;
	}

	/**
	 * Lowest gene on this landscape.
	 *
	 * @param nParticle number of random starting points, used only when N exceeds
	 *                  {@link #EXACT_OPTIMUM_MAX_N}
	 * @param iterations maximum downhill moves per starting point, used only when N
	 *                   exceeds {@link #EXACT_OPTIMUM_MAX_N}
	 * @return a gene whose stored fitness always equals {@code getFitness(its loci)}
	 */
	private NKGene<Boolean> calculateLowestFitnessGene(int nParticle, int iterations) {
		if(this.N <= EXACT_OPTIMUM_MAX_N) {
			this.lowestFitnessExact = Boolean.TRUE;
			return findLowestFitnessGeneExhaustive();
		}
		this.lowestFitnessExact = Boolean.FALSE;
		return findLowestFitnessGeneBySearch(nParticle, iterations);
	}

	/**
	 * Walk every genotype and keep the best: a proven global minimum.
	 */
	private NKGene<Boolean> findLowestFitnessGeneExhaustive() {
		if(this.N > MAX_EXHAUSTIVE_N) {
			throw new IllegalStateException("Refusing to enumerate the 2^N genotype space for N="
					+ this.N + " (limit MAX_EXHAUSTIVE_N=" + MAX_EXHAUSTIVE_N + ")");
		}
		List<Boolean> loci = new ArrayList<Boolean>(this.N);
		for(int iter=0; iter < this.N; iter++) {
			loci.add(Boolean.FALSE);
		}
		NKGene<Boolean> best = new NKGene<Boolean>(new ArrayList<Boolean>(loci), this.N, this.K, getFitness(loci));
		long total = 1L << this.N;
		for(long visited = 1; visited < total; visited++) {
			incrementLoci(loci);
			Double fitness = getFitness(loci);
			if(fitness < best.getFitness()) {
				best = new NKGene<Boolean>(new ArrayList<Boolean>(loci), this.N, this.K, fitness);
			}
		}
		return best;
	}

	/**
	 * Multi-start local search, used only when N exceeds
	 * {@link #EXACT_OPTIMUM_MAX_N}. Seeded from {@link #getSeed()} so repeated
	 * constructions of the same landscape agree.
	 */
	private NKGene<Boolean> findLowestFitnessGeneBySearch(int nParticle, int iterations) {
		if(nParticle < 1) {
			throw new IllegalArgumentException("nParticle must be >= 1, got " + nParticle);
		}
		if(iterations < 0) {
			throw new IllegalArgumentException("iterations must be >= 0, got " + iterations);
		}
		Random random = new Random(this.seed ^ SEARCH_SEED_SALT);
		List<NKGene<Boolean>> genePool = new ArrayList<NKGene<Boolean>>(nParticle);
		// initialize
		for(int iterParticle=0; iterParticle<nParticle; iterParticle++) {
			List<Boolean> randomLoci = NKUtils.generateRandomGene(this.N, random);
			genePool.add(new NKGene<Boolean>(randomLoci, this.N, this.K, this.getFitness(randomLoci)));
		}
		// try walk to lower fitness
		for(int iterIteration=0; iterIteration<iterations; iterIteration++) {
			List<NKGene<Boolean>> newGenePool = new ArrayList<NKGene<Boolean>>(nParticle);
			for(NKGene<Boolean> oldGene: genePool) {
				newGenePool.add(tryGetLowerFitness(oldGene));
			}
			genePool = newGenePool;
		}
		// return lowest gene from gene pool
		NKGene<Boolean> lowestFitnessGene = genePool.get(0);
		for(int iter=1; iter<genePool.size(); iter++) {
			NKGene<Boolean> inspectedGene = genePool.get(iter);
			if(inspectedGene.getFitness() < lowestFitnessGene.getFitness()) {
				lowestFitnessGene = inspectedGene;
			}
		}
		return lowestFitnessGene;
	}

	/**
	 * Take a single first-improvement step: return the first single-locus flip with
	 * a strictly lower fitness, or {@code input} unchanged when there is none.
	 *
	 * <p>
	 * Every candidate is evaluated from a private copy of {@code input}'s loci, so
	 * the input gene is never modified and never ends up half-flipped with a stale
	 * fitness attached to it.
	 * </p>
	 *
	 * @param input a gene carrying a real fitness value
	 * @return a new gene differing in exactly one locus, or {@code input} itself
	 * @throws IllegalArgumentException if {@code input} has no fitness value
	 */
	private NKGene<Boolean> tryGetLowerFitness(NKGene<Boolean> input){
		if(input == null || input.getFitness() == null) {
			throw new IllegalArgumentException("A gene with a fitness value is required");
		}
		List<Boolean> currentLoci = input.getInternalComponents();
		for(int i=0; i<this.N; i++) {
			// try modify i-th element on a private copy
			List<Boolean> candidateLoci = new ArrayList<Boolean>(currentLoci);
			candidateLoci.set(i, !currentLoci.get(i).booleanValue());
			// get fitness
			Double fitness = this.getFitness(candidateLoci);
			if(fitness < input.getFitness()) {
				return new NKGene<Boolean>(candidateLoci, this.N, this.K, fitness);
			}
		}
		// no improving neighbour
		return input;
	}
}
