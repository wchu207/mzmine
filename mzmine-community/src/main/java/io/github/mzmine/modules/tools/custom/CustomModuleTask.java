/*
 * Copyright (c) 2004-2025 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.tools.custom;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.*;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.DataTypes;
import io.github.mzmine.datamodel.features.types.FeatureGroupType;
import io.github.mzmine.datamodel.features.types.FeaturesType;
import io.github.mzmine.datamodel.features.types.annotations.CommentType;
import io.github.mzmine.datamodel.features.types.annotations.CompoundDatabaseMatchesType;
import io.github.mzmine.datamodel.features.types.annotations.ManualAnnotationType;
import io.github.mzmine.datamodel.features.types.annotations.SpectralLibraryMatchesType;
import io.github.mzmine.datamodel.features.types.annotations.iin.IonIdentityListType;
import io.github.mzmine.datamodel.features.types.networking.NetworkStatsType;
import io.github.mzmine.datamodel.features.types.numbers.FragmentScanNumbersType;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractSimpleToolTask;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.maths.Precision;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralDBAnnotation;

import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CustomModuleTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(
      CustomModuleTask.class.getName());
  private final ModularFeatureList[] featureLists;

  public CustomModuleTask(final MZmineProject project, final ParameterSet parameters,
                          final Instant moduleCallDate,  final ModularFeatureList[] featureLists) {
    super(null, moduleCallDate); // no new data stored -> null
    this.featureLists = featureLists;
  }


  public void run() {
    List<FeatureListRow> rows = new ArrayList<FeatureListRow>();
    for (var flist : featureLists) {
      rows.addAll(flist.getRows());
    }

    SpectralLibraryMatchesType matchesType = DataTypes.get(SpectralLibraryMatchesType.class);
    for (var row : rows) {
      List<SpectralDBAnnotation> matches = row.get(DataTypes.get(SpectralLibraryMatchesType.class));
      if (matches != null) {
        for (var match : matches) {
          match.getEntry().putIfNotNull(DBEntryField.COMMENT, match.getEntry().getLibraryName());
        }
      }
    }

  }

  @Override
  public String getTaskDescription() {
    return "";
  }

  @Override
  public double getFinishedPercentage() {
    return 0;
  }
}
