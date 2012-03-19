package net.phedny.valuemanager.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.phedny.valuemanager.data.Account;
import net.phedny.valuemanager.data.AccountRetriever;
import net.phedny.valuemanager.data.AssetRate;
import net.phedny.valuemanager.data.AssetRateRetriever;

public class ParallelRetriever implements AccountRetriever, AssetRateRetriever {

	private Set<AccountRetriever> accountRetrievers = new HashSet<AccountRetriever>();

	private Set<AssetRateRetriever> assetRateRetrievers = new HashSet<AssetRateRetriever>();

	private Set<Callable<Void>> retrieverCallables = new HashSet<Callable<Void>>();

	private Map<String, AccountRetriever> accounts = new HashMap<String, AccountRetriever>();

	private Map<String, AssetRateRetriever> assetRates = new HashMap<String, AssetRateRetriever>();

	public void addRetriever(final AccountRetriever retriever) {
		accountRetrievers.add(retriever);
		retrieverCallables.add(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				retriever.retrieve();
				return null;
			}
		});
	}

	public void addRetriever(final AssetRateRetriever retriever) {
		assetRateRetrievers.add(retriever);
		retrieverCallables.add(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				retriever.retrieve();
				return null;
			}
		});
	}

	@Override
	public Account getAccount(String accountId) {
		if (accounts == null) {
			return null;
		}
		AccountRetriever retriever = accounts.get(accountId);
		if (retriever == null) {
			return null;
		}
		return retriever.getAccount(accountId);
	}

	@Override
	public String[] getAccountIds() {
		if (accounts == null) {
			return null;
		}
		return accounts.keySet().toArray(new String[accounts.size()]);
	}

	@Override
	public AssetRate getAssetRate(String assetId) {
		if (assetRates == null) {
			return null;
		}
		AssetRateRetriever retriever = assetRates.get(assetId);
		if (retriever == null) {
			return null;
		}
		return retriever.getAssetRate(assetId);
	}

	@Override
	public String[] getAssetRateIds() {
		if (assetRates == null) {
			return null;
		}
		return assetRates.keySet().toArray(new String[assetRates.size()]);
	}

	@Override
	public void retrieve() {
		try {
			ExecutorService executor = Executors.newCachedThreadPool();
			executor.invokeAll(retrieverCallables);
			executor.shutdown();

			accounts = new HashMap<String, AccountRetriever>();
			for (AccountRetriever retriever : accountRetrievers) {
				for (String accountId : retriever.getAccountIds()) {
					accounts.put(accountId, retriever);
				}
			}

			assetRates = new HashMap<String, AssetRateRetriever>();
			for (AssetRateRetriever retriever : assetRateRetrievers) {
				for (String assetRateId : retriever.getAssetRateIds()) {
					assetRates.put(assetRateId, retriever);
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
