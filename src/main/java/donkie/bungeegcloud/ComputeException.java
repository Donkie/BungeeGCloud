package donkie.bungeegcloud;

public class ComputeException extends Exception {
	/**
     *
     */
    private static final long serialVersionUID = 1L;

    private String code = null;
    private String message = null;

    public ComputeException(String errmsg, String code, String message) {
        super(errmsg);

        this.code = code;
        this.message = message;
	}

	public ComputeException() {
        super();
    }

    public String getCode(){
        return code;
    }

    public String getMessage(){
        return message;
    }
}
