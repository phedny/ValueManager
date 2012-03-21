package net.phedny.valuemanager.output;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAnyGetter;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

public class CouchDbAccount {

	private String id;

	private String revision;

	private Calendar timestamp;

	private String account;

	private String asset;

	private Number amountInAsset;

	private String expressedIn;

	private Number assetRate;

	@JsonProperty("_id")
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@JsonProperty("_rev")
	public String getRevision() {
		return revision;
	}

	public void setRevision(String revision) {
		this.revision = revision;
	}

	public Calendar getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Calendar timestamp) {
		this.timestamp = timestamp;
	}

	public String getAccount() {
		return account;
	}

	public void setAccount(String account) {
		this.account = account;
	}

	public String getAsset() {
		return asset;
	}

	public void setAsset(String asset) {
		this.asset = asset;
	}

	@JsonIgnore
	public Number getAmountInAsset() {
		return amountInAsset;
	}

	public void setAmountInAsset(Number amountInAsset) {
		this.amountInAsset = amountInAsset;
	}

	@JsonIgnore
	public String getExpressedIn() {
		return expressedIn;
	}

	public void setExpressedIn(String expressedIn) {
		this.expressedIn = expressedIn;
	}

	@JsonIgnore
	public Number getAssetRate() {
		return assetRate;
	}

	public void setAssetRate(Number assetRate) {
		this.assetRate = assetRate;
	}

	@JsonAnyGetter
	public Map<String, Object> getAmounts() {
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(getAsset(), getAmountInAsset());
		if (expressedIn != null && assetRate != null) {
			BigDecimal assetRate = new BigDecimal(getAssetRate().toString());
			BigDecimal totalValue = assetRate.multiply(new BigDecimal(getAmountInAsset().toString()));
			properties.put(expressedIn, totalValue);
			Map<String, Object> thisRate = new HashMap<String, Object>();
			thisRate.put(expressedIn, assetRate);
			properties.put("rate", thisRate);
		}
		return properties;
	}

}
