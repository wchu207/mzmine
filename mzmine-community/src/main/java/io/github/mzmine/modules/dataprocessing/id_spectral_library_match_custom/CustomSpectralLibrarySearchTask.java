/*
 * Copyright (c) 2004-2022 The MZmine Development Team
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

package io.github.mzmine.modules.dataprocessing.id_spectral_library_match_custom;

import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.types.DataTypes;
import io.github.mzmine.datamodel.features.types.annotations.CustomSpectralLibraryMatchesGlobalSummaryType;
import io.github.mzmine.datamodel.features.types.annotations.CustomSpectralLibraryMatchesLocalSummaryType;
import io.github.mzmine.datamodel.features.types.annotations.SpectralLibraryMatchesType;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.TaskStatus;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

class CustomSpectralLibrarySearchTask extends CustomRowsSpectralMatchTask {

  private static final Logger logger = Logger.getLogger(CustomSpectralLibrarySearchTask.class.getName());
  private final FeatureList[] featureLists;

  public CustomSpectralLibrarySearchTask(ParameterSet parameters, FeatureList[] featureLists,
                                         @NotNull Instant moduleCallDate) {
    super(parameters, combineRows(featureLists), moduleCallDate);
    this.featureLists = featureLists;
  }

  public static List<Feature> combineRows(FeatureList[] featureLists) {
    List<Feature> rows = new ArrayList<>();
    // add row type
    for (var flist : featureLists) {
      flist.addFeatureType(DataTypes.get(CustomSpectralLibraryMatchesLocalSummaryType.class));
      flist.addFeatureType(DataTypes.get(SpectralLibraryMatchesType.class));
      for (var raw : flist.getRawDataFiles()) {
        rows.addAll(flist.getFeatures(raw));
      }
    }
    return rows;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    logger.info(() -> String
        .format("Spectral library matching in %d feature lists (%d rows) against libraries: %s",
            featureLists.length, features.size(), librariesJoined));

    // run the actual subtask
    super.run();

    // Add task description to peakList
    if (!isCanceled()) {
      for (var flist : featureLists) {
        flist.addDescriptionOfAppliedTask(new SimpleFeatureListAppliedMethod(
            "Spectral library matching with libraries: " + librariesJoined,
            CustomSpectralLibrarySearchModule.class, parameters, getModuleCallDate()));
      }

      setStatus(TaskStatus.FINISHED);
    }
  }

}
