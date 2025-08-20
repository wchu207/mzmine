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

package io.github.mzmine.modules.dataprocessing.align_ri;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.dataprocessing.align_common.BaseFeatureListAligner;
import io.github.mzmine.modules.dataprocessing.align_common.FeatureCloner;
import io.github.mzmine.modules.dataprocessing.align_common.FeatureCloner.ExtractMzMismatchFeatureCloner;
import io.github.mzmine.modules.dataprocessing.align_gc.GCConsensusAlignerPostProcessor;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
import io.github.mzmine.taskcontrol.AbstractFeatureListTask;
import io.github.mzmine.util.FeatureListRowSorter;
import io.github.mzmine.util.MemoryMapStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

public class RIAlignerTask extends AbstractFeatureListTask {

  private static final Logger logger = Logger.getLogger(RIAlignerTask.class.getName());


  /**
   * All feature lists except the base list
   */
  private final List<FeatureList> featureLists;
  private final MZmineProject project;
  private final String featureListName;
  private final ParameterSet parameters;
  private final boolean reuseOriginalFeatures;
  private ModularFeatureList alignedFeatureList;
  private final OriginalFeatureListOption handleOriginal;
  private BaseFeatureListAligner listAligner;

  public RIAlignerTask(MZmineProject project, @Nullable MemoryMapStorage storage,
                       @NotNull Instant moduleCallDate, @NotNull ParameterSet parameters,
                       @NotNull Class<? extends MZmineModule> moduleClass) {
    super(storage, moduleCallDate, parameters, moduleClass);
    this.project = project;
    handleOriginal = parameters.getValue(RIAlignerParameters.handleOriginal);
    reuseOriginalFeatures = handleOriginal != OriginalFeatureListOption.KEEP;

    featureListName = parameters.getValue(RIAlignerParameters.FEATURE_LIST_NAME);

    featureLists = Arrays.stream(
            parameters.getValue(RIAlignerParameters.FEATURE_LISTS).getMatchingFeatureLists())
        .sorted(Comparator.comparing(ModularFeatureList::toString))
        .map(flist -> (FeatureList) flist).toList();

    this.parameters = parameters;
  }

  @Override
  public double getFinishedPercentage() {
    if (listAligner == null) {
      return 0;
    }
    return listAligner.getFinishedPercentage();
  }

  @Override
  protected void process() {

    logger.info(() -> "Running parallel GC aligner on " + featureLists.size() + " feature lists.");

    var mzTolerance = parameters.getValue(RIAlignerParameters.MZ_TOLERANCE);
    FeatureCloner featureCloner = new ExtractMzMismatchFeatureCloner(mzTolerance);

    // create the row aligner that handles the scoring
    var postProcessor = new GCConsensusAlignerPostProcessor(mzTolerance);
    var rowAligner = new RIRowAlignScorer(parameters);
    listAligner = new BaseFeatureListAligner(this, featureLists, featureListName,
        getMemoryMapStorage(), rowAligner, featureCloner, FeatureListRowSorter.DEFAULT_RI, postProcessor);

    alignedFeatureList = listAligner.alignFeatureLists();
    if (alignedFeatureList == null || isCanceled()) {
      return;
    }

    handleOriginal.reflectNewFeatureListToProject(project, alignedFeatureList, featureLists);

    logger.info("Finished GC aligner");
  }


  @Override
  protected @NotNull List<FeatureList> getProcessedFeatureLists() {
    return List.of(alignedFeatureList);
  }

  @Override
  public String getTaskDescription() {
    return "Align RI feature lists";
  }
}