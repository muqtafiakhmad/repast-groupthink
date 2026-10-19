package jp.ac.titech.dis.cs.nk;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jp.ac.titech.dis.cs.nk.NKLandscapePath.NeighborhoodType;

/**
 * Helpers for building NK dependency matrices ("neighbourhoods"), generating
 * genes and slicing a gene into the input set of each locus.
 *
 * <p>
 * Every generator validates its N/K arguments up front: NK only makes sense for
 * {@code N >= 1} and {@code 0 <= K <= N-1}, and a modular neighbourhood
 * additionally requires {@code N} to be a multiple of the module size
 * {@code K+1}. Silently producing a degenerate matrix (duplicate neighbours,
 * self-dependency, wrong dependency count) used to surface much later as an
 * opaque {@code IllegalStateException} from the landscape.
 * </p>
 */
public class NKUtils {
	public static final Byte NO_DEPENDENCY = 0;
	public static final Byte EXIST_DEPENDENCY = 1;

	/**
	 * Whether {@code entry} is the marker for "no dependency".
	 *
	 * <p>
	 * Deliberately value-based: comparing boxed {@code Byte} values with {@code ==}
	 * compares references, which would silently depend on {@code Byte.valueOf}
	 * caching.
	 * </p>
	 */
	public static boolean isNoDependency(Byte entry) {
		return entry != null && entry.byteValue() == 0;
	}

	/** Whether {@code entry} is the marker for "dependency exists". Value-based. */
	public static boolean isExistingDependency(Byte entry) {
		return entry != null && entry.byteValue() == 1;
	}

	/** Whether {@code entry} is a well-formed dependency matrix entry (0 or 1). */
	public static boolean isDependencyMarker(Byte entry) {
		return isNoDependency(entry) || isExistingDependency(entry);
	}

	/**
	 * Validate the size and coupling arguments shared by every NK generator.
	 *
	 * @param N number of loci, must be &gt;= 1
	 * @param K number of dependencies per locus, must be in [0, N-1]
	 * @throws IllegalArgumentException when the arguments are outside those bounds
	 */
	public static void validateNK(int N, int K) {
		if(N < 1) {
			throw new IllegalArgumentException("N must be >= 1, got " + N);
		}
		if(K < 0 || K > N - 1) {
			throw new IllegalArgumentException(
					"K must be between 0 and N-1, got N=" + N + ", K=" + K);
		}
	}

	public static void printSplitGenes(List<List<Boolean>> splitGenes) {
		for(List<Boolean> splitGene: splitGenes) {
			System.out.println(splitGene);
		}
	}

	public static void printNeighborhood(List<List<Byte>> neighborhood) {
		for(List<Byte> neighborhoodForLocus: neighborhood) {
			System.out.println(neighborhoodForLocus);
		}
	}

	/**
	 * Force the first {@code numReset} loci to {@code true}.
	 *
	 * @param opinion loci to copy
	 * @param numReset number of leading loci to set, in [0, opinion.size()]
	 * @throws IllegalArgumentException if {@code numReset} is out of range
	 */
	public static List<Boolean> resetInitialLociAdjacent(List<Boolean> opinion, int numReset){
		requireLoci(opinion);
		if(numReset < 0 || numReset > opinion.size()) {
			throw new IllegalArgumentException(
					"numReset must be in [0, " + opinion.size() + "], got " + numReset);
		}
		List<Boolean> result = new ArrayList<Boolean>(opinion);
		for(int idx=0; idx<numReset; idx++) {
			result.set(idx, true);
		}
		return result;
	}

