package net.phedny.valuemanager.data;

public interface AssetRateRetriever {

	void retrieve() throws RetrieverException;

	AssetRate getAssetRate(String assetId);

	String[] getAssetRateIds();

}
