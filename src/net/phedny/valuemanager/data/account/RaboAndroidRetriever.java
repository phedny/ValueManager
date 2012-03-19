package net.phedny.valuemanager.data.account;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.phedny.valuemanager.data.Account;
import net.phedny.valuemanager.data.AccountRetriever;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

public class RaboAndroidRetriever implements AccountRetriever {

	private static final String LOGON_INFO = "<?xml version='1.0' encoding='UTF-8' ?><LogonInfo xmlns=\"https://bankservices.rabobank.nl/auth/logoninfo/v1/request\"><EntranceCode>%s</EntranceCode><RequestedService>%s</RequestedService></LogonInfo>";

	private static final String AC_LOGIN = "<?xml version='1.0' encoding='UTF-8' ?><LogonAc xmlns=\"https://bankservices.rabobank.nl/services/auth/logonac/v2/request\"><AccessCode>%s</AccessCode><BankAccountNumber>%s</BankAccountNumber><LogonToken>%s</LogonToken><SetUserDeviceAccess>1</SetUserDeviceAccess></LogonAc>";

	private static final String LOGOFF = "<?xml version='1.0' encoding='UTF-8' ?><Logoff xmlns=\"https://bankservices.rabobank.nl/auth/logoff/v2/request\"><LogoffBy>A</LogoffBy></Logoff>";

	private static final Pattern LOGON_TOKEN = Pattern.compile(".*<LogonToken>([0-9]*)</LogonToken>.*");

	private static final Pattern ACCOUNT_LINE = Pattern
			.compile("<Account><Number>([0-9]*)</Number><ProductName>([A-Za-z0-9 ]*)</ProductName><Currency>([A-Z]*)</Currency><Balance>([0-9]*\\.[0-9]*)</Balance><SpendingLimit>([0-9]*\\.[0-9]*)</SpendingLimit><TransactionInformation/></Account>");

	private final String accountNumber;

	private final String accessCode;

	private String registrationCookie;

	private Map<String, Account> accounts;

	public RaboAndroidRetriever(String accountNumber, String accessCode, String registrationCookie) {
		this.accountNumber = accountNumber;
		this.accessCode = accessCode;
		this.registrationCookie = registrationCookie;
	}

	@Override
	public Account getAccount(String accountId) {
		if (accounts == null) {
			return null;
		}
		return accounts.get(accountId);
	}

	@Override
	public String[] getAccountIds() {
		if (accounts == null) {
			return null;
		}
		Set<String> keySet = accounts.keySet();
		return keySet.toArray(new String[keySet.size()]);
	}

	private boolean checkAppStatus(DefaultHttpClient httpClient, HttpContext context) throws IOException {
		HttpGet get = new HttpGet("https://bankservices.rabobank.nl/appstatus/android/2.0.1-status.xml");
		get.setHeader("Content-Type", "application/xml");
		get.setHeader("Accept", "application/xml");

		HttpResponse response = httpClient.execute(get, context);
		if (response.getStatusLine().getStatusCode() != 200) {
			get.abort();
			return false;
		}

		get.abort();
		return true;
	}

	private String requestLogonToken(DefaultHttpClient httpClient, HttpContext context, String service)
			throws IOException {
		HttpPost post = new HttpPost("https://bankservices.rabobank.nl/auth/logoninfo/v1/");
		final Calendar nowGmt = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		final String formattedDate = new SimpleDateFormat("yyyyMMddHHmm").format(nowGmt.getTime());
		final String requestXml = String.format(LOGON_INFO, formattedDate, service);
		StringEntity stringEntity = new StringEntity(requestXml, "application/xml", "UTF-8");
		post.setEntity(stringEntity);
		post.setHeader("Content-Type", "application/xml");
		post.setHeader("Accept", "application/xml");

		HttpResponse response = httpClient.execute(post, context);
		if (response.getStatusLine().getStatusCode() != 200) {
			post.abort();
			return null;
		}

		InputStream contentStream = null;
		try {
			HttpEntity entity = response.getEntity();
			contentStream = entity.getContent();
			InputStreamReader isReader = new InputStreamReader(contentStream, "UTF-8");
			BufferedReader reader = new BufferedReader(isReader);

			String line;
			String logonToken = null;
			while ((line = reader.readLine()) != null) {
				Matcher m = LOGON_TOKEN.matcher(line);
				if (m.matches()) {
					logonToken = m.group(1);
				}
			}

			return logonToken;
		} finally {
			if (contentStream != null) {
				contentStream.close();
			}
		}
	}