	/**
	 * Force the first {@code numReset} loci of every module to {@code true}.
	 *
	 * <p>
	 * Requires a well formed modular landscape: {@code N} divisible by
	 * {@code K+1}, and {@code numReset} within one module, otherwise indices would
	 * silently land in the wrong module (or out of bounds).
	 * </p>
	 *
	 * @param opinion loci to copy
	 * @param N number of loci
	 * @param K dependencies per locus
	 * @param numReset number of leading loci to set per module, in [0, K+1]
	 * @throws IllegalArgumentException when N, K or numReset are inconsistent
	 */
	public static List<Boolean> resetInitialLociModular(List<Boolean> opinion, int N, int K, int numReset){
		validateNK(N, K);
		requireLoci(opinion);
		if(opinion.size() != N) {
			throw new IllegalArgumentException(
					"opinion holds " + opinion.size() + " loci but N=" + N);
		}
		int moduleSize = K + 1;
		if(N % moduleSize != 0) {
			throw new IllegalArgumentException(
					"N (" + N + ") must be a multiple of the modular unit K+1 (" + moduleSize
					+ ") so that loci fall into whole modules");
		}
		if(numReset < 0 || numReset > moduleSize) {
			throw new IllegalArgumentException(
					"numReset must be in [0, K+1=" + moduleSize + "], got " + numReset);
		}
		int numModul = N / moduleSize;

		List<Boolean> result = new ArrayList<Boolean>(opinion); //copy
		for(int lociIter=0; lociIter<numReset; lociIter++) {
			for(int modulIter=0; modulIter<numModul; modulIter++) {
				int index = (modulIter*moduleSize)+lociIter;
				result.set(index, true);
			}
		}
		return result;
	}

	/**
	 * Modular neighbourhood: loci are partitioned into blocks of {@code K+1} and
	 * each locus depends on the other members of its block.
	 *
	 * @param N number of loci, must be a multiple of K+1
	 * @param K dependencies per locus
	 * @return an N x N dependency matrix with exactly K dependencies per row
	 * @throws IllegalArgumentException if N/K are invalid or N is not a multiple of K+1
	 */
	public static List<List<Byte>> generateModularNeighborhood(int N, int K){
		validateNK(N, K);
		int moduleSize = K+1;
		if( N % moduleSize !=0 ) {
			throw new IllegalArgumentException(
					"N (" + N + ") must be a multiple of the module size K+1 (" + moduleSize
					+ ") for a modular neighbourhood");
		}
		List<List<Byte>> result = new ArrayList<List<Byte>>();
		for(int iterX=0; iterX<N; iterX++) {
			List<Byte> neighborhood = new ArrayList<Byte>(N);
			for(int iterY=0; iterY<N; iterY++) {
				int moduleNumX=iterX/moduleSize;
				int moduleNumY=iterY/moduleSize;
				if((iterX!=iterY) && (moduleNumX==moduleNumY)) {
					neighborhood.add(EXIST_DEPENDENCY);
				}else {
					neighborhood.add(NO_DEPENDENCY);
				}
			}
			result.add(neighborhood);
		}
		return result;
	}

	/**
	 * Adjacent ("nearest neighbour") neighbourhood: locus i depends on i+1 ... i+K
	 * modulo N, so the dependency graph is a directed ring.
	 *
	 * @param N number of loci
	 * @param K dependencies per locus, must be in [0, N-1] so a locus never depends on itself
	 * @return an N x N dependency matrix with exactly K dependencies per row
	 * @throws IllegalArgumentException if N/K are invalid
	 */
	public static List<List<Byte>> generateAdjacentNeighborhood(int N, int K){
		validateNK(N, K);
		List<List<Byte>> result = new ArrayList<List<Byte>>();
		for(int iterN=0; iterN<N; iterN++) {
			// generate neighbor indexes for iterN
			List<Integer> neighborIndexes = new ArrayList<Integer>(K);
			for(int iterK=1; iterK<=K; iterK++) {
				neighborIndexes.add((iterN+iterK)%N);
			}
			List<Byte> neighborForLocus = new ArrayList<Byte>(N);
			for(int iterNeighbor=0; iterNeighbor<N; iterNeighbor++) {
				if(neighborIndexes.contains(iterNeighbor)) {
					neighborForLocus.add(EXIST_DEPENDENCY);
				}else {
					neighborForLocus.add(NO_DEPENDENCY);
				}
			}
			result.add(neighborForLocus);
		}
		return result;
	}

