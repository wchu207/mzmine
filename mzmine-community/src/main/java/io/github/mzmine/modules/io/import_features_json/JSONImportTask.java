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

package io.github.mzmine.modules.io.import_features_json;

import com.google.common.collect.Range;
import com.google.common.primitives.Doubles;
import de.isas.mztab2.io.MzTabFileParser;
import de.isas.mztab2.model.*;
import io.github.mzmine.datamodel.*;
import io.github.mzmine.datamodel.featuredata.impl.SimpleIonTimeSeries;
import io.github.mzmine.datamodel.features.*;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.numbers.IntensityRangeType;
import io.github.mzmine.datamodel.features.types.numbers.MZRangeType;
import io.github.mzmine.datamodel.features.types.numbers.RTRangeType;
import io.github.mzmine.datamodel.impl.SimpleDataPoint;
import io.github.mzmine.datamodel.impl.SimpleFeatureIdentity;
import io.github.mzmine.datamodel.impl.SimplePseudoSpectrum;
import io.github.mzmine.datamodel.msms.MsMsInfo;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.visualization.spectra.simplespectra.datapointprocessing.datamodel.MSLevel;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.UserParameter;
import io.github.mzmine.parameters.parametertypes.StringParameter;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.RawDataFileUtils;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.ac.ebi.pride.jmztab2.utils.errors.MZTabErrorList;
import uk.ac.ebi.pride.jmztab2.utils.errors.MZTabErrorType;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JSONImportTask extends AbstractTask {

  // parameter values
  private final MZmineProject project;
  private final ParameterSet parameters;
  private final File inputFile;
  private double finishedPercentage = 0.0;
  private final Map<String, Class<? extends DataType<?>>> keyMap = null;
  private RawDataFile rawDataFile = null;
  private ModularFeatureList featureList = null;


  JSONImportTask(MZmineProject project, ParameterSet parameters, File inputFile,
                 @Nullable MemoryMapStorage storage, @NotNull Instant moduleCallDate) {
    super(storage, moduleCallDate);
    this.project = project;
    this.parameters = parameters;
    this.inputFile = inputFile;
  }

  @Override
  public String getTaskDescription() {
    return "Loading feature list from JSON file";
  }

  @Override
  public void cancel() {
    super.cancel();

  }

  @Override
  public double getFinishedPercentage() {
    return finishedPercentage;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    try {

      List<String> lines = Files.readAllLines(inputFile.toPath());

      if (!lines.isEmpty()) {
        rawDataFile = project.getDataFileByName((new JSONObject(lines.get(0))).get("raw_file_name").toString());
        if (rawDataFile != null) {
          boolean success = initializeFeatureList(rawDataFile, this.storage);
          if (success) {
            List<ModularFeatureListRow> rows = lines.parallelStream().map(this::parseLine).toList();
            // Add the new feature list to the project
            project.addFeatureList(featureList);
          }
        }

      }


      // Finish
      setStatus(TaskStatus.FINISHED);
      finishedPercentage = 1.0;


    } catch (Exception e) {
      e.printStackTrace();
      setStatus(TaskStatus.ERROR);
      setErrorMessage("Could not import data from " + inputFile + ": " + e.getMessage());
    }
  }

  private boolean initializeFeatureList(RawDataFile rawDataFile, MemoryMapStorage storage) {
    try {
      featureList = new ModularFeatureList(rawDataFile.getName(), storage, rawDataFile);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
  private ModularFeatureListRow parseLine(String line) {
    try {
      JSONObject rowAsJSON = new JSONObject(line);

      List<Double> mzList = new ArrayList<Double>();
      List <Double> intensityList = new ArrayList<Double>();
      JSONArray peaksJSON = rowAsJSON.getJSONArray("peaks");
      for (int i = 0; i < peaksJSON.length(); i++) {
        mzList.add((peaksJSON.getJSONArray(i).getDouble(0)));
        intensityList.add((peaksJSON.getJSONArray(i).getDouble(1)));
      }

      int msLevel = rowAsJSON.getInt("ms_level");
      double rt = rowAsJSON.getDouble("rt");

      PolarityType polarity = PolarityType.parseFromString(rowAsJSON.getString("polarity"));
      FeatureStatus status = FeatureStatus.valueOf(rowAsJSON.getString("status"));
      String scanDefinition = rowAsJSON.getString("scan_definition");
      PseudoSpectrumType pseudoType = PseudoSpectrumType.valueOf(rowAsJSON.getString("pseudospectrum"));

      Scan scan = new SimplePseudoSpectrum(rawDataFile, msLevel, (float) rt, null, Doubles.toArray(mzList), Doubles.toArray(intensityList), polarity, scanDefinition, pseudoType);

      SimpleIonTimeSeries ions = new SimpleIonTimeSeries(storage, Doubles.toArray(mzList), Doubles.toArray(intensityList), List.of(scan));
      ModularFeature feature = new ModularFeature(featureList, rawDataFile, ions, status);

      double mz = rowAsJSON.getDouble("mz");
      double height = rowAsJSON.getDouble("height");
      double area = rowAsJSON.getDouble("area");

      JSONArray rtRangeArray = rowAsJSON.getJSONArray("rt_range");
      Range<Float> rtRange = Range.closed(rtRangeArray.getFloat(0), rtRangeArray.getFloat(1));
      feature.set(RTRangeType.class, rtRange);

      JSONArray mzRangeArray = rowAsJSON.getJSONArray("mz_range");
      Range<Double> mzRange = Range.closed(mzRangeArray.getDouble(0), mzRangeArray.getDouble(1));
      feature.set(MZRangeType.class, mzRange);

      JSONArray intensityRangeArray = rowAsJSON.getJSONArray("intensity_range");
      Range<Float> intensityRange = Range.closed(intensityRangeArray.getFloat(0), intensityRangeArray.getFloat(1));
      feature.set(IntensityRangeType.class, intensityRange);
      return null;
    } catch (Exception e) {
      return null;
    }
  }

}
