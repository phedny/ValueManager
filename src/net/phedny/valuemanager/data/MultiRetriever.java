package net.phedny.valuemanager.data;

public interface MultiRetriever<T> {

	void initialize();
	
	T[] getItems();
	
	void retrieve(T item);
	
}
