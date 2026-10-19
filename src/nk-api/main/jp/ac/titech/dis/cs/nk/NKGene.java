package jp.ac.titech.dis.cs.nk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A gene (one point) of an NK landscape, together with its fitness.
 *
 * <p>
 * Instances are immutable: the locus list is copied on the way in and exposed as
 * an unmodifiable view on the way out, so a gene can safely be shared between
 * agents (opinions are handed around by reference) without one holder observing
 * another holder's edits. Use
 * {@link NKGeneFactory#mutateComponent(NKGene, Integer, Object)} to obtain a
 * modified copy.
 * </p>
 *
 * <p>
 * The setters are public only so that Jackson can (de)serialise landscapes; they
 * are not part of the model's behaviour and must not be used to edit a gene in
 * place.
 * </p>
 *
 * @author Muqtafi Akhmad
 *
 * @param <TComponent> type of a single locus value
 */
public class NKGene <TComponent>{
	private List<TComponent> internalComponents;
	private Integer N;
	private Integer K;
	private Double fitness;

	public NKGene() {
	}

	public NKGene(List<TComponent> internalComponents, Integer n, Integer k, Double fitness) {
		super();
		// defensive copy: the caller keeps ownership of its list
		this.internalComponents = (internalComponents == null) ? null
				: new ArrayList<TComponent>(internalComponents);
		this.N = n;
		this.K = k;
		this.fitness = fitness;
	}

	/**
	 * @return an unmodifiable view of this gene's loci
	 */
	public List<TComponent> getInternalComponents() {
		return (internalComponents == null) ? null
				: Collections.unmodifiableList(internalComponents);
	}

	public Integer getN() {
		return N;
	}

	public Integer getK() {
		return K;
	}

	public Double getFitness() {
		return fitness;
	}

	public void setInternalComponents(List<TComponent> internalComponents) {
		// defensive copy so the caller's list cannot be mutated behind our back
		this.internalComponents = (internalComponents == null) ? null
				: new ArrayList<TComponent>(internalComponents);
	}

	public void setN(Integer n) {
		N = n;
	}

	public void setK(Integer k) {
		K = k;
	}

	public void setFitness(Double fitness) {
		this.fitness = fitness;
	}

	@Override
	public String toString() {
		return "NKGene{N=" + this.N + ", K=" + this.K + ", loci=" + this.internalComponents
				+ ", fitness=" + this.fitness + "}";
	}
}
