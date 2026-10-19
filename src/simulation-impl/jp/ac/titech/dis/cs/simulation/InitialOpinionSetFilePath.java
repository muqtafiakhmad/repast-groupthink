package jp.ac.titech.dis.cs.simulation;

public class InitialOpinionSetFilePath {
	private String prefix;
	private Integer runId;
	private Integer N;
	public static final String OPINION_FILE_TYPE = "json";
	
	public InitialOpinionSetFilePath(String prefix, Integer runId, Integer N) {
		super();
		if(prefix == null) {
			throw new IllegalArgumentException("Path prefix must not be null");
		}
		if(runId == null) {
			throw new IllegalArgumentException("Run id must not be null");
		}
		if(N == null || N < 1) {
			throw new IllegalArgumentException("N must be a positive number, got " + N);
		}
		this.prefix = withTrailingSeparator(prefix);
		this.runId = runId;
		this.N = N;
	}

	/**
	 * The file name is appended directly to the prefix, so a prefix without a
	 * separator (e.g. {@code initial-loci}) would produce
	 * {@code initial-locin12op1.json}. Accept either form.
	 */
	private static String withTrailingSeparator(String prefix) {
		if(prefix.isEmpty() || prefix.endsWith("/") || prefix.endsWith("\\")) {
			return prefix;
		}
		return prefix + java.io.File.separator;
	}
	
	public Integer getN() {
		return N;
	}

	public void setN(Integer n) {
		N = n;
	}

	public InitialOpinionSetFilePath() {
		super();
	}
	public String getPrefix() {
		return prefix;
	}
	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}
	public Integer getRunId() {
		return runId;
	}
	public void setRunId(Integer runId) {
		this.runId = runId;
	}
	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer()
				.append(this.prefix)
				.append("n").append(this.N)
				.append("op").append(this.runId)
				.append(".").append(InitialOpinionSetFilePath.OPINION_FILE_TYPE);
		return buffer.toString();
	};
}
