package net.phedny.valuemanager.data;

public class RetrieverException extends Exception {

	private final boolean permanent;

	public RetrieverException() {
		this(false);
	}

	public RetrieverException(boolean permanent) {
		this.permanent = permanent;
	}

	public RetrieverException(String message, Throwable cause, boolean permanent) {
		super(message, cause);
		this.permanent = permanent;
	}

	public RetrieverException(String message, boolean permanent) {
		super(message);
		this.permanent = permanent;
	}
	
	public RetrieverException(Throwable cause) {
		this(cause, false);
	}

	public RetrieverException(Throwable cause, boolean permanent) {
		super(cause);
		this.permanent = permanent;
	}

	public boolean isPermanent() {
		return permanent;
	}

}
