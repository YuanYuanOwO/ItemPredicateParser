package me.blvckbytes.storage_query.parse;

import me.blvckbytes.storage_query.token.IntegerToken;
import me.blvckbytes.storage_query.token.QuotedStringToken;
import me.blvckbytes.storage_query.token.Token;
import me.blvckbytes.storage_query.token.UnquotedStringToken;
import me.blvckbytes.storage_query.predicate.*;
import me.blvckbytes.storage_query.translation.DeteriorationKey;
import me.blvckbytes.storage_query.translation.TranslatedTranslatable;
import me.blvckbytes.storage_query.translation.TranslationRegistry;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PredicateParser {

  public static List<ItemPredicate> parsePredicates(List<Token> tokens, TranslationRegistry registry) {
    var result = new ArrayList<ItemPredicate>();
    var remainingTokens = new ArrayList<>(tokens);

    while (!remainingTokens.isEmpty()) {
      var currentToken = remainingTokens.removeFirst();

      if (currentToken instanceof QuotedStringToken textSearch) {
        result.add(new TextSearchPredicate(textSearch.value()));
        continue;
      }

      if (!(currentToken instanceof UnquotedStringToken translationSearch))
        throw new ArgumentParseException(currentToken.commandArgumentIndex(), ParseConflict.EXPECTED_SEARCH_PATTERN);

      var searchString = translationSearch.value();

      if (searchString.isEmpty())
        continue;

      var searchResult = registry.search(searchString);

      if (searchResult.wildcardPresence() == SearchWildcardPresence.CONFLICT_OCCURRED_REPEATEDLY)
        throw new ArgumentParseException(currentToken.commandArgumentIndex(), ParseConflict.MULTIPLE_SEARCH_PATTERN_WILDCARDS);

      var searchResultEntries = searchResult.result();

      // Wildcards may only apply to materials, not only because that's the only place where they make sense, but
      // because otherwise, predicate-ambiguity would arise.
      if (searchResult.wildcardPresence() == SearchWildcardPresence.PRESENT) {
        var materials = new ArrayList<Material>();

        for (var resultEntry : searchResultEntries) {
          if (resultEntry.translatable() instanceof Material material)
            materials.add(material);
        }

        if (materials.isEmpty())
          throw new ArgumentParseException(currentToken.commandArgumentIndex(), ParseConflict.NO_SEARCH_MATCH);

        result.add(new MaterialPredicate(null, translationSearch, materials));
        continue;
      }

      var shortestMatch = getShortestMatch(searchResultEntries);

      if (shortestMatch == null)
        throw new ArgumentParseException(currentToken.commandArgumentIndex(), ParseConflict.NO_SEARCH_MATCH);

      if (shortestMatch.translatable() instanceof Material predicateMaterial) {
        result.add(new MaterialPredicate(shortestMatch, translationSearch, List.of(predicateMaterial)));
        continue;
      }

      if (shortestMatch.translatable() instanceof Enchantment predicateEnchantment) {
        IntegerToken enchantmentLevel = tryConsumeIntegerArgument(remainingTokens);
        throwOnTimeNotation(enchantmentLevel);

        result.add(new EnchantmentPredicate(shortestMatch, predicateEnchantment, enchantmentLevel));
        continue;
      }

      if (shortestMatch.translatable() instanceof PotionEffectType predicatePotionEffect) {
        IntegerToken potionEffectAmplifier = tryConsumeIntegerArgument(remainingTokens);
        throwOnTimeNotation(potionEffectAmplifier);

        IntegerToken potionEffectDuration = tryConsumeIntegerArgument(remainingTokens);
        result.add(new PotionEffectPredicate(shortestMatch, predicatePotionEffect, potionEffectAmplifier, potionEffectDuration));
        continue;
      }

      if (shortestMatch.translatable() instanceof DeteriorationKey) {
        IntegerToken deteriorationPercentageMin = tryConsumeIntegerArgument(remainingTokens);
        throwOnTimeNotation(deteriorationPercentageMin);

        IntegerToken deteriorationPercentageMax = tryConsumeIntegerArgument(remainingTokens);
        throwOnTimeNotation(deteriorationPercentageMax);

        result.add(new DeteriorationPredicate(shortestMatch, deteriorationPercentageMin, deteriorationPercentageMax));
        continue;
      }

      throw new ArgumentParseException(currentToken.commandArgumentIndex(), ParseConflict.UNIMPLEMENTED_TRANSLATABLE);
    }

    return result;
  }

  private static void throwOnTimeNotation(@Nullable IntegerToken token) {
    if (token == null)
      return;

    if (!token.wasTimeNotation())
      return;

    throw new ArgumentParseException(token.commandArgumentIndex(), ParseConflict.DOES_NOT_ACCEPT_TIME_NOTATION);
  }

  private static @Nullable IntegerToken tryConsumeIntegerArgument(List<Token> tokens) {
    IntegerToken integerToken = null;

    if (!tokens.isEmpty()) {
      var nextToken = tokens.getFirst();

      if (nextToken instanceof IntegerToken argument) {
        integerToken = argument;
        tokens.removeFirst();
      }
    }

    return integerToken;
  }

  private static @Nullable TranslatedTranslatable getShortestMatch(List<TranslatedTranslatable> matches) {
    if (matches.isEmpty())
      return null;

    var numberOfMatches = matches.size();

    if (numberOfMatches == 1)
      return matches.getFirst();

    var shortestMatchLength = Integer.MAX_VALUE;
    var shortestMatchIndex = 0;

    for (var matchIndex = 0; matchIndex < numberOfMatches; ++matchIndex) {
      var currentLength = matches.get(matchIndex).translation().length();

      if (currentLength < shortestMatchLength) {
        shortestMatchLength = currentLength;
        shortestMatchIndex = matchIndex;
      }
    }

    return matches.get(shortestMatchIndex);
  }
}
