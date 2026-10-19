package jp.ac.titech.dis.cs.simulation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fasterxml.jackson.databind.ObjectMapper;

import jp.ac.titech.dis.cs.nk.NKUtils;

/**
 * Builds and persists the initial opinions every run starts from.
 *
 * <p>
 * Generation is seedable: {@link #generateRandom(Integer, Integer, Integer,
 * Integer, Long)} draws all opinions from one seeded stream, so a run can be
 * reproduced exactly. The unseeded overload still exists but draws a fresh seed
 * per call, which is what made the original batch generation impossible to
 * replay.
 * </p>
 */
public class InitialOpinionSetFactory {

	/**
	 * Generate a fresh random opinion set. Reproducibility note: this overload
	 * draws a new seed on every call; use the seeded overload to repeat a set.
	 */
	public InitialOpinionSet generateRandom(Integer N, Integer numDecisionMakers, Integer numObservers, Integer numDevilAdvocate) {
		return generateRandom(N, numDecisionMakers, numObservers, numDevilAdvocate, new Random().nextLong());
	}

	/**
	 * Generate an opinion set from the given seed: the same arguments and seed
	 * always produce byte-identical opinions.
	 */
	public InitialOpinionSet generateRandom(Integer N, Integer numDecisionMakers, Integer numObservers,
			Integer numDevilAdvocate, Long seed) {
		if(N == null || N < 1) {
			throw new IllegalArgumentException("N must be a positive number, got " + N);
		}
		if(numDecisionMakers == null || numDecisionMakers < 1) {
			throw new IllegalArgumentException("at least one decision maker is required, got " + numDecisionMakers);
		}
		if(numObservers == null || numObservers < 0) {
			throw new IllegalArgumentException("observer count must not be negative, got " + numObservers);
		}
		if(numDevilAdvocate == null || numDevilAdvocate < 0) {
			throw new IllegalArgumentException("devil advocate count must not be negative, got " + numDevilAdvocate);
		}
		if(seed == null) {
			throw new IllegalArgumentException("seed must not be null");
		}
		// one shared, seeded stream: order of generation is part of the contract
		Random random = new Random(seed);
		Map<String, List<Boolean>> decisionMakerOpinions = new HashMap<String, List<Boolean>>();
		for(int iter=1; iter <= numDecisionMakers; iter++) {
			String agentId = "DM"+iter;
			decisionMakerOpinions.put(agentId, NKUtils.generateRandomGene(N, random));
		}
		Map<String, List<Boolean>> observerOpinions = new HashMap<String, List<Boolean>>();
		for(int iter=1; iter <= numObservers; iter++) {
			String agentId = "OB"+iter;
			observerOpinions.put(agentId, NKUtils.generateRandomGene(N, random));
		}
		Map<String, List<Boolean>> devilAdvocateOpinions = new HashMap<String, List<Boolean>>();
		for(int iter=1; iter <= numDevilAdvocate; iter++) {
			String agentId = "DV"+iter;
			devilAdvocateOpinions.put(agentId, NKUtils.generateRandomGene(N, random));
		}
		return new InitialOpinionSet(decisionMakerOpinions, observerOpinions, devilAdvocateOpinions);
	}

	public InitialOpinionSet load(InitialOpinionSetFilePath filePath) {
		if(filePath == null) {
			throw new IllegalArgumentException("file path must not be null");
		}
		String path = filePath.toString();
		try {
			byte[] jsonData = Files.readAllBytes(Paths.get(path));
			ObjectMapper objectMapper = new ObjectMapper();
			return objectMapper.readValue(jsonData, InitialOpinionSet.class);
		} catch (IOException e) {
			throw new IllegalStateException("could not read initial opinion set from " + path, e);
		}
	}

	public void save(InitialOpinionSet initialOpinionSet, InitialOpinionSetFilePath initialOpinionFilePath) {
		if(initialOpinionSet == null) {
			throw new IllegalArgumentException("opinion set must not be null");
		}
		if(initialOpinionFilePath == null) {
			throw new IllegalArgumentException("file path must not be null");
		}
		String path = initialOpinionFilePath.toString();
		File target = new File(path);
		// the parent directory has to exist; writeValue does not create it
		File parent = target.getAbsoluteFile().getParentFile();
		if(parent != null && !parent.isDirectory() && !parent.mkdirs()) {
			throw new IllegalStateException("could not create directory " + parent);
		}
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			objectMapper.writeValue(target, initialOpinionSet);
		} catch (IOException e) {
			throw new IllegalStateException("could not write initial opinion set to " + path, e);
		}
	}
}
