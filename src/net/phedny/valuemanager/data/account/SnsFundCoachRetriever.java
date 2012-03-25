package net.phedny.valuemanager.data.account;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.phedny.valuemanager.data.Account;
import net.phedny.valuemanager.data.AccountAsset;
import net.phedny.valuemanager.data.AccountRetriever;
import net.phedny.valuemanager.data.RetrieverException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

public class SnsFundCoachRetriever implements AccountRetriever {

	private static final Pattern FUND_ACCOUNT_LINE = Pattern
			.compile(".*<a href=\"#\" onClick=\"document.getElementById\\('form([0-9]*)'\\).submit\\(\\); return false;\">([0-9A-Z]*)</a>.*");

	private static final Pattern CASH_ACCOUNT_LINE = Pattern
			.compile(".*<a href=\"#\" onClick=\"document.getElementById\\('formCashAccount'\\).submit\\(\\); return false;\">([0-9]*).*");

	private static final Pattern CASH_AMOUNT_LINE = Pattern.compile(".*<td[^>]*>. ([0-9.,]*)</td>.*");

	private static final Pattern FUND_ID_LINE = Pattern
			.compile(".*<td[^>]*><a href=\"/alla_fonder/fondfakta/helper.asp\\?fund_id=([0-9]*)&BolagId=[0-9]*\" class=\"Fondlist\">[^<]*</a>&nbsp;<br></td>.*");

	private static final Pattern AMOUNT_LINE = Pattern.compile(".*<td[^>]*>([0-9.,]*)</td>.*");

	private final String username;

	private final String password;

	private Map<String, Account> accounts;

	public SnsFundCoachRetriever(String username, String password) {
		this.username = username;
		this.password = password;
	}

	@Override
	public Account getAccount(String accontId) {
		if (accounts == null) {
			return null;
		}
		return accounts.get(accontId);
	}

	@Override
	public String[] getAccountIds() {
		if (accounts == null) {
			return null;
		}
		Set<String> keySet = accounts.keySet();
		return keySet.toArray(new String[keySet.size()]);
	}

