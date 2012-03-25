package net.phedny.valuemanager.data;

public interface AccountRetriever {

	void retrieve() throws RetrieverException;
	
	String[] getAccountIds();
	
	Account getAccount(String accountId);
	
}
