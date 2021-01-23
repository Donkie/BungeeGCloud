package donkie.bungeegcloud;

public class ServiceException extends Exception {
	/**
     *
     */
    private static final long serialVersionUID = 1L;

    private String code = null;
    private String message = null;

    public ServiceException(String errmsg, String code, String message) {
        super(errmsg);

        this.code = code;
        this.message = message;
	}

	public ServiceException() {
        super();
    }

    public String getCode(){
        return code;
    }

    public String getMessage(){
        return message;
    }
}
