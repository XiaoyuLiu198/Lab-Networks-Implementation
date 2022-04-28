@SuppressWarnings("serial")
public class UnexpectedFlagException extends Exception {
  public UnexpectedFlagException(String message) {
    super(message);
  }

  public UnexpectedFlagException() {
    super();
  }

  public UnexpectedFlagException(String message, TCPsegment segment) {
    super(message + "Got Syn" + segment.syn + ", Ack: " + segment.ack + ", Fin: "
        + segment.fin + ", dataLength: " + segment.dataLength);
  }
}
