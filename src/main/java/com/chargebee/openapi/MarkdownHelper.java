package com.chargebee.openapi;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

public class MarkdownHelper {

  public static String convertHtmlToMarkdown(String html) {
    FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder().build();
    return converter.convert(html).replaceAll("<br\\s*/?>", "");
  }
}
