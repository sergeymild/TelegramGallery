/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tangxiaolv.telegramgallery.exoplayer2.text.webvtt;

import android.text.TextUtils;
import com.tangxiaolv.telegramgallery.exoplayer2.util.ColorParser;
import com.tangxiaolv.telegramgallery.exoplayer2.util.ParsableByteArray;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides a CSS parser for STYLE blocks in Webvtt files. Supports only a subset of the CSS
 * features.
 */
/* package */ final class CssParser {

  private static final String PROPERTY_BGCOLOR = "background-color";
  private static final String PROPERTY_FONT_FAMILY = "font-family";
  private static final String PROPERTY_FONT_WEIGHT = "font-weight";
  private static final String PROPERTY_TEXT_DECORATION = "text-decoration";
  private static final String VALUE_BOLD = "bold";
  private static final String VALUE_UNDERLINE = "underline";
  private static final String BLOCK_START = "{";
  private static final String BLOCK_END = "}";
  private static final String PROPERTY_FONT_STYLE = "font-style";
  private static final String VALUE_ITALIC = "italic";

  private static final Pattern VOICE_NAME_PATTERN = Pattern.compile("\\[voice=\"([^\"]*)\"\\]");

  // Temporary utility data structures.
  private final ParsableByteArray styleInput;
  private final StringBuilder stringBuilder;

  public CssParser() {
    styleInput = new ParsableByteArray();
    stringBuilder = new StringBuilder();
  }

  /**
   * Takes a CSS style block and consumes up to the first empty line found. Attempts to parse the
   * contents of the style block and returns a {@link WebvttCssStyle} instance if successful, or
   * {@code null} otherwise.
   *
   * @param input The input from which the style block should be read.
   * @return A {@link WebvttCssStyle} that represents the parsed block.
   */
  public WebvttCssStyle parseBlock(ParsableByteArray input) {
    stringBuilder.setLength(0);
    int initialInputPosition = input.getPosition();
    skipStyleBlock(input);
    styleInput.reset(input.data, input.getPosition());
    styleInput.setPosition(initialInputPosition);
    String selector = parseSelector(styleInput, stringBuilder);
    if (selector == null || !BLOCK_START.equals(parseNextToken(styleInput, stringBuilder))) {
      return null;
    }
    WebvttCssStyle style = new WebvttCssStyle();
    applySelectorToStyle(style, selector);
    String token = null;
    boolean blockEndFound = false;
    while (!blockEndFound) {
      int position = styleInput.getPosition();
      token = parseNextToken(styleInput, stringBuilder);
      blockEndFound = token == null || BLOCK_END.equals(token);
      if (!blockEndFound) {
        styleInput.setPosition(position);
        parseStyleDeclaration(styleInput, style, stringBuilder);
      }
    }
    return BLOCK_END.equals(token) ? style : null; // Check that the style block ended correctly.
  }

  /**
   * Returns a string containing the selector. The input is expected to have the form
   * {@code ::cue(tag#id.class1.class2[voice="someone"]}, where every element is optional.
   *
   * @param input From which the selector is obtained.
   * @return A string containing the target, empty string if the selector is universal
   *     (targets all cues) or null if an error was encountered.
   */
  private static String parseSelector(ParsableByteArray input, StringBuilder stringBuilder) {
    skipWhitespaceAndComments(input);
    if (input.bytesLeft() < 5) {
      return null;
    }
    String cueSelector = input.readString(5);
    if (!"::cue".equals(cueSelector)) {
      return null;
    }
    int position = input.getPosition();
    String token = parseNextToken(input, stringBuilder);
    if (token == null) {
      return null;
    }
    if (BLOCK_START.equals(token)) {
      input.setPosition(position);
      return "";
    }
    String target = null;
    if ("(".equals(token)) {
      target = readCueTarget(input);
    }
    token = parseNextToken(input, stringBuilder);
    if (!")".equals(token) || token == null) {
      return null;
    }
    return target;
  }

  /**
   * Reads the contents of ::cue() and returns it as a string.
   */
  private static String readCueTarget(ParsableByteArray input) {
    int position = input.getPosition();
    int limit = input.limit();
    boolean cueTargetEndFound = false;
    while (position < limit && !cueTargetEndFound) {
      char c = (char) input.data[position++];
      cueTargetEndFound = c == ')';
    }
    return input.readString(--position - input.getPosition()).trim();
    // --offset to return ')' to the input.
  }

  private static void parseStyleDeclaration(ParsableByteArray input, WebvttCssStyle style,
      StringBuilder stringBuilder) {
    skipWhitespaceAndComments(input);
    String property = parseIdentifier(input, stringBuilder);
    if ("".equals(property)) {
      return;
    }
    if (!":".equals(parseNextToken(input, stringBuilder))) {
      return;
    }
    skipWhitespaceAndComments(input);
    String value = parsePropertyValue(input, stringBuilder);
    if (value == null || "".equals(value)) {
      return;
    }
    int position = input.getPosition();
    String token = parseNextToken(input, stringBuilder);
    if (";".equals(token)) {
      // The style declaration is well formed.
    } else if (BLOCK_END.equals(token)) {
      // The style declaration is well formed and we can go on, but the closing bracket had to be
      // fed back.
      input.setPosition(position);
    } else {
      // The style declaration is not well formed.
      return;
    }
    // At this point we have a presumably valid declaration, we need to parse it and fill the style.
    if ("color".equals(property)) {
      style.setFontColor(ColorParser.parseCssColor(value));
    } else if (PROPERTY_BGCOLOR.equals(property)) {
      style.setBackgroundColor(ColorParser.parseCssColor(value));
    } else if (PROPERTY_TEXT_DECORATION.equals(property)) {
      if (VALUE_UNDERLINE.equals(value)) {
        style.setUnderline(true);
      }
    } else if (PROPERTY_FONT_FAMILY.equals(property)) {
      style.setFontFamily(value);
    } else if (PROPERTY_FONT_WEIGHT.equals(property)) {
      if (VALUE_BOLD.equals(value)) {
        style.setBold(true);
      }
    } else if (PROPERTY_FONT_STYLE.equals(property)) {
      if (VALUE_ITALIC.equals(value)) {
        style.setItalic(true);
      }
    }
    // TODO: Fill remaining supported styles.
  }

  // Visible for testing.
  /* package */ static void skipWhitespaceAndComments(ParsableByteArray input) {
    boolean skipping = true;
    while (input.bytesLeft() > 0 && skipping) {
      skipping = maybeSkipWhitespace(input) || maybeSkipComment(input);
    }
  }

  // Visible for testing.
  /* package */ static String parseNextToken(ParsableByteArray input, StringBuilder stringBuilder) {
    skipWhitespaceAndComments(input);
    if (input.bytesLeft() == 0) {
      return null;
    }
    String identifier = parseIdentifier(input, stringBuilder);
    if (!"".equals(identifier)) {
      return identifier;
    }
    // We found a delimiter.
    return "" + (char) input.readUnsignedByte();
  }

  private static boolean maybeSkipWhitespace(ParsableByteArray input) {
    switch(peekCharAtPosition(input, input.getPosition())) {
      case '\t':
      case '\r':
      case '\n':
      case '\f':
      case ' ':
        input.skipBytes(1);
        return true;
      default:
        return false;
    }
  }

  // Visible for testing.
  /* package */ static void skipStyleBlock(ParsableByteArray input) {
    // The style block cannot contain empty lines, so we assume the input ends when a empty line
    // is found.
    String line;
    do {
      line = input.readLine();
    } while (!TextUtils.isEmpty(line));
  }

  private static char peekCharAtPosition(ParsableByteArray input, int position) {
    return (char) input.data[position];
  }

  private static String parsePropertyValue(ParsableByteArray input, StringBuilder stringBuilder) {
    StringBuilder expressionBuilder = new StringBuilder();
    String token;
    int position;
    boolean expressionEndFound = false;
    // TODO: Add support for "Strings in quotes with spaces".
    while (!expressionEndFound) {
      position = input.getPosition();
      token = parseNextToken(input, stringBuilder);
      if (token == null) {
        // Syntax error.
        return null;
      }
      if (BLOCK_END.equals(token) || ";".equals(token)) {
        input.setPosition(position);
        expressionEndFound = true;
      } else {
        expressionBuilder.append(token);
      }
    }
    return expressionBuilder.toString();
  }

  private static boolean maybeSkipComment(ParsableByteArray input) {
    int position = input.getPosition();
    int limit = input.limit();
    byte[] data = input.data;
    if (position + 2 <= limit && data[position++] == '/' && data[position++] == '*') {
      while (position + 1 < limit) {
        char skippedChar = (char) data[position++];
        if (skippedChar == '*') {
          if (((char) data[position]) == '/') {
            position++;
            limit = position;
          }
        }
      }
      input.skipBytes(limit - input.getPosition());
      return true;
    }
    return false;
  }

  private static String parseIdentifier(ParsableByteArray input, StringBuilder stringBuilder) {
    stringBuilder.setLength(0);
    int position = input.getPosition();
    int limit = input.limit();
    boolean identifierEndFound = false;
    while (position  < limit && !identifierEndFound) {
      char c = (char) input.data[position];
      if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '#'
          || c == '-' || c == '.' || c == '_') {
        position++;
        stringBuilder.append(c);
      } else {
        identifierEndFound = true;
      }
    }
    input.skipBytes(position - input.getPosition());
    return stringBuilder.toString();
  }

  /**
   * Sets the target of a {@link WebvttCssStyle} by splitting a selector of the form
   * {@code ::cue(tag#id.class1.class2[voice="someone"]}, where every element is optional.
   */
  private void applySelectorToStyle(WebvttCssStyle style, String selector) {
    if ("".equals(selector)) {
      return; // Universal selector.
    }
    int voiceStartIndex = selector.indexOf('[');
    if (voiceStartIndex != -1) {
      Matcher matcher = VOICE_NAME_PATTERN.matcher(selector.substring(voiceStartIndex));
      if (matcher.matches()) {
        style.setTargetVoice(matcher.group(1));
      }
      selector = selector.substring(0, voiceStartIndex);
    }
    String[] classDivision = selector.split("\\.");
    String tagAndIdDivision = classDivision[0];
    int idPrefixIndex = tagAndIdDivision.indexOf('#');
    if (idPrefixIndex != -1) {
      style.setTargetTagName(tagAndIdDivision.substring(0, idPrefixIndex));
      style.setTargetId(tagAndIdDivision.substring(idPrefixIndex + 1)); // We discard the '#'.
    } else {
      style.setTargetTagName(tagAndIdDivision);
    }
    if (classDivision.length > 1) {
      style.setTargetClasses(Arrays.copyOfRange(classDivision, 1, classDivision.length));
    }
  }

}