	private boolean loginWithAccessCode(DefaultHttpClient httpClient, HttpContext context, String bankAccount,
			String accessCode) throws IOException {
		String logonToken = requestLogonToken(httpClient, context,
				"https://bankservices.rabobank.nl/services/balanceviewsettings/v1");

		HttpPost post = new HttpPost("https://bankservices.rabobank.nl/auth/logonac/v2/");
		final String requestXml = String.format(AC_LOGIN, accessCode, bankAccount, logonToken);
		StringEntity stringEntity = new StringEntity(requestXml, "application/xml", "UTF-8");
		post.setEntity(stringEntity);
		post.setHeader("Content-Type", "application/xml");
		post.setHeader("Accept", "application/xml");

		HttpResponse response = httpClient.execute(post, context);
		// if (response.getStatusLine().getStatusCode() != 200) {
		// post.abort();
		// return false;
		// }

		InputStream contentStream = null;
		try {
			HttpEntity entity = response.getEntity();
			contentStream = entity.getContent();
			InputStreamReader isReader = new InputStreamReader(contentStream, "UTF-8");
			BufferedReader reader = new BufferedReader(isReader);

			String line;
			while ((line = reader.readLine()) != null) {
//				System.out.println(line);
			}
		} finally {
			if (contentStream != null) {
				contentStream.close();
			}
		}

		// post.abort();
		return true;
	}

	private List<Account> retrieveAccounts(DefaultHttpClient httpClient, HttpContext context) throws IOException {
		List<Account> accountList = new ArrayList<Account>();
		HttpGet get = new HttpGet("https://bankservices.rabobank.nl/services/productoverview/v1");
		get.setHeader("Content-Type", "application/xml");
		get.setHeader("Accept", "application/xml");

		HttpResponse response = httpClient.execute(get, context);
		if (response.getStatusLine().getStatusCode() != 200) {
			get.abort();
			return null;
		}

		InputStream contentStream = null;
		try {
			HttpEntity entity = response.getEntity();
			contentStream = entity.getContent();
			InputStreamReader isReader = new InputStreamReader(contentStream, "UTF-8");
			BufferedReader reader = new BufferedReader(isReader);

			String line;
			while ((line = reader.readLine()) != null) {
				Matcher m = ACCOUNT_LINE.matcher(line);
				while (m.find()) {
					Locale dutchLocale = new Locale("en", "US");
					NumberFormat numberParser = NumberFormat.getNumberInstance(dutchLocale);
					final String accountName = m.group(1);
					final String accountId = "net.phedny.valuemanager.sepa.NL57RABO0000000000".substring(0,
							47 - accountName.length());
					Account account = new SimpleAccount(accountId + accountName, accountName, m.group(3), numberParser
							.parse(m.group(4)));
					accountList.add(account);
				}
			}

			return accountList;
		} catch (ParseException e) {
			e.printStackTrace();
		} finally {
			if (contentStream != null) {
				contentStream.close();
			}
		}
		return null;
	}

	private void logoff(DefaultHttpClient httpClient, HttpContext context) throws IOException {
		HttpPost post = new HttpPost("https://bankservices.rabobank.nl/auth/logoff/v2/");
		final String requestXml = String.format(LOGOFF);
		StringEntity stringEntity = new StringEntity(requestXml, "application/xml", "UTF-8");
		post.setEntity(stringEntity);
		post.setHeader("Content-Type", "application/xml");
		post.setHeader("Accept", "application/xml");

		HttpResponse response = httpClient.execute(post, context);
		if (response.getStatusLine().getStatusCode() != 200) {
			post.abort();
			return;
		}

		post.abort();
	}

	@Override
	public void retrieve() {
		DefaultHttpClient httpClient = new DefaultHttpClient();
		httpClient.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY);
		HttpContext context = new BasicHttpContext();

		accounts = new HashMap<String, Account>();
		try {
			CookieStore cookieStore = httpClient.getCookieStore();
			BasicClientCookie newCookie = new BasicClientCookie("XPRDMBP1E", registrationCookie);
			newCookie.setVersion(0);
			newCookie.setDomain("bankservices.rabobank.nl");
			newCookie.setPath("/");
			cookieStore.addCookie(newCookie);

			if (!checkAppStatus(httpClient, context)) {
				System.err.println("App status not OK");
				return;
			}

			if (!loginWithAccessCode(httpClient, context, accountNumber, accessCode)) {
				System.err.println("Login with access code not OK");
				return;
			}

			List<Account> accountList = retrieveAccounts(httpClient, context);

			logoff(httpClient, context);

			accounts = new HashMap<String, Account>();
			for (Account account : accountList) {
				accounts.put(account.getAccountName(), account);
			}

			for (Cookie cookie : httpClient.getCookieStore().getCookies()) {
				if ("XPRDMBP1E".equals(cookie.getName())) {
					registrationCookie = cookie.getValue();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String getRegistrationCookie() {
		return registrationCookie;
	}
}