	@Override
	public void retrieve() throws RetrieverException {
		accounts = new HashMap<String, Account>();
		HttpClient httpClient = new DefaultHttpClient();
		httpClient.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY);
		InputStream contentStream = null;
		try {
			HttpContext context = new BasicHttpContext();
			HttpPost post = new HttpPost(
					"https://www.snsfundcoach.nl/login/index.asp?goto=%2Fditt%5Fkonto%2Findex%5Fmain%2Easp&navID=4100&");
			List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
			params.add(new BasicNameValuePair("txt_login", username));
			params.add(new BasicNameValuePair("txt_pass", password));
			params.add(new BasicNameValuePair("btn_login.x", "0"));
			params.add(new BasicNameValuePair("btn_login.y", "0"));
			params.add(new BasicNameValuePair("btn_login", "submit"));
			post.setEntity(new UrlEncodedFormEntity(params));
			HttpResponse response = httpClient.execute(post, context);
			if (response.getStatusLine().getStatusCode() != 302) {
				post.abort();
				return;
			}

			post.abort();

			HttpGet get = new HttpGet("https://www.snsfundcoach.nl/ditt_konto/deposit_overview.asp?NavID=4000");
			response = httpClient.execute(get, context);
			if (response.getStatusLine().getStatusCode() != 200) {
				get.abort();
				return;
			}

			HttpEntity entity = response.getEntity();
			contentStream = entity.getContent();
			InputStreamReader isReader = new InputStreamReader(contentStream, "UTF-8");
			BufferedReader reader = new BufferedReader(isReader);

			String cashAccountStr = null;
			String line;
			while ((line = reader.readLine()) != null) {
				Matcher m = FUND_ACCOUNT_LINE.matcher(line);
				if (m.matches()) {
					Account account = new FundAccount(m.group(1), m.group(2));
					accounts.put(account.getAccountId(), account);
					continue;
				}

				m = CASH_ACCOUNT_LINE.matcher(line);
				if (m.matches()) {
					cashAccountStr = m.group(1);
					continue;
				}

				m = CASH_AMOUNT_LINE.matcher(line);
				if (cashAccountStr != null && m.matches()) {
					Locale dutchLocale = new Locale("nl", "NL");
					NumberFormat numberParser = NumberFormat.getNumberInstance(dutchLocale);
					final String accountName = cashAccountStr;
					final String accountId = "net.phedny.valuemanager.sepa.NL44SNSB0000000000".substring(0,
							47 - accountName.length())
							+ accountName;
					Account account = new SimpleAccount(accountId, accountName, "EUR", numberParser.parse(m.group(1)));
					accounts.put(accountName, account);
				}

			}
			contentStream.close();
			contentStream = null;

			for (Account account : accounts.values()) {
				if (!(account instanceof FundAccount)) {
					continue;
				}

				FundAccount fundAccount = (FundAccount) account;
				post = new HttpPost("https://www.snsfundcoach.nl/ditt_konto/index_main.asp");
				params = new ArrayList<BasicNameValuePair>();
				params.add(new BasicNameValuePair("sel_deposit", fundAccount.getAccountId()));
				params.add(new BasicNameValuePair("navID", "4100"));
				post.setEntity(new UrlEncodedFormEntity(params));
				response = httpClient.execute(post, context);

				if (response.getStatusLine().getStatusCode() != 200) {
					post.abort();
					return;
				}

				entity = response.getEntity();
				contentStream = entity.getContent();
				isReader = new InputStreamReader(contentStream, "UTF-8");
				reader = new BufferedReader(isReader);

				String fundIdStr = null;
				String fundAmountStr = null;
				while ((line = reader.readLine()) != null) {
					Matcher m = FUND_ID_LINE.matcher(line);
					if (m.matches() && (line = reader.readLine()) != null) {
						fundIdStr = m.group(1);

						m = AMOUNT_LINE.matcher(line);
						if (m.matches()) {
							fundAmountStr = m.group(1);

							Locale dutchLocale = new Locale("nl", "NL");
							NumberFormat numberParser = NumberFormat.getNumberInstance(dutchLocale);
							final String fundId = "nl.snsfundcoach.fund." + fundIdStr;
							final Number fundAmount = numberParser.parse(fundAmountStr);
							AccountAsset asset = new AccountAsset() {

								@Override
								public Number getAmount() {
									return fundAmount;
								}

								@Override
								public String getAssetId() {
									return fundId;
								}

							};
							fundAccount.addAsset(asset);
						}
					}
				}
				contentStream.close();
				contentStream = null;
			}
			
			get = new HttpGet("https://www.snsfundcoach.nl/logout.asp");
			response = httpClient.execute(get, context);
			get.abort();

		} catch (ClientProtocolException e) {
			throw new RetrieverException(e);
		} catch (IOException e) {
			throw new RetrieverException(e);
		} catch (ParseException e) {
			throw new RetrieverException(e);
		} finally {
			if (contentStream != null) {
				try {
					contentStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private final class FundAccount implements Account {
		private final String depositId;
		private final String accountName;
		private final Map<String, AccountAsset> assets = new HashMap<String, AccountAsset>();

		private FundAccount(String depositId, String accountName) {
			this.depositId = "nl.snsfundcoach.deposit." + depositId;
			this.accountName = accountName;
		}

		@Override
		public String getAccountId() {
			return depositId;
		}

		@Override
		public String getAccountName() {
			return accountName;
		}

		@Override
		public AccountAsset getAsset(String assetId) {
			return assets.get(assetId);
		}

		@Override
		public String[] getAssetIds() {
			Set<String> keySet = assets.keySet();
			return keySet.toArray(new String[keySet.size()]);
		}

		void addAsset(AccountAsset asset) {
			assets.put(asset.getAssetId(), asset);
		}
	}

}
