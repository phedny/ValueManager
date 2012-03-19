package net.phedny.valuemanager.data.account;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

public class RaboAndroidRegistrationClient {

	private static final String LOGON_INFO = "<?xml version='1.0' encoding='UTF-8' ?><LogonInfo xmlns=\"https://bankservices.rabobank.nl/auth/logoninfo/v1/request\"><EntranceCode>%s</EntranceCode><RequestedService>%s</RequestedService></LogonInfo>";

	private static final String RR_LOGIN = "<?xml version='1.0' encoding='UTF-8' ?><LogonRr xmlns=\"https://bankservices.rabobank.nl/auth/logonrr/v2/request\"><BankAccountNumber>%s</BankAccountNumber><BankCardNumber>%s</BankCardNumber><LogonToken>%s</LogonToken><UserAuthenticationCode>%s</UserAuthenticationCode></LogonRr>";

	private static final String LOGOFF = "<?xml version='1.0' encoding='UTF-8' ?><Logoff xmlns=\"https://bankservices.rabobank.nl/auth/logoff/v2/request\"><LogoffBy>A</LogoffBy></Logoff>";

	private static final Pattern LOGON_TOKEN = Pattern.compile(".*<LogonToken>([0-9]*)</LogonToken>.*");

	private static final Pattern MB_ACTIVE = Pattern
			.compile(".*<MobileBankingActive>(true|false)</MobileBankingActive>.*");

	private static final Pattern AC_ACTIVE = Pattern.compile(".*<AccesscodeActive>(true|false)</AccesscodeActive>.*");

	private static final Pattern RF_ACTIVE = Pattern.compile(".*<RabofoonActive>(true|false)</RabofoonActive>.*");

	private final String accountNumber;

	private final String cardNumber;

	private String registrationCookie;

	public RaboAndroidRegistrationClient(String accountNumber, String cardNumber) {
		this.accountNumber = accountNumber;
		this.cardNumber = cardNumber;
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

	private boolean loginWithRandomReader(DefaultHttpClient httpClient, HttpContext context, String bankAccount,
			String cardNumber, String accessCode) throws IOException {
		String logonToken = requestLogonToken(httpClient, context,
				"https://bankservices.rabobank.nl/services/getactivesubscriptions/v1");

		HttpPost post = new HttpPost("https://bankservices.rabobank.nl/auth/logonrr/v2/");
		final String requestXml = String.format(RR_LOGIN, bankAccount, cardNumber, logonToken, accessCode);
		StringEntity stringEntity = new StringEntity(requestXml, "application/xml", "UTF-8");
		post.setEntity(stringEntity);
		post.setHeader("Content-Type", "application/xml");
		post.setHeader("Accept", "application/xml");

		HttpResponse response = httpClient.execute(post, context);
		if (response.getStatusLine().getStatusCode() != 200) {
			post.abort();
			return false;
		}

		post.abort();
		return true;
	}

	private boolean checkActiveSubscriptions(DefaultHttpClient httpClient, HttpContext context) throws IOException {
		HttpGet get = new HttpGet("https://bankservices.rabobank.nl/services/getactivesubscriptions/v1");
		get.setHeader("Content-Type", "application/xml");
		get.setHeader("Accept", "application/xml");

		HttpResponse response = httpClient.execute(get, context);
		if (response.getStatusLine().getStatusCode() != 200) {
			get.abort();
			return false;
		}

		InputStream contentStream = null;
		try {
			HttpEntity entity = response.getEntity();
			contentStream = entity.getContent();
			InputStreamReader isReader = new InputStreamReader(contentStream, "UTF-8");
			BufferedReader reader = new BufferedReader(isReader);

			String line;
			boolean mobileBankingActive = false;
			boolean accesscodeActive = false;
			boolean rabofoonActive = false;
			while ((line = reader.readLine()) != null) {
				Matcher m = MB_ACTIVE.matcher(line);
				if (m.matches()) {
					mobileBankingActive = "true".equals(m.group(1));
				}
				m = AC_ACTIVE.matcher(line);
				if (m.matches()) {
					accesscodeActive = "true".equals(m.group(1));
				}
				m = RF_ACTIVE.matcher(line);
				if (m.matches()) {
					rabofoonActive = "true".equals(m.group(1));
				}
			}

			return mobileBankingActive && accesscodeActive && rabofoonActive;
		} finally {
			if (contentStream != null) {
				contentStream.close();
			}
		}
	}

	private boolean registerDevice(DefaultHttpClient httpClient, HttpContext context) throws IOException {
		HttpGet get = new HttpGet("https://bankservices.rabobank.nl/services/deviceregistration/v1");
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

	public void registerDevice(String randomReaderCode) {
		DefaultHttpClient httpClient = new DefaultHttpClient();
		httpClient.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY);
		HttpContext context = new BasicHttpContext();
		try {
			if (!checkAppStatus(httpClient, context)) {
				System.err.println("App status not OK");
				return;
			}

			if (!loginWithRandomReader(httpClient, context, accountNumber, cardNumber, randomReaderCode)) {
				System.err.println("Login with random reader not OK");
				return;
			}

			if (!checkActiveSubscriptions(httpClient, context)) {
				System.err.println("Active subscriptions not OK");
				return;
			}

			if (!registerDevice(httpClient, context)) {
				System.err.println("Device not registered");
				return;
			}

			logoff(httpClient, context);

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
