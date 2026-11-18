package in.aitechapp.teluguoffline;

/** Preserve root causes rather than exposing only a failed Java class name. */
public final class JobFailure {
  public static String message(Throwable failure) {
    Throwable root = failure;
    for (int i = 0; i < 12 && root.getCause() != null && root.getCause() != root; i++)
      root = root.getCause();
    if (failure instanceof LinkageError || root instanceof LinkageError)
      return "Could not load the offline engine. Close and reopen the app after updating."
          + " Your models and recordings are kept.";
    String message = root.getMessage();
    return "Could not complete: "
        + (message == null || message.isBlank() ? root.getClass().getSimpleName() : message);
  }

  private JobFailure() {}
}
