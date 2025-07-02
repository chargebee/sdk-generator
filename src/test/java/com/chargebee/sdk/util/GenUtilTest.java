package com.chargebee.sdk.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.chargebee.GenUtil;
import org.junit.jupiter.api.Test;

class GenUtilTest {
  @Test
  void testGetVarName() {
    assertEquals("johnDoe", GenUtil.getVarName("john", "doe"));
  }

  @Test
  void testToClazName() {
    assertEquals("JohnDoe", GenUtil.toClazName("john", "doe"));
  }

  @Test
  void testToCamelCase() {
    assertEquals("JohnDoe", GenUtil.toCamelCase("john", "doe"));
  }

  @Test
  void testToUnderScores() {
    assertEquals("john_doe", GenUtil.toUnderScores("johnDoe"));
  }

  @Test
  void testPluralize() {
    assertEquals("kites", GenUtil.pluralize("kite"));
  }
}
