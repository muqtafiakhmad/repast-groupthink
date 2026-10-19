package jp.ac.titech.dis.cs.nk;

/**
 * Conventional file name of a persisted NK landscape:
 * {@code <prefix>n<N>k<K>-<neighbourhood>-run<runId>.json}
 */
public class NKLandscapePath {
	private final String prefix;
	private final Integer N;
	private final Integer K;
	private final NeighborhoodType neighborHoodType;
	private final Integer runId;

	/**
	 * Topology of the dependency matrix: which K loci each locus depends on.
	 */
	public static enum NeighborhoodType{
		/** Locus i depends on i+1 ... i+K (cyclically). */
		ADJACENT,
		/** Loci are split into blocks of K+1 and depend on the rest of their block. */
		MODULAR,
		/** Locus i depends on K loci chosen at random (excluding itself). */
		RANDOM;

		public static NeighborhoodType getFromString(String input) {
			if(input == null) {
				throw new IllegalArgumentException("Neighbourhood type must not be null");
			}
			String normalised = input.trim().toLowerCase();
			switch(normalised) {
				case "adj":
				case "adjacent":
					return ADJACENT;
				case "mod":
				case "modular":
					return MODULAR;
				case "rand":
				case "random":
					return RANDOM;
				default:
					throw new IllegalArgumentException(
							"Unknown neighbourhood '" + input + "' (expected adj, mod or rand)");
			}
		}
	}

	public static final String NK_LANDSCAPE_FILETYPE="json";

	public NKLandscapePath(String prefix, Integer n, Integer k, NeighborhoodType neighborhoodType, Integer runId) {
		super();
		if(prefix == null) {
			throw new IllegalArgumentException("Path prefix must not be null");
		}
		if(neighborhoodType == null) {
			throw new IllegalArgumentException("Neighbourhood type must not be null");
		}
		if(runId == null) {
			throw new IllegalArgumentException("Run id must not be null");
		}
		if(n == null || n < 1) {
			throw new IllegalArgumentException("N must be a positive number, got " + n);
		}
		if(k == null || k < 0 || k >= n) {
			throw new IllegalArgumentException("K must satisfy 0 <= K < N, got K=" + k + " N=" + n);
		}
		this.prefix = withTrailingSeparator(prefix);
		this.N = n;
		this.K = k;
		this.neighborHoodType = neighborhoodType;
		this.runId = runId;
	}

	/**
	 * The file name is appended directly to the prefix, so a prefix such as
	 * {@code landscape} (no separator) would produce {@code landscapen12k3-...}.
	 * Accept either form rather than requiring the caller to get it right.
	 */
	private static String withTrailingSeparator(String prefix) {
		if(prefix.isEmpty() || prefix.endsWith("/") || prefix.endsWith("\\")) {
			return prefix;
		}
		return prefix + java.io.File.separator;
	}

	public String getPrefix() {
		return prefix;
	}

	public Integer getN() {
		return N;
	}
	public Integer getK() {
		return K;
	}
	public Integer getRunId() {
		return runId;
	}

	public NeighborhoodType getNeighborHoodType() {
		return neighborHoodType;
	}

	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer()
				.append(this.prefix)
				.append("n").append(this.N)
				.append("k").append(this.K)
				.append("-")
				.append(this.neighborHoodType)
				.append("-")
				.append("run").append(this.runId)
				.append(".").append(NKLandscapePath.NK_LANDSCAPE_FILETYPE);
		return buffer.toString();
	}
}
