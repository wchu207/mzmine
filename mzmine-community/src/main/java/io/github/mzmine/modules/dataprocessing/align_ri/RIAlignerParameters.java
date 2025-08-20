/*
 * Copyright (c) 2004-2024 The MZmine Development Team
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

import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter;
import io.github.mzmine.parameters.parametertypes.StringParameter;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsParameter;
import io.github.mzmine.parameters.parametertypes.submodules.ModuleOptionsEnumComboParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.RIToleranceParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.ToleranceType;
import io.github.mzmine.util.scans.similarity.SpectralSimilarityFunctions;

public class RIAlignerParameters extends SimpleParameterSet {

  public static final FeatureListsParameter FEATURE_LISTS = new FeatureListsParameter();

  public static final MZToleranceParameter MZ_TOLERANCE = new MZToleranceParameter(
      ToleranceType.SCAN_TO_SCAN);

  public static final RIToleranceParameter RI_TOLERANCE = new RIToleranceParameter();

  public static final DoubleParameter RI_WEIGHT = new DoubleParameter("Weight for RI", """
      Score for perfectly matching RI values. The RI delta is divided by the RI tolerance and scaled 0-1.
      The weight then multiplies this score so that higher RI delta give a penalty.
      The RI weight shifts the focus on RI or spectral similarity (minimum similarity-1).""",
      MZmineCore.getConfiguration().getGuiFormats().scoreFormat(), 0.5d);

  public static final ModuleOptionsEnumComboParameter<SpectralSimilarityFunctions> SIMILARITY_FUNCTION = new ModuleOptionsEnumComboParameter<>(
      "Similarity", "Algorithm to calculate spectral similarity between to samples",
      SpectralSimilarityFunctions.NIST_COMPOSITE_COSINE);

  public static final StringParameter FEATURE_LIST_NAME = new StringParameter("Feature list name",
      "Feature list name", "Aligned feature list");


  public static final OriginalFeatureListHandlingParameter handleOriginal = new OriginalFeatureListHandlingParameter(
      false);

  public RIAlignerParameters() {
    super(new Parameter[]{FEATURE_LISTS, MZ_TOLERANCE, RI_TOLERANCE, RI_WEIGHT, SIMILARITY_FUNCTION,
            FEATURE_LIST_NAME, handleOriginal},
        "https://mzmine.github.io/mzmine_documentation/module_docs/align_gcei/align_gc_ei.html");
  }
}
