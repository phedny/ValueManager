package net.phedny.valuemanager.data;

public interface AccountRetriever {

	void retrieve();
	
	String[] getAccountIds();
	
	Account getAccount(String accountId);
	
}
