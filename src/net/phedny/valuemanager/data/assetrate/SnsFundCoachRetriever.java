package net.phedny.valuemanager.data.assetrate;

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

import net.phedny.valuemanager.data.AssetRate;
import net.phedny.valuemanager.data.AssetRateRetriever;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

public class SnsFundCoachRetriever implements AssetRateRetriever {

	private static final Pattern RATE_LINE = Pattern
			.compile(".*<td[^>]*>(&#[0-9]*;|[A-Z]*)</td><td[^>]*><a[^>]*>([0-9.,]*)</a></td>.*");

	private static final Pattern NAME_LINE = Pattern
			.compile(".*<td><a href=\"(/fonds/[^\"]*)\" class=\"Fondlist\">([^<]*)</a></td>.*");

	private static final Pattern COMPARE_LINE = Pattern
			.compile(".*<td[^>]*><input[^>]*name=\"Compare\" value=\"([0-9]*)\"></td>.*");

	private final int rating;

	private Map<String, AssetRate> assetRates;

	public SnsFundCoachRetriever() {
		this.rating = -1;
	}

	public SnsFundCoachRetriever(int rating) {
		this.rating = rating;
	}

	@Override
	public AssetRate getAssetRate(String assetId) {
		if (assetRates == null) {
			return null;
		}
		return assetRates.get(assetId);
	}

	@Override
	public String[] getAssetRateIds() {
		if (assetRates == null) {
			return null;
		}
		Set<String> keySet = assetRates.keySet();
		return keySet.toArray(new String[keySet.size()]);
	}

	@Override
	public void retrieve() {
		assetRates = new HashMap<String, AssetRate>();
		if (rating == -1) {
			for (int i = 1; i < 6; i++) {
				retrieve(i);
			}
		} else {
			retrieve(rating);
		}
	}

	private void retrieve(int rating) {
		HttpClient httpClient = new DefaultHttpClient();
		InputStream contentStream = null;
		try {
			HttpPost post = new HttpPost("https://www.snsfundcoach.nl/alla_fonder/index_main.asp?navID=1000");
			List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
			params.add(new BasicNameValuePair("spec", "0"));
			params.add(new BasicNameValuePair("order", "1"));
			params.add(new BasicNameValuePair("bolag", "0"));
			params.add(new BasicNameValuePair("category", "0"));
			params.add(new BasicNameValuePair("ReturnRows", "0"));
			params.add(new BasicNameValuePair("StijlID", "0"));
			params.add(new BasicNameValuePair("LooptijdID", "0"));
			params.add(new BasicNameValuePair("KapID", "0"));
			params.add(new BasicNameValuePair("KredietwID", "0"));
			params.add(new BasicNameValuePair("Risk", "0"));
			params.add(new BasicNameValuePair("Rating", "0"));
			params.add(new BasicNameValuePair("ratMorID", String.valueOf(rating)));
			params.add(new BasicNameValuePair("Submit.x", "0"));
			params.add(new BasicNameValuePair("Submit.y", "0"));
			params.add(new BasicNameValuePair("period", "1"));
			post.setEntity(new UrlEncodedFormEntity(params));
			HttpResponse response = httpClient.execute(post);
			HttpEntity entity = response.getEntity();
			contentStream = entity.getContent();
			InputStreamReader isReader = new InputStreamReader(contentStream, "UTF-8");
			BufferedReader reader = new BufferedReader(isReader);

			String valueStr = null;
			String expressedInStr = null;
			String identifierStr = null;
			String nameStr = null;
			String line;
			while ((line = reader.readLine()) != null) {
				Matcher m = RATE_LINE.matcher(line);
				if (m.matches()) {
					expressedInStr = m.group(1);
					valueStr = m.group(2);
					continue;
				}

				m = NAME_LINE.matcher(line);
				if (m.matches()) {
					identifierStr = m.group(1);
					nameStr = m.group(2);
					continue;
				}

				m = COMPARE_LINE.matcher(line);
				if (m.matches()) {

					Locale dutchLocale = new Locale("nl", "NL");
					NumberFormat numberParser = NumberFormat.getNumberInstance(dutchLocale);
					final String assetId = "http://www.snsfundcoach.nl" + identifierStr;
					final String assetName = nameStr;
					final Number assetValue = numberParser.parse(valueStr);
					final String expressedIn;
					if ("&#8364;".equals(expressedInStr)) {
						expressedIn = "EUR";
					} else if ("&#36;".equals(expressedInStr)) {
						expressedIn = "USD";
					} else if ("&pound;".equals(expressedInStr)) {
						expressedIn = "GBP";
					} else if ("&yen;".equals(expressedInStr)) {
						expressedIn = "JPY";
					} else {
						expressedIn = expressedInStr;
					}

					AssetRate assetRate = new AssetRate() {

						@Override
						public String getAssetId() {
							return assetId;
						}

						@Override
						public String getAssetName() {
							return assetName;
						}

						@Override
						public String getExpressedIn() {
							return expressedIn;
						}

						@Override
						public Number getAssetValue() {
							return assetValue;
						}

					};
					assetRates.put(assetId, assetRate);
				}
			}

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
