package net.phedny.valuemanager.data.account;

import net.phedny.valuemanager.data.Account;
import net.phedny.valuemanager.data.AccountRetriever;

public class StaticAccountRetriever implements AccountRetriever {

	private final Account account;

	public StaticAccountRetriever(Account account) {
		this.account = account;
	}

	public StaticAccountRetriever(String accountId, String accountName, String assetId, Number amount) {
		account = new SimpleAccount(accountId, accountName, assetId, amount);
	}

	@Override
	public Account getAccount(String accountId) {
		if (account.getAccountId().equals(accountId)) {
			return account;
		}
		return null;
	}

	@Override
	public String[] getAccountIds() {
		return new String[] { account.getAccountId() };
	}

	@Override
	public void retrieve() {
		// Nothing to do
	}

}
