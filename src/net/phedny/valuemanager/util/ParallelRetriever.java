package net.phedny.valuemanager.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.phedny.valuemanager.data.Account;
import net.phedny.valuemanager.data.AccountRetriever;
import net.phedny.valuemanager.data.AssetRate;
import net.phedny.valuemanager.data.AssetRateRetriever;
import net.phedny.valuemanager.data.MultiRetriever;
import net.phedny.valuemanager.data.RetrieverException;

public class ParallelRetriever implements AccountRetriever, AssetRateRetriever {

	private Set<AccountRetriever> accountRetrievers = new HashSet<AccountRetriever>();

	private Set<AssetRateRetriever> assetRateRetrievers = new HashSet<AssetRateRetriever>();
	
	private Set<MultiRetriever<?>> multiRetrievers = new HashSet<MultiRetriever<?>>();

	private Set<Callable<Void>> retrieverCallables = new HashSet<Callable<Void>>();

	private Map<String, AccountRetriever> accounts = new HashMap<String, AccountRetriever>();

	private Map<String, AssetRateRetriever> assetRates = new HashMap<String, AssetRateRetriever>();

	private Set<RetrieverException> permanentExceptions;

	private Set<RetrieverException> volatileExceptions;

	private final boolean ignorePermanentExceptions;

	private final boolean ignoreVolatileExceptions;

	public ParallelRetriever() {
		ignorePermanentExceptions = false;
		ignoreVolatileExceptions = false;
	}

	public ParallelRetriever(boolean ignorePermanentExceptions, boolean ignoreVolatileExceptions) {
		this.ignorePermanentExceptions = ignorePermanentExceptions;
		this.ignoreVolatileExceptions = ignoreVolatileExceptions;
	}

	private <T> boolean addMultiRetriever(final Object retriever) {
		if (retriever instanceof MultiRetriever<?>) {
			@SuppressWarnings("unchecked")
			final MultiRetriever<T> multiRetriever = (MultiRetriever<T>) retriever;
			multiRetrievers.add(multiRetriever);
			for (final T item : multiRetriever.getItems()) {
				retrieverCallables.add(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						multiRetriever.retrieve(item);
						return null;
					}
				});
			}
			return true;
		} else {
			return false;
		}
	}

	public <T> void addRetriever(final AccountRetriever retriever) {
		accountRetrievers.add(retriever);
		if (!addMultiRetriever(retriever)) {
			retrieverCallables.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					retriever.retrieve();
					return null;
				}
			});
		}
	}

	public void addRetriever(final AssetRateRetriever retriever) {
		assetRateRetrievers.add(retriever);
		if (!addMultiRetriever(retriever)) {
			retrieverCallables.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					retriever.retrieve();
					return null;
				}
			});
		}
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
	public void retrieve() throws RetrieverException {
		for (MultiRetriever<?> retriever : multiRetrievers) {
			retriever.initialize();
		}
		
		try {
			ExecutorService executor = Executors.newCachedThreadPool();
			List<Future<Void>> futures = executor.invokeAll(retrieverCallables);
			executor.shutdown();

			permanentExceptions = new HashSet<RetrieverException>();
			volatileExceptions = new HashSet<RetrieverException>();
			for (Future<Void> future : futures) {
				try {
					future.get();
				} catch (ExecutionException e) {
					Throwable cause = e.getCause();
					if (cause != null && cause instanceof RetrieverException) {
						RetrieverException re = (RetrieverException) cause;
						if (re.isPermanent()) {
							permanentExceptions.add(re);
						} else {
							volatileExceptions.add(re);
						}
					} else if (cause != null) {
						volatileExceptions.add(new RetrieverException(cause));
					} else {
						volatileExceptions.add(new RetrieverException(e));
					}
				}
			}

			if (!ignorePermanentExceptions || !ignoreVolatileExceptions) {
				if (!volatileExceptions.isEmpty()) {
					throw new RetrieverException("One or more retrievers threw a volatile exception");
				} else if (!permanentExceptions.isEmpty()) {
					throw new RetrieverException("One or more retrievers threw a permanent exception", true);
				}
			}

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

	public RetrieverException[] getPermanentExceptions() {
		if (permanentExceptions == null) {
			return null;
		} else {
			return permanentExceptions.toArray(new RetrieverException[permanentExceptions.size()]);
		}
	}

	public RetrieverException[] getVolatileExceptions() {
		if (volatileExceptions == null) {
			return null;
		} else {
			return volatileExceptions.toArray(new RetrieverException[volatileExceptions.size()]);
		}
	}

}
