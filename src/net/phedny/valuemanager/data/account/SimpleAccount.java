package net.phedny.valuemanager.data.account;

import net.phedny.valuemanager.data.Account;
import net.phedny.valuemanager.data.AccountAsset;

public class SimpleAccount implements Account {

	private final String accountId;
	private final String accountName;
	private final String assetId;
	private final Number amount;

	public SimpleAccount(String accountId, String accountName, String assetId, Number amount) {
		this.accountId = accountId;
		this.accountName = accountName;
		this.assetId = assetId;
		this.amount = amount;
	}

	@Override
	public String getAccountId() {
		return accountId;
	}

	@Override
	public String getAccountName() {
		return accountName;
	}

	@Override
	public AccountAsset getAsset(String assetId) {
		if (this.assetId.equals(assetId)) {
			return new AccountAsset() {

				@Override
				public Number getAmount() {
					return amount;
				}

				@Override
				public String getAssetId() {
					return SimpleAccount.this.assetId;
				}

			};
		}
		return null;
	}

	@Override
	public String[] getAssetIds() {
		return new String[] { assetId };
	}

}
