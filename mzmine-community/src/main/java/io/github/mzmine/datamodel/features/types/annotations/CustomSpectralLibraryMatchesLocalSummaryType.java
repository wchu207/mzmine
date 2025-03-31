package io.github.mzmine.datamodel.features.types.annotations;

import io.github.mzmine.datamodel.features.*;
import io.github.mzmine.datamodel.features.types.abstr.StringType;
import io.github.mzmine.datamodel.features.types.modifiers.AnnotationType;
import io.github.mzmine.util.spectraldb.entry.SpectralDBAnnotation;
import javafx.util.Pair;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static io.github.mzmine.datamodel.features.types.DataTypes.get;
import io.github.mzmine.datamodel.features.types.modifiers.BindingsType;

public class CustomSpectralLibraryMatchesLocalSummaryType extends StringType implements AnnotationType {

  @NotNull
  public final String getUniqueID() {
    // Never change the ID for compatibility during saving/loading of type
    return "library_matches";
  }

  public @NotNull String getHeaderString() {
    return "Library matches";
  }

  public List<RowBinding> createDefaultRowBindings() {
    return List.of(
        new SimpleRowBinding(this, get(SpectralLibraryMatchesType.class), BindingsType.LIST)
    );
  }

  private List<String> extractCompounds(String compounds, Set<String> targets) {
    List<String> discoveredCompounds = new ArrayList<>();
    String[] tokens = compounds.split(";");
    for (var token : tokens) {
      String compoundName = token.substring(0, token.indexOf("(")).trim();
      if (targets == null || targets.contains(compoundName.toLowerCase())) {
        try {
          discoveredCompounds.add(compoundName);
        } catch (Exception e) {
        }
      }
    }
    return discoveredCompounds;
  }

  private Map<String, Integer> extractCompoundCounts(String compounds, Set<String> targets) {
    Map<String, Integer> discoveredCompounds = new HashMap<>();
    String[] tokens = compounds.split(";");
    for (var token : tokens) {
      String compoundName = token.substring(0, token.indexOf("(")).trim();
      if (targets == null || targets.contains(compoundName.toLowerCase())) {
        try {
          String count = StringUtils.substringBetween(token, "(", ")");
          discoveredCompounds.put(compoundName.toLowerCase(), Integer.parseInt(count));
        } catch (Exception e) {
        }
      }
    }
    return discoveredCompounds;
  }

  public Object evaluateBindings(@NotNull BindingsType bindingType,
                                 @NotNull List<? extends ModularDataModel> models) {
    if (bindingType == BindingsType.LIST && !models.isEmpty() && models.getFirst() instanceof ModularFeature){
      ModularFeatureList flist = (ModularFeatureList) ((ModularFeature) models.getFirst()).getFeatureList();
      ModularFeatureListRow thisRow = (ModularFeatureListRow) ((ModularFeature) models.getFirst()).getRow();
      String localCompoundsString = thisRow.get(CustomSpectralLibraryMatchesLocalSummaryType.class);
      if (localCompoundsString != null) {
        Map<String, Integer> localCompounds = extractCompoundCounts(localCompoundsString, null);
        Map<String, Integer> globalCompounds = new HashMap<>();
        for (var cmpd : localCompounds.keySet()) {
          globalCompounds.put(cmpd, 0);
        }

        for (var row : flist.getRows()) {
          String currentCompoundsString = row.get(CustomSpectralLibraryMatchesLocalSummaryType.class);
          if (currentCompoundsString != null) {
            Map<String, Integer> currentCompounds = extractCompoundCounts(currentCompoundsString, globalCompounds.keySet());
            for (var cmpd : currentCompounds.keySet()) {
              globalCompounds.put(cmpd, globalCompounds.get(cmpd) + currentCompounds.get(cmpd));
            }
          }

        }

        Map<String, Float> percentagesMap = new HashMap<>();
        for (var cmpd : localCompounds.keySet()) {
          percentagesMap.put(cmpd, localCompounds.get(cmpd) / (float) globalCompounds.get(cmpd));
        }
        List<String> compoundNames = extractCompounds(localCompoundsString, globalCompounds.keySet());
        List<String> majorCompounds = percentagesMap.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getValue, Comparator.reverseOrder()))
                .map(entry -> {
                  Optional<String> name = compoundNames.stream().filter(s -> s.toLowerCase().equals(entry.getKey())).findFirst();
                  return name.get() + String.format(" (%.2f%%)", entry.getValue() * 100);
                })
                .toList();
        return String.join("; ", majorCompounds);

      }

      return null;

    } else {
      return super.evaluateBindings(bindingType, models);
    }
  }
}
