package io.github.mzmine.datamodel.features.types.annotations;

import io.github.mzmine.datamodel.features.RowBinding;
import io.github.mzmine.datamodel.features.SimpleRowBinding;
import io.github.mzmine.datamodel.features.types.abstr.StringType;
import io.github.mzmine.datamodel.features.types.modifiers.AnnotationType;
import io.github.mzmine.datamodel.features.types.modifiers.BindingsType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static io.github.mzmine.datamodel.features.types.DataTypes.get;

public class CustomSpectralLibraryMatchesGlobalSummaryType extends StringType implements AnnotationType {

  @NotNull
  public final String getUniqueID() {
    // Never change the ID for compatibility during saving/loading of type
    return "major_library_matches";
  }

  public @NotNull String getHeaderString() {
    return "Major matches";
  }

  @Override
  public @NotNull List<RowBinding> createDefaultRowBindings() {
    return List.of(
        new SimpleRowBinding(this, get(CustomSpectralLibraryMatchesLocalSummaryType.class), BindingsType.LIST)
    );
  }
}
