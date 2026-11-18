package in.aitechapp.teluguoffline;

import android.content.Context;
import android.content.pm.PackageManager;

/** Installed package is the source of truth; never a hardcoded release label in a screen. */
public final class AppIdentity {
  public static boolean fresh(Context context) {
    return context.getPackageName().endsWith(".fresh");
  }

  public static String version(Context context) {
    try {
      String value =
          context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
      return value == null ? "Unknown version" : value;
    } catch (PackageManager.NameNotFoundException e) {
      return "Unknown version";
    }
  }

  public static String channel(Context context) {
    return "Local";
  }

  public static String label(Context context) {
    return version(context) + " · " + channel(context);
  }

  private AppIdentity() {}
}
