package jp.ac.titech.dis.cs.nk;

import java.util.Random;

import jp.ac.titech.dis.cs.nk.NKLandscapePath.NeighborhoodType;

/**
 * Bundles the three pieces of the NK model: the landscape, the gene factory that
 * evaluates genes against it, and the factory used to persist it.
 *
 * <p>
 * When a landscape file is given it is loaded (and validated) from disk;
 * otherwise a landscape is generated in memory. In both cases the gene factory
 * is seeded from the landscape's seed, so random genes drawn from it repeat
 * across runs of the same configuration.
 * </p>
 */
public class NKComponent {
	private final NKGeneFactory<Boolean> nkGeneFactory;
	private final NKLandscape<Boolean> nkLandscape;
	private final NKLandscapeFactory<Boolean> nkLandscapeFactory;

	public NKComponent(Config config){
		if(config == null) {
			throw new IllegalArgumentException("config must not be null");
		}
		this.nkLandscapeFactory = new NKLandscapeFactoryImpl();
		// load from filepath or generate new
		if(config.getLandscapeFilePath() != null) {
			this.nkLandscape = this.nkLandscapeFactory.loadLandscape(config.getLandscapeFilePath());
		}else {
			this.nkLandscape = new NKLandscapeImpl(config.getN(), config.getK(),
								   NKUtils.generateNeighborhood(config.getNeighborhoodType(),
										   					   config.getN(),
										   					   config.getK(),
										   					   new Random(config.getEffectiveSeed())),
								   NKLandscapeImpl.DEFAULT_N_PARTICLE,
								   NKLandscapeImpl.DEFAULT_ITERATIONS,
								   config.getEffectiveSeed());
		}
		this.nkGeneFactory = new NKGeneFactoryImpl(this.nkLandscape, new Random(this.nkLandscape.getSeed()));
	}

	public NKGeneFactory<Boolean> getNkGeneFactory() {
		return nkGeneFactory;
	}

	public NKLandscape<Boolean> getNkLandscape() {
		return nkLandscape;
	}

	public static class Config{
		private int N;
		private int K;
		private String landscapeFilePath;
		private NeighborhoodType neighborhoodType = NeighborhoodType.ADJACENT;
		private Long seed;

		public Config() {
			super();
		}
		public Config(int n, int k, String landscapeFilePath) {
			super();
			this.N = n;
			this.K = k;
			this.landscapeFilePath = landscapeFilePath;
		}

		/**
		 * Seed for generation: the configured one, or a random seed drawn and
		 * retained on first use so repeated calls agree.
		 *
		 * @return the effective seed, never null
		 */
		public Long getEffectiveSeed() {
			if(this.seed == null) {
				this.seed = new Random().nextLong();
			}
			return this.seed;
		}

		public int getN() {
			return N;
		}
		public void setN(int n) {
			N = n;
		}
		public int getK() {
			return K;
		}
		public void setK(int k) {
			K = k;
		}
		public String getLandscapeFilePath() {
			return landscapeFilePath;
		}
		public void setLandscapeFilePath(String landscapeFilePath) {
			this.landscapeFilePath = landscapeFilePath;
		}
		public NeighborhoodType getNeighborhoodType() {
			return neighborhoodType;
		}
		/**
		 * Topology used when generating in memory (ignored when a file is loaded).
		 * Defaults to {@link NeighborhoodType#ADJACENT}.
		 */
		public void setNeighborhoodType(NeighborhoodType neighborhoodType) {
			this.neighborhoodType = (neighborhoodType != null) ? neighborhoodType : NeighborhoodType.ADJACENT;
		}
		public Long getSeed() {
			return seed;
		}
		/**
		 * Seed for the generated landscape. When null a random seed is drawn, which
		 * the landscape then retains.
		 */
		public void setSeed(Long seed) {
			this.seed = seed;
		}
	}
}
