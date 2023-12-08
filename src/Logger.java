import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
  public static boolean ENABLE_LOGGING = true;

  public static void log(String message) {

    if (ENABLE_LOGGING) {
      String timestamp = new SimpleDateFormat("hh:mm:ss").format(new Date());
      System.out.println("\u001B[36m" + timestamp + ": " + "\u001B[0m" + message);
    }
  }
}
