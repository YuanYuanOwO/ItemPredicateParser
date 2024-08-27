package me.blvckbytes.storage_query.parse;

import me.blvckbytes.storage_query.token.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TokenParser {

  private static final char INTEGER_WILDCARD_CHAR = '*';

  public static List<Token> parseTokens(String[] args) {
    var result = new ArrayList<Token>();

    Token deferredToken = null;
    var stringBeginArgumentIndex = -1;
    var stringContents = new StringBuilder();

    for (var argumentIndex = 0; argumentIndex < args.length; ++argumentIndex) {
      if (deferredToken != null) {
        result.add(deferredToken);
        deferredToken = null;
      }

      var arg = args[argumentIndex];
      var argLength = arg.length();

      if (argLength == 0) {
        result.add(new UnquotedStringToken(argumentIndex, ""));
        continue;
      }

      var firstChar = arg.charAt(0);
      var lastChar = arg.charAt(argLength - 1);

      if (firstChar == '(' && stringBeginArgumentIndex < 0) {
        result.add(new ParenthesisToken(argumentIndex, true));

        if (argLength == 1)
          continue;

        arg = arg.substring(1);
        firstChar = arg.charAt(0);
        --argLength;
      }

      if (
        lastChar == ')' &&
        // Either a multi-arg string hasn't begun yet, or the closing-paren is
        // prepended by a multi-arg string termination quote
        (stringBeginArgumentIndex < 0 || (argLength >= 2 && arg.charAt(argLength - 2) == '"'))
      ) {
        deferredToken = new ParenthesisToken(argumentIndex, false);

        if (argLength == 1)
          continue;

        arg = arg.substring(0, argLength - 1);
        lastChar = arg.charAt(argLength - 2);
        --argLength;
      }

      if (firstChar == '"') {
        var terminationIndex = arg.indexOf('"', 1);

        // Argument contains both the start- and end-marker
        if (terminationIndex > 0) {
          if (arg.indexOf('"', terminationIndex + 1) != -1)
            throw new ArgumentParseException(argumentIndex, ParseConflict.MALFORMED_STRING_ARGUMENT);

          result.add(new QuotedStringToken(argumentIndex, arg.substring(1, argLength - 1)));
          continue;
        }

        // Contains only one double-quote, which is leading

        // Terminated a string which contains a trailing whitespace (valid use-case)
        if (stringBeginArgumentIndex != -1) {
          if (argLength != 1)
            throw new ArgumentParseException(argumentIndex, ParseConflict.MALFORMED_STRING_ARGUMENT);

          stringContents.append(' ');
          result.add(new QuotedStringToken(stringBeginArgumentIndex, stringContents.toString()));
          stringBeginArgumentIndex = -1;
          stringContents.setLength(0);
          continue;
        }

        // Started a string which contains a leading whitespace (valid use-case)
        if (argLength == 1) {
          stringBeginArgumentIndex = argumentIndex;
          continue;
        }

        // Multi-arg string beginning
        stringBeginArgumentIndex = argumentIndex;
        stringContents.append(arg, 1, argLength);
        continue;
      }

      if (lastChar == '"') {
        if (stringBeginArgumentIndex == -1)
          throw new ArgumentParseException(argumentIndex, ParseConflict.MALFORMED_STRING_ARGUMENT);

        // Multi-arg string termination
        stringContents.append(' ').append(arg, 0, argLength - 1);
        result.add(new QuotedStringToken(stringBeginArgumentIndex, stringContents.toString()));
        stringBeginArgumentIndex = -1;
        stringContents.setLength(0);
        continue;
      }

      // Within string
      if (stringBeginArgumentIndex >= 0) {
        stringContents.append(' ').append(arg);
        continue;
      }

      if (firstChar == INTEGER_WILDCARD_CHAR && argLength == 1) {
        result.add(new IntegerToken(argumentIndex, null));
        continue;
      }

      // No names will have a leading digit; expect integer
      if (Character.isDigit(firstChar)) {
        var integerArgument = parseIntegerToken(arg, argumentIndex);

        if (integerArgument == null)
          throw new ArgumentParseException(argumentIndex, ParseConflict.EXPECTED_INTEGER);

        result.add(integerArgument);
        continue;
      }

      // Ensure that there are no quotes wedged into search-terms
      // At this point, first and last char have been checked against, so skip them
      for (var argIndex = 1; argIndex < argLength - 1; ++argIndex) {
        if (arg.charAt(argIndex) == '"')
          throw new ArgumentParseException(argumentIndex, ParseConflict.MALFORMED_STRING_ARGUMENT);
      }

      result.add(new UnquotedStringToken(argumentIndex, arg));
    }

    if (stringBeginArgumentIndex != -1)
      throw new ArgumentParseException(stringBeginArgumentIndex, ParseConflict.MISSING_STRING_TERMINATION);

    if (deferredToken != null)
      result.add(deferredToken);

    return result;
  }

  private static @Nullable IntegerToken parseIntegerToken(String arg, int argumentIndex) {
    var argLength = arg.length();

    var radixPower = 0;
    var blockCounter = 0;
    var currentNumber = 0;
    var resultingNumber = 0;

    for (var argIndex = argLength - 1; argIndex >= 0; --argIndex) {
      var argChar = arg.charAt(argIndex);

      if (argChar == ':') {
        radixPower = 0;
        resultingNumber += currentNumber * (int) Math.pow(60, blockCounter);
        currentNumber = 0;
        ++blockCounter;
        continue;
      }

      if (!(argChar >= '0' && argChar <= '9'))
        return null;

      currentNumber += (argChar - '0') * (int) Math.pow(10, radixPower);

      ++radixPower;
    }

    resultingNumber += currentNumber * (int) Math.pow(60, blockCounter);

    return new IntegerToken(argumentIndex, resultingNumber, blockCounter != 0);
  }
}