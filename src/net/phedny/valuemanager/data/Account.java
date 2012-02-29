package net.phedny.valuemanager.data;

public interface Account {

	String getAccountId();
	
	String getAccountName();
	
	String[] getAssetIds();
	
	AccountAsset getAsset(String assetId);
	
}
