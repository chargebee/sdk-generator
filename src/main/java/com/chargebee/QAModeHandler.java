package com.chargebee;

public class QAModeHandler {
  private static QAModeHandler instance;
  private boolean value = false;

  private QAModeHandler() {}

  public static QAModeHandler getInstance() {
    if (instance == null) {
      instance = new QAModeHandler();
    }
    return instance;
  }

  public boolean getValue() {
    return this.value;
  }

  public void setValue(boolean value) {
    this.value = value;
  }
}
