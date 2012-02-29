package net.phedny.valuemanager.data.assetrate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.phedny.valuemanager.data.AssetRate;
import net.phedny.valuemanager.data.AssetRateRetriever;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

public class SilverMountainRetriever implements AssetRateRetriever {

	private static final String ASSET_ID_GOLD = "XAU";

	private static final String ASSET_ID_SILVER = "XAG";

	private static final Pattern GOLD_RATE_LINE = Pattern
			.compile("<p>&euro; ([0-9.,]*)<br /> <a href=\"/nl/goudkoers/\">Goudkoers &gt;</a></p>");

	private static final Pattern SILVER_RATE_LINE = Pattern
			.compile("<p>&euro; ([0-9.,]*)<br /> <a href=\"/nl/zilverkoers/\">Zilverkoers &gt;</a></p>");

	private Number goldValue = null;

	private Number silverValue = null;

	@Override
	public AssetRate getAssetRate(String assetId) {
		if (ASSET_ID_GOLD.equals(assetId)) {
			return new AssetRate() {

				@Override
				public String getAssetId() {
					return ASSET_ID_GOLD;
				}

				@Override
				public String getAssetName() {
					return "Physical Gold";
				}

				@Override
				public String getExpressedIn() {
					return "EUR";
				}

				@Override
				public Number getAssetValue() {
					return goldValue;
				}
			};
		} else if (ASSET_ID_SILVER.equals(assetId)) {
			return new AssetRate() {

				@Override
				public String getAssetId() {
					return ASSET_ID_SILVER;
				}

				@Override
				public String getAssetName() {
					return "Physical Silver";
				}

				@Override
				public String getExpressedIn() {
					return "EUR";
				}

				@Override
				public Number getAssetValue() {
					return silverValue;
				}
			};
		}
		return null;
	}

	@Override
	public String[] getAssetRateIds() {
		return new String[] { ASSET_ID_GOLD, ASSET_ID_SILVER };
	}

	@Override
	public void retrieve() {
		HttpClient httpClient = new DefaultHttpClient();
		InputStream contentStream = null;
		try {
			HttpGet get = new HttpGet("https://www.thesilvermountain.nl/nl/goudkoers/");
			HttpResponse response = httpClient.execute(get);
			HttpEntity entity = response.getEntity();
			contentStream = entity.getContent();
			InputStreamReader isReader = new InputStreamReader(contentStream, "UTF-8");
			BufferedReader reader = new BufferedReader(isReader);

			String goldValueStr = null;
			String silverValueStr = null;
			String line;
			while ((line = reader.readLine()) != null) {
				Matcher m = GOLD_RATE_LINE.matcher(line);
				if (m.matches()) {
					goldValueStr = m.group(1);
					if (silverValueStr != null) {
						get.abort();
						break;
					}
					continue;
				}

				m = SILVER_RATE_LINE.matcher(line);
				if (m.matches()) {
					silverValueStr = m.group(1);
					if (goldValueStr != null) {
						get.abort();
						break;
					}
					continue;
				}
			}

			if (goldValueStr == null || silverValueStr == null) {
				goldValue = null;
				silverValue = null;
				return;
			}

			Locale dutchLocale = new Locale("nl", "NL");
			NumberFormat numberParser = NumberFormat.getNumberInstance(dutchLocale);
			goldValue = numberParser.parse(goldValueStr);
			silverValue = numberParser.parse(silverValueStr);

		} catch (ClientProtocolException e) {
			e.printStackTrace();
			goldValue = null;
			silverValue = null;
		} catch (IOException e) {
			e.printStackTrace();
			goldValue = null;
			silverValue = null;
		} catch (ParseException e) {
			e.printStackTrace();
			goldValue = null;
			silverValue = null;
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
