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
import net.phedny.valuemanager.data.AccountRetriever;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

public class AsnBankRetriever implements AccountRetriever {

	private static final Pattern ACCOUNT_LINE = Pattern
			.compile(".*<tr><td><span[^>]*><a[^>]*><b>([0-9.]*)</b></a></span></td><td>(?:[A-Za-z0-9 ]*)</td><td>rkh</td><td[^>]*>[0-9]*%<br/>-[0-9]*%</td><td[^>]*>&euro;&nbsp;([0-9.,]*)</td><td><a[^>]*><IMG[^>]*></A><a[^>]*><IMG[^>]*></A><a[^>]*><IMG[^>]*></A><a[^>]*><IMG[^>]*></A></td></tr>.*");

	private final String username;

	private final String password;

	private Map<String, Account> accounts;

	public AsnBankRetriever(String username, String password) {
		this.username = username;
		this.password = password;
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

	@Override
	public void retrieve() {
		accounts = new HashMap<String, Account>();
		DefaultHttpClient httpClient = new DefaultHttpClient();
		httpClient.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY);
		InputStream contentStream = null;
		try {
			HttpContext context = new BasicHttpContext();
			HttpPost post = new HttpPost("https://www.asnbank.nl/secure/bankieren/scripts/login/ProcessLogin.asp");
			List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
			params.add(new BasicNameValuePair("unfield", username));
			params.add(new BasicNameValuePair("wwfield", password));
			params.add(new BasicNameValuePair("Login", "Login"));
			params.add(new BasicNameValuePair("REQURL", ""));
			params.add(new BasicNameValuePair("method", ""));
			post.setEntity(new UrlEncodedFormEntity(params));

			HttpResponse response = httpClient.execute(post, context);
			if (response.getStatusLine().getStatusCode() != 302) {
				post.abort();
				return;
			}
			post.abort();
			
			HttpGet get = new HttpGet("https://www.asnbank.nl/secure/bankieren/scripts/sparen/oz_rekeningen.asp");
			response = httpClient.execute(get, context);
			if (response.getStatusLine().getStatusCode() != 200) {
				get.abort();
				return;
			}

			HttpEntity entity = response.getEntity();
			contentStream = entity.getContent();
			InputStreamReader isReader = new InputStreamReader(contentStream, "UTF-8");
			BufferedReader reader = new BufferedReader(isReader);

			String line;
			while ((line = reader.readLine()) != null) {
				Matcher m = ACCOUNT_LINE.matcher(line);
				if (m.matches()) {
					Locale dutchLocale = new Locale("nl", "NL");
					NumberFormat numberParser = NumberFormat.getNumberInstance(dutchLocale);
					final String accountName = m.group(1).replaceAll("\\.", "");
					final String accountId = "net.phedny.valuemanager.sepa.NL42SNSB0000000000".substring(0,
							47 - accountName.length());
					Account account = new SimpleAccount(accountId + accountName, accountName, "EUR", numberParser
							.parse(m.group(2)));
					accounts.put(accountName, account);
				}
			}
			contentStream.close();
			contentStream = null;
			
			get = new HttpGet("https://www.asnbank.nl/secure/bankieren/scripts/login/uitgelogd.asp");
			response = httpClient.execute(get, context);
			get.abort();

		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
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

}