	/**
	 * Random neighbourhood: locus i depends on K loci drawn uniformly among the
	 * other N-1 loci (a locus never depends on itself). This is the most common
	 * variant of the NK model in the literature; together with
	 * {@link #generateAdjacentNeighborhood(int, int)} (no interaction) and
	 * {@link #generateModularNeighborhood(int, int)} (decomposable) it spans the
	 * usual spectrum of epistatic structure.
	 *
	 * @param N number of loci
	 * @param K dependencies per locus, must be in [0, N-1]
	 * @param random source of randomness
	 * @return an N x N dependency matrix with exactly K dependencies per row
	 * @throws IllegalArgumentException if N/K are invalid or random is null
	 */
	public static List<List<Byte>> generateRandomNeighborhood(int N, int K, Random random){
		validateNK(N, K);
		if(random == null) {
			throw new IllegalArgumentException("random source must not be null");
		}
		List<List<Byte>> result = new ArrayList<List<Byte>>(N);
		for(int iterN=0; iterN<N; iterN++) {
			// every locus except iterN is a candidate
			List<Integer> candidates = new ArrayList<Integer>(N - 1);
			for(int other=0; other<N; other++) {
				if(other != iterN) {
					candidates.add(other);
				}
			}
			// partial Fisher-Yates: pick the first K entries as the dependencies
			for(int pick=0; pick<K; pick++) {
				int swap = pick + random.nextInt(N - 1 - pick);
				int tmp = candidates.get(pick);
				candidates.set(pick, candidates.get(swap));
				candidates.set(swap, tmp);
			}
			List<Byte> neighborForLocus = new ArrayList<Byte>(N);
			for(int iterNeighbor=0; iterNeighbor<N; iterNeighbor++) {
				neighborForLocus.add(
						(iterNeighbor != iterN && candidates.subList(0, K).contains(iterNeighbor))
						? EXIST_DEPENDENCY : NO_DEPENDENCY);
			}
			result.add(neighborForLocus);
		}
		return result;
	}

	/**
	 * Build the dependency matrix for a topology.
	 *
	 * @param type topology
	 * @param N number of loci
	 * @param K dependencies per locus
	 * @param random source of randomness (only used by {@link NeighborhoodType#RANDOM})
	 * @return an N x N dependency matrix with exactly K dependencies per row
	 * @throws IllegalArgumentException if N/K are invalid, random is null for a
	 *                                  random topology, or the topology is incompatible
	 *                                  with N/K
	 */
	public static List<List<Byte>> generateNeighborhood(NeighborhoodType type, int N, int K, Random random){
		if(type == null) {
			throw new IllegalArgumentException("neighbourhood type must not be null");
		}
		switch(type) {
			case ADJACENT:  return generateAdjacentNeighborhood(N, K);
			case MODULAR:   return generateModularNeighborhood(N, K);
			case RANDOM:    return generateRandomNeighborhood(N, K, random);
			default:        throw new IllegalArgumentException("Unsupported neighbourhood " + type);
		}
	}

	/**
	 * Canonical {@code 0}/{@code 1} encoding of a component input set, used as the
	 * key of the fitness table.
	 *
	 * @param componentList input values, in the order produced by
	 *                      {@link #splitToGeneComponents(Integer, Integer, List, List)}
	 * @return a string of 0s and 1s
	 * @throws IllegalArgumentException if componentList is null
	 */
	public static String getComponentListSignature(List<Boolean> componentList){
		if(componentList == null) {
			throw new IllegalArgumentException("component list must not be null");
		}
		StringBuffer buffer = new StringBuffer(componentList.size());
		for(Boolean component : componentList){
			if(component == null) {
				throw new IllegalArgumentException("component list must not contain null");
			}
			buffer.append(component ? "1" : "0");
		}
		return buffer.toString();
	}

	/**
	 * Generate a gene of {@code N} random loci.
	 *
	 * @param N number of loci
	 * @return a gene whose loci are independent fair coin flips
	 * @throws IllegalArgumentException if N &lt; 1
	 */
	public static List<Boolean> generateRandomGene(int N){
		return generateRandomGene(N, new Random());
	}

