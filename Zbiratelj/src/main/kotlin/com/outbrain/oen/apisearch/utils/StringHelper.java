package com.outbrain.oen.apisearch.utils;

import java.util.regex.Pattern;

public class StringHelper {

  private static final Pattern nonPrintableCharPattern = Pattern.compile("\\p{C}");

  public static String replaceNonPrintableChars(String s) {
    return nonPrintableCharPattern.matcher(s).replaceAll("");
  }
}
