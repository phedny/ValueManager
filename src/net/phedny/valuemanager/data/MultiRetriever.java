package net.phedny.valuemanager.data;

public interface MultiRetriever<T> {

	void initialize() throws RetrieverException;
	
	T[] getItems();
	
	void retrieve(T item) throws RetrieverException;
	
}
