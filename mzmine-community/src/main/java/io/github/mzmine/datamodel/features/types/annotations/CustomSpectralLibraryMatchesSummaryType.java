package io.github.mzmine.datamodel.features.types.annotations;

import io.github.mzmine.datamodel.features.RowBinding;
import io.github.mzmine.datamodel.features.SimpleRowBinding;
import io.github.mzmine.datamodel.features.types.abstr.StringType;
import io.github.mzmine.datamodel.features.types.modifiers.AnnotationType;
import io.github.mzmine.datamodel.features.types.numbers.RIType;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.List;

import static io.github.mzmine.datamodel.features.types.DataTypes.get;
import io.github.mzmine.datamodel.features.types.modifiers.BindingsType;

public class CustomSpectralLibraryMatchesSummaryType extends StringType implements AnnotationType {

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
}
