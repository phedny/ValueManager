package net.phedny.valuemanager;

import net.phedny.valuemanager.data.AssetRate;
import net.phedny.valuemanager.data.AssetRateRetriever;
import net.phedny.valuemanager.data.assetrate.SilverMountainAssetRateRetriever;
import net.phedny.valuemanager.data.assetrate.SnsFundCoachRateRetriever;

public class ValueManager {

	public static void main(String[] args) {
		AssetRateRetriever assetRateRetrievers[] = { new SilverMountainAssetRateRetriever(),
				new SnsFundCoachRateRetriever() };

		for (AssetRateRetriever retriever : assetRateRetrievers) {
			retriever.retrieve();
			for (String assetId : retriever.getAssetRateIds()) {
				AssetRate assetRate = retriever.getAssetRate(assetId);
				System.out.format("%s: %s %s [%s]\n", assetRate.getAssetName(), assetRate.getExpressedIn(), assetRate
						.getAssetValue(), assetRate.getAssetId());
			}
		}
	}
}
