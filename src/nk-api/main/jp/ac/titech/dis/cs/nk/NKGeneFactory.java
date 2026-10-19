package jp.ac.titech.dis.cs.nk;

import java.util.List;

public interface NKGeneFactory <TComponent>{
	/**
	 * generate gene by given loci string
	 * @param loci
	 * @param N
	 * @param K
	 * @return
	 */
	NKGene<TComponent> generateByLociString(List<TComponent> loci, Integer N, Integer K);
	/**
	 * return random gene with assigned fitness
	 * @param N
	 * @param K
	 * @return
	 */
	NKGene<TComponent> generateRandomGene(Integer N, Integer K);
	/**
	 * return new gene with replaced component at component index and new fitness value
	 * TODO sanity check whether the component is need to be changed or not  
	 * @param gene
	 * @param componentIndex with value from 0..N-1
	 * @param component
	 * @return
	 */
	NKGene<TComponent> mutateComponent(NKGene<TComponent> gene, Integer componentIndex, TComponent component);
}
