package net.phedny.valuemanager.data;

public interface AssetRateRetriever {

	void retrieve();

	AssetRate getAssetRate(String assetId);

	String[] getAssetRateIds();

}
