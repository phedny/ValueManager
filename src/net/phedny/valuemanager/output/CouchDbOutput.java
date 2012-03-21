package net.phedny.valuemanager.output;

import java.io.IOException;
import java.util.Calendar;
import java.util.UUID;

import net.phedny.valuemanager.data.Account;
import net.phedny.valuemanager.data.AccountAsset;
import net.phedny.valuemanager.data.AccountRetriever;
import net.phedny.valuemanager.data.AssetRate;
import net.phedny.valuemanager.data.AssetRateRetriever;

import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.http.HttpClient;
import org.ektorp.http.StdHttpClient;
import org.ektorp.impl.StdCouchDbConnector;
import org.ektorp.impl.StdCouchDbInstance;

public class CouchDbOutput {

	private final String couchDbUrl;

	private final String database;

	public CouchDbOutput(String couchDbUrl, String database) {
		this.couchDbUrl = couchDbUrl;
		this.database = database;
	}

	public void storeAccounts(AccountRetriever accounts, AssetRateRetriever assetRates) throws IOException {
		Calendar now = Calendar.getInstance();

		HttpClient ektorpClient = new StdHttpClient.Builder().url(couchDbUrl).build();
		CouchDbInstance dbInstance = new StdCouchDbInstance(ektorpClient);
		CouchDbConnector db = new StdCouchDbConnector(database, dbInstance);

		for (String accountId : accounts.getAccountIds()) {
			Account account = accounts.getAccount(accountId);
			for (String assetId : account.getAssetIds()) {
				AccountAsset asset = account.getAsset(assetId);
				CouchDbAccount cAccount = new CouchDbAccount();
				cAccount.setId(UUID.randomUUID().toString());
				cAccount.setTimestamp(now);
				cAccount.setAccount(accountId);
				cAccount.setAsset(assetId);
				cAccount.setAmountInAsset(asset.getAmount());

				AssetRate assetRate = assetRates.getAssetRate(assetId);
				if (assetRate != null) {
					cAccount.setExpressedIn(assetRate.getExpressedIn());
					cAccount.setAssetRate(assetRate.getAssetValue());
				}

				// Store in CouchDB
				db.create(cAccount);
			}
		}
	}

}
