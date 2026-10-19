package jp.ac.titech.dis.cs.nk;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Generates NK landscapes and persists them as JSON so that every run of an
 * experiment searches the very same landscape.
 *
 * <p>
 * Loading validates the persisted fitness table (an incomplete table used to
 * pass the completeness check) and refreshes the lowest-fitness gene, so files
 * written before those checks existed are repaired on the way in.
 * </p>
 */
public class NKLandscapeFactoryImpl implements NKLandscapeFactory<Boolean>{

	@Override
	public NKLandscape<Boolean> generateRandomLandscape(Integer N, Integer K, List<List<Byte>> neighbors) {
		return generateRandomLandscape(N, K, neighbors, null);
	}

	@Override
	public NKLandscape<Boolean> generateRandomLandscape(Integer N, Integer K, List<List<Byte>> neighbors, Long seed) {
		NKLandscapeImpl result = new NKLandscapeImpl(N, K, neighbors, NKLandscapeImpl.DEFAULT_N_PARTICLE, NKLandscapeImpl.DEFAULT_ITERATIONS, seed);
		result.generateFitnessCache();
		if(!result.isComplete()) {
			throw new IllegalStateException("Should return landscape with complete cache values");
		}
		return result;
	}

	@Override
	public void dumpLandscape(NKLandscape<Boolean> landscape, String filePath) {
		if(landscape == null) {
			throw new IllegalArgumentException("landscape must not be null");
		}
		if(filePath == null) {
			throw new IllegalArgumentException("filePath must not be null");
		}
		NKLandscapeImpl landscapeImpl = (NKLandscapeImpl) landscape;
		BooleanNKLandscape booleanNKLandscape = new BooleanNKLandscape(landscapeImpl.getN(), landscapeImpl.getK(),
													landscapeImpl.getComponentFitnessCache(), landscapeImpl.getNeighbors(),
													landscapeImpl.getLowestFitnessGene(),
													landscapeImpl.getSeed(),
													landscapeImpl.isLowestFitnessExact()
												);
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			objectMapper.writeValue(new File(filePath), booleanNKLandscape);
		} catch (JsonGenerationException e) {
			throw new IllegalStateException(e);
		} catch (JsonMappingException e) {
			throw new IllegalStateException(e);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public NKLandscape<Boolean> loadLandscape(String filePath) {
		byte[] jsonData;
		try {
			jsonData = Files.readAllBytes(Paths.get(filePath));
			ObjectMapper objectMapper = new ObjectMapper();
			BooleanNKLandscape readLandscape = objectMapper.readValue(jsonData, BooleanNKLandscape.class);
			NKLandscapeImpl result = new NKLandscapeImpl(
											readLandscape.getN(), readLandscape.getK(),
											readLandscape.getComponentFitnessCache(),
											readLandscape.getNeighbors(),
											readLandscape.getLowestFitnessGene(),
											readLandscape.getSeed()
											);
			result.setLowestFitnessExact(readLandscape.getLowestFitnessExact());
			// files written before the optimum was validated may carry a gene whose
			// loci do not match its fitness, and files older than the exactness flag
			// carry none; re-deriving it makes the contract hold for every load
			result.refreshLowestFitnessGene();
			return result;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public NKLandscape<Boolean> loadLandscape(NKLandscapePath landscapePath) {
		return loadLandscape(landscapePath.toString());
	}

	/**
	 * Serialisation form of {@link NKLandscapeImpl}. Kept separate from the model
	 * class so the persisted format stays stable even if the implementation changes.
	 */
	public static class BooleanNKLandscape{
		private Integer N;
		private Integer K;
		private List<Map<String, Double>> componentFitnessCache;
		private List<List<Byte>> neighbors;
		private NKGene<Boolean> lowestFitnessGene;
		private Long seed;
		private Boolean lowestFitnessExact;

		public BooleanNKLandscape() {

		}
		public BooleanNKLandscape(Integer n, Integer k, List<Map<String, Double>> componentFitnessCache, List<List<Byte>> neighbors, NKGene<Boolean> lowestFitnessGene, Long seed, Boolean lowestFitnessExact) {
			super();
			this.N = n;
			this.K = k;
			this.componentFitnessCache = componentFitnessCache;
			this.neighbors = neighbors;
			this.lowestFitnessGene = lowestFitnessGene;
			this.seed = seed;
			this.lowestFitnessExact = lowestFitnessExact;
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
		public List<Map<String, Double>> getComponentFitnessCache() {
			return componentFitnessCache;
		}
		public void setComponentFitnessCache(List<Map<String, Double>> componentFitnessCache) {
			this.componentFitnessCache = componentFitnessCache;
		}
		public List<List<Byte>> getNeighbors() {
			return neighbors;
		}
		public void setNeighbors(List<List<Byte>> neighbors) {
			this.neighbors = neighbors;
		}
		public NKGene<Boolean> getLowestFitnessGene() {
			return lowestFitnessGene;
		}
		public void setLowestFitnessGene(NKGene<Boolean> lowestFitnessGene) {
			this.lowestFitnessGene = lowestFitnessGene;
		}
		public Long getSeed() {
			return seed;
		}
		public void setSeed(Long seed) {
			this.seed = seed;
		}
		public Boolean getLowestFitnessExact() {
			return lowestFitnessExact;
		}
		public void setLowestFitnessExact(Boolean lowestFitnessExact) {
			this.lowestFitnessExact = lowestFitnessExact;
		}
	}
}
