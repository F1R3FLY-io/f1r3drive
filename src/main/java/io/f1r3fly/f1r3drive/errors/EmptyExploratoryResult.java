package io.f1r3fly.f1r3drive.errors;

/** A successful exploratory query returned no data, rather than failing to read it. */
public class EmptyExploratoryResult extends F1r3DriveError {
  public EmptyExploratoryResult() {
    super("Exploratory query succeeded but returned no data");
  }
}