	/**
	 * Generate a gene of {@code N} random loci drawn from {@code random}, so the
	 * result is reproducible when the caller supplies a seeded source.
	 *
	 * @param N number of loci
	 * @param random source of randomness
	 * @return a gene whose loci are independent fair coin flips
	 * @throws IllegalArgumentException if N &lt; 1 or random is null
	 */
	public static List<Boolean> generateRandomGene(int N, Random random){
		if(N < 1) {
			throw new IllegalArgumentException("N must be >= 1, got " + N);
		}
		if(random == null) {
			throw new IllegalArgumentException("random source must not be null");
		}
		List<Boolean> result = new ArrayList<Boolean>(N);
		for(int iter = 0; iter < N; iter++){
			result.add(random.nextBoolean());
		}
		return result;
	}

	/**
	 * Slice a gene into the input set of every locus: for locus i the result holds
	 * the value of i itself followed by the values of the loci i depends on, in
	 * increasing index order. That ordering is fixed by the dependency matrix, so
	 * the same locus+input combination always maps to the same fitness-table key.
	 *
	 * @param N number of loci
	 * @param K dependencies per locus
	 * @param geneComponentList the N loci of the gene
	 * @param neighbors N x N dependency matrix
	 * @return N lists of exactly K+1 values
	 * @throws IllegalArgumentException if the arguments are inconsistent with each other
	 * @throws IllegalStateException if the dependency matrix does not give each locus
	 *                               exactly K dependencies
	 */
	public static List<List<Boolean>> splitToGeneComponents(Integer N, Integer K, List<Boolean> geneComponentList, List<List<Byte>> neighbors){
		if(N == null || K == null) {
			throw new IllegalArgumentException("N and K must not be null");
		}
		validateNK(N, K);
		if(geneComponentList == null || geneComponentList.size() != N) {
			throw new IllegalArgumentException("Expected " + N + " loci but got "
					+ (geneComponentList == null ? "null" : geneComponentList.size()));
		}
		if(neighbors == null || neighbors.size() != N) {
			throw new IllegalArgumentException("Expected a " + N + "x" + N
					+ " dependency matrix but got "
					+ (neighbors == null ? "null" : neighbors.size() + " rows"));
		}
		// construct component list
		List<List<Boolean>> result = new ArrayList<List<Boolean>>(N);
		for(int componentIndex = 0; componentIndex < N; componentIndex++){
			List<Boolean> componentWithNeighbor = new ArrayList<Boolean>(K + 1);
			// the locus itself always contributes
			componentWithNeighbor.add(geneComponentList.get(componentIndex));
			List<Byte> componentNeighbors = neighbors.get(componentIndex);
			if(componentNeighbors == null || componentNeighbors.size() != N) {
				throw new IllegalArgumentException("Dependency row " + componentIndex
						+ " must hold " + N + " entries");
			}
			// walk the remaining loci in a fixed (cyclic) order
			for(int neighborIndex = 1; neighborIndex <= N; neighborIndex++) {
				int targetIndex = (componentIndex+neighborIndex)%N;
				Byte marker = componentNeighbors.get(targetIndex);
				if(!isDependencyMarker(marker)) {
					throw new IllegalArgumentException("Dependency row " + componentIndex
							+ " holds " + marker + " at index " + targetIndex
							+ "; entries must be 0 or 1");
				}
				if(isExistingDependency(marker)) {
					componentWithNeighbor.add(geneComponentList.get(targetIndex));
				}
			}
			if(componentWithNeighbor.size() != (K+1)) {
				throw new IllegalStateException("Locus " + componentIndex + " must feed exactly K+1="
						+ (K+1) + " values but got " + componentWithNeighbor.size()
						+ " - the dependency matrix does not match K=" + K);
			}
			result.add(componentWithNeighbor);
		}
		return result;
	}

	private static void requireLoci(List<Boolean> opinion) {
		if(opinion == null) {
			throw new IllegalArgumentException("opinion must not be null");
		}
	}
}
